package com.inheritence;

class Shape {
	int height;
	int width;
	String name;

	public void render() {
		System.out.println("======= Shape render method ====");
	}
}

class Circle extends Shape {
	int r;

	public void render() {
		System.out.println("==== Cirlce Render method ====== ");
	}
}

public class Box extends Shape {

	int val;

	public void display() {
		System.out.println("===== Box Display method =========");
	}
}
