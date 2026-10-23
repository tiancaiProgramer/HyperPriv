package io.github.hyperpriv;

/** Installation-only decisions, independently testable without an Android runtime. */
final class CorePolicy {
    private CorePolicy(){}
    static boolean updateCapability(int flags){
        // INSTALLED_DATA and ROLLBACK only. Never SHARED_USER_ID, PERMISSION or AUTH,
        // including combinations containing those bits.
        return flags==1||flags==8;
    }
    static boolean installation(StackTraceElement[] stack){
        for(StackTraceElement frame:stack){
            String c=frame.getClassName(),m=frame.getMethodName();
            if(c.equals("com.android.server.pm.PackageManagerServiceUtils")&&m.equals("verifySignatures"))return true;
            if(c.equals("com.android.server.pm.InstallPackageHelper")&&m.equals("preparePackage"))return true;
            if(c.equals("com.android.server.pm.ReconcilePackageUtils")&&m.equals("reconcilePackages"))return true;
        }
        return false;
    }
    static boolean apkParsing(StackTraceElement[] stack){
        for(StackTraceElement frame:stack){
            String c=frame.getClassName();
            if(c.equals("android.util.apk.ApkSignatureVerifier")||c.equals("android.content.pm.PackageParser")
                    ||c.equals("com.android.internal.pm.pkg.parsing.ParsingPackageUtils")
                    ||c.equals("com.android.server.pm.PackageInstallerSession"))return true;
        }
        return false;
    }
    static boolean splitVerification(StackTraceElement[] stack){
        for(StackTraceElement frame:stack){
            String c=frame.getClassName(),m=frame.getMethodName();
            if(c.equals("android.content.pm.SigningDetails")&&m.equals("signaturesMatchExactly"))continue;
            // Only the direct split-consistency caller may bypass exact matching.
            // Nested capability/permission checks must retain the original result.
            if(c.startsWith("android.")||c.startsWith("com.android."))
                return c.equals("com.android.server.pm.PackageInstallerSession")
                    &&(m.equals("assertApkConsistentLocked")||m.equals("validateApkInstallLocked"));
        }
        return false;
    }
}
