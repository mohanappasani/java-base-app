
public class Showroom {

	public static void main(String[] args) {
		Vehicle v1 = new Vehicle(709, "car", 999999.78f, (byte) 13);
		System.out.println(v1);
		v1.displayVehicleInfo();
		
		Vehicle v2 = new Vehicle(890);
		v2.displayVehicleInfo();
	}

}
