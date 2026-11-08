package io.github.hyperpriv;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Reads declared DEX methods without loading vendor classes or executing their initializers. */
final class DexEntrances {
    static final class Result {
        final boolean complete;final Set<String> found;
        Result(boolean complete,Set<String> found){this.complete=complete;this.found=Collections.unmodifiableSet(new HashSet<>(found));}
    }
    private static final Map<String,Result> CACHE=new HashMap<>();
    static String signature(Rule r){StringBuilder s=new StringBuilder("L"+r.cls.replace('.','/')+";#"+r.method+"(");for(String p:r.params)s.append(p);return s.append(')').append(r.returns).toString();}
    static synchronized Result inspect(String[] paths,List<Rule> rules){
        Set<String> wanted=new TreeSet<>(),classes=new HashSet<>();
        for(Rule r:rules){wanted.add(signature(r));classes.add("L"+r.cls.replace('.','/')+";");}
        StringBuilder key=new StringBuilder(wanted.toString());
        for(String path:paths){File f=new File(path);key.append('|').append(path).append(':').append(f.length()).append(':').append(f.lastModified());}
        Result cached=CACHE.get(key.toString());if(cached!=null)return cached;
        Set<String> found=new HashSet<>();boolean complete=true,anyDex=false;
        for(String path:paths)try(ZipFile zip=new ZipFile(path)){
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()){
                ZipEntry entry=entries.nextElement();if(!entry.getName().matches("classes(?:[2-9][0-9]*|1[0-9]+)?\\.dex"))continue;
                anyDex=true;
                try(InputStream in=zip.getInputStream(entry);ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[32768];int n;
                    while((n=in.read(buffer))!=-1){if(bytes.size()+n>64*1024*1024)throw new IOException("DEX too large");bytes.write(buffer,0,n);}
                    new Dex(bytes.toByteArray()).find(classes,wanted,found);
                }
            }
        }catch(Exception error){complete=false;}
        Result result=new Result(complete&&anyDex,found);CACHE.put(key.toString(),result);return result;
    }
    private static final class Dex {
        final byte[] data;final int strings,stringOffset,types,typeOffset,protos,protoOffset,methods,methodOffset,classes,classOffset;
        final Map<Integer,String> textCache=new HashMap<>();
        Dex(byte[] data)throws IOException{
            this.data=data;if(data.length<112||data[0]!='d'||data[1]!='e'||data[2]!='x'||data[3]!='\n'||u32(40)!=0x12345678)throw new IOException("Not standard DEX");
            strings=u32(56);stringOffset=u32(60);types=u32(64);typeOffset=u32(68);protos=u32(72);protoOffset=u32(76);methods=u32(88);methodOffset=u32(92);classes=u32(96);classOffset=u32(100);
            table(strings,stringOffset,4);table(types,typeOffset,4);table(protos,protoOffset,12);table(methods,methodOffset,8);table(classes,classOffset,32);
        }
        void range(long at,long size)throws IOException{if(at<0||size<0||at+size>data.length)throw new IOException("Truncated DEX");}
        void table(int count,int offset,int size)throws IOException{range(offset,(long)count*size);}
        int u16(int at)throws IOException{range(at,2);return (data[at]&255)|((data[at+1]&255)<<8);}
        int u32(int at)throws IOException{range(at,4);long value=(data[at]&255L)|((data[at+1]&255L)<<8)|((data[at+2]&255L)<<16)|((data[at+3]&255L)<<24);if(value>Integer.MAX_VALUE)throw new IOException("Unsupported DEX integer");return (int)value;}
        int leb(int[] at)throws IOException{long value=0;for(int i=0;i<5;i++){range(at[0],1);int b=data[at[0]++]&255;value|=(long)(b&127)<<(7*i);if((b&128)==0){if(value>Integer.MAX_VALUE)throw new IOException("ULEB overflow");return (int)value;}}throw new IOException("Bad ULEB");}
        String string(int id)throws IOException{
            if(id<0||id>=strings)throw new IOException("Bad string ID");String cached=textCache.get(id);if(cached!=null)return cached;
            int[] at={u32(stringOffset+4*id)};leb(at);int start=at[0];while(true){range(at[0],1);if(data[at[0]++]==0)break;}
            String text=new String(data,start,at[0]-start-1,StandardCharsets.UTF_8);textCache.put(id,text);return text;
        }
        String type(int id)throws IOException{if(id<0||id>=types)throw new IOException("Bad type ID");return string(u32(typeOffset+4*id));}
        String method(int id)throws IOException{
            if(id<0||id>=methods)throw new IOException("Bad method ID");int at=methodOffset+8*id,proto=u16(at+2);if(proto>=protos)throw new IOException("Bad proto ID");int p=protoOffset+12*proto;
            StringBuilder s=new StringBuilder(type(u16(at))+"#"+string(u32(at+4))+"(");int list=u32(p+8);
            if(list!=0){int n=u32(list);table(n,list+4,2);for(int i=0;i<n;i++)s.append(type(u16(list+4+2*i)));}
            return s.append(')').append(type(u32(p+4))).toString();
        }
        void find(Set<String> wantedClasses,Set<String> wanted,Set<String> found)throws IOException{
            for(int i=0;i<classes;i++){
                int c=classOffset+32*i;if(!wantedClasses.contains(type(u32(c))))continue;
                int offset=u32(c+24);if(offset==0)continue;int[] at={offset};int sf=leb(at),inf=leb(at),direct=leb(at),virtual=leb(at);
                if((long)sf+inf+direct+virtual>data.length)throw new IOException("Bad class data counts");
                for(int j=0;j<sf+inf;j++){leb(at);leb(at);}
                for(int count:new int[]{direct,virtual}){int method=0;for(int j=0;j<count;j++){method=Math.addExact(method,leb(at));leb(at);leb(at);String signature=method(method);if(wanted.contains(signature))found.add(signature);}}
            }
        }
    }
    private DexEntrances(){}
}
