package com.inheritence;

public class Demo {

	public static void main(String[] args) {

		Circle c = new Circle();
		c.r =89;
		c.height = 90;
		c.render();
		
		Shape s = new Shape();
		s.height = 78;
		s.width = 96;
		s.render();
		
		Box b = new Box();
		b.height = 56;
		b.width = 67;
		b.render();
		b.display();
	}

}
