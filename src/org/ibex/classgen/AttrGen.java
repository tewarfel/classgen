package org.ibex.classgen;

import java.io.*;
import java.util.*;

public class AttrGen {
    private final CPGen cp;
    private final Hashtable ht = new Hashtable();
    
    public AttrGen(CPGen cp) {
        this.cp = cp;
    }
    
    public void add(String s, byte[] data) {
        cp.addUtf8(s);
        ht.put(s,data);
    }
    
    public boolean contains(String s) { return ht.get(s) != null; }
    
    public int size() { return ht.size(); }
    
    public void dump(DataOutput o) throws IOException {
        for(Enumeration e = ht.keys(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            byte[] val = (byte[]) ht.get(name);
            o.writeShort(cp.getUtf8Index(name));
            o.writeInt(val.length);
            o.write(val);
        }
    }
}
