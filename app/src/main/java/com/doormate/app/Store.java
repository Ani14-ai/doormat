package com.doormate.app;

import android.content.*;
import com.doormate.app.core.*;
import org.json.*;
import java.util.*;

final class Store {
    static volatile String status="Automatic calls are off", report="", modelStatus="AI model not loaded";
    static volatile boolean running=false, modelReady=false, downloading=false;
    static volatile LocalModel model;
    static final CallSession session=new CallSession();
    static android.content.SharedPreferences p(Context c){return c.getSharedPreferences("doormate_v3",0);}
    static String get(Context c,String key){return p(c).getString(key,"");}
    static void put(Context c,String key,String value){p(c).edit().putString(key,value).apply();}
    static boolean armed(Context c){return running && modelReady && p(c).getBoolean("armed",false);}
    static Map<String,String> approved(Context c){
        Map<String,String> result=new LinkedHashMap<>();
        try{JSONArray a=new JSONArray(get(c,"profiles").isEmpty()?"[]":get(c,"profiles"));
            for(int i=0;i<a.length();i++){JSONObject row=a.getJSONObject(i);String n=Numbers.normalize(row.optString("number"));if(!n.isEmpty())result.put(n,row.optString("instructions"));}}
        catch(JSONException ignored){}return result;
    }
    static synchronized LocalModel prepare(Context c)throws Exception {
        if(model==null)model=new LocalModel(ModelFiles.file(c).getAbsolutePath());
        modelStatus="Loading AI on your phone…";
        try{model.prepare();modelReady=true;modelStatus="Local conversational AI ready";return model;}
        catch(Exception e){modelReady=false;modelStatus="AI could not load: "+e.getClass().getSimpleName();throw e;}
    }
    static void pause(Context c){p(c).edit().putBoolean("armed",false).apply();session.stop();status="Automatic calls paused";}
}
