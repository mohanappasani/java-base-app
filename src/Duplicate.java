
public class Duplicate {

	public static void main(String[] args) {

		int[] arr = new int[] {3,4,4,5,6,6,7,7,9,10};
		
		for (int i =0;i<arr.length;i++) {
	  for(int j = i + 1; j < arr.length; j++)	{
		  if (arr [i] == arr [j])
		  {
			  System.out.println("Hii" + arr[j]);
			  System.out.println("Hii..");
			  System.out.println("Hii..");
		  }
	  }
			
			
		}
		
	}

	
}   

