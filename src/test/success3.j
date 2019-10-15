class Main {
Void main(Int i, Int a, Int b,Int d){
 Int t1;

 Int t2;

 t2 = (i + a)*(i + a);
 t1 = (b + d)*(b + d);

 if(t2>t1){

    println("i+a is bigger in absolute value than b + d");

 }
 
 else {

while(t2>t1){
	println("Now t2 value is");
	println(t2);
	println("");
	t2 = t2 - 1;

}
}



}



}