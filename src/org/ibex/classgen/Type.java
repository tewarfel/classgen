package org.ibex.classgen;

import java.util.StringTokenizer;

public class Type {
    public static final Type VOID = new Type("V");
    public static final Type INT = new Type("I");
    public static final Type LONG = new Type("J");
    public static final Type BOOLEAN = new Type("Z");
    public static final Type DOUBLE = new Type("D");
    public static final Type FLOAT = new Type("F");
    public static final Type BYTE = new Type("B");
    public static final Type CHAR = new Type("C");
    public static final Type SHORT = new Type("S");
    
    public static final Type.Object OBJECT = new Type.Object("java.lang.Object");
    public static final Type.Object STRING = new Type.Object("java.lang.String");
    public static final Type.Object STRINGBUFFER = new Type.Object("java.lang.StringBuffer");
    public static final Type.Object INTEGER_OBJECT = new Type.Object("java.lang.Integer");
    public static final Type.Object DOUBLE_OBJECT = new Type.Object("java.lang.Double");
    public static final Type.Object FLOAT_OBJECT = new Type.Object("java.lang.Float");
    
    /** A zero element Type[] array (can be passed as the "args" param when a method takes no arguments */
    public static final Type[] NO_ARGS = new Type[0];
    
    final String descriptor;
    
    Type(String descriptor) { this.descriptor = descriptor; }
    
    /** Returns the Java descriptor string for this object ("I", or "Ljava/lang/String", "[[J", etc */
    public final String getDescriptor() { return descriptor; }
    public int hashCode() { return descriptor.hashCode(); }
    public boolean equals(java.lang.Object o) { return o instanceof Type && ((Type)o).descriptor.equals(descriptor); }
    
    /** Returns a one dimensional array type for the base type <i>base</i>
        @param base The base type
        @return A one dimensional array of the base type
    */
    public static Type arrayType(Type base) { return arrayType(base,1); }
    /** Returns a <i>dim</i> dimensional array type for the base type <i>base</i>
        @param base The base type
        @param dim Number if dimensions
        @return A one dimensional array of the base type
    */
    public static Type arrayType(Type base, int dim) {
        StringBuffer sb = new StringBuffer(base.descriptor.length() + dim);
        for(int i=0;i<dim;i++) sb.append("[");
        sb.append(base.descriptor);
        return new Type(sb.toString());
    }
    
    /** Class representing Objec types (any non-primitive type) */
    public static class Object extends Type {
        /** Create an Type.Object instance for the specified string. <i>s</i> can be a string in the form
            "java.lang.String", "java/lang/String", or "Ljava/lang/String;".
            @param s The type */
        public Object(String s) { super(_initHelper(s)); }
        
        private static String _initHelper(String s) {
            if(!s.startsWith("L") || !s.endsWith(";")) s = "L" + s.replace('.','/') + ";";
            if(!validDescriptorString(s)) throw new IllegalArgumentException("invalid descriptor string");
            return s;
        }

        String[] components() {
            StringTokenizer st = new StringTokenizer(descriptor.substring(1,descriptor.length()-1),"/");
            String[] a = new String[st.countTokens()];
            for(int i=0;st.hasMoreTokens();i++) a[i] = st.nextToken();
            return a;
        }
        
        String internalForm() { return descriptor.substring(1,descriptor.length()-1); }
        
        // FEATURE: Do a proper check here (invalid chars, etc)
        static boolean validDescriptorString(String s) {
            return s.startsWith("L") && s.endsWith(";");
        }
    }    
}
