package com.doormate.app;

import android.telecom.*;
import android.os.SystemClock;

public class Screening extends CallScreeningService {
    @Override public void onScreenCall(Call.Details d){
        boolean incoming=d.getCallDirection()==Call.Details.DIRECTION_INCOMING;
        String number=d.getHandle()==null?"":d.getHandle().getSchemeSpecificPart();
        // Always respond promptly. Never generate AI or start model loading on this callback.
        if(incoming){
            if(Store.session.active(SystemClock.elapsedRealtime())){
                Store.session.stop();Store.status="Second call detected; automation stopped";
            } else {
                String token=Store.session.begin(number,Store.approved(this),Store.armed(this),SystemClock.elapsedRealtime());
                Store.status=token.isEmpty()?"Call left to you":"Approved caller detected";
            }
        }
        respondToCall(d,new CallResponse.Builder().setDisallowCall(false).setRejectCall(false).setSkipCallLog(false).setSkipNotification(false).build());
    }
}
