package com.Throw;

public class Dbconnection {

	User getuser(String id) throws Exception {
		User u = new User();
		u.id = 78;
		u.name = "jhg";
		System.out.println("Dbconnection.getuser()");
		if (1 == 1) {
			Exception e = new Exception();
			throw e;
		}
		return u;

	}

}
