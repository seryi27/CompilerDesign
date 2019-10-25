This is a project consisting on a compiler for a little languaje called jflex. For the moment it only parses and "prettyprint" a program, being able to detect some errors along the way.

How to run it:
First of all, in order to run this project, we should follow this steps:
	- unzip the files.
	- run the run.sh script, passing as an argument the name of the file we want to compile. (For example you can run "./run.sh src/test/success1.j")


Organization of the project:
I have divided the project in 3 main parts. 

First of all, we have the grammar package, consisting in the cup and jflex files. In my case, this files are named littlejava.cup and littlejava.jflex. This are the main files of this part of the project, and in them the hole grammar and rules of the jflex programming language are defined. 

Then, I have created another package, called javaCode, which contains all the other auxiliar classes of the project. It includes Ast.java (which is the abstract syntax tree class), LexerException.java (for the lexer problems such as comments without close) and NamedSymbolFactory, which is simply an extension of the ComplexSymbolFactory class.
The most important class here is the Ast.java, in which all the classes for the elements (both terminals and non-terminals). It defines the hierarchy  

Finally, a folder named test, which includes all the programs I have tried with my compiler (6 in total, with 2 of them being failing programs). One of the failing programs has an error in the lexer part (it ends without closing a comment) and the other one fails because a bad use of the if else sentences in jflex.

Major difficulties:
During the development of the project, I have encountered two main difficulties: 
First of all, the "hacking" of the regular expressions in the cup file. In order to define everything correctly, I have had to do a bunch of trial and error in this part, which has being the most time-consuming. On the other hand, defining the 'prettyprinting' methods has being the second most time-consuming part, as there were so many of them and, eventhough they are not difficult to implement, I needed to be really careful when implementing them.

Possible improvements:
Defining methods to print the abstract syntax tree. It is not necessary to print it, but it's always better to include as much functionality and options as possible.
Comment the code more in depth. Comments are really important in a professional code, specially if that code is gonna be used by other people.
Defining options in the run script, so that it is not necessary to do the hole process every time we want to "compile" a file.
