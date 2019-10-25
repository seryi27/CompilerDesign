ARG = irCode1

all:
	jflex -d ./src src/littlejava.jflex
	java -jar libs/java-cup-11b.jar -parser Parser -destdir ./src src/littlejava.cup
	javac -d ./compiled -cp libs/java-cup-11b-runtime.jar:. src/*.java
	java -cp ".:./libs/java-cup-11b-runtime.jar:./compiled" Ir3Generator ./test/$(ARG).j


compile:
	jflex -d ./src src/littlejava.jflex
	java -jar libs/java-cup-11b.jar -parser Parser -destdir ./src src/littlejava.cup
	javac -d ./compiled -cp libs/java-cup-11b-runtime.jar:. src/*.java

run:
	java -cp ".:./libs/java-cup-11b-runtime.jar:./compiled" Ir3Generator ./test/$(ARG).j


clean:
	rm src/Lexer.java src/Parser.java src/sym.java
	rm compiled/*.class
