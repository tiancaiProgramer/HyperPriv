package io.github.hyperpriv;

/** Exact local EU APK/framework fingerprints; no preference can relax these checks. */
final class EuBuild {
static final String EXT_HASH="dc1d8588baf4ac33bcb4d1a64338942abccbcf70e275d8ecb31f0c9c56af00fe";
static final String[] SYSTEM_PATHS={"/system/framework/services.jar","/system/framework/framework.jar","/system_ext/framework/miui-services.jar"};
static final String[] SYSTEM_HASHES={"3247e260f765dce05f5324e5106727314d22f691043643fd8b1fd15a136dc092","c483a1ba755aa834379d5e77eeff874e77e36cd956eb3ed0b9b649dcee215e88","41466bd4d48c1464abe6cca91a409543b823803104198e99390e66fa99ba6c4f"};
static String expectedHash(String pkg,long version){
if(pkg.equals("com.miui.packageinstaller")&&version==55500L)return "6d00f8e114cb14e080257b7d267587e0a8942861b40900a277f3217e0773d414";
if(pkg.equals("com.miui.securitycenter")&&version==40001283L)return "43fb27795d9934490c896ae983baab82a790dec29b7f263286c58e6464f439d3";
if(pkg.equals("com.android.settings")&&version==36L)return "d7f1315084fcacca5ca0eccd11d1e70e2a36dc60c75a3c005846192e95849890";
if(pkg.equals("com.miui.powerkeeper")&&version==40200L)return "bdcc3f8238cc71d4e41ed7bff05088d17be3b8a1bcf5ccfb1760a2b84449ed68";
if(pkg.equals("com.xiaomi.joyose")&&version==514L)return "399db5119c6d7f5799723da63c13d52bafef535bd5a1f7667f6d989e43ed1e58";
if(pkg.equals("com.lbe.security.miui")&&version==192L)return "1da3d7e9d75f5c266315fd7eac9e53b092007a88c35c7879118e33f13d1d4299";
return null;
}
static boolean matches(String pkg,long version,String hash){String expected=expectedHash(pkg,version);return expected!=null&&expected.equals(hash);}
private EuBuild(){}
}
