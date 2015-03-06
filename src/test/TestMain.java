package test;

import java.io.IOException; 
import java.util.Scanner;

public class TestMain {
	public static void main(String[] args) throws InterruptedException {
		
		//looks crazy but it works
		String bashStringBeginning = "gnome-terminal -x sh -c \"cd HW1; cd bin; java threepc.Process ";
		String bashStringEnding = "; bash\"";
		
		int numProc = 3;
		
		
		for(int thisProcessID = 0; thisProcessID < numProc; thisProcessID ++){
			//int thisProcessID = 1;
			String bashString = bashStringBeginning + thisProcessID + bashStringEnding;
			
			ProcessBuilder pb = new ProcessBuilder("bash","-c",bashString);
			try {
				pb.start();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		Scanner sc = new Scanner(System.in);
		System.out.println("type ID to restart process");
		System.out.println("be careful to make sure it already failed!");
		
		while(true){
			int thisProcessID = sc.nextInt();
			String bashString = bashStringBeginning + thisProcessID + bashStringEnding;
			ProcessBuilder pb = new ProcessBuilder("bash","-c",bashString);
			try {
				pb.start();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
	}
}
/*
///EDIT3:  adding in system properties, don't know how to set otherwise
String directory = System.getProperty("user.dir");
if (directory.substring(directory.length() - 4).equals("/bin")){
	directory = directory.substring(0,directory.length() - 4);
}
if (!directory.substring(directory.length() - 4).equals("/HW1")){
	directory = directory + "/HW1";
}
System.out.println(directory);
System.setProperty("CONFIG_PATH",directory + "/src/config0");
System.setProperty("DELAY","1000");
System.setProperty("LOG_FOLDER",directory + "/log");
System.setProperty("DeathAfter","2=2");
System.setProperty("PartialPreCommit", "-1");
System.setProperty("PartialCommit", "-1");
System.setProperty("FAIL_PATH",directory + "/failfiles/nofail");

*/