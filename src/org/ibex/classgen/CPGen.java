package org.ibex.classgen;

import java.util.*;
import java.io.*;

import org.ibex.classgen.util.*;

public class CPGen {
    private Hashtable entries = new Hashtable();
    private int nextIndex = 1; // 0 is reserved
    private boolean sealed;
    
    CPGen() { }
    
    /*
     * Entries 
     */
    abstract static class Ent implements Sort.Comparable {
        int index;
        public abstract int tag();
        public void dump(DataOutput o) throws IOException { o.writeByte(tag()); }
        public int compareTo(Object o) {
            if(!(o instanceof Ent)) return 1;
            int oi = ((Ent)o).index;
            if(index < oi) return -1;
            if(index > oi) return 1;
            return 0;
        }
    }
    
    abstract static class OneU2Ent extends Ent      { int i;  public void dump(DataOutput o) throws IOException { super.dump(o); o.writeShort(i);  } }
    abstract static class OneU4Ent extends Ent      { int i;  public void dump(DataOutput o) throws IOException { super.dump(o); o.writeInt(i);    } }
    abstract static class TwoU2Ent extends OneU2Ent { int i2; public void dump(DataOutput o) throws IOException { super.dump(o); o.writeShort(i2); } }
    abstract static class TwoU4Ent extends OneU4Ent { int i2; public void dump(DataOutput o) throws IOException { super.dump(o); o.writeInt(i2);   } }
    
    static class IntEnt         extends OneU4Ent { public int tag() { return 3;  } } // word1: bytes
    static class FloatEnt       extends OneU4Ent { public int tag() { return 4;  } } // word1: bytes
    static class LongEnt        extends TwoU4Ent { public int tag() { return 5;  } } // word1/2: bytes
    static class DoubleEnt      extends TwoU4Ent { public int tag() { return 6;  } } // word1/2: bytes
    static class ClassEnt       extends OneU2Ent { public int tag() { return 7;  } } // word1: name_index
    static class StringEnt      extends OneU2Ent { public int tag() { return 8;  } } // word1: string_index
    static class FieldRefEnt    extends TwoU2Ent { public int tag() { return 9;  } } // word1: class_index word2: name_and_type_index
    static class MethodRefEnt   extends TwoU2Ent { public int tag() { return 10; } } // word1: class_index word2: name_and_type_index
    static class IMethodRefEnt  extends TwoU2Ent { public int tag() { return 11; } } // word1: class_index word2: name_and_type_index
    static class NameAndTypeEnt extends TwoU2Ent { public int tag() { return 12; } } // word1: name_index  word2: descriptor_index
    
    static class Utf8Ent extends Ent {
        String s;
        public int tag() { return 1; }
        public void dump(DataOutput o) throws IOException {
            super.dump(o);
            o.writeUTF(s);
        }
        public String toString() { return "Utf8: " + s; }
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
    
    public static class NameAndType {
        String name;
        String type;
        public NameAndType(String name, String type) { this.name = name; this.type = type; }
        public boolean equals(Object o_) {
            if(!(o_ instanceof NameAndType)) return false;
            NameAndType o = (NameAndType) o_;
            return o.name.equals(name) && o.type.equals(type);
        }
        public int hashCode() { return name.hashCode() ^ type.hashCode(); }
    }
    
    static abstract class FieldMethodRef {
        Type.Object klass;
        NameAndType nameAndType;
        public FieldMethodRef(Type.Object klass, NameAndType nameAndType) { this.klass = klass; this.nameAndType = nameAndType; }
        public boolean equals(Object o_) {
            if(!(o_ instanceof FieldMethodRef)) return false;
            FieldMethodRef o = (FieldMethodRef) o_;
            return o.klass.equals(klass) && o.nameAndType.equals(nameAndType);
        }
    }
    
    public static class FieldRef   extends FieldMethodRef { public FieldRef  (Type.Object c, NameAndType t) { super(c,t); } }
    public static class MethodRef  extends FieldMethodRef { public MethodRef (Type.Object c, NameAndType t) { super(c,t); } }
    public static class IMethodRef extends FieldMethodRef { public IMethodRef(Type.Object c, NameAndType t) { super(c,t); } }
    
    /*
     * Methods
     */
    public void seal() { sealed = true; }
    
    public final Ent get(Object o) { return (Ent) entries.get(o); }
    public final Ent getUtf8(String s) { return get(new Utf8Key(s)); }
    
    public final Ent addNameAndType(String name, String descriptor) { return add(new NameAndType(name,descriptor)); }
    public final Ent addUtf8(String s) { return add(new Utf8Key(s)); }
    
    public final Ent add(Object o) {
        if(sealed) throw new IllegalStateException("constant pool is sealed");
            
        Ent ent = get(o);
        if(ent != null) return ent;
        
        if(nextIndex == 65536) throw new ClassGen.Exn("constant pool full");
        
        if(o instanceof Type.Object) {
            ClassEnt ce = new ClassEnt();
            ce.i = addUtf8(((Type.Object)o).internalForm()).index;
            ent = ce;
        } else if(o instanceof String) {
            StringEnt se = new StringEnt();
            se.i = addUtf8((String)o).index;
            ent = se;
        } else if(o instanceof Integer) {
            IntEnt ie = new IntEnt();
            ie.i = ((Integer)o).intValue();
            ent = ie;
        } else if(o instanceof Float) {
            FloatEnt fe = new FloatEnt();
            fe.i = Float.floatToIntBits(((Float)o).floatValue());
            ent = fe;
        } else if(o instanceof Long) {
            LongEnt le = new LongEnt();
            long l = ((Long)o).longValue();
            le.i = (int)(l>>>32);
            le.i2 = (int)l;
            ent = le;
        } else if(o instanceof Double) {
            DoubleEnt de = new DoubleEnt();
            long l = Double.doubleToLongBits(((Double)o).doubleValue());
            de.i = (int)(l>>>32);
            de.i2 = (int)l;
            ent = de;
        } else if(o instanceof Utf8Key) {
            Utf8Ent ue = new Utf8Ent();
            ue.s = ((Utf8Key)o).s;
            ent = ue;
        } else if(o instanceof NameAndType) {
            NameAndTypeEnt ne = new NameAndTypeEnt();
            NameAndType key = (NameAndType) o;
            ne.i = addUtf8(key.name).index;
            ne.i2 = addUtf8(key.type).index;
            ent = ne;
        } else if(o instanceof FieldMethodRef) {
            FieldMethodRef key = (FieldMethodRef) o;
            TwoU2Ent fme;
            if(o instanceof MethodRef) fme = new MethodRefEnt();
            else if(o instanceof IMethodRef) fme = new IMethodRefEnt();
            else if(o instanceof FieldRef) fme = new FieldRefEnt();
            else throw new Error("should never happen");
            fme.i = add(key.klass).index;
            fme.i2 = add(key.nameAndType).index;
            ent = fme;
        } else {
            throw new IllegalArgumentException("Unknown type passed to add");
        }
        
        ent.index = nextIndex++;
        entries.put(o,ent);
        return ent;
    }
    
    public int size() { return nextIndex; }
    
    public void dump(DataOutput o) throws IOException {
        Ent[] ents = new Ent[nextIndex-1];
        int i=0;
        Enumeration e = entries.keys();
        while(e.hasMoreElements()) ents[i++] = (Ent) entries.get(e.nextElement());
        Sort.sort(ents);
        for(i=0;i<ents.length;i++) {
            //System.err.println("" + (i+1) + ": " + ents[i]);
            ents[i].dump(o);
        }
    }
}
