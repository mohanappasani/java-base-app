
public class Result {

	public static void main(String[] args) {
		Marks p1 = new Marks();
		p1.StudentName= "Mohan";
		p1.RollNumber= 23;
		//p1.score = 67.5f;
		p1.setScore(67.5f);
		p1.subject = "Hindi";
		System.out.println(p1.RollNumber);
		System.out.println(p1.score);
		System.out.println(p1.subject);
		System.out.println(p1.subject);
		p1.displayMarks();
	}

}
