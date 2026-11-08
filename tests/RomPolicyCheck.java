package io.github.hyperpriv;
import java.util.*;
public final class RomPolicyCheck {
 static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
 static RomPolicy.State eu(){
  Map<String,String> hashes=new HashMap<>();
  hashes.put("com.miui.packageinstaller",EuBuild.expectedHash("com.miui.packageinstaller",55500));
  hashes.put("com.miui.securitycenter",EuBuild.expectedHash("com.miui.securitycenter",40001283));
  hashes.put("com.android.settings",EuBuild.expectedHash("com.android.settings",36));
  hashes.put("com.miui.guardprovider",RomPolicy.GUARD_301);
  return new RomPolicy.State(true,true,hashes,new HashSet<>(Arrays.asList("com.miui.analytics","com.miui.systemAdSolution")));
 }
 public static void main(String[] args){
  RomPolicy.State eu=eu(),normal=RomPolicy.normal();
  for(String key:Arrays.asList("ads","scan","permission_timer","analytics","msa","app_list","guard_environment")){
   require(eu.hidden(key),"audited redundant EU option still visible: "+key);
   require(normal.hidden(key)==key.equals("guard_environment"),"standard visibility must retain real entries and hide unimplemented placeholder: "+key);
  }
  for(String key:Arrays.asList("upload","cloud","guards","completed_tip","root_detection","permission_tips","safepay_scan","systemhelper_report","core_system_update","accessibility_keep","usb_intercept","risk_notice"))require(!eu.hidden(key),"real EU function hidden without evidence: "+key);
  for(RomPolicy.State state:Arrays.asList(eu,normal))require(state.defaultValue(Compatibility.KEY),"default compatibility is off");
  require(normal.defaultValue("ads")&&!normal.defaultValue("systemhelper_report"),"standard defaults changed");
  require(!eu.defaultValue("ads")&&!eu.defaultValue("systemhelper_report"),"hidden ROM state became a module default");
  RomPolicy.State unknown=new RomPolicy.State(true,true,Collections.emptyMap(),Collections.emptySet());
  require(!unknown.hidden("ads")&&!unknown.hidden("app_list"),"unknown EU APK was assumed handled");
  require(!new RomPolicy.State(true,false,eu.hashes,eu.missing).hidden("scan"),"non-international path assumed disabled");
  Rule usb=new Rule("com.android.settings",36,"controller","method","V","usb_local","usb_install",new String[0]);
  require(eu.skip(usb)&&!normal.skip(usb)&&!unknown.skip(usb),"USB deduplication applied outside confirmed EU Settings");
  require(!eu.needsScope("com.miui.guardprovider")&&!eu.needsScope("com.miui.analytics")&&eu.needsScope("com.mobiletools.systemhelper"),"EU recommended scope mismatch");
  require(normal.needsScope("com.miui.guardprovider"),"normal GuardProvider scope lost");
  require(RomPolicy.skipComponent(true,"com.miui.guardprovider",RomPolicy.GUARD_301)&&!RomPolicy.skipComponent(false,"com.miui.guardprovider",RomPolicy.GUARD_301)&&!RomPolicy.skipComponent(true,"com.miui.guardprovider","unknown"),"component bootstrap skipped without exact EU evidence");
  require(Profiles.apkHash("com.mobiletools.systemhelper",31)!=null&&Profiles.apkHash("com.mobiletools.systemhelper",34)!=null,"exact helper profiles not retained");
  for(long version:new long[]{31L,34L}){require(RomPolicy.supportsHelper(version,Profiles.apkHash("com.mobiletools.systemhelper",version)),"audited helper blocked");require(!RomPolicy.supportsHelper(version,"unknown"),"unknown helper hash allowed");}
  require(!RomPolicy.supportsHelper(35L,"unknown"),"unknown helper version allowed");
  System.out.println("Unified ROM policy: EU hidden/real/unknown cases, standard defaults, compatibility default-on and per-rule USB deduplication passed");
 }
}
