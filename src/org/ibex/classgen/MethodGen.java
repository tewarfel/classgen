package org.ibex.classgen;

import java.io.*;
import java.util.*;

public class MethodGen implements CGConst {
    private final static boolean EMIT_NOPS = true;
    
    private final CPGen cp;
    private final String name;
    private final Type ret;
    private final Type[] args;
    private final int flags;
    private final ClassGen.AttrGen attrs;
    private final ClassGen.AttrGen codeAttrs;
    private final Hashtable exnTable = new Hashtable();
    private final Hashtable thrownExceptions = new Hashtable();
    
    private int maxStack = 16;
    private int maxLocals;
    
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
        
        attrs = new ClassGen.AttrGen(cp);
        codeAttrs = new ClassGen.AttrGen(cp);
        
        cp.addUtf8(name);
        cp.addUtf8(getDescriptor());
        
        if((owner.flags & ACC_INTERFACE) != 0 || (flags & (ACC_ABSTRACT|ACC_NATIVE)) != 0) size = capacity = -1;
        
        maxLocals = Math.max(args.length + (flags&ACC_STATIC)==0 ? 1 : 0,4);
    }
    
    public String getDescriptor() { return MethodRef.getDescriptor(ret,args); }
    
    private class ExnTableEnt {
        public int start;
        public int end;
        public int handler;
        public CPGen.Ent typeEnt;
        public ExnTableEnt(int start, int end, int handler, CPGen.Ent typeEnt) {
            this.start = start;
            this.end = end;
            this.handler = handler;
            this.typeEnt = typeEnt;
        }
        public void dump(DataOutput o, int[] pc, int endPC) throws IOException {
            o.writeShort(pc[start]);
            o.writeShort(end==pc.length ? endPC : pc[end]);
            o.writeShort(pc[handler]);
            o.writeShort(typeEnt.getIndex());
        }
    }
    
    public final void addExceptionHandler(int startPC, int endPC, int handlerPC, Type.Object type) {
        exnTable.put(type, new ExnTableEnt(startPC,endPC,handlerPC,cp.add(type)));
    }
    
    public final void addThrow(Type.Object type) {
        thrownExceptions.put(type,cp.add(type));
    }
    
    private final void grow() { if(size == capacity) grow(size+1); }
    private final void grow(int newCap) {
        if(capacity == -1) throw new IllegalStateException("method can't have code");
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
    
    // These two are optimized for speed, they don't call set() below
    public final int add(byte op) {
        int s = size;
        if(s == capacity) grow();
        this.op[s] = op;
        size++;
        return s;
    }
    public final void set(int pos, byte op) { this.op[pos] = op; }
        
    public final int add(byte op, Object arg) { if(capacity == size) grow(); set(size,op,arg); return size++; }
    public final int add(byte op, boolean arg) { if(capacity == size) grow(); set(size,op,arg); return size++; }
    public final int add(byte op, int arg) { if(capacity == size) grow(); set(size,op,arg); return size++; }
    
    public final byte get(int pos) { return op[pos]; }
    public final Object getArg(int pos) { return arg[pos]; }
    
    public final void setArg(int pos, int arg) { set(pos,op[pos],N(arg)); }
    public final void setArg(int pos, Object arg) { set(pos,op[pos],arg); }
    
    
    public final void set(int pos, byte op, boolean b) { set(pos,op,b?1:0); }
    public final void set(int pos, byte op, int n) {
        if(op == LDC) {
            switch(n) {
                case -1: set(pos,ICONST_M1); return;
                case 0:  set(pos,ICONST_0);  return;
                case 1:  set(pos,ICONST_1);  return;
                case 2:  set(pos,ICONST_2);  return; 
                case 3:  set(pos,ICONST_3);  return;
                case 4:  set(pos,ICONST_4);  return;
                case 5:  set(pos,ICONST_5);  return;
            }
            Object arg;
            if(n >= -128 && n <= 127) { op = BIPUSH; arg = N(n); } 
            else if(n >= -32767 && n <= 32767) { op = SIPUSH; arg = N(n); }
            else { arg = cp.add(N(n)); }
            this.op[pos] = op;
            this.arg[pos] = arg;
        } else {
            set(pos,op,N(n));
        }
    }
    
    public void set(int pos, byte op, Object arg) {
        switch(op) {
            case ILOAD: case ISTORE: case LLOAD: case LSTORE: case FLOAD:
            case FSTORE: case DLOAD: case DSTORE: case ALOAD: case ASTORE:
            {
                int iarg = ((Integer)arg).intValue();
                if(iarg >= 0 && iarg <= 3) {
                    byte base = 0;
                    switch(op) {
                        case ILOAD:  base = ILOAD_0; break;
                        case ISTORE: base = ISTORE_0; break;
                        case LLOAD:  base = LLOAD_0; break;
                        case LSTORE: base = LSTORE_0; break; 
                        case FLOAD:  base = FLOAD_0; break;
                        case FSTORE: base = FSTORE_0; break;
                        case DLOAD:  base = DLOAD_0; break;
                        case DSTORE: base = DSTORE_0; break;
                        case ALOAD:  base = ALOAD_0; break;
                        case ASTORE: base = ASTORE_0; break;
                    }
                    op = (byte)((base&0xff) + iarg);
                } else {
                    if(iarg >= maxLocals) maxLocals = iarg + 1;
                }
                break;
            }
            case LDC:
                if(arg instanceof Integer) { set(pos,op,((Integer)arg).intValue()); return; }
                if(arg instanceof Boolean) { set(pos,op,((Boolean)arg).booleanValue()); return; }
                if(arg instanceof Long) {
                    long l = ((Long)arg).longValue();
                    if(l == 0L) { set(pos,LCONST_0); return; }
                    if(l == 1L) { set(pos,LCONST_1); return; }
                }
                
                if(arg instanceof Long || arg instanceof Double) op = LDC2_W;
                // fall through
            default: {
                int opdata = OP_DATA[op&0xff];
                if((opdata&OP_CPENT_FLAG) != 0 && !(arg instanceof CPGen.Ent))
                    arg = cp.add(arg);
                else if((opdata&OP_VALID_FLAG) == 0)
                    throw new IllegalArgumentException("unknown bytecode");
                break;
            }
        }
        this.op[pos] = op;
        this.arg[pos] = arg;
    }
    
    public static class SI {
        public final Object[] targets;
        public Object defaultTarget;

        SI(int size) { targets = new Object[size]; }
        public void setTarget(int pos, Object val) { targets[pos] = val; }
        public void setTarget(int pos, int val) { targets[pos] = N(val); }
        public void setDefaultTarget(int val) { setDefaultTarget(N(val)); }
        public void setDefaultTarget(Object o) { defaultTarget = o; }
        public int size() { return targets.length; }
        
        public int getTarget(int pos) { return ((Integer)targets[pos]).intValue(); }
        public int getDefaultTarget() { return ((Integer)defaultTarget).intValue(); }
    }
    
    public static class TSI extends SI {
        public final int lo;
        public final int hi;
        public int defaultTarget = -1;
        public TSI(int lo, int hi) {
            super(hi-lo+1);
            this.lo = lo;
            this.hi = hi;
        }
        public void setTargetForVal(int val, Object o) { setTarget(val-lo,o); }
        public void setTargetForVal(int val, int n) { setTarget(val-lo,n); }
    }
    
    public static class LSI extends SI {
        public final int[] vals;
        public LSI(int size) {
           super(size);
           this.vals = new int[size];
        }
        public final void setVal(int pos, int val) { vals[pos] = val; }
    }
    
    public static class Pair {
        public int i1;
        public int i2;
        public Pair(int i1, int i2) { this.i1 = i1; this.i2 = i2; }
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
    
    private Object resolveTarget(Object arg) {
        int target;
        if(arg instanceof PhantomTarget) {
            target = ((PhantomTarget)arg).getTarget();
            if(target == -1) throw new IllegalStateException("unresolved phantom target");
            arg = N(target);
        } else {
            target = ((Integer)arg).intValue();
        }
        if(target < 0 || target >= size)
            throw new IllegalStateException("invalid target address");
        return arg;
    }
    
    private void _finish() throws IOException {
        if(size == -1) return;
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutput o = new DataOutputStream(baos);
    
        int[] pc = new int[size];
        int[] maxpc = pc;
        int p,i;
        
        // Pass1 - Calculate maximum pc of each bytecode, widen some insns, resolve any unresolved jumps, etc
        for(i=0,p=0;i<size;i++) {
            byte op = this.op[i];
            int opdata = OP_DATA[op&0xff];
            int j;
            maxpc[i] = p;
            
            if((opdata & OP_BRANCH_FLAG)!= 0) { 
                try { 
                    arg[i] = resolveTarget(arg[i]);
                } catch(RuntimeException e) {
                    System.err.println("WARNING: Error resolving target for " + Integer.toHexString(op&0xff));
                    throw e;
                }
            }
            
            switch(op) {
                case GOTO:
                case JSR:
                    p += 3;
                    break;
                case NOP:
                    if(EMIT_NOPS) p++;
                    break;
                case LOOKUPSWITCH:
                case TABLESWITCH: {
                    SI si = (SI) arg[i];
                    Object[] targets = si.targets;
                    for(j=0;j<targets.length;j++) targets[j] = resolveTarget(targets[j]);
                    si.defaultTarget = resolveTarget(si.defaultTarget);
                    p += 1 + 3 + 4; // opcode itself, padding, default
                    if(op == TABLESWITCH) p += 4 + 4 + targets.length * 4; // lo, hi, targets
                    else p += 4 + targets.length * 4 * 2; // count, key,val * targets
                    if(op == LOOKUPSWITCH) {
                        int[] vals = ((LSI)si).vals;
                        for(j=1;j<vals.length;j++)
                            if(vals[j] <= vals[j-1])
                                throw new IllegalStateException("out of order/duplicate lookupswitch values");
                    }
                    break;
                }
                case LDC:
                    j = ((CPGen.Ent)arg[i]).getIndex();
                    if(j >= 256) this.op[i] = op = LDC_W;
                    // fall through
                default:
                    if((j = (opdata&OP_ARG_LENGTH_MASK)) == 7) throw new Error("shouldn't be here");
                    p += 1 + j;
                    break;
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
                    break;
                }
            }
        }
        
        // Pass3 - Calculate actual pc
        for(i=0,p=0;i<size;i++) {
            byte op = this.op[i];
            pc[i] = p;
            switch(op) {
                case NOP:
                    if(EMIT_NOPS) p++;
                    break;
                case TABLESWITCH:
                case LOOKUPSWITCH: {
                    SI si = (SI) arg[i];
                    p++; // opcpde itself
                    p = (p + 3) & ~3; // padding
                    p += 4; // default
                    if(op == TABLESWITCH) p += 4 + 4 + si.size() * 4; // lo, hi, targets
                    else p += 4 + si.size() * 4 * 2; // count, key,val * targets
                    break;
                }
                default: {
                    int l = OP_DATA[op&0xff] & OP_ARG_LENGTH_MASK;
                    if(l == 7) throw new Error("shouldn't be here");
                    p += 1 + l;                    
                }
            }
        }
        
        int codeSize = p;
        
        o.writeShort(maxStack);
        o.writeShort(maxLocals);
        o.writeInt(codeSize);
        
        // Pass 4 - Actually write the bytecodes
        for(i=0;i<size;i++) {
            byte op = this.op[i];
            int opdata = OP_DATA[op&0xff];
            if(op == NOP && !EMIT_NOPS) continue;
            
            o.writeByte(op&0xff);
            int argLength = opdata & OP_ARG_LENGTH_MASK;
            
            if(argLength == 0) continue; // skip if no args
            
            // Write args
            Object arg = this.arg[i];  
            
            switch(op) {
                case IINC: {
                    Pair pair = (Pair) arg;
                    if(pair.i1 > 255 || pair.i2 < -128 || pair.i2 > 127) throw new ClassGen.Exn("overflow of iinc arg"); 
                    o.writeByte(pair.i1);
                    o.writeByte(pair.i2);
                }
                case TABLESWITCH:
                case LOOKUPSWITCH: {
                    SI si = (SI) arg;
                    int mypc = pc[i];
                    for(p = pc[i]+1;(p&3)!=0;p++) o.writeByte(0);
                    o.writeInt(pc[si.getDefaultTarget()] - mypc);
                    if(op == LOOKUPSWITCH) {
                        int[] vals = ((LSI)si).vals;
                        o.writeInt(si.size());
                        for(int j=0;j<si.size();j++) {
                            o.writeInt(vals[j]);
                            o.writeInt(pc[si.getTarget(j)] - mypc);
                        }
                    } else {
                        TSI tsi = (TSI) si;
                        o.writeInt(tsi.lo);
                        o.writeInt(tsi.hi);
                        for(int j=0;j<tsi.size();j++) o.writeInt(pc[tsi.getTarget(j)] - mypc);
                    }
                    break;
                }
                    
                default:
                    if((opdata & OP_BRANCH_FLAG) != 0) {
                        int v = pc[((Integer)arg).intValue()] - pc[i];
                        if(v < -32768 || v > 32767) throw new ClassGen.Exn("overflow of s2 offset");
                        o.writeShort(v);
                    } else if((opdata & OP_CPENT_FLAG) != 0) {
                        int v = ((CPGen.Ent)arg).getIndex();
                        if(argLength == 1) o.writeByte(v);
                        else if(argLength == 2) o.writeShort(v);
                        else throw new Error("should never happen");
                    } else if(argLength == -1) {
                        throw new Error("should never happen - variable length instruction not explicitly handled");
                    } else {
                        int iarg  = ((Integer)arg).intValue();
                        if(argLength == 1) {
                            if(iarg < -128 || iarg >= 256) throw new ClassGen.Exn("overflow of s/u1 option");
                            o.writeByte(iarg);
                        } else if(argLength == 2) {
                            if(iarg < -32767 || iarg >= 65536) throw new ClassGen.Exn("overflow of s/u2 option"); 
                            o.writeShort(iarg);
                        } else {
                            throw new Error("should never happen");
                        }
                    }
                    break;
            }
        }

        //if(baos.size() - 8 != codeSize) throw new Error("we didn't output what we were supposed to");
        
        o.writeShort(exnTable.size());
        for(Enumeration e = exnTable.keys();e.hasMoreElements();)
            ((ExnTableEnt)exnTable.get(e.nextElement())).dump(o,pc,codeSize);
        
        o.writeShort(codeAttrs.size());
        codeAttrs.dump(o);
        
        baos.close();
        
        byte[] codeAttribute = baos.toByteArray();
        attrs.add("Code",codeAttribute);
        
        baos.reset();
        o.writeShort(thrownExceptions.size());
        for(Enumeration e = thrownExceptions.keys();e.hasMoreElements();)
            o.writeShort(((CPGen.Ent)thrownExceptions.get(e.nextElement())).getIndex());
        attrs.add("Exceptions",baos.toByteArray());
        
        size = -1;        
    }
        
    public void dump(DataOutput o) throws IOException {
        o.writeShort(flags);
        o.writeShort(cp.getUtf8Index(name));
        o.writeShort(cp.getUtf8Index(getDescriptor()));
        o.writeShort(attrs.size());
        attrs.dump(o);
    }
    
    public static byte negate(byte op) {
        switch(op) {
            case IFEQ: return IFNE;
            case IFNE: return IFEQ;
            case IFLT: return IFGE;
            case IFGE: return IFLT;
            case IFGT: return IFLE;
            case IFLE: return IFGT;
            case IF_ICMPEQ: return IF_ICMPNE;
            case IF_ICMPNE: return IF_ICMPEQ;
            case IF_ICMPLT: return IF_ICMPGE;
            case IF_ICMPGE: return IF_ICMPLT;
            case IF_ICMPGT: return IF_ICMPLE;
            case IF_ICMPLE: return IF_ICMPGT;
            case IF_ACMPEQ: return IF_ACMPNE;
            case IF_ACMPNE: return IF_ACMPEQ;
            
            default:
                throw new IllegalArgumentException("Can't negate " + Integer.toHexString(op));
        }
    }
    
    public static class PhantomTarget {
        private int target = -1;
        public void setTarget(int target) { this.target = target; }
        public int getTarget() { return target; }
    }
    
    private static Integer N(int n) { return new Integer(n); }
    private static Long N(long n) { return new Long(n); }
    private static Float N(float f) { return new Float(f); }
    private static Double N(double d) { return new Double(d); }
    private static int max(int a, int b) { return a > b ? a : b; }
    
    private static final int OP_BRANCH_FLAG = 1<<3;
    private static final int OP_CPENT_FLAG = 1<<4;
    private static final int OP_VALID_FLAG = 1<<5;
    private static final int OP_ARG_LENGTH_MASK = 7;
    private static final boolean OP_VALID(byte op) { return (OP_DATA[op&0xff] & OP_VALID_FLAG) != 0; }
    private static final int OP_ARG_LENGTH(byte op) { return (OP_DATA[op&0xff]&OP_ARG_LENGTH_MASK); }
    private static final boolean OP_CPENT(byte op) { return (OP_DATA[op&0xff]&OP_CPENT_FLAG) != 0; }
    private static final boolean OP_BRANCH(byte op) { return (OP_DATA[op&0xff]&OP_BRANCH_FLAG ) != 0; }
    
    // Run perl -x src/org/ibex/classgen/CGConst.java to generate this
    private static final byte[] OP_DATA = {
            0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20,
            0x21, 0x22, 0x31, 0x32, 0x32, 0x21, 0x21, 0x21, 0x21, 0x21, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20,
            0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20,
            0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x21, 0x21, 0x21, 0x21, 0x21, 0x20, 0x20, 0x20, 0x20, 0x20,
            0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20,
            0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20,
            0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20,
            0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20,
            0x20, 0x20, 0x20, 0x20, 0x22, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20,
            0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x2a, 0x2a, 0x2a, 0x2a, 0x2a, 0x2a, 0x2a,
            0x2a, 0x2a, 0x2a, 0x2a, 0x2a, 0x2a, 0x2a, 0x2a, 0x2a, 0x21, 0x27, 0x27, 0x20, 0x20, 0x20, 0x20,
            0x20, 0x20, 0x32, 0x32, 0x32, 0x32, 0x32, 0x32, 0x32, 0x32, 0x01, 0x32, 0x21, 0x32, 0x20, 0x20,
            0x32, 0x32, 0x20, 0x20, 0x27, 0x23, 0x2a, 0x2a, 0x2c, 0x2c, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
            0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
            0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
            0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01
    };
}
