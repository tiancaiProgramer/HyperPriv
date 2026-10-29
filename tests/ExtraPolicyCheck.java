package io.github.hyperpriv;

public final class ExtraPolicyCheck {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static StackTraceElement frame(String c,String m){return new StackTraceElement(c,m,"Test",1);}
    public static void main(String[] args){
        for(int op=-1;op<150;op++)require(ExtraPolicy.unknownInstallOp(op)==(op==66),"unrelated AppOp "+op);
        for(int op=-1;op<150;op++)require(ExtraPolicy.clipboardReadOp(op)==(op==29),"unrelated clipboard operation "+op);
        for(int flags=-1;flags<64;flags++)require(ExtraPolicy.sharedCapability(flags)==(flags==2),"unrelated signature capability "+flags);
        require(!ExtraPolicy.clipboardAllowed("","com.example.app"),"empty whitelist grants access");
        require(ExtraPolicy.clipboardAllowed("com.example.app\ncom.example.two","com.example.app"),"explicit whitelist rejected");
        require(!ExtraPolicy.clipboardAllowed("com.example.app","com.example.app.other"),"prefix whitelist grants access");
        for(String invalid:new String[]{"*","com.example.*","com.example.app|*","com/example/app"}){
            require(!ExtraPolicy.clipboardAllowed(invalid,"com.example.app"),"invalid wildcard list grants access");
        }
        require(ExtraPolicy.normalizePackages("com.example.app, com.example.app;com.example.two").equals("com.example.app\ncom.example.two"),"whitelist normalization");
        require(ExtraPolicy.restoreFlag(ExtraPolicy.LOW_SDK_FLAG|128,0,ExtraPolicy.LOW_SDK_FLAG)==128,"restore overwrote unrelated install flags");
        require(ExtraPolicy.restoreFlag(ExtraPolicy.LOW_SDK_FLAG|128,ExtraPolicy.LOW_SDK_FLAG,ExtraPolicy.LOW_SDK_FLAG)==(ExtraPolicy.LOW_SDK_FLAG|128),"restore removed originally set flag");
        require(ExtraPolicy.parsingSplit(new StackTraceElement[]{frame("com.android.internal.pm.pkg.parsing.ParsingPackageUtils","getSigningDetails")}),"split collection context missing");
        require(ExtraPolicy.parsingSplit(new StackTraceElement[]{frame("android.graphics.FrameworkBridge","areExactMatch"),frame("com.android.internal.pm.pkg.parsing.ParsingPackageUtils","getSigningDetails")}),"framework bridge hid parser caller");
        require(!ExtraPolicy.parsingSplit(new StackTraceElement[]{frame("android.content.pm.SigningDetails","checkCapability"),frame("com.android.internal.pm.pkg.parsing.ParsingPackageUtils","getSigningDetails")}),"nested capability inherited split bypass");
        for(int id=0;id<20;id++)require(ExtraPolicy.environmentTask(id)==(id==10||id>=13&&id<=16),"unrelated environment request blocked");
        System.out.println("Extra policy: AppOp isolation, exact whitelist, signature boundaries, request selection and flag restoration passed");
    }
}

