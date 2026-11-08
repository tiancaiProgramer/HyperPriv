package io.github.hyperpriv;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.*;
/** Same binary on standard and EU ROMs; only audited facts suppress a feature. */
final class RuntimeProfile {
    private static volatile RomPolicy.State current=RomPolicy.normal();
    private static volatile HelperCompatibility.Match helper;
    static void helper(HelperCompatibility.Match match){helper=match;}
    static RomPolicy.State state(){return current;}
    static void apply(RomPolicy.State state){helper=null;current=state;}
    static boolean eu(){return current.eu;}
    static boolean allowed(String key){return Options.accepts(key)&&!current.hidden(key);}
    static boolean defaultValue(String key){return current.defaultValue(key);}
    static boolean register(Rule rule){return !current.skip(rule);}
    static List<Rule> select(Rule[] rules,String pkg,long version,boolean compatibility){
        if(pkg.equals(HelperCompatibility.PACKAGE)&&rules==Profiles.ALL&&helper!=null){List<Rule> out=new ArrayList<>();for(Rule r:helper.rules)if(register(r))out.add(r);return out;}
        List<Rule> out=new ArrayList<>();for(Rule r:Compatibility.select(rules,pkg,version,compatibility))if(register(r))out.add(r);return out;
    }
    static void initializeSystem(){helper=null;current=new RomPolicy.State(EuEnvironment.isEuRom(),EuEnvironment.international(),Collections.emptyMap(),Collections.emptySet());}
    static void initializePackage(String pkg,String hash){helper=null;Map<String,String> hashes=new HashMap<>();hashes.put(pkg,hash);current=new RomPolicy.State(EuEnvironment.isEuRom(),EuEnvironment.international(),hashes,Collections.emptySet());}
    static void initializeUi(Context context){
        boolean eu=EuEnvironment.isEuRom();Map<String,String> hashes=new HashMap<>();Set<String> missing=new HashSet<>();
        helper=null;
        Map<String,List<Rule>> candidates=new HashMap<>();Map<String,DexEntrances.Result> scans=new HashMap<>();
        for(String pkg:Options.TARGETS)try{
            PackageInfo info=context.getPackageManager().getPackageInfo(pkg,0);
            if(pkg.equals(HelperCompatibility.PACKAGE))helper=HelperCompatibility.resolve(info.getLongVersionCode(),EuEnvironment.hash(info.applicationInfo.sourceDir),info.applicationInfo.sourceDir,Settings.local(context).getBoolean(Compatibility.KEY,Options.defaultValue(Compatibility.KEY)));
            if(eu&&(pkg.equals("com.miui.packageinstaller")||pkg.equals("com.miui.securitycenter")||pkg.equals("com.android.settings")||pkg.equals("com.miui.guardprovider")))hashes.put(pkg,EuEnvironment.hash(info.applicationInfo.sourceDir));
            List<Rule> rules=presenceRules(pkg,info.getLongVersionCode());
            candidates.put(pkg,rules);
            if(!rules.isEmpty()){
                List<String> paths=new ArrayList<>();paths.add(info.applicationInfo.sourceDir);
                if(info.applicationInfo.splitSourceDirs!=null)Collections.addAll(paths,info.applicationInfo.splitSourceDirs);
                scans.put(pkg,DexEntrances.inspect(paths.toArray(new String[0]),rules));
            }else scans.put(pkg,new DexEntrances.Result(true,Collections.emptySet()));
        }catch(PackageManager.NameNotFoundException error){missing.add(pkg);}catch(Exception error){/* Unknown, not proof of absence. */}
        List<Rule> system=presenceRules("android",Build.VERSION.SDK_INT);candidates.put("android",system);
        scans.put("android",DexEntrances.inspect(new String[]{"/system/framework/services.jar","/system_ext/framework/miui-services.jar","/system/framework/framework.jar"},system));
        current=new RomPolicy.State(eu,EuEnvironment.international(),hashes,missing,FeaturePresence.absent(candidates,scans));
    }
    static List<Rule> presenceRules(String pkg,long version){
        List<Rule> out=new ArrayList<>();
        for(Rule[] table:new Rule[][]{Profiles.ALL,ExtraTargets.ALL}){
            Iterable<Rule> selected=pkg.equals(HelperCompatibility.PACKAGE)&&table==Profiles.ALL&&helper!=null?Arrays.asList(helper.rules):Compatibility.select(table,pkg,version,true);
            for(Rule r:selected)if(FeaturePresence.CHECKED.contains(r.group))out.add(r);
        }
        return out;
    }
    static boolean knownSystem(){return Compatibility.strictSystem(Build.VERSION.SDK_INT,Build.FINGERPRINT)||EuEnvironment.systemVerified();}
    static boolean hashMatches(String pkg,long version,String hash){
        if(pkg.equals(HelperCompatibility.PACKAGE))return HelperCompatibility.exact(version,hash);
        if(pkg.equals("com.android.settings")&&version==36L&&EuBuild.matches(pkg,version,hash))return true;
        String expected=Profiles.apkHash(pkg,version);return expected==null||expected.equals(hash);
    }
    private RuntimeProfile(){}
}
