/* Mainly test multiple class (defined later but referenced first),

   Variable shadowing in Dummy class,

   chained field access expressions,

   e.g. this.getCompute().square(-3);

   Test combination of "if .. else .." "return" and "while"

 */



class Main {
Void main(Int i, Int a, Int b,Int d){
 Int t1;

 Int t2;

 /*

 help = new Compute();

 help.chachedValue = t1 * 3;

 t1 = help.addSquares(a,b) + help.square(i);

 t2 = help.square(d);

 if(t2>t1){

    println("Square of d larger than sum of squares");

 }

 else{

      println("Square of d larger than sum of squares");

 }

