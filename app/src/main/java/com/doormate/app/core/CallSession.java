package com.doormate.app.core;

import java.util.*;

/** Authorization and stale-result protection independent of Android or any AI model. */
public final class CallSession {
    private String token = "", number = "", instructions = "", stable = "";
    private long expires = 0, stableSince = 0;
    private int turns = 0;
    private final Set<String> handled = new HashSet<>();
    public synchronized String begin(String raw, Map<String,String> approved, boolean enabled, long now) {
        stop();
        String n = Numbers.normalize(raw);
        if (!enabled || n.isEmpty() || !approved.containsKey(n) || approved.get(n).trim().isEmpty()) return "";
        number = n; instructions = approved.get(n); token = UUID.randomUUID().toString(); expires = now + 180000;
        return token;
    }
    public synchronized void stop() { token = ""; number = ""; instructions = ""; expires = 0; turns = 0; stable = ""; stableSince = 0; handled.clear(); }
    public synchronized boolean active(long now) { return !token.isEmpty() && now < expires && turns < 12; }
    public synchronized boolean valid(String t, long now) { return !t.isEmpty() && t.equals(token) && active(now); }
    public synchronized String token() { return token; }
    public synchronized String number() { return number; }
    public synchronized String instructions() { return instructions; }
    public synchronized int turns() { return turns; }
    public synchronized void sent(String t, long now) { if (valid(t, now)) turns++; }
    public synchronized boolean claimStable(String fingerprint, long now) {
        if (!active(now) || fingerprint.isEmpty()) return false;
        if (!fingerprint.equals(stable)) { stable = fingerprint; stableSince = now; return false; }
        if (now - stableSince < 1200 || handled.contains(fingerprint)) return false;
        handled.add(fingerprint); return true;
    }
}
