package io.github.hyperpriv;
import java.util.*;
/** These existing standard features gained new EU component mappings; absent declarations hide their controls. */
final class FeaturePresence {
    static final Set<String> CHECKED=Collections.unmodifiableSet(new HashSet<>(Arrays.asList("upload","cloud","guards","completed_tip","root_detection","permission_tips","safepay_scan","systemhelper_report","core_system_update","accessibility_keep","usb_intercept","risk_notice")));
    static Map<String,String> absent(Map<String,List<Rule>> candidates,Map<String,DexEntrances.Result> scans){
        Set<String> considered=new HashSet<>(CHECKED),present=new HashSet<>(),unknown=new HashSet<>();
        for(Rule[] table:new Rule[][]{Profiles.ALL,ExtraTargets.ALL})for(Rule rule:table)if(CHECKED.contains(rule.group)&&!candidates.containsKey(rule.pkg))unknown.add(rule.group);
        for(Map.Entry<String,List<Rule>> entry:candidates.entrySet()){
            DexEntrances.Result scan=scans.get(entry.getKey());
            for(Rule rule:entry.getValue())if(CHECKED.contains(rule.group)){
                considered.add(rule.group);
                if(scan==null||!scan.complete)unknown.add(rule.group);
                if(scan!=null&&scan.found.contains(DexEntrances.signature(rule)))present.add(rule.group);
            }
        }
        Map<String,String> absent=new HashMap<>();
        for(String key:considered)if(!present.contains(key)&&!unknown.contains(key))absent.put(key,"当前组件没有匹配的功能入口");
        return absent;
    }
    private FeaturePresence(){}
}
