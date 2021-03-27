package com.methodex;

public class Demo {

	public Product getProduct(int id, String name) {
		Product p = new Product();
		p.id = id;
		p.name = name;
		return p;
	}

	public Product[] getProducts() {
		Product products[] = new Product[2];
		products[0] = new Product();
		products[0].id = 23;
		products[0].name = "abc";

		products[1] = new Product();
		products[1].id = 45;
		products[1].name = "xyz";

		return products;
		
	}
	
	

	public static void main(String[] args) {
		Demo d = new Demo();
		Product p = d.getProduct(23, "Washing..");
		p.display(p);
		

		Product[] prods = d.getProducts();
		for (int i = 0; i < prods.length; i++) {
			prods[i].display(prods[i]);
		}
	}

}
