package com.doormate.app.core;

public final class Numbers {
    private Numbers() {}
    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.trim().replaceAll("[ ()-]", "");
        if (s.startsWith("00")) s = "+" + s.substring(2);
        if (s.matches("[6-9][0-9]{9}")) s = "+91" + s;
        return s.matches("\\+[1-9][0-9]{7,14}") ? s : "";
    }
}
