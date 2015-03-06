package threepc;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

import util.DTLog;

public class RecoveryTransaction extends Transaction {
	TransactionState restartState;
	
	boolean isTotalFailure = true;
	
	Set<Integer> upProcessSet;
	Hashtable<Integer, Set<Integer>> allUpSets;
		
	public RecoveryTransaction(Process process, Message msg) {
		super(process, msg);
		
		this.state = TransactionState.RECOVERING;
		this.restartState = TransactionState.STARTING;
		
		upProcessSet = process.dtLog.getLastUpProcessSet(process.processId);
		allUpSets = new Hashtable<Integer, Set<Integer>>();
		
		this.BUFFER_TIMEOUT = 5000;
		this.DECISION_TIMEOUT = Process.delay + this.BUFFER_TIMEOUT;
	}
	
	@Override
	public void run() {
		lock.lock();

		// Allow time to connect.
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		while(state != TransactionState.COMMIT || state != TransactionState.ABORT ) {
			if (restartState == TransactionState.STARTING) {
				Message msg = new Message(this.process.processId, MessageType.STATE_ENQUIRY, command);
				
				restartState = TransactionState.STATE_ENQUIRY_WAIT;
				Process.config.logger.info("Sending: " + msg.toString());
				process.controller.broadCastMsgs(msg.toString());
				
				Thread th = new Thread() {
					public void run() {
						try {
							Thread.sleep(DECISION_TIMEOUT);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						lock.lock();
						if (isTotalFailure) {
							if (isIntersectionPresent()) {
								process.dtLog.write(TransactionState.ABORT, command);
								state = TransactionState.ABORT;
								Process.config.logger.info("All process present in the intersection set are UP.");
								Process.config.logger.info("Transaction aborted.");
								process.notifyTransactionComplete();
							} else {
								try {
									// WAIT for another 5 seconds and then re-try.
									lock.unlock();
									Thread.sleep(5000);
									lock.lock();
									restartState = TransactionState.STARTING;
									isTotalFailure = true;
									nextMessageArrived.signal();
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
							}
						}
						allUpSets.clear();
						lock.unlock();
					}
				};
				th.start();
			} else if (restartState == TransactionState.STATE_ENQUIRY_WAIT) {
				if (message.type == MessageType.STATE_COMMIT) {
					process.dtLog.write(TransactionState.COMMIT, command);
					state = TransactionState.COMMIT;
					Process.config.logger.info("Received: " +  message.toString());
					Process.config.logger.info("Transaction committed.");
					process.notifyTransactionComplete();
					break;
				} else if (message.type == MessageType.STATE_ABORT) {
					process.dtLog.write(TransactionState.ABORT, command);
					state = TransactionState.ABORT;
					Process.config.logger.info("Received: " +  message.toString());
					Process.config.logger.info("Transaction aborted.");
					process.notifyTransactionComplete();
					break;
				} else if (message.type != MessageType.STATE_RECOVERING) { 
					// If I get any message, abort or commit = recovery complete.
					// If I get resting or uncertain, it also means that someone
					// is running the transaction.
					Process.config.logger.info("Received: " +  message.toString());
					isTotalFailure = false;
				} else if (message.type == MessageType.COMMIT) {
						process.dtLog.write(TransactionState.COMMIT, command);
						state = TransactionState.COMMIT;
						Process.config.logger.info("Received: " +  message.toString());
						Process.config.logger.info("Transaction committed.");
						process.notifyTransactionComplete();
						break;
				} else if (message.type == MessageType.ABORT) {
						process.dtLog.write(TransactionState.ABORT, command);
						state = TransactionState.ABORT;
						Process.config.logger.info("Received: " +  message.toString());
						Process.config.logger.info("Transaction aborted.");
						process.notifyTransactionComplete();
						break;
				} else {
					Process.config.logger.info("Received: " + message.toString());
					
					Set<Integer> upProcessSetRecived = new HashSet<Integer>();
					String payload = message.payLoad.substring(1, message.payLoad.length() - 1);
					String[] payload_split = payload.split(DTLog.UpSet_SEPARATOR);
					for(String proc_no: payload_split){
						if(!proc_no.trim().isEmpty()){
							upProcessSetRecived.add(Integer.parseInt(proc_no));
						}
		   		    }
					
					allUpSets.put(message.process_id, upProcessSetRecived);
				}
			} 
			
			try {
				nextMessageArrived.await();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		lock.unlock();
	}
	
	public void update(Message message) {
		lock.lock();
		
		dieIfNMessagesReceived();

		this.message = message;
		nextMessageArrived.signal();
		
		lock.unlock();
	}
	
	private boolean isIntersectionPresent() {
		Set<Integer> intersection = new HashSet<Integer>(upProcessSet);
		intersection.add(process.processId);
		
		for (Map.Entry<Integer, Set<Integer>> entry : allUpSets.entrySet())
		{
			Set<Integer> temp = entry.getValue();
			temp.add((Integer) entry.getKey());
			
			intersection.retainAll(temp);
		}

		boolean isIntersectionPresent = true;
		allUpSets.put(process.processId, upProcessSet);
		for (Integer i : intersection) {
			if (!allUpSets.containsKey(i)) {
				isIntersectionPresent = false;
			}
		}
		
		return isIntersectionPresent;
	}
	
	public String getUpStates() {
		return upProcessSet.toString();
	}
}
