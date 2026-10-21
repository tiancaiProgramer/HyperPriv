#!/usr/bin/env python3
"""Build against JDK 17, Android SDK 35/36, libxposed API/service/interface 102.
The API is compile-only. Only the service/interface runtime is packaged.
"""
import argparse,os,shutil,subprocess,zipfile
from pathlib import Path
p=argparse.ArgumentParser()
for key in ('jdk','sdk','libxposed','keystore','out'):p.add_argument('--'+key,required=True,type=Path)
p.add_argument('--password-env',default='HYPERPRIV_SIGN_PASSWORD')
p.add_argument('--build-dir',type=Path)
a=p.parse_args();root=Path(__file__).resolve().parent;build=a.build_dir or root/'build';tools=a.sdk/'build-tools/36.0.0';android=a.sdk/'platforms/android-35/android.jar'
api=a.libxposed/'api/classes.jar';runtime=[a.libxposed/'service/classes.jar',a.libxposed/'interface/classes.jar']
def run(*args):subprocess.run(list(map(str,args)),check=True)
for file in [android,api,*runtime,a.keystore]:
    if not file.is_file():raise FileNotFoundError(file)
for n in ('classes','dex'):
    shutil.rmtree(build/n,ignore_errors=True);(build/n).mkdir(parents=True)
run(a.jdk/'bin/javac','-encoding','UTF-8','-source','8','-target','8','-classpath',os.pathsep.join(map(str,[android,api,*runtime])),'-d',build/'classes',*sorted((root/'src').rglob('*.java')))
with zipfile.ZipFile(build/'code.jar','w')as z:
    for f in sorted((build/'classes').rglob('*.class')):z.write(f,f.relative_to(build/'classes'))
run(a.jdk/'bin/java','-cp',tools/'lib/d8.jar','com.android.tools.r8.D8','--lib',android,'--classpath',api,'--min-api','28','--output',build/'dex',build/'code.jar',*runtime)
run(tools/'aapt2','compile','--dir',root/'res','-o',build/'resources.zip')
run(tools/'aapt2','link','-I',android,'--manifest',root/'AndroidManifest.xml','-o',build/'base.apk',build/'resources.zip')
with zipfile.ZipFile(build/'base.apk','a',compression=zipfile.ZIP_DEFLATED)as z:
    for f in (build/'dex').glob('*.dex'):z.write(f,f.name)
    for f in sorted((root/'resources').rglob('*')):
        if f.is_file():z.write(f,f.relative_to(root/'resources'))
run(tools/'zipalign','-f','4',build/'base.apk',build/'aligned.apk')
run(a.jdk/'bin/java','-jar',tools/'lib/apksigner.jar','sign','--ks',a.keystore,'--ks-pass','env:'+a.password_env,'--ks-key-alias','hyperpriv','--out',a.out,build/'aligned.apk')
run(a.jdk/'bin/java','-jar',tools/'lib/apksigner.jar','verify',a.out)
print('Built',a.out)
