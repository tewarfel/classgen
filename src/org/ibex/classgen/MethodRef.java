package org.ibex.classgen;

public class MethodRef extends ClassGen.FieldOrMethodRef {
    public MethodRef(Type.Object c, String name, Type ret, Type[] args) {
        super(c,name,getDescriptor(ret,args));
    }
    public MethodRef(String s, String name, Type ret, Type[] args) {
        this(new Type.Object(s),name,ret,args);
    }
    MethodRef(MethodRef i) { super(i); }
    
    static String getDescriptor(Type ret, Type[] args) {
        StringBuffer sb = new StringBuffer(args.length*4);
        sb.append("(");
        for(int i=0;i<args.length;i++) sb.append(args[i].getDescriptor());
        sb.append(")");
        sb.append(ret.getDescriptor());
        return sb.toString();
    }
    
    public static class I extends MethodRef {
        public I(Type.Object c, String name, Type ret, Type[] args) { super(c,name,ret,args); }
        public I(String s, String name, Type ret, Type[] args) { super(s,name,ret,args); }
        I(MethodRef m) { super(m); }
    }
}
