package com.doormate.app;

import android.app.*;import android.content.*;import android.os.*;import android.telephony.*;
import com.doormate.app.core.*;
import org.json.*;import java.util.*;import java.util.concurrent.*;

public class AgentService extends Service {
    private ScheduledExecutorService worker;
    private TelephonyManager phone;
    private volatile int callState=TelephonyManager.CALL_STATE_IDLE;
    private volatile boolean stateKnown=false;
    private String current="",remote="";
    private long began=0,lastAction=0;
    private final Map<String,Integer> attempts=new HashMap<>();
    private Conversation conversation;
    private final PhoneStateListener listener=new PhoneStateListener(){
        @Override public void onCallStateChanged(int state,String unused){
            int previous=callState;callState=state;stateKnown=true;
            // Initial listener IDLE must not cancel a newly screened incoming call.
            if(state==TelephonyManager.CALL_STATE_IDLE && previous!=TelephonyManager.CALL_STATE_IDLE){Store.session.stop();Store.status="Call ended";}
        }
    };
    @Override public void onCreate(){
        super.onCreate();Store.running=true;
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("calls","DoorMate calls",NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,AgentService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent open=PendingIntent.getActivity(this,2,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(7,new Notification.Builder(this,"calls").setSmallIcon(R.drawable.icon).setContentTitle("DoorMate call assistant")
            .setContentText("Approved numbers only · device compatibility unverified").setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null,"Stop",stop).build()).build());
        phone=getSystemService(TelephonyManager.class);
        try{phone.listen(listener,PhoneStateListener.LISTEN_CALL_STATE);}catch(SecurityException e){Store.pause(this);stopSelf();return;}
        BridgeClient.connect(this);worker=Executors.newSingleThreadScheduledExecutor();worker.scheduleWithFixedDelay(this::tick,500,800,TimeUnit.MILLISECONDS);
    }
    @Override public int onStartCommand(Intent i,int f,int id){if(i!=null&&"stop".equals(i.getAction())){Store.pause(this);stopSelf();}return START_NOT_STICKY;}
    @Override public IBinder onBind(Intent i){return null;}
    private void tick(){try{
        long now=SystemClock.elapsedRealtime();
        if(!Store.armed(this)||!Store.session.active(now)){
            if(!remote.isEmpty()&&BridgeClient.binder!=null){BridgeClient.request(BridgeClient.op("stop"));remote="";}
            return;
        }
        if(!stateKnown || callState==TelephonyManager.CALL_STATE_IDLE)return;
        if(BridgeClient.binder==null){Store.status="Phone connection unavailable; call left to you";Store.session.stop();return;}
        String token=Store.session.token();
        if(!token.equals(current)){
            current=token;began=now;attempts.clear();
            conversation=new Conversation(Store.session.instructions(),Store.model);
            JSONObject armed=BridgeClient.request(BridgeClient.op("arm").put("token",token));
            if(!armed.optBoolean("ok"))throw new IllegalStateException("Could not authorize phone bridge");remote=token;
        }
        JSONObject snap=BridgeClient.request(BridgeClient.op("snapshot").put("token",token));
        if(!snap.optBoolean("ok"))throw new IllegalStateException(snap.optString("error"));
        JSONArray raw=snap.optJSONArray("nodes");if(raw==null)return;
        List<ScreenParser.Node> nodes=decode(raw);Store.report=report(nodes);
        ScreenParser.Mapping map=ScreenParser.parse(nodes);
        if(map==null){
            // Once conversation UI is visible, never keep tapping call controls.
            boolean editor=false;for(ScreenParser.Node n:nodes)editor|=n.editable;
            if(!editor&&now-lastAction>1800){
                String label=ScreenParser.nextAction(nodes);
                int count=attempts.getOrDefault(label,0);
                if(!label.isEmpty()&&count<2){
                    BridgeClient.request(BridgeClient.op("pickup").put("token",token).put("label",label));
                    attempts.put(label,count+1);lastAction=now;
                }
            }
            Store.status=editor?"Waiting for a recognizable caller message…":"Opening Samsung call assistant…";
            if(now-began>30000){Store.session.stop();Store.status="Phone layout not supported yet. Share the diagnostic from Setup.";}
            return;
        }
        if(!Store.session.claimStable(map.fingerprint,now))return;
        Store.status="Listening understood · generating a local reply…";
        long started=SystemClock.elapsedRealtime();String answer=conversation.reply(map.callerText);
        if(!Store.armed(this)||!Store.session.valid(token,SystemClock.elapsedRealtime()))return;
        if(SystemClock.elapsedRealtime()-started>20000){Store.session.stop();Store.status="AI took over 20 seconds; reply discarded";return;}
        JSONObject result=BridgeClient.request(BridgeClient.op("send").put("token",token).put("callerId",map.callerId).put("scope",map.callerScope)
            .put("expected",map.callerText).put("editId",map.editId).put("sendId",map.sendId).put("text",answer));
        if(result.optBoolean("ok")){
            Store.session.sent(token,SystemClock.elapsedRealtime());Store.put(this,"last_reply",answer);Store.status="Reply submitted to Samsung · "+Store.session.turns()+" turns";
        }else {Store.session.stop();Store.status="Stopped: "+result.optString("error");}
    }catch(Exception e){Store.session.stop();Store.status="Call automation stopped: "+e.getClass().getSimpleName();}}
    static List<ScreenParser.Node> decode(JSONArray a)throws JSONException{
        List<ScreenParser.Node> out=new ArrayList<>();
        for(int i=0;i<a.length();i++){JSONObject n=a.getJSONObject(i);List<String> anc=new ArrayList<>();JSONArray ar=n.optJSONArray("anc");if(ar!=null)for(int j=0;j<ar.length();j++)anc.add(ar.optString(j));out.add(new ScreenParser.Node(n.optString("id"),n.optString("text"),n.optString("desc"),anc,n.optBoolean("edit"),n.optBoolean("click"),n.optInt("index")));}return out;
    }
    static String report(List<ScreenParser.Node> nodes){
        StringBuilder b=new StringBuilder("DoorMate 0.3 research\n"+Build.MODEL+" Android "+Build.VERSION.RELEASE+"\nBuild "+Build.DISPLAY+"\nOnly structural IDs, no conversation text:\n");
        for(ScreenParser.Node n:nodes)b.append(n.id).append(" editable=").append(n.editable).append(" clickable=").append(n.clickable).append(" ancestors=").append(n.ancestors).append('\n');return b.toString();
    }
    @Override public void onDestroy(){Store.running=false;Store.pause(this);if(worker!=null)worker.shutdownNow();if(phone!=null)phone.listen(listener,PhoneStateListener.LISTEN_NONE);BridgeClient.close();super.onDestroy();}
}
