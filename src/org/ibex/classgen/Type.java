package org.ibex.classgen;

import java.util.StringTokenizer;

public class Type {
    public static final Type VOID = new Type("V");
    public static final Type INT = new Type("I");
    public static final Type LONG = new Type("J");
    public static final Type BOOLEAN = new Type("Z");
    public static final Type DOUBLE = new Type("D");
    public static final Type FLOAT = new Type("F");
    
    public static final Type.Object OBJECT = new Type.Object("java.lang.Object");
    public static final Type.Object STRING = new Type.Object("java.lang.String");
    public static final Type.Object STRINGBUFFER = new Type.Object("java.lang.StringBuffer");
    public static final Type.Object INTEGER_OBJECT = new Type.Object("java.lang.Integer");
    public static final Type.Object DOUBLE_OBJECT = new Type.Object("java.lang.Double");
    public static final Type.Object FLOAT_OBJECT = new Type.Object("java.lang.Float");
    
    public static final Type[] NO_ARGS = new Type[0];
    
    String descriptor;
    
    Type() { }
    Type(String descriptor) { this.descriptor = descriptor; }
    
    public final String getDescriptor() { return descriptor; }
    public int hashCode() { return descriptor.hashCode(); }
    public boolean equals(Object o) { return o instanceof Type && ((Type)o).descriptor.equals(descriptor); }
    
    public static Type arrayType(Type base) { return arrayType(base,1); }
    public static Type arrayType(Type base, int dim) {
        StringBuffer sb = new StringBuffer(base.descriptor.length() + dim);
        for(int i=0;i<dim;i++) sb.append("[");
        sb.append(base.descriptor);
        return new Type(sb.toString());
    }
    
    public static class Object extends Type {
        public Object(String s) {
            if(!s.startsWith("L") || !s.endsWith(";")) s = "L" + s.replace('.','/') + ";";
            if(!validDescriptorString(s)) throw new IllegalArgumentException("invalid descriptor string");
            descriptor = s;
        }
        
        public String[] components() {
            StringTokenizer st = new StringTokenizer(descriptor.substring(1,descriptor.length()-1),"/");
            String[] a = new String[st.countTokens()];
            for(int i=0;st.hasMoreTokens();i++) a[i] = st.nextToken();
            return a;
        }
        
        public String internalForm() { return descriptor.substring(1,descriptor.length()-1); }
        
        // FEATURE: Do a proper check here (invalid chars, etc)
        public boolean validDescriptorString(String s) {
            return s.startsWith("L") && s.endsWith(";");
        }
    }    
}
