package org.ibex.classgen;

public class FieldRef extends ClassGen.FieldOrMethodRef {
    public FieldRef(Type.Object c, String name, Type t) { super(c,name,t.getDescriptor()); }
    public FieldRef(String s, String name, Type t) { this(new Type.Object(s),name,t); }
}
