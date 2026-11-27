package io.github.hyperpriv;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
public final class DexEntrancesCheck {
 static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
 static Rule rule(String cls,String method,String returns,String...params){return new Rule("fixture",1,cls,method,returns,"cloud","null",params);}
 public static void main(String[] args)throws Exception{
  Rule present=rule("io.github.hyperpriv.fixture.PresenceFixture","existing","I","Ljava/lang/String;"),ctor=rule("io.github.hyperpriv.fixture.PresenceFixture","<init>","V"),wrongReturn=rule(present.cls,"existing","Z","Ljava/lang/String;"),wrongParam=rule(present.cls,"existing","I","I"),reference=rule("java.lang.Integer","parseInt","I","Ljava/lang/String;");
  List<Rule> rules=Arrays.asList(present,ctor,wrongReturn,wrongParam,reference);
  DexEntrances.Result scan=DexEntrances.inspect(new String[]{args[0]},rules);
  require(scan.complete&&scan.found.size()==2&&scan.found.contains(DexEntrances.signature(present))&&scan.found.contains(DexEntrances.signature(ctor)),"declared method matching failed, references/overloads must not count");
  String field=DexEntrances.fieldSignature(present.cls,"marker","I");
  String wrongField=DexEntrances.fieldSignature(present.cls,"marker","Z");
  String foreignField=DexEntrances.fieldSignature("java.lang.Integer","MAX_VALUE","I");
  scan=DexEntrances.inspect(new String[]{args[0]},rules,Arrays.asList(field,wrongField,foreignField));
  require(scan.complete&&scan.found.size()==3&&scan.found.contains(field),"declared field or field type matching failed");
  Path corrupt=Paths.get(args[1]);try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(corrupt))){zip.putNextEntry(new ZipEntry("classes.dex"));zip.write(new byte[]{100,101,120,10});zip.closeEntry();}
  require(!DexEntrances.inspect(new String[]{corrupt.toString()},rules).complete,"corrupt DEX treated as proof of absence");
  require(!DexEntrances.inspect(new String[]{args[0]+".missing"},rules).complete,"read failure treated as absence");
  scan=DexEntrances.inspect(new String[]{args[0],corrupt.toString()},rules);require(!scan.complete&&scan.found.contains(DexEntrances.signature(present)),"partial read lost known match or asserted complete");
  System.out.println("DEX entrance scan: declared methods only, exact parameters/returns, constructors, corrupt/missing and partial inputs passed");

  AppearanceSupport.StatusMatch status=AppearanceSupport.matchStatus(37,1,"",args[0],null,true);
  require(status.match==AppearanceSupport.Match.COMPATIBLE
      &&status.parts.equals(EnumSet.of(AppearanceSupport.StatusPart.NOTIFICATION_LIMIT)),
      "notification limit must match without a battery or phone-bar inflation callback");
  require(status.supports(Appearance.Value.NOTIFICATIONS)&&!status.supports(Appearance.Value.BATTERY)
      &&!status.supports(Appearance.Value.TOP_BAR_PERCENT)
      &&status.supports(Appearance.Value.PILL_OPACITY),"unsupported status settings must be excluded independently");
  require(status.supportsBatteryStyle(0)&&!status.supportsBatteryStyle(Appearance.BATTERY_CUTOUT_STYLE)
      &&!status.supportsBatteryStyle(Appearance.BATTERY_TOP_STYLE),"unmatched battery styles must fall back to system");
  require(AppearanceSupport.matchStatus(37,1,"",args[0],null,false).match==AppearanceSupport.Match.NONE,
      "structural match requires experimental compatibility");
  require(AppearanceSupport.matchStatus(37,1,"",corrupt.toString(),null,true).match==AppearanceSupport.Match.NONE,
      "unreadable APK must not enable experimental status hooks");
  status=AppearanceSupport.matchStatus(36,202501210L,
      "0ac0c967145dd5cda61b682525ed78d6d25c8abc31e8e32789138bfb2813ebac",args[0],null,false);
  require(status.match==AppearanceSupport.Match.EXACT
      &&status.parts.equals(EnumSet.allOf(AppearanceSupport.StatusPart.class)),
      "verified SystemUI must keep all status options when experimental compatibility is off");
  require(status.supportsBatteryStyle(Appearance.BATTERY_TOP_STYLE)
      &&status.supports(Appearance.Value.TOP_BAR_PERCENT)
      &&status.supportsBatteryStyle(Appearance.BATTERY_CUTOUT_STYLE),"verified battery styles must appear in the selector");
  System.out.println("Status bar compatibility: independent options, experimental switch, unreadable APK and exact profile passed");
 }
}
