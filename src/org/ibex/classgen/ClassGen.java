package org.ibex.classgen;

import java.util.*;
import java.io.*;

public class ClassGen implements CGConst {
    private Type.Object thisType;
    private Type.Object superType;
    int flags;
    
    private Vector interfaces = new Vector();
    private Vector fields = new Vector();
    private Vector methods = new Vector();
    
    final CPGen cp;
    private final AttrGen attributes;
    
    public ClassGen(String name, String superName, int flags) {
        this(new Type.Object(name),new Type.Object(superName),flags);
    }
    
    public ClassGen(Type.Object thisType,Type.Object superType, int flags) {
        if((flags & ~(ACC_PUBLIC|ACC_FINAL|ACC_SUPER|ACC_INTERFACE|ACC_ABSTRACT)) != 0)
            throw new IllegalArgumentException("invalid flags");
        this.thisType = thisType;
        this.superType = superType;
        this.flags = flags;
        
        cp = new CPGen();
        attributes = new AttrGen(cp);
    }
    
    public final MethodGen addMethod(String name, Type ret, Type[] args, int flags) {
        MethodGen mg = new MethodGen(this,name,ret,args,flags);
        methods.addElement(mg);
        return mg;
    }
    
    public final FieldGen addField(String name, Type type, int flags) {
        FieldGen fg = new FieldGen(this,name,type,flags);
        fields.addElement(fg);
        return fg;
    }
    
    public void dump(String s) throws IOException { dump(new File(s)); }
    public void dump(File f) throws IOException {
        if(f.isDirectory()) {
            String[] a = thisType.components();
            int i;
            for(i=0;i<a.length-1;i++) {
                f = new File(f,a[i]);
                f.mkdir();
            }
            f = new File(f,a[i] + ".class");
        }
        dump(new FileOutputStream(f));
    }
    public void dump(OutputStream os) throws IOException {
        DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(os));
        _dump(dos);
        dos.flush();
    }
    
    private void _dump(DataOutput o) throws IOException {
        cp.add(thisType);
        cp.add(superType);
        for(int i=0;i<interfaces.size();i++) cp.add((Type.Object)interfaces.elementAt(i));
        for(int i=0;i<methods.size();i++) ((MethodGen)methods.elementAt(i)).finish();
        cp.seal();
        
        o.writeInt(0xcafebabe); // magic
        o.writeShort(3); // minor_version
        o.writeShort(45); // major_version
        
        o.writeShort(cp.size()); // constant_pool_count
        cp.dump(o); // constant_pool
        
        o.writeShort(flags);
        o.writeShort(cp.getIndex(thisType)); // this_class
        o.writeShort(cp.getIndex(superType)); // super_class
        
        o.writeShort(interfaces.size()); // interfaces_count
        for(int i=0;i<interfaces.size();i++) o.writeShort(cp.getIndex(interfaces.elementAt(i))); // interfaces
        
        o.writeShort(fields.size()); // fields_count
        for(int i=0;i<fields.size();i++) ((FieldGen)fields.elementAt(i)).dump(o); // fields

        o.writeShort(methods.size()); // methods_count
        for(int i=0;i<methods.size();i++) ((MethodGen)methods.elementAt(i)).dump(o); // methods
        
        o.writeShort(attributes.size()); // attributes_count
        attributes.dump(o); // attributes        
    }
    
    // FEATURE: Make some of these checked exceptions?
    public static class Exn extends RuntimeException {
        public Exn(String s) { super(s); }
    }

    // FEATURE: Remove these - they are just here to be compatible with the old api
    public final static MethodRef methodRef(Type.Object c, String name, Type ret, Type[] args) {        
        return new MethodRef(c,name,ret,args);
    }
    public final static FieldRef fieldRef(Type.Object c, String name, Type type) {
        return new FieldRef(c,name,type);
    }
    
    public static class NameAndType {
        String name;
        String type;
        NameAndType(String name, String type) { this.name = name; this.type = type; }
        public boolean equals(Object o_) {
            if(!(o_ instanceof NameAndType)) return false;
            NameAndType o = (NameAndType) o_;
            return o.name.equals(name) && o.type.equals(type);
        }
        public int hashCode() { return name.hashCode() ^ type.hashCode(); }
    }
    
    public static abstract class FieldMethodRef {
        Type.Object klass;
        NameAndType nameAndType;
        FieldMethodRef(Type.Object klass, NameAndType nameAndType) { this.klass = klass; this.nameAndType = nameAndType; }
        public boolean equals(Object o_) {
            if(!(o_ instanceof FieldMethodRef)) return false;
            FieldMethodRef o = (FieldMethodRef) o_;
            return o.klass.equals(klass) && o.nameAndType.equals(nameAndType);
        }
        public int hashCode() { return klass.hashCode() ^ nameAndType.hashCode(); }
    }
    
    public static class InterfaceMethodRef extends FieldMethodRef {
        public InterfaceMethodRef(MethodRef r) { super(r.klass,r.nameAndType); }
        public InterfaceMethodRef(Type.Object c, NameAndType t) { super(c,t); }
    }
    
    public static void main(String[] args) throws Exception {
        Type.Object me = new Type.Object("Test");
        ClassGen cg = new ClassGen("Test","java.lang.Object",ACC_PUBLIC|ACC_SUPER|ACC_FINAL);
        FieldGen fg = cg.addField("foo",Type.INT,ACC_PUBLIC|ACC_STATIC);
        
        MethodGen mg = cg.addMethod("main",Type.VOID,new Type[]{Type.arrayType(Type.STRING)},ACC_STATIC|ACC_PUBLIC);
        mg.setMaxLocals(1);
        mg.addPushConst(0);
        //mg.add(ISTORE_0);
        mg.add(PUTSTATIC,cg.fieldRef(me,"foo",Type.INT));
        int top = mg.size();
        mg.add(GETSTATIC,cg.fieldRef(new Type.Object("java.lang.System"),"out",new Type.Object("java.io.PrintStream")));
        //mg.add(ILOAD_0);
        mg.add(GETSTATIC,cg.fieldRef(me,"foo",Type.INT));
        mg.add(INVOKEVIRTUAL,cg.methodRef(new Type.Object("java.io.PrintStream"),"println",Type.VOID, new Type[]{Type.INT}));
        //mg.add(IINC,new int[]{0,1});
        //mg.add(ILOAD_0);
        mg.add(GETSTATIC,cg.fieldRef(me,"foo",Type.INT));
        mg.addPushConst(1);
        mg.add(IADD);
        mg.add(DUP);
        mg.add(PUTSTATIC,cg.fieldRef(me,"foo",Type.INT));       
        mg.addPushConst(10);
        mg.add(IF_ICMPLT,top);
        mg.add(RETURN);
        cg.dump("Test.class");
    }
}
