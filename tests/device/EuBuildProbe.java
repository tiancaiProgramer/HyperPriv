package io.github.hyperpriv;
/** Reads public ROM build flags only; does not call package business methods. */
public final class EuBuildProbe {
 public static void main(String[] args)throws Exception{
  Class<?> build=Class.forName("miui.os.Build");
  System.out.println("IS_INTERNATIONAL_BUILD="+build.getField("IS_INTERNATIONAL_BUILD").get(null));
  System.out.println("IS_GLOBAL_BUILD="+build.getField("IS_GLOBAL_BUILD").get(null));
  System.out.println("EU_EXTENSION_MATCH="+EuEnvironment.isEuRom());
  System.out.println("EU_FRAMEWORK_MATCH="+EuEnvironment.systemVerified());
 }
}
