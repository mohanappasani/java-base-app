package Callvalues;

public class Student {

	int id;
	String name;
	final static String clg = "AKRG";

	Student(int pId, String pName) {
		id = pId;
		name = pName;
	}

	final static String getColName() {
		return clg;
	}

	@Override
	public String toString() {
		return "Student [id=" + id + ", name=" + name + ", clg=" + clg + "]";
	}
	
	
}