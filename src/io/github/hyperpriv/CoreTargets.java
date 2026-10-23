package io.github.hyperpriv;

/** Exact signatures checked against the device framework; no arbitrary overload hooks. */
final class CoreTargets {
    static final String FINGERPRINT="Xiaomi/babylon/babylon:16/BP2A.250605.031.A3/OS3.0.304.0.WMVCNXM:user/release-keys";
    static final class Target {
        final String cls,method,returns,key,guard,value;
        final String[] params;
        Target(String cls,String method,String returns,String key,String guard,String value,String...params){
            this.cls=cls;this.method=method;this.returns=returns;this.key=key;this.guard=guard;this.value=value;this.params=params;
        }
        String id(){return cls+"#"+method+java.util.Arrays.toString(params);}
    }
    static final Target[] ALL={
        new Target("com.android.server.pm.PackageManagerServiceUtils","checkDowngrade","V","core_downgrade","always","null","Lcom/android/server/pm/PackageSetting;","Landroid/content/pm/PackageInfoLite;"),
        new Target("com.android.server.pm.PackageManagerServiceUtils","checkDowngrade","V","core_downgrade","always","null","Lcom/android/server/pm/pkg/AndroidPackage;","Landroid/content/pm/PackageInfoLite;"),
        new Target("android.content.pm.SigningDetails","checkCapability","Z","core_update_signature","capability","true","Landroid/content/pm/SigningDetails;","I"),
        new Target("android.content.pm.SigningDetails","checkCapabilityRecover","Z","core_update_signature","capability","true","Landroid/content/pm/SigningDetails;","I"),
        new Target("com.android.server.pm.KeySetManagerService","shouldCheckUpgradeKeySetLocked","Z","core_update_signature","keyset_start","true","Lcom/android/server/pm/pkg/PackageStateInternal;","Lcom/android/server/pm/pkg/SharedUserApi;","I"),
        new Target("com.android.server.pm.KeySetManagerService","checkUpgradeKeySetLocked","Z","core_update_signature","keyset_check","true","Lcom/android/server/pm/pkg/PackageStateInternal;","Lcom/android/server/pm/pkg/AndroidPackage;"),
        new Target("android.util.apk.ApkSigningBlockUtils","verifyIntegrity","V","core_apk_integrity","always","null","Ljava/util/Map;","Ljava/io/RandomAccessFile;","Landroid/util/apk/SignatureInfo;"),
        new Target("android.util.jar.StrictJarVerifier","verifyMessageDigest","Z","core_apk_integrity","apk","true","[B","[B"),
        new Target("android.util.jar.StrictJarVerifier","verify","Z","core_apk_integrity","apk","true","Ljava/util/jar/Attributes;","Ljava/lang/String;","[B","I","I","Z","Z"),
        new Target("android.util.apk.ApkSignatureVerifier","getMinimumSignatureSchemeVersionForTargetSdk","I","core_apk_integrity","always","one","I"),
        new Target("com.android.server.pm.ScanPackageUtils","assertMinSignatureSchemeIsValid","V","core_apk_integrity","always","null","Lcom/android/server/pm/pkg/AndroidPackage;","I"),
        new Target("android.content.pm.SigningDetails","signaturesMatchExactly","Z","core_split_signature","split","true","Landroid/content/pm/SigningDetails;"),
        new Target("com.android.server.pm.VerifyingSession","isVerificationEnabled","Z","core_verification_agent","always","false","Landroid/content/pm/PackageInfoLite;","I","Ljava/util/List;"),
        new Target("android.content.res.AssetManager","containsAllocatedTable","Z","core_resources","apk","false")
    };
    private CoreTargets(){}
}
