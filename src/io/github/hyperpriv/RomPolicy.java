package io.github.hyperpriv;
import java.util.*;
/** Immutable profile facts from package presence and audited ROM bytes, never from a hooked getter. */
final class RomPolicy {
    static final String GUARD_301="ac32f0595d441ca921201cf634ba27df7ea6197b806400580151de83a9c112ec";
    static String target(String key){
        if(key.equals("master")||key.equals(Compatibility.KEY)||key.equals("core_enabled"))return "module";
        return key.startsWith("core_")?"android":UiCatalog.target(key);
    }
    static final class State {
        final boolean eu,international;
        final Map<String,String> hashes;
        final Set<String> missing;
        final Map<String,String> unavailable;
        State(boolean eu,boolean international,Map<String,String> hashes,Set<String> missing){
            this(eu,international,hashes,missing,Collections.emptyMap());
        }
        State(boolean eu,boolean international,Map<String,String> hashes,Set<String> missing,Map<String,String> unavailable){
            this.eu=eu;this.international=international;
            this.hashes=Collections.unmodifiableMap(new HashMap<>(hashes));this.missing=Collections.unmodifiableSet(new HashSet<>(missing));
            this.unavailable=Collections.unmodifiableMap(new HashMap<>(unavailable));
        }
        boolean known(String pkg,long version){String expected=EuBuild.expectedHash(pkg,version);return expected!=null&&expected.equals(hashes.get(pkg));}
        String reason(String key){
            if(unavailable.containsKey(key))return unavailable.get(key);
            if(missing.contains(target(key)))return "未安装对应组件";
            if(key.equals("guard_environment")&&!guardImplemented())return "当前版本没有已适配的安全守护 Root 业务入口";
            if(!eu)return "";
            if(known("com.miui.packageinstaller",55500)&&(key.equals("ads")||(key.equals("scan")&&international)))return "EU ROM 已处理";
            if(known("com.miui.securitycenter",40001283)&&key.equals("permission_timer"))return "EU ROM 已处理";
            if(GUARD_301.equals(hashes.get("com.miui.guardprovider"))&&(key.equals("app_list")||key.equals("guard_environment")))return "此 EU 安全守护未提供对应业务入口";
            return "";
        }
        boolean hidden(String key){return !reason(key).isEmpty();}
        boolean skip(Rule rule){
            if(hidden(rule.group))return true;
            if(!eu)return false;
            if(rule.group.equals("usb_local")&&rule.pkg.equals("com.android.settings")&&known(rule.pkg,36))return true;
            return international&&known("com.miui.packageinstaller",55500)&&rule.cls.equals("f2.n")&&rule.group.equals("guards")&&(rule.method.equals("s")||rule.method.equals("x"));
        }
        boolean defaultValue(String key){return eu?(key.equals("master")||key.equals(Compatibility.KEY)):Options.defaultValue(key);}
        boolean needsScope(String pkg){
            for(String key:Options.KEYS)if(target(key).equals(pkg)&&!hidden(key))return true;
            if(pkg.equals("com.miui.packageinstaller")&&!hidden("core_system_update"))return true;
            return false;
        }
    }
    static State normal(){return new State(false,false,Collections.emptyMap(),Collections.emptySet());}
    private static boolean guardImplemented(){for(Rule[] rules:new Rule[][]{Profiles.ALL,ExtraTargets.ALL})for(Rule r:rules)if(r.group.equals("guard_environment"))return true;return false;}
    static boolean supportsHelper(long version,String hash){return HelperCompatibility.exact(version,hash);}
    static boolean skipComponent(boolean eu,String pkg,String hash){return eu&&pkg.equals("com.miui.guardprovider")&&GUARD_301.equals(hash);}
    private RomPolicy(){}
}
