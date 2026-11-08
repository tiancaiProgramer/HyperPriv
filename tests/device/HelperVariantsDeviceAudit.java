package io.github.hyperpriv;
import dalvik.system.PathClassLoader;
import java.util.*;
/** Loads definitions in a disposable shell process, using only fake callbacks and synthetic arguments. */
public final class HelperVariantsDeviceAudit {
 public static void main(String[] args)throws Throwable{
  System.out.println("ISOLATED_HELPER_VARIANTS pid="+android.os.Process.myPid());
  for(String path:args){
   String hash=EuEnvironment.hash(path);HelperCompatibility.Match match=HelperCompatibility.resolve(34,hash,path,false);
   AdaptedDeviceAudit.require(match!=null&&!match.experimental,"audited variant not selected");
   RuntimeProfile.initializePackage(HelperCompatibility.PACKAGE,hash);RuntimeProfile.helper(match);
   ClassLoader loader=new PathClassLoader(path,HelperVariantsDeviceAudit.class.getClassLoader());
   int count=0,typed=0;
   for(Rule r:RuntimeProfile.select(Profiles.ALL,HelperCompatibility.PACKAGE,34,true)){
    Reflect.executable(r,loader);count++;
    AdaptedDeviceAudit.values.clear();AdaptedDeviceAudit.values.put("master",true);AdaptedDeviceAudit.values.put("systemhelper_report",true);AdaptedDeviceAudit.originalCalls=0;
    AdaptedDeviceAudit.install(r,loader);Object[] input=new Object[r.params.length];
    if(r.action.equals("empty_device")){
     Object value=AdaptedDeviceAudit.invoke(new AdaptedDeviceAudit.Trap(),"X");AdaptedDeviceAudit.require(value!=null&&value.getClass().getName().equals("e1.b")&&AdaptedDeviceAudit.originalCalls==0,"invalid empty record");typed++;
    }else{
     Object value=AdaptedDeviceAudit.invoke(input);AdaptedDeviceAudit.require(AdaptedDeviceAudit.originalCalls==0,"collector/upload original called");
     if(r.action.equals("false"))AdaptedDeviceAudit.require(Boolean.FALSE.equals(value),"upload not blocked");
     if(r.action.equals("empty_list"))AdaptedDeviceAudit.require(value instanceof List&&((List<?>)value).isEmpty(),"usage list not empty");
    }
    AdaptedDeviceAudit.values.put("master",false);AdaptedDeviceAudit.require(AdaptedDeviceAudit.invoke(input)==AdaptedDeviceAudit.ORIGINAL&&AdaptedDeviceAudit.originalCalls==1,"master off path lost");
    AdaptedDeviceAudit.values.put("master",true);AdaptedDeviceAudit.values.put("systemhelper_report",false);AdaptedDeviceAudit.require(AdaptedDeviceAudit.invoke(input)==AdaptedDeviceAudit.ORIGINAL&&AdaptedDeviceAudit.originalCalls==2,"feature off path lost");
   }
   AdaptedDeviceAudit.require(count==match.rules.length&&typed==2&&AdaptedDeviceAudit.contextReads==0,"incomplete variant audit");
   System.out.println("VARIANT "+match.label+" reflected="+count+" on/off gates="+count+" typedFactories="+typed+" contextReads=0 externalRequests=0; fake framework, no component started");
  }
 }
}
