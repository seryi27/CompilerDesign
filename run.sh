#!/bin/bash
 
echo "generating the lexer"
java -jar libs/jflex-full-1.7.0.jar src/littlejava.jflex
echo "generating the parser"
java -jar libs/java-cup-11b.jar src/littlejava.cup

mv *.java ./src

echo "compiling the java classes"
javac -d ./compiled ./src/LexerException.java

javac -cp ".:./libs/java-cup-11b.jar:./libs/java-cup-11b-runtime.jar:./libs/jflex-full-1.7.0.jar:./compiled" -d ./compiled  ./src/Ast.java
javac ./src/NamedSymbolFactory.java -cp ".:./libs/java-cup-11b.jar:./libs/java-cup-11b-runtime.jar:./libs/jflex-full-1.7.0.jar:./compiled" -d ./compiled 

javac -cp ".:./libs/java-cup-11b.jar:./libs/java-cup-11b-runtime.jar:./libs/jflex-full-1.7.0.jar:./compiled" -d ./compiled ./src/sym.java

javac -cp ".:./libs/java-cup-11b.jar:./libs/java-cup-11b-runtime.jar:./libs/jflex-full-1.7.0.jar:./compiled" -d ./compiled ./src/Lexer.java

javac -cp ".:./libs/java-cup-11b.jar:./libs/java-cup-11b-runtime.jar:./libs/jflex-full-1.7.0.jar:./compiled" -d ./compiled ./src/parser.java

echo "running the program"
java -cp ".:./libs/java-cup-11b.jar:./libs/java-cup-11b-runtime.jar:./libs/jflex-full-1.7.0.jar:./compiled" parser $1




