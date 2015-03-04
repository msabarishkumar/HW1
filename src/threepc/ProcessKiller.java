package threepc;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public class ProcessKiller {
	
	/// for testing purposes
	public static void main(String[] args){
		String file = "/home/motro/Documents/Classes/DistComp/HW1/src/fail0";
		
		try {
			ProcessKiller fc = new ProcessKiller(file, 0);
			if(fc.failType != null){
				System.out.println("Failure: "+fc.failType.toString());
			}
			if(fc.messageType != null){
				System.out.println("Message: "+fc.messageType.toString());
			}
			if(fc.transactionState != null){
				System.out.println("State: "+fc.transactionState.toString());
			}
			System.out.println("counter is " + fc.maxCount);
			System.out.println("target process is "+ fc.targetProcess);
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public ProcessKiller(String filename, int procNum) throws FileNotFoundException, IOException {

		Properties prop = new Properties();
		prop.load(new FileInputStream(filename));
		
		// search the file for this processor's instructions
		if (prop.containsKey("fail"+procNum)){
			failType = FailType.valueOf( prop.getProperty("fail"+procNum) );
		} else {
			failType = null;
		}
	
		if (prop.containsKey("target"+procNum)){
			targetProcess = loadInt(prop, "target"+procNum);
		} else {
			targetProcess = -1;
		}
		
		if (prop.containsKey("message"+procNum)){
			messageType = MessageType.valueOf(prop.getProperty("message"+procNum));
		} else {
			messageType = null;
		}
		
		if (prop.containsKey("counter"+procNum)){
			maxCount = loadInt(prop, "counter"+procNum);
		} else {
			maxCount = 1;
		}
		
		if (prop.containsKey("state"+procNum)){
			transactionState = TransactionState.valueOf( prop.getProperty("state"+procNum) );
		} else {
			transactionState = null;
		}
		
		counter = 0;
	}
	
	private int loadInt(Properties prop, String s) {
		return Integer.parseInt(prop.getProperty(s.trim()));
	}
	
	public boolean check(FailType ft, TransactionState state){
		if (failType != ft){
			return false;
		}
		switch (failType){
			case BEGINNING_STATE:
			case ENDING_STATE:
				if(transactionState == null){
					return trigger();
				}
				if(transactionState == state){
					return trigger();
				}
				break;
			default:
				System.out.println("Error! Transaction state input for unrelated failure case.");
		}
		return false;
	}
	
	// overload for easy use
	public boolean check(FailType ft, MessageType message, int process){
		if (failType != ft){
			return false;
		}
		switch (failType){
			case BEFORE_DELIVER:
			case AFTER_DELIVER:
			case BEFORE_SEND:
			case AFTER_SEND:
				if((messageType == null) || (messageType == message)){
					if ((targetProcess < 0 ) || (targetProcess == process)){
						return trigger();
					}
				}
				break;
			default:
				System.out.println("Error! Message input for unrelated failure case.");
		}
		return false;
	}
	
	private boolean trigger(){
		counter++;
		if (counter >= maxCount){
			return true;
		}
		return false;
	}
	
	private FailType failType;
	private int maxCount;
	private int counter;
	private int targetProcess;
	private MessageType messageType;
	private TransactionState transactionState;

}
