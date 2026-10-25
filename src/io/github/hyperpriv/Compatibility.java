package io.github.hyperpriv;

import java.util.*;

/** Explicit opt-in trial of existing definitions; signature matches are not behavior validation. */
final class Compatibility {
    static final String KEY="experimental_compatibility";
    static boolean strictSystem(int sdk,String fingerprint){return sdk==36&&CoreTargets.FINGERPRINT.equals(fingerprint);}
    static long profileVersion(Rule[] rules,String pkg,long actual,boolean experimental){
        long latest=-1;
        for(Rule r:rules)if(r.pkg.equals(pkg)){
            if(r.version==actual)return actual;
            latest=Math.max(latest,r.version);
        }
        return experimental?latest:-1;
    }
    static List<Rule> select(Rule[] rules,String pkg,long actual,boolean experimental){
        long profile=profileVersion(rules,pkg,actual,experimental);
        Map<String,Rule> selected=new LinkedHashMap<>();Set<String> conflicts=new HashSet<>();
        for(Rule r:rules)if(r.pkg.equals(pkg)&&r.version==profile){
            String signature=r.cls+"#"+r.method+Arrays.toString(r.params)+r.returns;
            Rule previous=selected.get(signature);
            if(previous!=null&&(!previous.group.equals(r.group)||!previous.action.equals(r.action)))conflicts.add(signature);
            else selected.put(signature,r);
        }
        for(String signature:conflicts)selected.remove(signature);
        return new ArrayList<>(selected.values());
    }
    static boolean supports(Rule[] rules,String key,String pkg,long version,boolean experimental){
        for(Rule r:select(rules,pkg,version,experimental))if(r.group.equals(key))return true;
        return false;
    }
    private Compatibility(){}
}
