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
  Path corrupt=Paths.get(args[1]);try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(corrupt))){zip.putNextEntry(new ZipEntry("classes.dex"));zip.write(new byte[]{100,101,120,10});zip.closeEntry();}
  require(!DexEntrances.inspect(new String[]{corrupt.toString()},rules).complete,"corrupt DEX treated as proof of absence");
  require(!DexEntrances.inspect(new String[]{args[0]+".missing"},rules).complete,"read failure treated as absence");
  scan=DexEntrances.inspect(new String[]{args[0],corrupt.toString()},rules);require(!scan.complete&&scan.found.contains(DexEntrances.signature(present)),"partial read lost known match or asserted complete");
  System.out.println("DEX entrance scan: declared methods only, exact parameters/returns, constructors, corrupt/missing and partial inputs passed");
 }
}
