package com.miduoki.jgr;

import android.util.Log;

public class JLOG {
    private static String TAG = "JGR_TAG";

    private static String objects2String(Object ...args){
        StringBuffer sb = new StringBuffer();
        for(Object o:args){
            if(o!=null){
                sb.append(o.toString());
            }else {
                sb.append("null");
            }
            sb.append(" ");
        }
        return sb.toString();
    }

    public static void e(Object ...args){
        Log.e(TAG,objects2String(args));
    }

    public static void w(Object ...args){
        Log.w(TAG,objects2String(args));
    }

    public static void d(Object ...args){
        Log.d(TAG,objects2String(args));
    }

    public static void i(Object ...args){
        Log.i(TAG,objects2String(args));
    }

    public static void v(Object ...args){
        Log.v(TAG,objects2String(args));
    }

}
