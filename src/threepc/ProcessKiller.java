package threepc;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public class ProcessKiller {
	
	/// for testing purposes
	/*
	public static void main(String[] args){
		String file = "/home/motro/Documents/Classes/DistComp/HW1/failfiles/failCoordOnly";
		
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
			
			Set<Integer> fds = new HashSet<Integer>();
			for(int i = 0; i < 4; i++){
				fds.add(i);
			}
			System.out.println(fc.check(FailType.PARTIAL_BROADCAST,MessageType.ACK,fds));
			System.out.println(fc.check(FailType.PARTIAL_BROADCAST,MessageType.COMMIT,fds));
			System.out.println(fc.check(FailType.PARTIAL_BROADCAST,MessageType.COMMIT,fds));
	} */
	
	private FailType failType;
	private int maxCount;
	private int counter;
	private int targetProcess;
	private MessageType messageType;
	private HashSet<Integer> procsToInclude;
	private TransactionState transactionState;
	
	public ProcessKiller(String filename, int procNum){

		Properties prop = new Properties();
		
		try {
			prop.load(new FileInputStream(filename));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
		// search the file for this processor's instructions
		if (prop.containsKey("fail"+procNum)){
			failType = FailType.valueOf( prop.getProperty("fail"+procNum) );
		} else {
			failType = null;
		}
	
		if (prop.containsKey("target"+procNum)){
			if((failType == FailType.PARTIAL_BROADCAST)||(failType == FailType.BEGINNING_STATE)){
				System.out.println("PKiller: target process named, but FailType doesn't use it");
			}
			targetProcess = loadInt(prop, "target"+procNum);
		} else {
			targetProcess = -1;
		}
		
		if (prop.containsKey("include"+procNum)){
			if(failType != FailType.PARTIAL_BROADCAST){
				System.out.println("PKiller: listed processes for partial broadcast without calling PARTIAL_BROADCAST");
			}
			String stringOfProcs = prop.getProperty("include"+procNum);
			String[] listOfProcs = stringOfProcs.split(",");
			if(listOfProcs.length > 0){
				procsToInclude = new HashSet<Integer>();
				for (String thisProcess: listOfProcs){
					if(!thisProcess.equals("")){
						procsToInclude.add(Integer.parseInt(thisProcess));
					}
				}
			}
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
				System.out.println("PKiller: arguments for check don't match fail type "+ft);
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
				if(((messageType == null)&&(message != MessageType.HEARTBEAT)) || (messageType == message)){
					if ((targetProcess < 0 ) || (targetProcess == process)){
						return trigger();
					}
				}
				break;
			default:
				System.out.println("PKiller: arguments for check don't match fail type "+ft);
		}
		return false;
	}
	
	//returns null if death not desired, full if death desired but no processes specified
	//and only some processes if those ones are specified in the fail file
	public Set<Integer> check(FailType ft, MessageType message, Set<Integer> allRecipients){
		if(ft != FailType.PARTIAL_BROADCAST){
			System.out.println("PKiller: arguments for check don't match fail type "+ft);
		}
		Set<Integer> procsToReturn = null;
		if (failType == FailType.PARTIAL_BROADCAST){
			if((messageType == null) || (messageType == message)){
				if(trigger()){
					procsToReturn = new HashSet<Integer>();
					for(Integer thisProcess : allRecipients){
						if((procsToInclude == null) || (procsToInclude.contains(thisProcess))){
							procsToReturn.add(thisProcess);
						}
					}
				}
			}
		}
		return procsToReturn;
	}
	
	private synchronized boolean trigger(){
		counter++;
		if (counter == maxCount){
			return true;
		}
		if(counter > maxCount){
			System.out.println("PKiller: counter too high, should have died before");
			return true;
		}
		return false;
	}
}
