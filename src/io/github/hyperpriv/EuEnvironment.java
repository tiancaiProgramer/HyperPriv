package io.github.hyperpriv;

import android.os.Build;
import android.content.Context;
import android.content.pm.PackageInfo;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Read-only ROM/profile checks; user preferences never override ownership or hashes. */
final class EuEnvironment {
    private static final Map<String,String> HASHES=new HashMap<>();
    static boolean isEuRom(){
        try{
            return new File("/product/priv-app/XiaomiEUExt/XiaomiEUExt.apk").isFile();
        }catch(Throwable error){return false;}
    }
    static boolean systemVerified(){
        if(!isEuRom())return false;
        for(int i=0;i<EuBuild.SYSTEM_PATHS.length;i++)if(!EuBuild.SYSTEM_HASHES[i].equals(hash(EuBuild.SYSTEM_PATHS[i])))return false;
        return true;
    }
    static boolean packageVerified(String pkg,long version,String source){
        return isEuRom()&&EuBuild.matches(pkg,version,hash(source));
    }
    static boolean packageVerified(Context context,String pkg){
        try{PackageInfo info=context.getPackageManager().getPackageInfo(pkg,0);return packageVerified(pkg,info.getLongVersionCode(),info.applicationInfo.sourceDir);}
        catch(Throwable error){return false;}
    }
    static boolean international(){try{return Class.forName("miui.os.Build").getField("IS_INTERNATIONAL_BUILD").getBoolean(null);}catch(Throwable error){return false;}}
    static synchronized String hash(String path){
        try{
            File file=new File(path);String cache=path+":"+file.length()+":"+file.lastModified();
            if(HASHES.containsKey(cache))return HASHES.get(cache);
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[131072];int n;while((n=in.read(buffer))>0)digest.update(buffer,0,n);}
            StringBuilder out=new StringBuilder();for(byte b:digest.digest())out.append(String.format(Locale.ROOT,"%02x",b&255));
            HASHES.put(cache,out.toString());return out.toString();
        }catch(Throwable error){return "";}
    }
    private EuEnvironment(){}
}
