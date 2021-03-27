package Callvalues;



public class Testclass {

	public static void main(String[] args) {

		System.out.println(Student.clg);
		System.out.println(Student.getColName());
		
		// pass primitive type as argument/ parameter.
		Display d = new Display();
		int x = 50;
		// pass primitive type
		
		d.display(x);
		String s = "Rambabu";
		// pass primitive type as argument/ parameter.
		d.display(s);
		Student st = new Student(0, "Raju");
		// pass user defined/custom type
		d.displayStu(st);
		// passing array as argument to method.
		int ar[] = { 1, 3, 5 };
		d.displayNumbers(ar);

		// get primitive type as return value.
		int a = d.getNumber();
		System.out.println(a);
		String b = d.getStr();
		System.out.println(b);
		// get user defined/custom type as return value.
		Student stu = d.getStu();
		System.out.println(stu.id + " " + stu.name + " " + stu.getColName());

		// getting array as return value from method.
		int numbers[] = d.getNumbers();
		for (int i = 0; i < numbers.length; i++) {
			System.out.println(numbers[i]);
		}

		System.out.println();
		System.out.println(numbers.length);
		
	}

	
}