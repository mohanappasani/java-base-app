package com.Collectionmap;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Demo {

	public static void main(String[] args) {

		Map<Integer, Product> pmap = new HashMap<Integer, Product>();

		Product p = new Product();
		p.id = 1;
		p.Name = "Mohan";
		p.prize = 34.5f;

		Product p1 = new Product();
		p1.id = 2;
		p1.Name = "Rohan";
		p1.prize = 64.5f;

		pmap.put(11, p);
		pmap.put(12, p1);

		Product pp = pmap.get(12);
		System.out.println(pp.id + pp.Name);

		Set<Integer> keys = pmap.keySet();
		for (Integer k : keys) {
			System.out.println("Key::" + k);
			Product pro = pmap.get(k);
			System.out.println("Value::" + pro.id + pro.Name);
		}
		
		System.out.println("Second way=====");
		Set<Map.Entry<Integer, Product>> entrys = pmap.entrySet();
		for (Map.Entry<Integer, Product> e : entrys) {
			System.out.println("Key::" + e.getKey());
			Product pro = e.getValue();
			System.out.println("Value::" + pro.id + pro.Name);
		}

	}

}
