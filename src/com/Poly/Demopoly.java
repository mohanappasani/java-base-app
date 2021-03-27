package com.Poly;

public class Demopoly {

	public static void main(String[] args) {

		Hello ih = null;
		
		String gender = "male";
		
		if (gender== "male") {
			
			ih = new Male();
		}		
		else
		{	
			ih = new Female();

		}
		
		ih.sayHii("ravi");
		
		}
		

}
