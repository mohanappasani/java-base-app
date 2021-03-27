
public class Marks {
	String StudentName;
	int RollNumber;
	String subject;
	float score;

	public void displayMarks() {
		System.out.println("result ==>>" + score);
	}

	public void setScore(float pScore) {
		score = pScore;
	}
}
