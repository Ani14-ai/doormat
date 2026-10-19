package com.doormate.app.core;

import java.util.*;

/** Conservative structural recognition. Unknown Samsung layouts are explicitly unsupported. */
public final class ScreenParser {
    public static final class Node {
        public final String id, text, description;
        public final List<String> ancestors;
        public final boolean editable, clickable;
        public final int index;
        public Node(String id, String text, String description, List<String> ancestors, boolean editable, boolean clickable, int index) {
            this.id=id; this.text=text; this.description=description; this.ancestors=ancestors; this.editable=editable; this.clickable=clickable; this.index=index;
        }
    }
    public static final class Mapping {
        public final String callerId, callerScope, editId, sendId, callerText, fingerprint;
        Mapping(Node n, String scope, Node input, Node send) {
            callerId=n.id; callerScope=scope; editId=input.id; sendId=send.id; callerText=n.text; fingerprint=n.index+":"+n.text;
        }
    }
    private static String tail(String id) { int p=id.indexOf(":id/"); return p<0?"":id.substring(p+4).toLowerCase(Locale.ROOT); }
    private static boolean conversation(String s) { return s.matches(".*(message|transcript|conversation|chat|bubble).*"); }
    private static boolean incoming(String s) { return s.matches(".*(^|_)(incoming|received|caller|remote)(_|$).*"); }
    private static boolean outgoing(String s) { return s.matches(".*(^|_)(outgoing|sent|assistant|bixby|local)(_|$).*"); }
    public static Mapping parse(List<Node> nodes) {
        Node input=null,send=null,caller=null; String scope="";
        for(Node n:nodes) {
            if (!n.id.startsWith("com.samsung.android.incallui:id/")) continue;
            if(n.editable) { if(input!=null) return null; input=n; }
            String label=n.description.isEmpty()?n.text:n.description;
            if(!n.editable && (label.equalsIgnoreCase("Send") || label.equalsIgnoreCase("Send message"))) { if(send!=null) return null; send=n; }
            if(n.editable || n.text.isEmpty() || n.text.length()>1000) continue;
            String id=tail(n.id); boolean in=incoming(id),out=outgoing(id),conv=conversation(id);String candidateScope="";
            for(String ancestor:n.ancestors) {
                String a=tail(ancestor);conv|=conversation(a);out|=outgoing(a);
                if(incoming(a)){in=true;candidateScope=ancestor;}
            }
            // A generic TextView in a role-labelled message container is acceptable.
            // Never treat a caller's contact-name header as conversation text.
            if(in&&!out&&conv&&!id.contains("name")&&!id.contains("number")&&!id.contains("avatar")) {
                if(caller!=null && caller.text.equals(n.text) && !caller.id.equals(n.id)) return null;
                caller=n;scope=candidateScope;
            }
        }
        if(caller==null || input==null || send==null) return null;
        if(!input.text.isEmpty()) return null; // user typed: never overwrite
        return new Mapping(caller,scope,input,send);
    }
    public static String nextAction(List<Node> nodes) {
        for(String label:new String[]{"Text call","Call screening","Call assist"})
            for(Node n:nodes) if(!n.editable&&(label.equalsIgnoreCase(n.text)||label.equalsIgnoreCase(n.description))) return label;
        return "";
    }
}
