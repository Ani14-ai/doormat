package com.doormate.app;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;

final class ModelFiles {
    static final String SHA256="c4a9b7289365ab80fe599b844b77ad253eb3c53fb0e5d963d699146a6f01255e";
    static final String URL="https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/5e6e7a5/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm";
    interface Progress { void update(String text); }
    static File file(Context c){return new File(c.getFilesDir(),"qwen.litertlm");}
    static synchronized void download(Context c, Progress progress)throws Exception {
        File target=file(c),partial=new File(c.getFilesDir(),"model.part");
        if(c.getFilesDir().getUsableSpace()<2000000000L)throw new IOException("Free at least 2 GB before downloading.");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        HttpURLConnection connection=(HttpURLConnection)new URL(URL).openConnection();
        connection.setConnectTimeout(20000);connection.setReadTimeout(30000);connection.setInstanceFollowRedirects(true);
        try {
            if(connection.getResponseCode()!=200)throw new IOException("Model download returned HTTP "+connection.getResponseCode());
            long count=0,total=connection.getContentLengthLong(),last=0;
            try(InputStream in=connection.getInputStream(); FileOutputStream out=new FileOutputStream(partial)) {
                byte[] buf=new byte[262144];int n;
                while((n=in.read(buf))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Download cancelled");
                    count+=n;if(count>1900000000L)throw new IOException("Model larger than expected");
                    digest.update(buf,0,n);out.write(buf,0,n);
                    if(count-last>8000000){progress.update("Downloading AI: "+(count/1000000)+" MB"+(total>0?" / "+total/1000000+" MB":""));last=count;}
                }
                out.getFD().sync();
            }
            StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format("%02x",b&255));
            if(!hex.toString().equals(SHA256))throw new IOException("Model checksum mismatch. File discarded.");
            if(!partial.renameTo(target))throw new IOException("Could not save the model");
        } finally { connection.disconnect(); if(partial.exists())partial.delete(); }
    }
}
