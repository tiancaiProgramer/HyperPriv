package io.github.hyperpriv;

public final class CorePolicyCheck {
    private static StackTraceElement frame(String cls,String method){return new StackTraceElement(cls,method,"Test.java",1);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        require(Appearance.homeScaleLimit(64,92)==128&&Appearance.homeScaleLimit(64,150)==150
                &&Appearance.homeScaleLimit(64,52)==100&&Appearance.homeScaleLimit(0,100)==100,
                "launcher menu cap must follow the grid used by the icon hook");
        require(Appearance.pillTop(187,5,200,-10,2)==195
                &&Appearance.pillTop(187,5,200,10,2)==179
                &&Appearance.pillTop(170,5,200,Appearance.Value.PILL_Y.min,1)==195
                &&Appearance.pillTop(170,5,200,Appearance.Value.PILL_Y.max,1)==145
                &&Appearance.pillTop(195,5,200,-12,2)==195,
                "pill edge settings must use the current frame and keep the handle inside it");
        require(Appearance.Value.CLOCK_POSITION.normalize(3)==0
                &&Appearance.Value.CLOCK_POSITION.normalize(1)==1
                &&Appearance.Value.CLOCK_POSITION.normalize(2)==2,
                "removed clock position must use the default and keep the supported positions");
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
        require(CorePolicy.splitVerification(new StackTraceElement[]{frame("android.content.pm.Signature","areExactMatch"),frame("android.content.pm.SigningDetails","signaturesMatchExactly"),split}),"exact signature helper chain must retain split context");
        require(CorePolicy.splitVerification(new StackTraceElement[]{frame("android.graphics.FrameworkBridge","signaturesMatchExactly"),split}),"framework bridge hid the direct split caller");
        require(CorePolicy.splitVerification(new StackTraceElement[]{frame("android.location.RandomBridge","signaturesMatchExactly"),split}),"randomized bridge package hid split caller");
        require(!CorePolicy.splitVerification(new StackTraceElement[]{frame("android.graphics.FrameworkBridge","signaturesMatchExactly"),frame("android.content.pm.SigningDetails","checkCapability"),split}),"bridge skipped nested capability checks");
        require(!CorePolicy.splitVerification(new StackTraceElement[]{frame("android.graphics.Unrelated","checkPermission"),split}),"unrelated graphics frame was skipped");
        require(!CorePolicy.splitVerification(new StackTraceElement[]{frame("android.content.pm.Signature","areExactMatch"),frame("android.content.pm.SigningDetails","checkCapability"),split}),"signature helper must not bypass nested capability checks");
        require(!CorePolicy.splitVerification(new StackTraceElement[]{frame("android.content.pm.SigningDetails","checkCapability"),split}),"nested capability must not inherit split bypass");
        require(!CorePolicy.splitVerification(new StackTraceElement[]{frame("com.android.server.permission.PermissionManagerService","checkPermission"),split}),"nested permissions must not inherit split bypass");
        require(!CorePolicy.splitVerification(new StackTraceElement[]{frame("android.content.pm.SigningInfo","signersMatchExactly"),split}),"public signing queries must stay original");
        java.util.Set<String> keys=new java.util.HashSet<>();
        for(String key:Options.KEYS)require(keys.add(key),"duplicate preference "+key);
        require(keys.size()==65+Appearance.KEYS.length,"preference migration count");
        for(String key:Appearance.KEYS)require(keys.contains(key)&&!Options.defaultValue(key),"new appearance option defaults off");
        java.util.Set<String> allPreferences=new java.util.HashSet<>(keys);
        for(Appearance.Value value:Appearance.Value.ALL)require(allPreferences.add(value.key),"duplicate appearance parameter "+value.key);
        require(Appearance.Value.NOTIFICATIONS.normalize(3)==0&&Appearance.Value.NOTIFICATIONS.normalize(4)==4
                &&Appearance.Value.NOTIFICATIONS.max==9&&Appearance.Value.NOTIFICATIONS.normalize(9)==9
                &&Appearance.Value.NOTIFICATIONS.normalize(10)==9&&Appearance.Value.NOTIFICATIONS.normalize(15)==9
                &&Appearance.Value.NOTIFICATIONS.normalize(16)==0,
                "notification custom range");
        require(Appearance.Value.SIGNAL.normalize(3)==2&&Appearance.Value.SIGNAL.normalize(4)==2
                &&Appearance.Value.SIGNAL.choices[3]==null&&Appearance.Value.SIGNAL.choices[4]==null
                &&Appearance.Value.SIGNAL.normalize(Appearance.DUO_SIGNAL_STYLE)==Appearance.DUO_SIGNAL_STYLE,
                "removed dual-dot styles migrate to thick split bars and preserve Duo");
        require(Appearance.Value.BATTERY.choices[Appearance.LEGACY_BATTERY_FRAME_STYLE]==null
                &&Appearance.Value.BATTERY.normalize(Appearance.LEGACY_BATTERY_FRAME_STYLE)==Appearance.BATTERY_PERCENT_STYLE
                &&Appearance.migrateBatteryFrameStyle(1)==Appearance.BATTERY_PERCENT_STYLE
                &&Appearance.migrateBatteryFrameStyle(3)==Appearance.BATTERY_PERCENT_STYLE
                &&Appearance.migrateBatteryFrameStyle(0)==Appearance.BATTERY_HIDDEN_STYLE
                &&Appearance.migrateBatteryFrameStyle(2)==Appearance.BATTERY_HIDDEN_STYLE,
                "legacy battery frame migrates according to native number visibility");
        require(Appearance.Value.HOME_SCALE.normalize(150)==150&&Appearance.Value.HOME_SCALE.normalize(151)==100
                &&Appearance.Value.PILL_Y.normalize(-6)==-6&&Appearance.Value.PILL_Y.normalize(6)==6
                &&Appearance.Value.PILL_Y.normalize(-12)==-12&&Appearance.Value.PILL_Y.normalize(12)==12
                &&Appearance.Value.PILL_Y.display(Appearance.Value.PILL_Y.min).equals("贴底")
                &&Appearance.Value.PILL_Y.display(-3).startsWith("下移")
                &&Appearance.Value.PILL_Y.display(3).startsWith("上移"),
                "appearance ranges include 150 percent icons and two-way gesture offset");
        require(Options.defaultValue(Compatibility.KEY),"compatibility is not enabled by default");
        for(ExtraFeatures.Feature f:ExtraFeatures.ALL)require(!Options.defaultValue(f.key),"new option enabled by default: "+f.key);
        for(String key:Options.CORE_KEYS)require(!Options.defaultValue(key),"new core option enabled by default: "+key);
        require(!Options.defaultValue("key_actions"),"key remapping must default off");
        require(Options.defaultValue("master")&&Options.defaultValue("ads"),"old defaults changed");
        java.util.Set<String> displayed=new java.util.HashSet<>();int sections=0;
        require(UiCatalog.GROUPS.length==4,"main card count changed");
        for(UiCatalog.Group group:UiCatalog.GROUPS)for(UiCatalog.Section section:group.sections){
            sections++;for(String key:section.keys){require(Options.accepts(key),"unknown UI preference "+key);require(displayed.add(key),"duplicate UI preference "+key);}
        }
        require(sections==15,"submenu grouping changed");
        keys.remove("master");keys.remove(Compatibility.KEY);require(displayed.equals(keys),"missing or orphaned UI preferences");
        for(Rule rule:ExtraTargets.ALL)require(Options.accepts(rule.group),"unreachable hook preference "+rule.group);
        System.out.println("Core policy: install-only context, capability boundaries and default-off migration passed");
    }
}

