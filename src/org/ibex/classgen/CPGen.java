package org.ibex.classgen;

import java.util.*;
import java.io.*;

import org.ibex.classgen.util.*;

class CPGen {
    private final Hashtable entries = new Hashtable();
    private int usedSlots = 1; // 0 is reserved
    private int state = OPEN;
    private static final int OPEN = 0;
    private static final int STABLE = 1; // existing entries won't change
    private static final int SEALED = 2; // no new entries
    
    CPGen() { }
    
    /*
     * Entries 
     */
    abstract static class Ent {
        int n; // this is the refcount if state == OPEN, index if >= STABLE
        int tag;
        
        Ent(int tag) { this.tag = tag; }
        
        void dump(DataOutput o) throws IOException { o.writeByte(tag); }
        String debugToString() { return toString(); } // so we can remove this method when not debugging
    }
    
    static class IntEnt extends Ent {
        int i;
        IntEnt(int tag) { super(tag); }
        void dump(DataOutput o) throws IOException { super.dump(o); o.writeInt(i);  }
    }
    
    static class LongEnt extends Ent {
        long l;
        LongEnt(int tag) { super(tag); }
        void dump(DataOutput o) throws IOException { super.dump(o); o.writeLong(l); }
    }
    
    static class CPRefEnt extends Ent {
        Ent e1;
        Ent e2;
        CPRefEnt(int tag) { super(tag); }
        
        String debugToString() { return "[" + e1.n + ":" + e1.debugToString() + (e2 == null ? "" : " + " + e2.n + ":" + e2.debugToString()) + "]"; }
        
        void dump(DataOutput o) throws IOException {
            super.dump(o);
            if(e1.n == 6 || (e2!=null && e2.n == 6)) System.err.println(debugToString() + " refs 6");
            o.writeShort(e1.n);
            if(e2 != null) o.writeShort(e2.n);
        }
    }
        
    static class Utf8Ent extends Ent {
        String s;
        Utf8Ent() { super(1); }
        String debugToString() { return s; }
        void dump(DataOutput o) throws IOException { super.dump(o); o.writeUTF(s); }
    }
    
    /*
     * Cache Keys
     */
    static class Utf8Key {
        String s;
        public Utf8Key(String s) { this.s = s; }
        public boolean equals(Object o) { return o instanceof Utf8Key && ((Utf8Key)o).s.equals(s); }
        public int hashCode() { return ~s.hashCode(); }
    }
        
    static class NameAndTypeKey {
        String name;
        String type;
        NameAndTypeKey(String name, String type) { this.name = name; this.type = type; }
        public boolean equals(Object o_) {
            if(!(o_ instanceof NameAndTypeKey)) return false;
            NameAndTypeKey o = (NameAndTypeKey) o_;
            return o.name.equals(name) && o.type.equals(type);
        }
        public int hashCode() { return name.hashCode() ^ type.hashCode(); }
    }
    
    /*
     * Methods
     */
    
    public final Ent get(Object o) { return (Ent) entries.get(o); }
    public final Ent getUtf8(String s) { return get(new Utf8Key(s)); }
    public final int getIndex(Object o) {
        Ent e = get(o);
        if(e == null) throw new IllegalStateException("entry not found");
        return getIndex(e);
    }
    public final int getUtf8Index(String s) {
        Ent e = getUtf8(s);
        if(e == null) throw new IllegalStateException("entry not found");
        return getIndex(e);
    }
    public final int getIndex(Ent ent) {
        if(state < STABLE) throw new IllegalStateException("constant pool is not stable");
        return ent.n;
    }
    
    public final Ent addNameAndType(String name, String descriptor) { return add(new NameAndTypeKey(name,descriptor)); }
    public final Ent addUtf8(String s) { return add(new Utf8Key(s)); }
    
