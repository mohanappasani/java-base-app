package com.Collectionex;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Demo {

	public static void main(String[] args) {

		List<Integer> list = new ArrayList<Integer>();
		list.add(10);
		list.add(20);
		int x = list.get(1);
		System.out.println(x);
		for (int i : list) {
			System.out.println("List value::" + i);

		}

		Set<Integer> set = new HashSet<>();
		set.add(45);
		set.add(56);

		for (int i : set) {
			System.out.println("Set value::" + i);

		}
	}

}
