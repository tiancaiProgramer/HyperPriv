package io.github.hyperpriv;

import java.util.*;

public final class CompatibilityCheck {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static Rule rule(String pkg,long version,String method,String group,String action,String...params){return new Rule(pkg,version,"example.Target",method,"Z",group,action,params);}
    public static void main(String[] args){
        Rule old=rule("pkg",10,"old","ads","false"),latest=rule("pkg",20,"latest","ads","false"),other=rule("other",99,"other","ads","false");
        Rule[] all={old,other,latest};
        require(Compatibility.select(all,"pkg",30,false).isEmpty(),"strict mode widened unknown version");
        require(Compatibility.select(all,"pkg",30,true).equals(Arrays.asList(latest)),"fallback mixed profile versions or packages");
        require(Compatibility.select(all,"pkg",10,true).equals(Arrays.asList(old)),"exact profile lost priority");
        require(Compatibility.select(all,"missing",30,true).isEmpty(),"missing component invented rules");
        Rule conflict=rule("pkg",20,"latest","upload","true");
        require(Compatibility.select(new Rule[]{latest,latest,conflict},"pkg",30,true).isEmpty(),"conflicting handlers were installed");
        Rule overload=rule("pkg",20,"latest","ads","false","I");
        require(Compatibility.select(new Rule[]{latest,latest,overload},"pkg",30,true).size()==2,"duplicate/overload selection failed");
        require(!Compatibility.supports(ExtraTargets.ALL,"guard_environment","com.miui.guardprovider",999,true),"unimplemented guard Root feature unlocked");
        require(!Compatibility.supports(ExtraTargets.ALL,"safepay_scan","com.miui.securitycenter",999,false),"unknown version enabled without opt-in");
        require(Compatibility.supports(ExtraTargets.ALL,"safepay_scan","com.miui.securitycenter",999,true),"known payment definitions unavailable for trial");
        require(Compatibility.strictSystem(36,CoreTargets.FINGERPRINT),"known build rejected");
        require(!Compatibility.strictSystem(36,"different-device")&&!Compatibility.strictSystem(35,CoreTargets.FINGERPRINT),"default build guard widened");
        require(!Options.defaultValue(Compatibility.KEY)&&Options.accepts(Compatibility.KEY),"compatibility preference default/persistence invalid");
        System.out.println("Compatibility selection: opt-in, exact-profile priority, isolated fallback, conflicts and missing features passed");
    }
}
