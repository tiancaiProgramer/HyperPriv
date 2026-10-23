package io.github.hyperpriv;

import java.lang.reflect.*;

final class Reflect {
    private Reflect(){}
    static Field field(Class<?> cls,String name)throws NoSuchFieldException{
        for(Class<?> c=cls;c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    static Object get(Object target,String name)throws Exception{return field(target instanceof Class?(Class<?>)target:target.getClass(),name).get(target instanceof Class?null:target);}
    static void set(Object target,String name,Object value)throws Exception{field(target instanceof Class?(Class<?>)target:target.getClass(),name).set(target instanceof Class?null:target,value);}
    static Object call(Object target,String name,Object...args)throws Exception{
        Class<?> cls=target instanceof Class?(Class<?>)target:target.getClass();
        Method found=null;
        for(Class<?> c=cls;c!=null&&found==null;c=c.getSuperclass())for(Method m:c.getDeclaredMethods()){
            if(!m.getName().equals(name)||m.getParameterTypes().length!=args.length)continue;
            Class<?>[] params=m.getParameterTypes();boolean match=true;
            for(int i=0;i<params.length;i++)if(args[i]!=null&&!boxed(params[i]).isInstance(args[i])){match=false;break;}
            if(match){found=m;break;}
        }
        if(found==null)for(Method m:cls.getMethods())if(m.getName().equals(name)&&m.getParameterTypes().length==args.length){
            Class<?>[] params=m.getParameterTypes();boolean match=true;
            for(int i=0;i<params.length;i++)if(args[i]!=null&&!boxed(params[i]).isInstance(args[i])){match=false;break;}
            if(match){found=m;break;}
        }
        if(found==null)throw new NoSuchMethodException(cls.getName()+"#"+name);
        found.setAccessible(true);return found.invoke(target instanceof Class?null:target,args);
    }
    private static Class<?> boxed(Class<?> c){
        if(!c.isPrimitive())return c;
        if(c==boolean.class)return Boolean.class;if(c==int.class)return Integer.class;if(c==long.class)return Long.class;
        if(c==float.class)return Float.class;if(c==double.class)return Double.class;if(c==byte.class)return Byte.class;
        if(c==short.class)return Short.class;if(c==char.class)return Character.class;return Void.class;
    }
    static Class<?> type(String d,ClassLoader loader)throws ClassNotFoundException{
        switch(d){case "V":return void.class;case "Z":return boolean.class;case "I":return int.class;case "J":return long.class;case "B":return byte.class;case "S":return short.class;case "F":return float.class;case "D":return double.class;case "C":return char.class;}
        return Class.forName(d.charAt(0)=='L'?d.substring(1,d.length()-1).replace('/','.'):d.replace('/','.'),false,loader);
    }
    static Executable executable(Rule r,ClassLoader loader)throws Exception{
        Class<?> cls=Class.forName(r.cls,false,loader);Class<?>[] params=new Class<?>[r.params.length];
        for(int i=0;i<params.length;i++)params[i]=type(r.params[i],loader);
        Executable e;
        if(r.method.equals("<init>"))e=cls.getDeclaredConstructor(params);
        else {Method m=cls.getDeclaredMethod(r.method,params);if(m.getReturnType()!=type(r.returns,loader))throw new NoSuchMethodException("return type");e=m;}
        e.setAccessible(true);return e;
    }
}
