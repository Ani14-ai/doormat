package com.doormate.app;
import android.content.*;import android.os.*;import org.json.*;import rikka.shizuku.Shizuku;
final class BridgeClient {
 static volatile IBinder binder;static volatile String error="Shizuku not connected";
 static Shizuku.UserServiceArgs args;static final ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){binder=b;error="";}public void onServiceDisconnected(ComponentName n){binder=null;error="Phone bridge disconnected";Store.session.stop();}};
 static synchronized void connect(Context c){try{if(!Shizuku.pingBinder()||Shizuku.checkSelfPermission()!=0){error="Start Shizuku and authorize DoorMate";return;}if(binder!=null&&binder.isBinderAlive())return;args=new Shizuku.UserServiceArgs(new ComponentName(c,PhoneBridge.class)).daemon(false).processNameSuffix("phone_bridge").debuggable(false).version(3);Shizuku.bindUserService(args,connection);}catch(Exception e){error="Shizuku connection failed: "+e.getClass().getSimpleName();}}
 static JSONObject request(JSONObject q)throws Exception{IBinder b=binder;if(b==null||!b.isBinderAlive())throw new IllegalStateException("Phone bridge not connected");Parcel in=Parcel.obtain(),out=Parcel.obtain();try{in.writeInterfaceToken(PhoneBridge.DESCRIPTOR);in.writeString(q.toString());if(!b.transact(1,in,out,0))throw new IllegalStateException("Unsupported bridge");out.readException();return new JSONObject(out.readString());}finally{in.recycle();out.recycle();}}
 static JSONObject op(String op)throws JSONException{return new JSONObject().put("op",op);}
 static void close(){try{if(args!=null)Shizuku.unbindUserService(args,connection,true);}catch(Exception ignored){}binder=null;}
}
