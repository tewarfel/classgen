package org.ibex.classgen;

import java.io.*;

public class FieldGen implements CGConst {
    private final CPGen cp;
    private final String name;
    private final Type type;
    private final int flags;
    private final AttrGen attrs;
    
    FieldGen(ClassGen owner, String name,Type type, int flags) {
        if((flags & ~(ACC_PUBLIC|ACC_PRIVATE|ACC_PROTECTED|ACC_VOLATILE|ACC_TRANSIENT|ACC_STATIC|ACC_FINAL)) != 0)
            throw new IllegalArgumentException("invalid flags");
        this.cp = owner.cp;
        this.name = name;
        this.type = type;
        this.flags = flags;
        this.attrs = new AttrGen(cp);
        
        cp.addUtf8(name);
        cp.addUtf8(type.getDescriptor());
    }
    
    public void dump(DataOutput o) throws IOException {
        o.writeShort(flags);
        o.writeShort(cp.getUtf8Index(name));
        o.writeShort(cp.getUtf8Index(type.getDescriptor()));
        o.writeShort(attrs.size());
        attrs.dump(o);
    }
}
