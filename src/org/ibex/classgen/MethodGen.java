package org.ibex.classgen;

import java.io.*;

public class MethodGen implements CGConst {
    private final static boolean EMIT_NOPS = true;
    
    private final CPGen cp;
    private final String name;
    private final Type ret;
    private final Type[] args;
    private final int flags;
    private final AttrGen attrs;
    private final AttrGen codeAttrs;
    
    private final int nameIndex;
    private final int descriptorIndex;
    
    private int maxStack = 16;
    private int maxLocals=1;
    
    private int size;
    private int capacity;
    private byte[] op;
    private Object[] arg;
    
    MethodGen(ClassGen owner, String name, Type ret, Type[] args, int flags) {
        if((flags & ~(ACC_PUBLIC|ACC_PRIVATE|ACC_PROTECTED|ACC_STATIC|ACC_FINAL|ACC_SYNCHRONIZED|ACC_NATIVE|ACC_ABSTRACT|ACC_STRICT)) != 0)
            throw new IllegalArgumentException("invalid flags");
        this.cp = owner.cp;
        this.name = name;
        this.ret = ret;
        this.args = args;
        this.flags = flags;
        
        attrs = new AttrGen(cp);
        codeAttrs = new AttrGen(cp);
        
        nameIndex = cp.addUtf8(name).index;
        descriptorIndex = cp.addUtf8(descriptor()).index;
        
        if((owner.flags & ACC_INTERFACE) != 0 || (flags & (ACC_ABSTRACT|ACC_NATIVE)) != 0) size = capacity = -1;
    }
    
    public String descriptor() { return descriptor(ret,args); }
    public static String descriptor(Type ret, Type[] args) {
        StringBuffer sb = new StringBuffer(args.length*4);
        sb.append("(");
        for(int i=0;i<args.length;i++) sb.append(args[i].getDescriptor());
        sb.append(")");
        sb.append(ret.getDescriptor());
        return sb.toString();
    }
        
    private final void grow() { if(size == capacity) grow(size+1); }
    private final void grow(int newCap) {
        if(newCap <= capacity) return;
        newCap = Math.max(newCap,capacity == 0 ? 256 : capacity*2);
        
        byte[] op2 = new byte[newCap];
        if(capacity != 0) System.arraycopy(op,0,op2,0,size);
        op = op2;
        
        Object[] arg2 = new Object[newCap];
        if(capacity != 0) System.arraycopy(arg,0,arg2,0,size);
        arg = arg2;
        
        capacity = newCap;
    }
    public final int size() { return size; }
    
    public final int addPushConst(int n) { grow(); setPushConst(size,n); return size++; }
    public final int addPushConst(long n) { grow(); setPushConst(size,n); return size++; }
    public final int addPushConst(float n) { grow(); setPushConst(size,n); return size++; }
    public final int addPushConst(double n) { grow(); setPushConst(size,n); return size++; }
    public final int addPushConst(Object o) { grow(); setPushConst(size,o); return size++; }
    public final int add(byte op, int arg) { return add(op,N(arg)); }
    public final int add(byte op, Object arg) { grow(); set(size,op,arg); return size++; }
    public final int add(byte op) { return add(op,null); }
        
    public final void set(int pos, byte op) { set(pos,op,null); }
    public final void set(int pos, byte op, int n) { set(pos,op,N(n)); }
    public final void set(int pos, byte op, Object arg) {
        if(capacity == -1) throw new IllegalStateException("method can't have code");
        if(size == -1) throw new IllegalStateException("method is finalized");
        int iarg = arg instanceof Integer ? ((Integer)arg).intValue() : -1;
        
        switch(op) {
            case LDC: if(iarg >= 256) op = LDC_W; break;
        }
        this.op[pos] = op;
        this.arg[pos] = arg;
    }
        
