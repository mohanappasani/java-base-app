package com.Throw;

 public class Profileconnection {
	
	int i= 10;
	
	Userrepo us = new Userrepo();
	
	
	public User handlelogin()  throws Exception {
		
		User u = us.getuser("0");
		System.out.println("Profileconnection.handlelogin()");
		return u;
		
	}


	

}
