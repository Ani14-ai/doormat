package com.doormate.app;
import android.app.UiAutomation;import android.accessibilityservice.AccessibilityServiceInfo;import android.os.*;import android.content.Context;import android.view.accessibility.*;import android.view.*;import android.graphics.Rect;import org.json.*;import java.util.*;import java.util.concurrent.*;
/** Privileged, user-authorized broker. No shell commands; only Samsung in-call UI operations. */
public class PhoneBridge extends Binder {
 static final String DESCRIPTOR="com.doormate.app.PhoneBridge";static final String SAMSUNG="com.samsung.android.incallui";
 final int owner;final HandlerThread thread=new HandlerThread("DoorMateUI");final Handler handler;UiAutomation ui;String lease="";long expires=0,inspectExpires=0;int sent=0;
 public PhoneBridge(Context context){owner=context.getApplicationInfo().uid;thread.start();handler=new Handler(thread.getLooper());}
 private void connect()throws Exception{if(ui!=null)return;Object c=Class.forName("android.app.UiAutomationConnection").getDeclaredConstructor().newInstance();ui=(UiAutomation)UiAutomation.class.getConstructor(Looper.class,Class.forName("android.app.IUiAutomationConnection")).newInstance(thread.getLooper(),c);UiAutomation.class.getMethod("connect",int.class).invoke(ui,1);AccessibilityServiceInfo info=ui.getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;ui.setServiceInfo(info);}
 @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
  if(code==16777115){if(Binder.getCallingUid()!=owner && Binder.getCallingUid()!=android.os.Process.myUid())throw new SecurityException("Unauthorized stop");if(ui!=null)try{UiAutomation.class.getMethod("disconnect").invoke(ui);}catch(Exception ignored){}thread.quitSafely();System.exit(0);return true;}
  if(code==INTERFACE_TRANSACTION){reply.writeString(DESCRIPTOR);return true;}if(code!=1)return super.onTransact(code,data,reply,flags);
  if(Binder.getCallingUid()!=owner)throw new SecurityException("DoorMate owner only");data.enforceInterface(DESCRIPTOR);String raw=data.readString();if(raw==null||raw.length()>12000)throw new IllegalArgumentException("Invalid request");
  try{JSONObject request=new JSONObject(raw);String result=handle(request).toString();reply.writeNoException();reply.writeString(result);}catch(Exception e){reply.writeNoException();reply.writeString("{\"ok\":false,\"error\":\"Phone bridge unavailable: "+e.getClass().getSimpleName()+"\"}");}return true;
 }
 synchronized JSONObject handle(JSONObject q)throws Exception{
  String op=q.optString("op");long now=SystemClock.elapsedRealtime();
  if(op.equals("stop")){lease="";expires=0;return ok();}
  if(op.equals("arm")){lease=q.getString("token");expires=now+180000;sent=0;return ok();}
  if(op.equals("inspect")){inspectExpires=now+90000;return ok();}
  if(op.equals("ping")){connect();return ok().put("uid",android.os.Process.myUid());}
  boolean active=!lease.isEmpty()&&now<expires&&lease.equals(q.optString("token"));if(!active&&!(op.equals("snapshot")&&now<inspectExpires))return new JSONObject().put("ok",false).put("error","No active authorized session");
  connect();List<AccessibilityNodeInfo> all=nodes();
  if(op.equals("snapshot")){JSONArray a=new JSONArray();int index=0;for(AccessibilityNodeInfo n:all){if(!n.isVisibleToUser())continue;Rect r=new Rect();n.getBoundsInScreen(r);JSONObject row=new JSONObject().put("id",str(n.getViewIdResourceName())).put("text",str(n.getText())).put("desc",str(n.getContentDescription())).put("edit",n.isEditable()).put("click",n.isClickable()).put("index",index++).put("anc",ancestors(n)).put("bounds",r.toShortString());a.put(row);if(a.length()>350)break;}return ok().put("nodes",a);}
  if(op.equals("pickup")){
   String label=q.optString("label");
   if(!Arrays.asList("Call assist","Call screening","Text call").contains(label))return fail("Unsupported call action");
   AccessibilityNodeInfo target=null;
   for(AccessibilityNodeInfo n:all)if(n.isVisibleToUser()&&!n.isEditable()&&(label.equalsIgnoreCase(str(n.getText()))||label.equalsIgnoreCase(str(n.getContentDescription())))){
    // Deduplicate nested labels only when they resolve to the same clickable ancestor.
    AccessibilityNodeInfo button=clickable(n);
    if(button!=null){if(target!=null&&!target.equals(button))return fail("Ambiguous call action");target=button;}
   }
   if(target!=null&&click(target))return ok().put("stage",label);
   return fail("Call control unavailable");
  }
  if(op.equals("send")){if(sent>=12)throw new IllegalStateException("Turn limit");String text=q.getString("text");if(text.length()<1||text.length()>700)throw new IllegalArgumentException("Reply length");String cid=q.getString("callerId"),scope=q.optString("scope"),expected=q.getString("expected");AccessibilityNodeInfo caller=lastCaller(all,cid,scope);if(caller==null||!expected.equals(str(caller.getText())))return fail("Caller screen changed");AccessibilityNodeInfo edit=id(all,q.getString("editId"));if(edit==null||!edit.isEditable())return fail("Reply field missing");if(!str(edit.getText()).isEmpty())return fail("Reply field is occupied; manual takeover");AccessibilityNodeInfo send=id(all,q.getString("sendId"));if(send==null)return fail("Send button missing");Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);if(!edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args))return fail("Text input unsupported");Thread.sleep(180);List<AccessibilityNodeInfo> fresh=nodes();AccessibilityNodeInfo verify=id(fresh,q.getString("editId"));AccessibilityNodeInfo latest=lastCaller(fresh,cid,scope);
   if(SystemClock.elapsedRealtime()>=expires||verify==null||!text.equals(str(verify.getText()))||latest==null||!expected.equals(str(latest.getText())))return fail("Screen changed before send");if(click(id(fresh,q.getString("sendId")))){sent++;return ok();}return fail("Send action failed");}
  return fail("Unknown operation");
 }
 static String str(CharSequence s){return s==null?"":s.toString().trim();}
 JSONArray ancestors(AccessibilityNodeInfo n){JSONArray a=new JSONArray();for(int i=0;n!=null&&i<8;i++,n=n.getParent()){String id=n.getViewIdResourceName();if(id!=null)a.put(id);}return a;}
 AccessibilityNodeInfo lastCaller(List<AccessibilityNodeInfo>a,String id,String scope){AccessibilityNodeInfo result=null;for(AccessibilityNodeInfo n:a)if(n.isVisibleToUser()&&id.equals(n.getViewIdResourceName())&&(scope.isEmpty()||ancestors(n).toString().contains("\""+scope+"\""))&&!str(n.getText()).isEmpty())result=n;return result;}
 List<AccessibilityNodeInfo> nodes(){List<AccessibilityNodeInfo>a=new ArrayList<>();for(AccessibilityWindowInfo w:ui.getWindows()){AccessibilityNodeInfo n=w.getRoot();if(n!=null&&SAMSUNG.contentEquals(n.getPackageName()))walk(n,a,0);}return a;}
 void walk(AccessibilityNodeInfo n,List<AccessibilityNodeInfo>a,int depth){if(depth>35||a.size()>500)return;a.add(n);for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null)walk(c,a,depth+1);}}
 AccessibilityNodeInfo id(List<AccessibilityNodeInfo>a,String id){if(id.isEmpty()||!id.startsWith(SAMSUNG+":id/"))return null;AccessibilityNodeInfo result=null;for(AccessibilityNodeInfo n:a)if(n.isVisibleToUser()&&id.equals(n.getViewIdResourceName())){if(result!=null)return null;result=n;}return result;}
 AccessibilityNodeInfo clickable(AccessibilityNodeInfo n){for(int i=0;n!=null&&i<4;i++,n=n.getParent())if(n.isVisibleToUser()&&n.isClickable()&&n.isEnabled())return n;return null;}
 boolean click(AccessibilityNodeInfo n){for(int i=0;n!=null&&i<4;i++,n=n.getParent())if(n.isVisibleToUser()&&n.isClickable()&&n.isEnabled())return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);return false;}
 JSONObject ok()throws JSONException{return new JSONObject().put("ok",true);}JSONObject fail(String e)throws JSONException{return new JSONObject().put("ok",false).put("error",e);}
}
