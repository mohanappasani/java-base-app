
public class Vehicle {
	int id;
	String type;
	float price;
	byte milage;

	Vehicle(int vid) {
		id = vid;
	}
	
	Vehicle(int vid, String vtype, float vprice, byte vmilage) {
		id = vid;
		type = vtype;
		price = vprice;
		milage = vmilage;
	}
   public void displayVehicleInfo() {
	   System.out.println("Vehicle [id=" + id + ", type=" + type + ", price=" + price + ", milage=" + milage + "]");
   }
	
//	@Override
//	public String toString() {
//		return "Vehicle [id=" + id + ", type=" + type + ", price=" + price + ", milage=" + milage + "]";
//	}
	
	
}
