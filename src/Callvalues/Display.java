package Callvalues;


public class Display {

	public void display(String s) {
		System.out.println(s);
	}

	public void display(int x) {
		System.out.println(x);
	}

	public void displayStu(Student s) {
		System.out.println(s.id + " " + s.name + " " + s.getColName());
	}

	public void displayNumbers(int arr[]) {
		for (int i = 0; i < arr.length; i++) {
			System.out.println(arr[i]);
		}
	}

	int getNumber() {
		return 25;
	}

	String getStr() {
		return "asfasdfasdf";
	}

	Student getStu() {
		Student stu = new Student(90, "Rama Krishna");
		return stu;
	}

	int[] getNumbers() {
		int arr[] = { 45, 67, 89 };
		return arr;
	}
}