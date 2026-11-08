package io.github.hyperpriv;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Audited SystemHelper variants: versionCode alone cannot distinguish CN34 from EU34. */
final class HelperCompatibility {
    static final String PACKAGE="com.mobiletools.systemhelper";
    static final String CN34_APK="1de8d965e295db27e189aa179cdb45438d7f759cffbb43ce55f840b614ff6c65";
    static final String EU34_DEX="b218dbb0e01770a0a8b6ce3d3c202ca8fc339c01c23063014e6fcc281e0c7326";
    static final String CN34_DEX="ee266c9f12dfa70f48d7d5c539b085614f1ef4555938d29f820c41253793747a";
    static final class Match {
        final String label;final boolean experimental;final Rule[] rules;
        Match(String label,boolean experimental,Rule[] rules){this.label=label;this.experimental=experimental;this.rules=rules;}
    }
    private static final Map<String,String> CACHE=new HashMap<>();
    static boolean exact(long version,String hash){String expected=Profiles.apkHash(PACKAGE,version);return (version==31L||version==34L)&&expected!=null&&(expected.equals(hash)||(version==34L&&CN34_APK.equals(hash)));}
    static Rule[] rules(long version,boolean cn){
        List<Rule> out=new ArrayList<>();
        for(Rule r:Profiles.ALL)if(r.pkg.equals(PACKAGE)&&r.version==version){
            if(cn&&r.cls.equals("g1.f")&&(r.method.equals("f")||r.method.equals("g")))continue;
            if(cn&&r.cls.equals("f1.p"))r=new Rule(r.pkg,r.version,"f1.o",r.method,r.returns,r.group,r.action,r.params);
            out.add(r);
        }
        if(cn)out.add(new Rule(PACKAGE,34L,"g1.f","b","Z","systemhelper_report","false",new String[]{"Ljava/lang/String;","[B","[B"}));
        return out.toArray(new Rule[0]);
    }
    static Match choose(long version,String apkHash,String dex,boolean compatibility){
        if(exact(version,apkHash))return new Match(version==31L?"CN31":CN34_APK.equals(apkHash)?"CN34":"EU34",false,rules(version,CN34_APK.equals(apkHash)));
        if(!compatibility)return null;
        if(EU34_DEX.equals(dex))return new Match("EU34 相同代码",true,rules(34L,false));
        if(CN34_DEX.equals(dex))return new Match("CN34 相同代码",true,rules(34L,true));
        return null;
    }
    static Match resolve(long version,String apkHash,String path,boolean compatibility){
        Match exact=choose(version,apkHash,"",false);if(exact!=null||!compatibility)return exact;
        return choose(version,apkHash,dexHash(path),true);
    }
    static synchronized String dexHash(String path){
        try{
            File file=new File(path);String key=path+":"+file.length()+":"+file.lastModified();if(CACHE.containsKey(key))return CACHE.get(key);
            String value="";int count=0;
            try(ZipFile zip=new ZipFile(file)){
                Enumeration<? extends ZipEntry> entries=zip.entries();
                while(entries.hasMoreElements()){
                    ZipEntry entry=entries.nextElement();if(!entry.getName().endsWith(".dex"))continue;
                    if(++count!=1||!entry.getName().equals("classes.dex"))return "";
                    MessageDigest digest=MessageDigest.getInstance("SHA-256");long size=0;
                    try(InputStream in=zip.getInputStream(entry)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){size+=n;if(size>64*1024*1024)throw new IOException("DEX too large");digest.update(buffer,0,n);}}
                    StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));value=hex.toString();
                }
            }
            if(count!=1)return "";CACHE.put(key,value);return value;
        }catch(Exception error){return "";}
    }
    private HelperCompatibility(){}
}
