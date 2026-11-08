"""Run pure JVM checks; optional Android/API jars also enable real callback proxy checks."""
import argparse, pathlib, subprocess, os, tempfile

p=argparse.ArgumentParser()
p.add_argument('--jdk',required=True,type=pathlib.Path)
p.add_argument('--android-jar',type=pathlib.Path)
p.add_argument('--libxposed',type=pathlib.Path)
a=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[1]
def exe(name):
    path=a.jdk/'bin'/name
    return str(path if path.is_file() else path.with_suffix('.exe'))
def run(*args):subprocess.run(list(map(str,args)),check=True)
build=root/'local-build';build.mkdir(exist_ok=True)
with tempfile.TemporaryDirectory(prefix='hyperpriv-checks-',dir=build) as directory:
    classes=pathlib.Path(directory)/'pure';classes.mkdir()
    names=['Policy','CorePolicy','ExtraPolicy','Options','ExtraFeatures','UiCatalog','ExtraTargets','Rule','Compatibility','CoreTargets','RomPolicy','EuBuild','Profiles','DexEntrances','FeaturePresence','HelperCompatibility']
    checks=['PolicyCheck','CorePolicyCheck','ExtraPolicyCheck','CompatibilityCheck','RomPolicyCheck','FeaturePresenceCheck','HelperCompatibilityCheck']
    sources=[root/'src/io/github/hyperpriv'/(n+'.java') for n in names]+[root/'tests'/(n+'.java') for n in checks]
    run(exe('javac'),'-encoding','UTF-8','-d',classes,*sources)
    for check in checks:run(exe('java'),'-cp',classes,'io.github.hyperpriv.'+check)
    if bool(a.android_jar)!=bool(a.libxposed):p.error('Pass both --android-jar and --libxposed for callback checks')
    if a.android_jar:
        fixture=pathlib.Path(directory)/'fixture';fixture.mkdir()
        fixture_jar=pathlib.Path(directory)/'fixture.jar'
        run(exe('javac'),'-encoding','UTF-8','-source','8','-target','8','-d',fixture,root/'tests/dex/PresenceFixture.java')
        run(exe('java'),'-cp',a.android_jar.parents[2]/'build-tools/36.0.0/lib/d8.jar','com.android.tools.r8.D8','--lib',a.android_jar,'--output',fixture_jar,*fixture.rglob('*.class'))
        run(exe('javac'),'-encoding','UTF-8','-cp',classes,'-d',classes,root/'tests/DexEntrancesCheck.java')
        run(exe('java'),'-cp',classes,'io.github.hyperpriv.DexEntrancesCheck',fixture_jar,pathlib.Path(directory)/'corrupt.jar')
        deps=[a.android_jar]+[a.libxposed/x/'classes.jar' for x in ('api','service','interface')]
        for dep in deps:
            if not dep.is_file():raise SystemExit('Missing dependency: '+str(dep))
        runtime=pathlib.Path(directory)/'runtime';runtime.mkdir()
        cp=os.pathsep.join(map(str,deps))
        run(exe('javac'),'-encoding','UTF-8','-source','8','-target','8','-classpath',cp,'-d',runtime,*sorted((root/'src').rglob('*.java')),*sorted((root/'tests/host').glob('*.java')))
        run(exe('java'),'-cp',os.pathsep.join([str(runtime),cp]),'io.github.hyperpriv.CompatibilityHooksCheck')
        run(exe('java'),'-cp',os.pathsep.join([str(runtime),cp]),'io.github.hyperpriv.PreferenceCleanupCheck')
        run(exe('java'),'-cp',os.pathsep.join([str(runtime),cp]),'io.github.hyperpriv.UnifiedHooksCheck')
        run(exe('java'),'-cp',os.pathsep.join([str(runtime),cp]),'io.github.hyperpriv.AdaptedCallbacksCheck')
