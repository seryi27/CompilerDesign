/* Mainly test multiple class (defined later but referenced first),
 */



class Main {
Void main(Int i, Int a, Int b,Int d){
 Int t1;

 Int t2;

 help =/*
			multicomment in the middle of a assignation 

 */ new Compute();

 help.chachedValue = t1 * 3;

 t1 = help.addSquares(a,b) + help.square(i);

 t2 = help.square(d);

 if(t2>t1){

    println("Square of d larger than sum of squares");

 }

 else{

      println("Square of d larger than sum of squares");

 }
 }
}