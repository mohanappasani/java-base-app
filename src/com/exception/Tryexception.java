package com.exception;

public class Tryexception {

	public static void main(String[] args) {
		
		try {
			int i=4;
			int j=0;
			int k=i/j;
			
			System.out.println(k);
			
		}
		catch (Exception e)
		{
			System.out.println("error");
		}

		System.out.println("thats the value");
	}

}
