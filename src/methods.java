
public class methods {

	public static int add(int x, int y) {
		int z = x + y;
		return z;
	}

	public static boolean isElementExists(int arr[], int ele) {
		boolean isPresent = false;
		for (int i = 0; i < arr.length; i++) {
			if (arr[i] == ele) {
				isPresent = true;
				break;
			}
		}
		return isPresent;
	}

	public static boolean isEven(int n) {
		if (n % 2 == 0) {
			return true;
		} else {
			return false;
		}
	}

	public static String displayMsg(String msg) {
		String s = "Hi welcome MR." + msg;
		return s;
	}

	public static boolean isOld(int age) {
		if (age >= 60) {
			return true;
		}
		return false;
	}

	public static int filterOldAge(int ages[]) {
		int oldPpl = 0;
		for (int i = 0; i < ages.length; i++) {
			if (ages[i] >= 60) {
				oldPpl = oldPpl + 1;
			}
		}
		return oldPpl;
	}

	public static boolean nagate(boolean b) {
		return !b;
	}

	public static float[] addBonus(float salaray[]) {
		for (int i = 0; i < salaray.length; i++) {
			if (salaray[i] > 500) {
				continue;
			}
			salaray[i] = salaray[i] + 10;
		}
		return salaray;
	}

	public static void main(String[] args) {
// int r = add(5, 6);
// int r1 = add(10, 20);
// System.out.println(r);
// System.out.println(r1);
// int elements[] = { 10, 50, 40, 90 };
// boolean result = isElementExists(elements, 50);

// boolean result = isEven(11);
// System.out.println("result ==>>" + result);

// String str = displayMsg("Ravi");

// boolean result = isOld(40);
// System.out.println(result);

// int ages[] = {40,50,60,80,89};
// int noOfOldPpl = filterOldAge(ages);
// System.out.println("noOfOldPpl===>"+noOfOldPpl);
		float sal[] = { 100.50f, 200.5f, 600.55f, 501.54f };
		float salBon[] = addBonus(sal);
		for (int i = 0; i < salBon.length; i++) {
			System.out.println(salBon[i]);
		}
	}

}