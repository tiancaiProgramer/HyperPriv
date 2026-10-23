package io.github.hyperpriv;

import java.util.*;

final class ExtraPolicy {
    private ExtraPolicy(){}
    static final int LOW_SDK_FLAG=0x01000000;
    static int restoreFlag(int current,int original,int bit){return (current&~bit)|(original&bit);}
    static boolean unknownInstallOp(int op){return op==66;}
    static boolean clipboardReadOp(int op){return op==29;}
    static boolean sharedCapability(int flags){return flags==2;}
    static boolean containsFrame(StackTraceElement[] stack,String cls,String method){
        for(StackTraceElement f:stack)if(f.getClassName().equals(cls)&&(method==null||f.getMethodName().equals(method)))return true;return false;
    }
    static Set<String> packages(String text){
        LinkedHashSet<String> result=new LinkedHashSet<>();
        for(String value:text.split("[\\s,;|]+")){if(value.isEmpty())continue;
            if(value.length()>255||!value.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"))throw new IllegalArgumentException("包名格式不正确："+value);
            result.add(value);
        }
        return result;
    }
    static String normalizePackages(String text){return String.join("\n",packages(text));}
    static boolean clipboardAllowed(String text,String pkg){try{return pkg!=null&&packages(text).contains(pkg);}catch(IllegalArgumentException e){return false;}}
    static boolean parsingSplit(StackTraceElement[] stack){
        for(StackTraceElement frame:stack){String c=frame.getClassName(),m=frame.getMethodName();
            if(c.equals("android.content.pm.Signature")&&m.equals("areExactMatch"))continue;
            if(c.startsWith("android.")||c.startsWith("com.android."))return c.equals("com.android.internal.pm.pkg.parsing.ParsingPackageUtils")&&m.equals("getSigningDetails");
        }return false;
    }
    static boolean environmentTask(int id){return id==10||id==13||id==14||id==15||id==16;}
}
