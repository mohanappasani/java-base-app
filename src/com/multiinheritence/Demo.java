package com.multiinheritence;

public class Demo {

	public static void main(String[] args) {
		Vehicle v = new Vehicle();
		v.fuel= "disel";
		v.wheels = "four";
		System.out.println(v.fuel);
		System.out.println(v.wheels);
		
		bike b = new bike();
		b.wheels ="two" ;
		b.fuel = "petrol";
		b.f1();
		System.out.println(b.wheels);
		System.out.println(b.fuel);
		
	}

}
