package com.doormate.app;

import android.app.*;import android.app.role.RoleManager;import android.os.*;import android.content.*;import android.content.pm.PackageManager;
import android.graphics.Color;import android.graphics.Typeface;import android.graphics.drawable.GradientDrawable;import android.net.Uri;
import android.view.*;import android.widget.*;import android.speech.RecognizerIntent;import android.speech.tts.TextToSpeech;
import com.doormate.app.core.*;import org.json.*;import rikka.shizuku.Shizuku;
import java.util.*;import java.util.concurrent.*;

public class MainActivity extends Activity {
    private final int teal=0xff126a60,ink=0xff19352f,muted=0xff59716b;
    private LinearLayout body,chat;private TextView status;private EditText message;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile Conversation practice;private String selectedInstructions="";private volatile boolean busy=false;private boolean speak=true,ttsReady=false;private String selectedNumber="";
    private TextToSpeech tts;private String page="Assistant";
    private final Runnable refresh=new Runnable(){public void run(){if(status!=null)status.setText(Store.modelStatus+"\n"+Store.status);handler.postDelayed(this,1000);}};
    private final Shizuku.OnRequestPermissionResultListener permission=(request,result)->{if(result==0){BridgeClient.connect(this);toast("Phone connection authorized");}else toast("Phone connection was not authorized");};
    @Override public void onCreate(Bundle saved){super.onCreate(saved);Shizuku.addRequestPermissionResultListener(permission);
        tts=new TextToSpeech(this,result->{ttsReady=result==TextToSpeech.SUCCESS;});show("Assistant");handler.post(refresh);
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView label(String text,int size,boolean bold){TextView t=new TextView(this);t.setText(text);t.setTextColor(bold?ink:muted);t.setTextSize(size);if(bold)t.setTypeface(null,Typeface.BOLD);t.setPadding(0,dp(12),0,dp(6));body.addView(t);return t;}
    private Button button(String text,Runnable action){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(Color.WHITE);b.setBackground(bg(teal,14));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(10);b.setPadding(dp(12),dp(12),dp(12),dp(12));body.addView(b,p);b.setOnClickListener(v->action.run());return b;}
    private EditText field(String title,String value,String hint,int lines){label(title,15,true);EditText e=new EditText(this);e.setText(value);e.setHint(hint);e.setTextSize(16);e.setTextColor(ink);e.setMinLines(lines);e.setGravity(Gravity.TOP);e.setPadding(dp(12),dp(12),dp(12),dp(12));e.setBackground(bg(Color.WHITE,12));body.addView(e);return e;}
    private void show(String tab){page=tab;LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(0xfff4f7f5);
        root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(i.left,i.top,i.right,i.bottom);return insets;});setContentView(root);
        TextView title=new TextView(this);title.setText("DoorMate");title.setTextSize(28);title.setTypeface(null,Typeface.BOLD);title.setTextColor(teal);title.setPadding(dp(20),dp(16),dp(20),dp(8));root.addView(title);
        LinearLayout tabs=new LinearLayout(this);tabs.setPadding(dp(12),0,dp(12),dp(10));for(String name:new String[]{"Assistant","Practice","Setup"}){Button b=new Button(this);b.setText(name);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(name.equals(tab)?Color.WHITE:teal);b.setBackground(bg(name.equals(tab)?teal:Color.WHITE,12));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1);p.setMargins(dp(3),0,dp(3),0);tabs.addView(b,p);b.setOnClickListener(v->show(name));}root.addView(tabs);
        ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(20),0,dp(20),dp(28));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        status=label(Store.modelStatus+"\n"+Store.status,14,false);status.setBackground(bg(0xffe1eee6,12));status.setPadding(dp(12),dp(12),dp(12),dp(12));
        if(tab.equals("Assistant"))assistant();else if(tab.equals("Practice"))practiceScreen();else setup();
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void alert(String title,String text){new AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("OK",null).show();}
    private void assistant(){
        label("Let your assistant take it",23,true);label("Add a caller and tell DoorMate what it may say. Try a conversation before enabling calls.",16,false);
        button(Store.armed(this)?"Pause automatic calls":"Enable automatic calls · experimental",()->{
            if(Store.armed(this)){Store.pause(this);stopService(new Intent(this,AgentService.class));show("Assistant");return;}
            if(busy){toast("Wait for the practice reply to finish");return;}
            if(Store.approved(this).isEmpty()){toast("Add a caller first");return;}
            if(!readyForCalls()){show("Setup");return;}
            new AlertDialog.Builder(this).setTitle("Test automatic calls?").setMessage("This research build has not been verified on your phone. Approved callers may hear Samsung's greeting before DoorMate replies. Keep your first test call under your supervision. Calls from other numbers ring normally.")
                .setNegativeButton("Cancel",null).setPositiveButton("Enable test",(d,w)->{
                    Store.p(this).edit().putBoolean("armed",true).apply();startForegroundService(new Intent(this,AgentService.class));Store.status="Starting approved-caller monitoring…";show("Assistant");}).show();
        });
        button("Add a caller",()->editProfile(null));
        Map<String,String> profiles=Store.approved(this);
        if(profiles.isEmpty())label("No callers added yet. Everyone rings normally.",15,false);
        for(Map.Entry<String,String> row:profiles.entrySet()){
            label(row.getKey(),17,true);String text=row.getValue();label(text.length()>120?text.substring(0,120)+"…":text,14,false);button("Edit instructions",()->editProfile(row.getKey()));
        }
        label("Call readiness",18,true);label("Device verification pending. Practice works independently of Samsung calls once the AI model is loaded. Screen-locked operation needs a real phone test.",14,false);
        String last=Store.get(this,"last_reply");if(!last.isEmpty()){label("Last reply submitted",18,true);label(last,15,false);}
    }
    private boolean readyForCalls(){
        if(!Store.modelReady){toast("Load the AI model in Setup first");return false;}
        if(checkSelfPermission("android.permission.READ_PHONE_STATE")!=PackageManager.PERMISSION_GRANTED||checkSelfPermission("android.permission.READ_CONTACTS")!=PackageManager.PERMISSION_GRANTED){toast("Allow call permissions in Setup");return false;}
        if(!getSystemService(RoleManager.class).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)){toast("Choose DoorMate as your call-screening app in Setup");return false;}
        if(BridgeClient.binder==null){toast("Connect and check the phone in Setup first");return false;}return true;
    }
    private void editProfile(String old){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(20),dp(8),dp(20),0);
        EditText n=new EditText(this);n.setHint("Caller number, including country code");n.setInputType(android.text.InputType.TYPE_CLASS_PHONE);n.setText(old==null?"":old);panel.addView(n);
        EditText instructions=new EditText(this);instructions.setHint("Example: Deliver to Flat 4B, Example House. Use the east gate. Leave parcels with security. Do not approve payments.\n\nYou can use instructions for other scenarios too.");instructions.setText(old==null?"":Store.approved(this).get(old));instructions.setMinLines(6);instructions.setMaxLines(10);instructions.setGravity(Gravity.TOP);instructions.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)});panel.addView(instructions);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(old==null?"Add approved caller":"Caller instructions").setView(panel).setNegativeButton("Cancel",null).setPositiveButton("Save",null)
            .setNeutralButton(old==null?"":"Remove",(d,w)->{if(old!=null){Map<String,String> p=Store.approved(this);p.remove(old);saveProfiles(p);show("Assistant");}}).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String number=Numbers.normalize(n.getText().toString()),text=instructions.getText().toString().trim();
            if(number.isEmpty()){n.setError("Enter a complete number, e.g. +919876543210");return;}
            if(text.isEmpty()){instructions.setError("Add instructions for this caller");return;}
            Map<String,String> p=Store.approved(this);if(old!=null)p.remove(old);p.put(number,text);saveProfiles(p);dialog.dismiss();show("Assistant");}));dialog.show();
    }
    private void saveProfiles(Map<String,String> profiles){
        Store.pause(this);JSONArray a=new JSONArray();try{for(Map.Entry<String,String> e:profiles.entrySet())a.put(new JSONObject().put("number",e.getKey()).put("instructions",e.getValue()));}catch(JSONException ignored){}
        Store.put(this,"profiles",a.toString());if(practice!=null)practice.clear();practice=null;toast("Saved. Enable automatic calls when ready.");
    }
    private void practiceScreen(){
        label("You play the caller",23,true);label("A real local AI uses your instructions and remembers recent turns. This does not place a phone call.",15,false);
        if(Store.armed(this)){label("Pause automatic calls to practice without competing for the AI model.",15,true);button("Pause and practice",()->{Store.pause(this);show("Practice");});return;}
        Map<String,String> profiles=Store.approved(this);List<String> names=new ArrayList<>(profiles.keySet());names.add("Example delivery · fictional details");
        Spinner picker=new Spinner(this);picker.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));int selection=names.indexOf(selectedNumber);if(selection<0)selection=0;picker.setSelection(selection);body.addView(picker);
        String sample="Delivery scenario. Address: Flat 4B, Example House, 12 Sample Road. Landmark: Example Pharmacy. Use the east gate. The building is on the fourth floor with a lift. Leave sealed parcels with security only if nobody answers. Do not approve payments. These are fictional practice details.";
        picker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> a){}public void onItemSelected(android.widget.AdapterView<?> a,View v,int pos,long id){selectedNumber=names.get(pos);String next=pos<profiles.size()?profiles.get(names.get(pos)):sample;if(!next.equals(selectedInstructions)){selectedInstructions=next;resetPractice();}}});
        String initial=selection<profiles.size()?profiles.get(names.get(selection)):sample;if(!initial.equals(selectedInstructions)){selectedInstructions=initial;resetPractice();}
        chat=new LinearLayout(this);chat.setOrientation(1);body.addView(chat);
        if(practice!=null)for(Conversation.Turn t:practice.history()){bubble("You",t.caller);bubble("DoorMate",t.assistant);}
        message=field("Caller message","","I'm outside. Which entrance should I use?",2);message.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1000)});
        button("Send",this::sendPractice);
        button("Speak as the caller",()->{if(busy)return;Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true).putExtra(RecognizerIntent.EXTRA_PROMPT,"Play the caller");try{startActivityForResult(intent,50);}catch(ActivityNotFoundException e){toast("Phone speech input is unavailable. You can type instead.");}});
        Switch voice=new Switch(this);voice.setText("Read AI replies aloud");voice.setChecked(speak);voice.setPadding(0,dp(16),0,dp(12));voice.setOnCheckedChangeListener((b,on)->{speak=on;if(!on&&tts!=null)tts.stop();});body.addView(voice);
        button("New conversation",this::resetPractice);
        button("Run delivery practice scenario",this::runScenario);
        label("Voice input uses your phone's speech provider, which may use a network service. The conversation model runs locally. Practice messages are held in memory, not saved.",13,false);
    }
    private void resetPractice(){if(practice!=null)practice.clear();practice=null;if(chat!=null)chat.removeAllViews();if(tts!=null)tts.stop();}
    private void bubble(String who,String words){if(chat==null)return;TextView t=new TextView(this);t.setText(who+"\n"+words);t.setTextSize(16);t.setTextColor(ink);t.setPadding(dp(14),dp(12),dp(14),dp(12));t.setBackground(bg(who.equals("You")?Color.WHITE:0xffdceee3,14));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(10);chat.addView(t,p);}
    private void sendPractice(){
        if(busy){toast("The AI is still replying");return;}if(!Store.modelReady){toast("Load the AI model in Setup first");show("Setup");return;}
        String input=message.getText().toString().trim();if(input.isEmpty())return;
        if(practice==null)practice=new Conversation(selectedInstructions,Store.model);final Conversation target=practice;
        bubble("You",input);message.setText("");busy=true;Store.modelStatus="Thinking on your phone…";
        worker.execute(()->{try{long start=SystemClock.elapsedRealtime();String answer=target.reply(input);long ms=SystemClock.elapsedRealtime()-start;
            runOnUiThread(()->{if(target==practice&&!isDestroyed()){if(page.equals("Practice"))bubble("DoorMate",answer+"\n\n"+String.format(Locale.ROOT,"%.1f s",ms/1000.0));if(page.equals("Practice")&&speak&&ttsReady){tts.speak(answer,TextToSpeech.QUEUE_FLUSH,null,"practice");}}});
        }catch(Exception e){runOnUiThread(()->{if(target==practice)alert("Reply unavailable",e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());});}
        finally{runOnUiThread(()->{busy=false;Store.modelStatus="Local conversational AI ready";});}});
    }
    private void runScenario(){
        if(busy){toast("Wait for the current reply");return;}
        if(!Store.modelReady){toast("Load the local AI in Setup first");show("Setup");return;}
        resetPractice();practice=new Conversation(selectedInstructions,Store.model);final Conversation target=practice;busy=true;
        final String[] callers={"Hello, I have a delivery for you. Where should I come?","Which entrance should I use?","And after that, which floor?","Nobody is answering. Can I leave it with security?","What is the OTP?"};
        worker.execute(()->{try{
            for(String caller:callers){
                if(practice!=target||Thread.currentThread().isInterrupted())break;
                runOnUiThread(()->{if(practice==target&&page.equals("Practice"))bubble("Simulated caller",caller);});
                long began=SystemClock.elapsedRealtime();String response=target.reply(caller);long elapsed=SystemClock.elapsedRealtime()-began;
                runOnUiThread(()->{if(practice==target&&page.equals("Practice"))bubble("DoorMate",response+"\n\n"+String.format(Locale.ROOT,"%.1f s",elapsed/1000.0));});
            }
        }catch(Exception e){runOnUiThread(()->{if(practice==target)alert("Simulation stopped",e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());});}
        finally{runOnUiThread(()->{busy=false;Store.modelStatus="Local AI ready · inspect practice replies for accuracy";});}});
    }
    private void setup(){
        label("1. Get the local AI",22,true);label("One-time download: about 1.6 GB. Qwen 2.5 1.5B runs on your phone with no API subscription. Use Wi-Fi. Its quality and speed still need testing on your S24.",15,false);
        button(ModelFiles.file(this).exists()?"Load AI":"Download AI model",()->{
            if(Store.downloading||busy){toast("Please wait for the current AI operation");return;}
            if(ModelFiles.file(this).exists()){loadModel();return;}
            new AlertDialog.Builder(this).setTitle("Download about 1.6 GB?").setMessage("The model is downloaded from Hugging Face and verified against its published checksum. Keep this app open until it finishes.").setNegativeButton("Cancel",null).setPositiveButton("Download",(d,w)->{
                Store.downloading=true;Store.modelStatus="Starting AI download…";worker.execute(()->{try{ModelFiles.download(getApplicationContext(),text->Store.modelStatus=text);Store.prepare(getApplicationContext());}catch(Exception e){Store.modelStatus="Download/load failed: "+e.getMessage();}finally{Store.downloading=false;runOnUiThread(()->{if(page.equals("Setup")&&!isDestroyed())show("Setup");});}});
            }).show();
        });
        label("2. Connect Samsung calls",22,true);label("Practice needs no call permissions. Automatic calls additionally require Shizuku and Samsung's call assistant. This is developer setup, not a normal permission; Shizuku needs restarting after reboot. Keep Play Protect enabled.",15,false);
        button("Open Shizuku setup guide",()->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://shizuku.rikka.app/guide/setup/"))));
        button("Connect phone",()->{try{if(!Shizuku.pingBinder()){alert("Start Shizuku first","Follow the Shizuku setup guide, then return here.");return;}if(Shizuku.checkSelfPermission()!=0)Shizuku.requestPermission(40);else{BridgeClient.connect(this);toast("Connection requested. Check it below.");}}catch(Exception e){toast("Shizuku is unavailable");}});
        button("Allow call permissions",()->{List<String> p=new ArrayList<>();p.add("android.permission.READ_PHONE_STATE");p.add("android.permission.READ_CONTACTS");if(Build.VERSION.SDK_INT>=33)p.add("android.permission.POST_NOTIFICATIONS");requestPermissions(p.toArray(new String[0]),41);});
        label("Phone state detects when calls end. Contacts permission lets Android deliver saved-contact calls to the screening service; DoorMate does not read your contacts. Notifications provide a Stop button.",13,false);
        button("Choose DoorMate for call screening",()->{RoleManager rm=getSystemService(RoleManager.class);if(rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING))startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING),42);else toast("Call screening is unavailable");});
        button("Check phone connection",()->worker.execute(()->{try{JSONObject r=BridgeClient.request(BridgeClient.op("ping"));runOnUiThread(()->alert("Phone connection",r.optBoolean("ok")?"Connected. This does not verify Samsung message controls or locked-screen operation. Use an approved test caller after enabling automatic calls.":r.optString("error")));}catch(Exception e){runOnUiThread(()->alert("Connection unavailable",BridgeClient.error.isEmpty()?e.getClass().getSimpleName():BridgeClient.error));}}));
        label("3. One real call test",22,true);label("Add a trusted number, enable automatic calls, then have them call and ask about your instructions. No test phrases or calibration. If the Samsung layout cannot be understood, the app stops and reports it. Test unlocked first, then locked. A successful screen tap does not prove the caller heard speech.",15,false);
        button("Share phone diagnostic",()->{String text=Store.report.isEmpty()?"DoorMate 0.3\n"+Build.MODEL+" Android "+Build.VERSION.RELEASE+"\n"+Store.status+"\nNo call screen captured.":Store.report+"\nStatus: "+Store.status;startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text),"Share diagnostic"));});
        button("Stop call assistant",()->{Store.pause(this);stopService(new Intent(this,AgentService.class));BridgeClient.close();show("Setup");});
        button("Erase my instructions and numbers",()->new AlertDialog.Builder(this).setTitle("Erase saved details?").setMessage("Stops calls and deletes numbers, instructions and the last reply. The downloaded AI model remains.").setNegativeButton("Cancel",null).setPositiveButton("Erase",(d,w)->{Store.pause(this);stopService(new Intent(this,AgentService.class));Store.p(this).edit().clear().apply();resetPractice();show("Assistant");}).show());
    }
    private void loadModel(){if(busy)return;busy=true;worker.execute(()->{try{Store.prepare(getApplicationContext());}catch(Exception e){runOnUiThread(()->alert("AI could not load",e.getMessage()==null?"Local model error":e.getMessage()));}finally{busy=false;}});}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==50&&result==RESULT_OK&&data!=null){ArrayList<String> words=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(words!=null&&!words.isEmpty()&&message!=null){message.setText(words.get(0));toast("Review the words, then tap Send");}}}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);Shizuku.removeRequestPermissionResultListener(permission);if(practice!=null)practice.clear();if(tts!=null){tts.stop();tts.shutdown();}worker.shutdownNow();super.onDestroy();}
}
