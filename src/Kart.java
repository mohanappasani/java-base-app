
public class Kart {
	

	public static void main(String[] args) {
		Product p1 = new Product();
		p1.displayProductInfo();
		p1.id= 70935 ;
		p1.name="Jumpingcar";
		p1.price= 780.50f;
		
		p1.displayProductInfo();
		float discount = p1.findDiscount();
		System.out.println("discount==>"+discount);
	}

}
