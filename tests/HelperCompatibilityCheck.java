package io.github.hyperpriv;
import java.util.*;
public final class HelperCompatibilityCheck {
 static void require(boolean v,String message){if(!v)throw new AssertionError(message);}
 public static void main(String[] args){
  HelperCompatibility.Match cn=HelperCompatibility.choose(34,HelperCompatibility.CN34_APK,"",false),eu=HelperCompatibility.choose(34,Profiles.apkHash(HelperCompatibility.PACKAGE,34),"",false);
  require(cn!=null&&!cn.experimental&&cn.rules.length==25,"known CN34 not mapped independently");
  require(eu!=null&&!eu.experimental&&eu.rules.length==26,"known EU34 changed");
  require(HelperCompatibility.choose(31,Profiles.apkHash(HelperCompatibility.PACKAGE,31),"",false).rules.length==22,"CN31 lost");
  require(HelperCompatibility.choose(34,"other",HelperCompatibility.CN34_DEX,false)==null,"manual compatibility off ignored");
  HelperCompatibility.Match variant=HelperCompatibility.choose(99,"other",HelperCompatibility.CN34_DEX,true);
  require(variant!=null&&variant.experimental&&variant.rules.length==25,"same-code compatibility used wrong profile");
  require(HelperCompatibility.choose(34,"other","unknown-code",true)==null,"matching version code must not authorize unknown code");
  require(HelperCompatibility.choose(99,"other","",true)==null,"unreadable code accepted");
  Set<String> cnMethods=new HashSet<>(),euMethods=new HashSet<>();for(Rule r:cn.rules)cnMethods.add(r.cls+"#"+r.method);for(Rule r:eu.rules)euMethods.add(r.cls+"#"+r.method);
  require(cnMethods.contains("f1.o#d")&&cnMethods.contains("g1.f#b")&&!cnMethods.contains("f1.p#d")&&!cnMethods.contains("g1.f#f"),"CN34 mixed with EU entry points");
  require(euMethods.contains("f1.p#d")&&euMethods.contains("g1.f#f")&&!euMethods.contains("f1.o#d"),"EU34 mixed with CN entry points");
  System.out.println("SystemHelper compatibility: CN31/CN34/EU34 isolated, same-DEX variants, manual off and changed/unreadable code rejection passed");
 }
}