    public final void setPushConst(int pos, int n) {
        switch(n) {
            case -1: set(pos,ICONST_M1); break;
            case 0:  set(pos,ICONST_0);  break;
            case 1:  set(pos,ICONST_1);  break;
            case 2:  set(pos,ICONST_2);  break; 
            case 3:  set(pos,ICONST_3);  break;
            case 4:  set(pos,ICONST_4);  break;
            case 5:  set(pos,ICONST_5);  break;
            default:
                if(n >= -128 && n <= 127) set(pos,BIPUSH,n);
                if(n >= -32767 && n <= 32767) set(pos,SIPUSH,n);
                setLDC(pos,N(n));
                break;
        }
    }
    public final void setPushConst(int pos, long l) {
        if(l==0) set(pos,LCONST_0);
        else if(l==1) set(pos,LCONST_1);
        else setLDC(pos,N(l));
    }
    
    public final void setPushConst(int pos, float f) {
        if(f == 1.0f) set(pos,FCONST_0);
        else if(f == 1.0f) set(pos,FCONST_1);
        else if(f == 2.0f) set(pos,FCONST_2);
        else setLDC(pos,N(f));
    }
    public final void setPushConst(int pos, double d) {
        if(d == 1.0) set(pos,DCONST_0);
        else if(d == 2.0) set(pos,DCONST_1);
        else setLDC(pos,N(d));
    }
    public final void setPushConst(int pos, Object o) {
        if(o instanceof Integer) setPushConst(pos,((Integer)o).intValue());
        else if(o instanceof Long) setPushConst(pos,((Long)o).longValue());
        else if(o instanceof Float) setPushConst(pos,((Float)o).floatValue());
        else if(o instanceof Double) setPushConst(pos,((Double)o).doubleValue());
        else setLDC(pos,o);
    }
        
    private void setLDC(int pos, Object o) { set(pos,LDC,cp.add(o)); }

    public final CPGen.Ent methodRef(Type.Object c, String name, Type ret, Type[] args) {        
        return methodRef(c,name,MethodGen.descriptor(ret,args));
    }
    public final CPGen.Ent methodRef(Type.Object c, String name, String descriptor) {
        return cp.add(new CPGen.MethodRef(c,new CPGen.NameAndType(name,descriptor)));
    }
    public final CPGen.Ent fieldRef(Type.Object c, String name, Type type) {
        return fieldRef(c,name,type.getDescriptor());
    }
    public final CPGen.Ent fieldRef(Type.Object c, String name, String descriptor) {
        return cp.add(new CPGen.FieldRef(c,new CPGen.NameAndType(name,descriptor)));
    }    
    
    public void setMaxLocals(int maxLocals) { this.maxLocals = maxLocals; }
    public void setMaxStack(int maxStack) { this.maxStack = maxStack; }
    
    public void finish() {
        try {
            _finish();
        } catch(IOException e) {
            throw new Error("should never happen");
        }
    }
    
