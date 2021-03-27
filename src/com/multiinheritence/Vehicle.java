package com.multiinheritence;

public class Vehicle {

    String fuel;
	String wheels;
	public void f1() {
		System.out.println("f1 is called---");
	}
}
class car extends Vehicle{
	
	int milage;
}
class bike extends Vehicle{
	
}
