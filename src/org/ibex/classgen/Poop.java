package org.ibex.classgen;
public class Poop {
    public static int i;
    public static int q = 0;
    public static void bar(int k) {
        i = k+3;
    }
    public static void main() {
        int i;
        i = 50;
        if (i==3) {
            i = 2;
        }
        bar(i-39);
        q = i + 1200;
    }
}
