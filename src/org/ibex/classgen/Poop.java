package org.ibex.classgen;
public class Poop {
    public static int i;
    public static void bar(int k) {
        i = k+3;
    }
    public static void main() {
        i = 5;
        bar(i-3);
        i += 3;
    }
}
