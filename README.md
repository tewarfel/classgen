Copyright (c) 2004 Brian Alliet

org.ibex.classgen is needed to build NestedVM.  The original version at
 http://git.megacz.com/org.ibex.classgen.git
was configured to build under Java 1.3, and was generating warnings under
Java 1.8.

This is essentially the same code, but with type checking 
added so that it compiles without complaint under Java 1.8 (JDK 1.8 u25),
and the Makefile has been tweaked to include the 1.8 flag.


