package Collectionlist;

import java.util.ArrayList;
import java.util.List;

public class Demo {

	public static void main(String[] args) {

		List<Product>  List = new ArrayList<Product>();
		
		Product p =new Product();
		p.id = 23;
		p.Name = "ram";
		p.prize = 89.6f;
		
		Product po = new Product();
		po.id =  22;
		po.Name = "cam";
		po.prize = 23.6f;
		
		List.add(p);
		List.add(po);
		
		for( Product pd : List) {
			
			System.out.println(pd.id  +  pd.Name + pd.prize);
			
		}
		
		
		
		
	}

}