    public final Ent add(Object o) {
        if(state == SEALED) throw new IllegalStateException("constant pool is sealed");
            
        Ent ent = get(o);
        if(ent != null) {
            if(state == OPEN) ent.n++;
            return ent;
        }
        
        if(o instanceof Type.Object) {
            CPRefEnt ce = new CPRefEnt(7);
            ce.e1 = addUtf8(((Type.Object)o).internalForm());
            ent = ce;
        } else if(o instanceof String) {
            CPRefEnt ce = new CPRefEnt(8);
            ce.e1 = addUtf8((String)o);
            ent = ce;
        } else if(o instanceof Integer) {
            IntEnt ue = new IntEnt(3);
            ue.i = ((Integer)o).intValue();
            ent = ue;
        } else if(o instanceof Float) {
            IntEnt ue = new IntEnt(4);
            ue.i = Float.floatToIntBits(((Float)o).floatValue());
            ent = ue;
        } else if(o instanceof Long) {
            LongEnt le = new LongEnt(5);
            le.l = ((Long)o).longValue();
            ent = le;
        } else if(o instanceof Double) {
            LongEnt le = new LongEnt(6);
            le.l = Double.doubleToLongBits(((Double)o).doubleValue());
            ent = le;
        } else if(o instanceof Utf8Key) {
            Utf8Ent ue = new Utf8Ent();
            ue.s = ((Utf8Key)o).s;
            ent = ue;
        } else if(o instanceof NameAndTypeKey) {
            CPRefEnt ce = new CPRefEnt(12);
            NameAndTypeKey key = (NameAndTypeKey) o;
            ce.e1 = addUtf8(key.name);
            ce.e2 = addUtf8(key.type);
            ent = ce;
        } else if(o instanceof ClassGen.FieldOrMethodRef) {
            ClassGen.FieldOrMethodRef key = (ClassGen.FieldOrMethodRef) o;
            int tag = o instanceof FieldRef ? 9 : o instanceof MethodRef ? 10 : o instanceof MethodRef.I ? 11 : 0;
            if(tag == 0) throw new Error("should never happen");
            CPRefEnt ce = new CPRefEnt(tag);
            ce.e1 = add(key.klass);
            ce.e2 = addNameAndType(key.name,key.descriptor);
            ent = ce;
        } else {
            throw new IllegalArgumentException("Unknown type passed to add");
        }
        
        int spaces = ent instanceof LongEnt ? 2 : 1;        
        if(usedSlots + spaces > 65536) throw new ClassGen.Exn("constant pool full");
        
        ent.n = state == OPEN ? 1 : usedSlots; // refcount or index

        usedSlots += spaces;        

        entries.put(o,ent);
        return ent;
    }
    
    public int slots() { return usedSlots; }

    public void seal() { state = SEALED; }
    
    private Ent[] asArray() {
        int count = entries.size();
        Ent[] ents = new Ent[count];
        int i=0;
        Enumeration e = entries.keys();
        while(e.hasMoreElements()) ents[i++] = (Ent) entries.get(e.nextElement());
        if(i != count) throw new Error("should never happen");
        return ents;
    }
    
    private static void assignIndex(Ent[] ents) {
        int index = 1;
        for(int i=0;i<ents.length;i++) {
            Ent ent = ents[i];
            ent.n = index;
            index += ent instanceof LongEnt ? 2 : 1;
        }
    }
        
    public void stable() {
        if(state != OPEN) return;
        state = STABLE;
        assignIndex(asArray());
    } 

    private static final Sort.CompareFunc compareFunc = new Sort.CompareFunc() {
        public int compare(Object a_, Object b_) {
            return ((Ent)a_).n - ((Ent)b_).n;
        }
    };
    
    private static final Sort.CompareFunc reverseCompareFunc = new Sort.CompareFunc() {
        public int compare(Object a_, Object b_) {
            return ((Ent)b_).n - ((Ent)a_).n;
        }
    };
    
    public void optimize() {
        if(state != OPEN) throw new IllegalStateException("can't optimize a stable constant pool");
        Ent[] ents = asArray();
        Sort.sort(ents,reverseCompareFunc);
        state = STABLE;
        assignIndex(ents);
    }
    
    public void dump(DataOutput o) throws IOException {
        Ent[] ents = asArray();
        Sort.sort(ents,compareFunc);
        for(int i=0;i<ents.length;i++) {
            //System.err.println("" + ents[i].n + ": " + ents[i].debugToString());
            ents[i].dump(o);
        }
    }
}
