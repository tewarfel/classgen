package org.ibex.classgen;

public class MethodRef extends ClassGen.FieldMethodRef {
    public MethodRef(Type.Object c, ClassGen.NameAndType t) { super(c,t); }
    public MethodRef(Type.Object c, String name, String descriptor) {
        this(c,new ClassGen.NameAndType(name,descriptor));
    }
    public MethodRef(Type.Object c, String name, Type ret, Type[] args) {
        this(c,name,getDescriptor(ret,args));
    }
    public MethodRef(String s, String name, Type ret, Type[] args) {
        this(new Type.Object(s),name,ret,args);
    }
    
    static String getDescriptor(Type ret, Type[] args) {
        StringBuffer sb = new StringBuffer(args.length*4);
        sb.append("(");
        for(int i=0;i<args.length;i++) sb.append(args[i].getDescriptor());
        sb.append(")");
        sb.append(ret.getDescriptor());
        return sb.toString();
    }
}

