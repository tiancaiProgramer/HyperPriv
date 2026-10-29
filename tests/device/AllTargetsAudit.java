package io.github.hyperpriv;

import dalvik.system.PathClassLoader;
import java.lang.reflect.*;
import java.util.*;

/** Read-only signature checks in an independent process; does not prove live injection. */
public final class AllTargetsAudit {
    public static void main(String[] args)throws Exception{
        Class<?> vm=Class.forName("dalvik.system.VMRuntime");
        Method get=vm.getDeclaredMethod("getRuntime"),ex=vm.getDeclaredMethod("setHiddenApiExemptions",String[].class);
        get.setAccessible(true);ex.setAccessible(true);ex.invoke(get.invoke(null),(Object)new String[]{"L"});
        ClassLoader system=new PathClassLoader("/system/framework/services.jar:/system_ext/framework/miui-services.jar",AllTargetsAudit.class.getClassLoader());
        int total=0,failed=0;
        for(String spec:args){
            String[] parts=spec.split("=",3);String pkg=parts[0];long version=Long.parseLong(parts[1]);
            ClassLoader loader=new PathClassLoader(parts[2],system);
            for(Rule[] rules:new Rule[][]{Profiles.ALL,ExtraTargets.ALL}){
                for(Rule r:Compatibility.select(rules,pkg,version,false)){
                    total++;
                    try{Reflect.executable(r,loader);System.out.println("MATCH "+r.group+" "+r.id());}
                    catch(Throwable error){failed++;System.out.println("FAIL "+r.group+" "+r.id()+" "+error);error.printStackTrace(System.out);}
                }
            }
        }
        for(Rule r:ExtraTargets.ALL)if(r.pkg.equals("android")){
            total++;try{Reflect.executable(r,system);System.out.println("MATCH "+r.group+" "+r.id());}
            catch(Throwable error){failed++;System.out.println("FAIL "+r.group+" "+r.id()+" "+error);error.printStackTrace(System.out);}
        }
        for(CoreTargets.Target t:CoreTargets.ALL){
            total++;Rule r=new Rule("android",36,t.cls,t.method,t.returns,t.key,t.value,t.params);
            try{Reflect.executable(r,system);System.out.println("MATCH "+t.key+" "+t.id());}
            catch(Throwable error){failed++;System.out.println("FAIL "+t.key+" "+t.id()+" "+error);error.printStackTrace(System.out);}
        }
        System.out.println("All-target reflective audit total="+total+" matched="+(total-failed)+" failed="+failed);
        if(failed!=0)System.exit(2);
    }
}
