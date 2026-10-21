package io.github.hyperpriv;

final class Rule {
    final String pkg, cls, method, returns, group, action;
    final long version;
    final String[] params;
    Rule(String p,long v,String c,String m,String r,String g,String a,String[] args) {
        pkg=p;version=v;cls=c;method=m;returns=r;group=g;action=a;params=args;
    }
    String id(){return cls+"#"+method;}
}
