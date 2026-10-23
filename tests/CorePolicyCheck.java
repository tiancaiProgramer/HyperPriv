package io.github.hyperpriv;

public final class CorePolicyCheck {
    private static StackTraceElement frame(String cls,String method){return new StackTraceElement(cls,method,"Test.java",1);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        for(int flags=-1;flags<64;flags++)require(CorePolicy.updateCapability(flags)==(flags==1||flags==8),"unexpected capability bypass "+flags);
        require(!CorePolicy.installation(new StackTraceElement[]{frame("com.android.server.accounts.AccountManagerService","hasSignatureCapability")}),"account auth must stay original");
        require(!CorePolicy.installation(new StackTraceElement[]{frame("com.android.server.permission.access.permission.PermissionService","checkSignaturePermission")}),"permissions must stay original");
        require(CorePolicy.installation(new StackTraceElement[]{frame("com.android.server.pm.PackageManagerServiceUtils","verifySignatures")}),"install signature context missing");
        require(!CorePolicy.installation(new StackTraceElement[]{frame("com.android.server.pm.PackageManagerServiceUtils","unrelated")}),"unrelated PM call bypassed");
        require(CorePolicy.apkParsing(new StackTraceElement[]{frame("android.util.apk.ApkSignatureVerifier","verifySignaturesInternal")}),"APK parsing context missing");
        require(!CorePolicy.apkParsing(new StackTraceElement[]{frame("java.security.MessageDigest","isEqual")}),"general crypto must stay original");
        require(!CorePolicy.apkParsing(new StackTraceElement[0]),"empty context must stay original");
        StackTraceElement split=frame("com.android.server.pm.PackageInstallerSession","assertApkConsistentLocked");
        require(CorePolicy.splitVerification(new StackTraceElement[]{split}),"direct split check must match");
        require(!CorePolicy.splitVerification(new StackTraceElement[]{frame("android.content.pm.SigningDetails","checkCapability"),split}),"nested capability must not inherit split bypass");
        require(!CorePolicy.splitVerification(new StackTraceElement[]{frame("com.android.server.permission.PermissionManagerService","checkPermission"),split}),"nested permissions must not inherit split bypass");
        require(!CorePolicy.splitVerification(new StackTraceElement[]{frame("android.content.pm.SigningInfo","signersMatchExactly"),split}),"public signing queries must stay original");
        java.util.Set<String> keys=new java.util.HashSet<>();
        for(String key:Options.KEYS)require(keys.add(key),"duplicate preference "+key);
        require(keys.size()==61,"preference migration count");
        for(ExtraFeatures.Feature f:ExtraFeatures.ALL)require(!Options.defaultValue(f.key),"new option enabled by default: "+f.key);
        for(String key:Options.CORE_KEYS)require(!Options.defaultValue(key),"new core option enabled by default: "+key);
        require(Options.defaultValue("master")&&Options.defaultValue("ads"),"old defaults changed");
        java.util.Set<String> displayed=new java.util.HashSet<>();int sections=0;
        require(UiCatalog.GROUPS.length==3,"main card count changed");
        for(UiCatalog.Group group:UiCatalog.GROUPS)for(UiCatalog.Section section:group.sections){
            sections++;for(String key:section.keys){require(Options.accepts(key),"unknown UI preference "+key);require(displayed.add(key),"duplicate UI preference "+key);}
        }
        require(sections==11,"submenu grouping changed");
        keys.remove("master");require(displayed.equals(keys),"missing or orphaned UI preferences");
        for(Rule rule:ExtraTargets.ALL)require(Options.accepts(rule.group),"unreachable hook preference "+rule.group);
        System.out.println("Core policy: install-only context, capability boundaries and default-off migration passed");
    }
}
