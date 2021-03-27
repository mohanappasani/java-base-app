
public class Product {
	int id;
	String name;
	float price;

	public void displayProductInfo() {
		System.out.println("Product id[" + id + "] Name[" + name + "] price[" + price + "]");
	}
	
	public float findDiscount() {
		if(price>500) {
			return 10;
		}
		return 0;
	}
}