    private void _finish() throws IOException {
        if(size == -1) return;
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutput o = new DataOutputStream(baos);
    
        int[] pc = new int[size];
        int[] maxpc = pc;
        int p,i;
        
        // Pass1 - Calculate maximum pc of each bytecode
        for(i=0,p=0;i<size;i++) {
            byte op = this.op[i];
            maxpc[i] = p;
            switch(op) {
                case GOTO: p += 3; break;
                case JSR: p += 3; break;
                case NOP: if(!EMIT_NOPS) continue; /* fall though */
                default: p += 1 + opArgLength(op); break;
            }
        }
        // Pass2 - Widen instructions if they can possibly be too short
        for(i=0;i<size;i++) {
            switch(op[i]) {
                case GOTO:
                case JSR: {
                    int arg = ((Integer)this.arg[i]).intValue();
                    int diff = maxpc[arg] - maxpc[i];
                    if(diff < -32768 || diff > 32767)
                        op[i] = op[i] == GOTO ? GOTO_W : JSR_W;
                }
            }
        }
        // Pass3 - Calculate actual pc
        for(i=0,p=0;i<size;i++) {
            byte op = this.op[i];
            pc[i] = p;
            p += op != NOP || EMIT_NOPS ? 1 + opArgLength(op) : 0;
        }
        
        o.writeShort(maxStack);
        o.writeShort(maxLocals);
        o.writeInt(p);
        
        // Pass 4 - Actually write the bytecodes
        for(i=0;i<size;i++) {
            byte op = this.op[i];
            System.err.println("" + i + " Writing " + Integer.toHexString(op) + " at " + pc[i]);
            if(op == NOP && !EMIT_NOPS) continue;
            int argLength = opArgLength(op);
            o.writeByte(op&0xff);
            if(argLength == 0) continue;
            Object arg = this.arg[i];            
            int iarg = arg instanceof Integer ? ((Integer)arg).intValue() : -1;
            int outArg = iarg;
            
            switch(op) {
                case IINC: {
                    int[] pair = (int[]) arg;
                    if(pair[0] > 255 || pair[1] < -128 || pair[1] > 127) throw new ClassGen.Exn("overflow of iinc arg"); 
                    o.writeByte(pair[0]);
                    o.writeByte(pair[1]);
                    continue;
                }
                case IF_ICMPLT:
                case GOTO:
                case GOTO_W:
                case JSR:
                case JSR_W:
                    outArg = pc[iarg] - pc[i];
                    if(outArg < -32768 || outArg > 32767) throw new ClassGen.Exn("overflow of s2 offset");
                    break;
                case INVOKESTATIC:
                case INVOKESPECIAL:
                case INVOKEVIRTUAL:
                case GETSTATIC:
                case PUTSTATIC:
                case GETFIELD:
                case PUTFIELD:
                case LDC_W:
                case LDC:
                    outArg = ((CPGen.Ent)arg).index;
                    break;
            }
            
            if(argLength == 1) o.writeByte(outArg);
            else if(argLength == 2) o.writeShort(outArg);
            else throw new Error("should never happen");
        }

        o.writeShort(0); // FIXME: Exception table
        o.writeShort(codeAttrs.size());
        codeAttrs.dump(o);
        
        baos.close();
        
        byte[] codeAttribute = baos.toByteArray();
        attrs.add("Code",codeAttribute);
        size = -1;        
    }
        
    public void dump(DataOutput o) throws IOException {
        o.writeShort(flags);
        o.writeShort(nameIndex);
        o.writeShort(descriptorIndex);
        o.writeShort(attrs.size());
        attrs.dump(o);
    }
    
    private static int opArgLength(byte op) {
        switch(op) {
            case NOP:
            case ICONST_M1:
            case ICONST_0:
            case ICONST_1:
            case ICONST_2:
            case ICONST_3:
            case ICONST_4:
            case ICONST_5:
            case LCONST_0:
            case LCONST_1:
            case FCONST_0:
            case FCONST_1:
            case FCONST_2:
            case DCONST_0:
            case DCONST_1:
            case ILOAD_0:
            case ILOAD_1:
            case ILOAD_2:
            case ILOAD_3:
            case ISTORE_0:
            case ISTORE_1:
            case ISTORE_2:
            case ISTORE_3:
            case RETURN:
                return 0;
            case LDC:
            case BIPUSH:
            case ILOAD:
            case ISTORE:
                return 1;
            case LDC_W:
            case SIPUSH:
            case GETSTATIC:
            case PUTSTATIC:
            case GETFIELD:
            case PUTFIELD:
            case INVOKESTATIC:
            case INVOKEVIRTUAL:
            case INVOKESPECIAL:
            case IINC:
            case GOTO:
            case JSR:
            case IF_ICMPLT:
                return 2;
            default:
                throw new ClassGen.Exn("unknown bytecode " + Integer.toHexString(op&0xff));
        }
    }
        
    private static Integer N(int n) { return new Integer(n); }
    private static Long N(long n) { return new Long(n); }
    private static Float N(float f) { return new Float(f); }
    private static Double N(double d) { return new Double(d); }
    private static int max(int a, int b) { return a > b ? a : b; }
}
