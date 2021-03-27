package com.Throw;

public class Demo {

	public static void main(String[] args) {
		
		Profileconnection pc = new Profileconnection();
		try {
		User b = pc.handlelogin();
		System.out.println(b.id+ " "+b.name);
		}catch (Exception e) {
			e.printStackTrace();
		}
	}

}
