package com.exception;

public class Multiplecatch {

	public static void main(String[] args) {
		int i =4;
		int j =3;


		
				
		
		try {
			int a[] = new int[6] ;
			a[9]=8;
			
			int k=i/j;
			System.out.println(k);
			System.out.println(a[9]);
		}
		catch (ArithmeticException e)
		{
			System.out.println("division error");
		
		}
		catch (ArrayIndexOutOfBoundsException e)
		{
			System.out.println("array error");
		
		}
		finally
		{
			System.out.println("code");
		}
		
	}

}
