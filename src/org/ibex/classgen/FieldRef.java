package org.ibex.classgen;

public class FieldRef extends ClassGen.FieldMethodRef {
    public FieldRef  (Type.Object c, ClassGen.NameAndType t) { super(c,t); }
    public FieldRef(Type.Object c, String name, Type t) { super(c,new ClassGen.NameAndType(name,t.getDescriptor())); }
}
