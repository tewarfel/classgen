package org.ibex.classgen;

import java.util.*;
import java.io.*;

import org.ibex.classgen.util.*;

// FEATURE: Add a "hit count" to each entry and optimize the table

class CPGen {
    private Hashtable entries = new Hashtable();
    private int nextIndex = 1; // 0 is reserved
    private int count;
    private int state;
    private static final int OPEN = 0;
    private static final int STABLE = 1; // existing entries won't change
    private static final int SEALED = 2; // no new entries
    
    CPGen() { }
    
    /*
     * Entries 
     */
    abstract static class Ent {
        int index;
        int tag;
        
        Ent(int tag) { this.tag = tag; }
        
        int getIndex() { return index; }
        
        void dump(DataOutput o) throws IOException { o.writeByte(tag); }
    }
    
    static class OneU4Ent extends Ent {
        int i;
        OneU4Ent(int tag) { super(tag); }
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
        void dump(DataOutput o) throws IOException {
            super.dump(o);
            o.writeShort(e1.index);
            if(e2 != null) o.writeShort(e2.index);
        }
    }
        
    static class Utf8Ent extends Ent {
        String s;
        Utf8Ent() { super(1); }
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
        
    /*
     * Methods
     */
    public void seal() { if(state >= SEALED) throw new IllegalStateException(); state = SEALED; }
    public void stable() { if(state >= STABLE) throw new IllegalStateException(); state = STABLE; }
    
    public final Ent get(Object o) { return (Ent) entries.get(o); }
    public final Ent getUtf8(String s) { return get(new Utf8Key(s)); }
    public final int getIndex(Object o) {
        Ent e = get(o);
        if(e == null) throw new IllegalStateException("entry not found");
        return e.getIndex();
    }
    public final int getUtf8Index(String s) {
        Ent e = getUtf8(s);
        if(e == null) throw new IllegalStateException("entry not found");
        return e.getIndex();
    }
    
    public final Ent addNameAndType(String name, String descriptor) { return add(new ClassGen.NameAndType(name,descriptor)); }
    public final Ent addUtf8(String s) { return add(new Utf8Key(s)); }
    
    public final Ent add(Object o) {
        if(state == SEALED) throw new IllegalStateException("constant pool is sealed");
            
        Ent ent = get(o);
        if(ent != null) return ent;
        
        if(nextIndex == 65536) throw new ClassGen.Exn("constant pool full");
        
        if(o instanceof Type.Object) {
            CPRefEnt ce = new CPRefEnt(7);
            ce.e1 = addUtf8(((Type.Object)o).internalForm());
            ent = ce;
        } else if(o instanceof String) {
            CPRefEnt ce = new CPRefEnt(8);
            ce.e1 = addUtf8((String)o);
            ent = ce;
        } else if(o instanceof Integer) {
            OneU4Ent ue = new OneU4Ent(3);
            ue.i = ((Integer)o).intValue();
            ent = ue;
        } else if(o instanceof Float) {
            OneU4Ent ue = new OneU4Ent(4);
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
        } else if(o instanceof ClassGen.NameAndType) {
            CPRefEnt ce = new CPRefEnt(12);
            ClassGen.NameAndType key = (ClassGen.NameAndType) o;
            ce.e1 = addUtf8(key.name);
            ce.e2 = addUtf8(key.type);
            ent = ce;
        } else if(o instanceof ClassGen.FieldMethodRef) {
            ClassGen.FieldMethodRef key = (ClassGen.FieldMethodRef) o;
            int tag = o instanceof FieldRef ? 9 : o instanceof MethodRef ? 10 : o instanceof ClassGen.InterfaceMethodRef ? 11 : 0;
            if(tag == 0) throw new Error("should never happen");
            CPRefEnt ce = new CPRefEnt(tag);
            ce.e1 = add(key.klass);
            ce.e2 = add(key.nameAndType);
            ent = ce;
        } else {
            throw new IllegalArgumentException("Unknown type passed to add");
        }
        
        ent.index = nextIndex++;
        if(ent instanceof LongEnt) nextIndex++;
        count++;

        entries.put(o,ent);
        return ent;
    }
    
    public int size() { return nextIndex; }
    
    private static final Sort.CompareFunc compareFunc = new Sort.CompareFunc() {
        public int compare(Object a_, Object b_) {
            return ((Ent)a_).index - ((Ent)b_).index;
        }
    };
    public void dump(DataOutput o) throws IOException {
        Ent[] ents = new Ent[count];
        int i=0;
        Enumeration e = entries.keys();
        while(e.hasMoreElements()) ents[i++] = (Ent) entries.get(e.nextElement());
        if(i != count) throw new Error("should never happen");
        Sort.sort(ents,compareFunc);
        for(i=0;i<ents.length;i++) {
            //System.err.println("" + (i+1) + ": " + ents[i]);
            ents[i].dump(o);
        }
    }
}
