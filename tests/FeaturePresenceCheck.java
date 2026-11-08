package io.github.hyperpriv;
import java.util.*;
public final class FeaturePresenceCheck {
 static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 static Rule rule(String pkg,String method){return new Rule(pkg,1,"fixture.Target",method,"V","core_system_update","null",new String[0]);}
 public static void main(String[] args){
  Rule app=rule("com.miui.packageinstaller","app"),system=rule("android","system");
  Map<String,List<Rule>> rules=new HashMap<>();rules.put("com.miui.packageinstaller",Arrays.asList(app));rules.put("android",Arrays.asList(system));
  Map<String,DexEntrances.Result> scans=new HashMap<>();scans.put("com.miui.packageinstaller",new DexEntrances.Result(true,Collections.emptySet()));scans.put("android",new DexEntrances.Result(true,Collections.singleton(DexEntrances.signature(system))));
  require(!FeaturePresence.absent(rules,scans).containsKey("core_system_update"),"one real cross-component entrance must keep the option");
  scans.put("android",new DexEntrances.Result(true,Collections.emptySet()));
  Map<String,String> absent=FeaturePresence.absent(rules,scans);require(absent.containsKey("core_system_update"),"confirmed absent entrance visible");
  for(boolean eu:new boolean[]{false,true})require(new RomPolicy.State(eu,false,Collections.emptyMap(),Collections.emptySet(),absent).hidden("core_system_update"),"presence filtering must apply to both ROM types");
  scans.put("android",new DexEntrances.Result(false,Collections.emptySet()));require(!FeaturePresence.absent(rules,scans).containsKey("core_system_update"),"read error treated as missing method");
  scans.remove("android");require(!FeaturePresence.absent(rules,scans).containsKey("core_system_update"),"uninspected component treated as absent");
  RomPolicy.State standard=new RomPolicy.State(false,false,Collections.emptyMap(),Collections.singleton("com.mobiletools.systemhelper"));
  require(standard.hidden("systemhelper_report"),"missing standard component visible");
  require(!standard.hidden("cloud"),"unrelated component hidden");
  rules.put("com.mobiletools.systemhelper",Collections.emptyList());scans.put("com.mobiletools.systemhelper",new DexEntrances.Result(true,Collections.emptySet()));
  require(FeaturePresence.absent(rules,scans).containsKey("systemhelper_report"),"profile with no selected implementation visible");
  System.out.println("Feature presence: standard/EU absence, cross-component union and unknown-read preservation passed");
 }
}
