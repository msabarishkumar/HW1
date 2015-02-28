package test;

public class Main {
	public static void main(String[] args) {
		try {
			System.out.println(System.getProperty("ConfigName"));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
