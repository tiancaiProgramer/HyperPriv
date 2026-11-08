package io.github.hyperpriv;
import java.util.*;
/** Only reads DEX definitions. Never executes vendor methods, changes preferences or hooks another process. */
public final class PresenceDeviceAudit {
 public static void main(String[] args){
  long start=System.nanoTime();Map<String,List<Rule>> candidates=new HashMap<>();Map<String,DexEntrances.Result> scans=new HashMap<>();
  for(String spec:args){String[] parts=spec.split("=",3);List<Rule> rules=RuntimeProfile.presenceRules(parts[0],Long.parseLong(parts[1]));if(rules.isEmpty())continue;
   candidates.put(parts[0],rules);DexEntrances.Result scan=DexEntrances.inspect(parts[2].split(":"),rules);scans.put(parts[0],scan);System.out.println("DEX package="+parts[0]+" complete="+scan.complete+" matched="+scan.found.size()+" candidates="+rules.size());
   if(!scan.complete)throw new AssertionError("sample should be fully readable");
  }
  List<Rule> system=RuntimeProfile.presenceRules("android",36);candidates.put("android",system);scans.put("android",DexEntrances.inspect(new String[]{"/system/framework/services.jar","/system_ext/framework/miui-services.jar","/system/framework/framework.jar"},system));
  Map<String,String> absent=FeaturePresence.absent(candidates,scans);
  if(!absent.isEmpty())throw new AssertionError("adapted EU entrances incorrectly hidden: "+absent);
  Set<String> present=new TreeSet<>();for(Map.Entry<String,List<Rule>> e:candidates.entrySet())for(Rule r:e.getValue())if(scans.get(e.getKey()).found.contains(DexEntrances.signature(r)))present.add(r.group);
  if(!present.equals(FeaturePresence.CHECKED))throw new AssertionError("not all twelve tested: "+present);
  for(boolean eu:new boolean[]{false,true}){RomPolicy.State state=new RomPolicy.State(eu,false,Collections.emptyMap(),Collections.emptySet(),absent);for(String key:FeaturePresence.CHECKED)if(state.hidden(key))throw new AssertionError("actual entrance hidden: "+key);}
  System.out.println("Presence audit: all 12 real sample features retained; standard/EU shared predicate; elapsedMs="+(System.nanoTime()-start)/1000000+"; read-only DEX, no vendor execution");
 }
}
