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
        o.writeShort(0); // minor_version
        o.writeShort(46); // major_version
        o.writeShort(cp.size()); // constant_pool_count
        cp.dump(o); // constant_pool
        o.writeShort(flags);
        o.writeShort(cp.get(thisType).index); // this_class
        o.writeShort(cp.get(superType).index); // super_class
        o.writeShort(interfaces.size()); // interfaces_count
        for(int i=0;i<interfaces.size();i++) o.writeShort(cp.get((Type.Object)interfaces.elementAt(i)).index); // interfaces
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

    public static void main(String[] args) throws Exception {
        ClassGen cg = new ClassGen("Test","java.lang.Object",ACC_PUBLIC|ACC_SUPER|ACC_FINAL);
        MethodGen mg = cg.addMethod("main",Type.VOID,new Type[]{Type.arrayType(Type.STRING)},ACC_STATIC|ACC_PUBLIC);
        mg.setMaxLocals(1);
        mg.addPushConst(0);
        mg.add(ISTORE_0);
        int top = mg.size();
        mg.add(GETSTATIC,mg.fieldRef(new Type.Object("java.lang.System"),"out",new Type.Object("java.io.PrintStream")));
        mg.add(ILOAD_0);
        mg.add(INVOKEVIRTUAL,mg.methodRef(new Type.Object("java.io.PrintStream"),"println",Type.VOID, new Type[]{Type.INT}));
        mg.add(IINC,new int[]{0,1});
        mg.add(ILOAD_0);
        mg.addPushConst(10);
        mg.add(IF_ICMPLT,top);
        mg.add(RETURN);
        cg.dump("Test.class");
    }
}
