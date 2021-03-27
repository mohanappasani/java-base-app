package com.Throw;

public class Userrepo {
	
	int y= 20;
	Dbconnection com = new Dbconnection();
	
	public User getuser(String id) throws Exception {
		
		User u = com.getuser(id);
		
		System.out.println("Userrepo.getuser()");
		
		return u;
	}

}
