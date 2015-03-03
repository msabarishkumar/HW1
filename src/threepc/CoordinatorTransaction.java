package threepc;

import java.util.HashSet;
import java.util.Set;

public class CoordinatorTransaction extends Transaction {
	Set<Integer> processWaitSet;
	
	Set<Integer> positiveResponseSet;
	
	private boolean abortFlag = false;
	private String reasonToAbort;
	
	public CoordinatorTransaction(Process process, Message message) {
		super(process, message);
		processWaitSet = new HashSet<Integer>();
		positiveResponseSet = new HashSet<Integer>();
		
		this.BUFFER_TIMEOUT = 2000;
		this.DECISION_TIMEOUT = process.delay + this.BUFFER_TIMEOUT;
	}
	
	public TransactionState getState()
	{
		if (state != TransactionState.COMMIT && state != TransactionState.ABORT) {
			return TransactionState.UNCERTAIN;
		} else {
			return state;
		}
	}
	
	@Override
	public void run() {
		lock.lock();
		// WaitSize > 0 is being checked because we have to collect all the responses because aborting/commiting.
		while((state != TransactionState.COMMIT && state != TransactionState.ABORT) || processWaitSet.size() > 0) {
			
			if(state == TransactionState.STARTING) {
				process.dtLog.write(TransactionState.UNCERTAIN, command);
				state = TransactionState.WAIT_DECISION;
				
				// Start a new transaction.
				Message msg = new Message(process.processId, MessageType.VOTE_REQ, command);
				processWaitSet.addAll(process.upProcess.keySet());
				process.config.logger.info("Received: " + message.toString());
				Process.waitTillDelay();
				process.config.logger.info("Sending VOTE_REQs.");
				process.controller.sendMsgs(processWaitSet, msg.toString(), -1);
				
				// Timeout if all the process don't reply back with a Yes or No.
				Thread th = new Thread() {
					public void run(){
						try {
							Thread.sleep(DECISION_TIMEOUT);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						lock.lock();
						state = TransactionState.DECISION_RECEIVED;
						if (processWaitSet.size() > 0 ) {
							reasonToAbort = "Did not get a reply from some processes.";
							abortFlag = true;
						}
						nextMessageArrived.signal();
						lock.unlock();
					}
				};
				th.start();
			}
			else if (state == TransactionState.WAIT_DECISION) {
				if (message.type == MessageType.YES) {
					process.config.logger.info("Received: " + message.toString());
					processWaitSet.remove(message.process_id);
					positiveResponseSet.add(message.process_id);
					if (processWaitSet.size() == 0) {
						process.config.logger.info("Successfully got all the YES replies.");
					}
				} else if (message.type == MessageType.NO) {
					process.config.logger.info("Received: " + message.toString());
					abortFlag = true;
					processWaitSet.remove(message.process_id);
					process.config.logger.info("Got a no from " + message.process_id);
					reasonToAbort = "Process " + message.process_id + " sent a NO !!";
				} else {
					process.config.logger.warning("Co-ordinator was waiting for YES/NO." + 
							" However got a " + message.type + ".");
					break;
				}
				
			}
			else if (state == TransactionState.DECISION_RECEIVED) {
				if (abortFlag) {
					abortTransaction();
				} else {
					// Send PRE_COMMIT message to all of them.
					Message msg = new Message(process.processId, MessageType.PRE_COMMIT, command);
					processWaitSet = positiveResponseSet;
					positiveResponseSet = new HashSet<Integer>();

					process.config.logger.info("Received Yes from all the processes");
					Process.waitTillDelay();
					process.config.logger.info("Sending PRE_COMMIT to all the processes.");
					
					int partial_count = -1;
					if (!System.getProperty("PartialPreCommit").equals("-1")) {
						partial_count = Integer.parseInt(System.getProperty("PartialPreCommit"));
					}
					process.controller.sendMsgs(processWaitSet, msg.toString(), partial_count);
					
					// Update your state to waiting for all the decisions to arrive.
					state = TransactionState.WAIT_ACK;
					
					// Timeout if all the process don't reply back with a Yes or No.
					Thread th = new Thread() {
						public void run(){
							try {
								Thread.sleep(DECISION_TIMEOUT);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							lock.lock();
							state = TransactionState.ACK_RECEIVED;
							nextMessageArrived.signal();
							lock.unlock();
						}
					};
					th.start();
				}
			} 
			else if (state == TransactionState.WAIT_ACK) {
				if (message.type != MessageType.ACK) {
					process.config.logger.warning("Co-ordinator was waiting for Acknowledgement." + 
							" However got a " + message.type + ".");
					break;
				}
				process.config.logger.info("Received: " + message.toString());
				processWaitSet.remove(message.process_id);
				positiveResponseSet.add(message.process_id);
				if (processWaitSet.size() == 0) {
					process.config.logger.info("Successfully got all the acknowledgements.");
				}
			}
			else if (state == TransactionState.ACK_RECEIVED) {
				Message msg = new Message(process.processId, MessageType.COMMIT, command);
				process.dtLog.write(TransactionState.COMMIT, command);
				state = TransactionState.COMMIT;
				process.config.logger.info("Acknowledgments have been received.");
				process.notifyTransactionComplete();
				Process.waitTillDelay();
				process.config.logger.info("Sending COMMIT message to processes from which received ACK.");
				
				int partial_count = -1;
				if (!System.getProperty("PartialCommit").equals("-1")) {
					partial_count = Integer.parseInt(System.getProperty("PartialCommit"));
				}
				process.controller.sendMsgs(process.upProcess.keySet(), msg.toString(), partial_count);
				positiveResponseSet.clear();
				processWaitSet.clear();
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
	
	public void abortTransaction() {
		process.dtLog.write(TransactionState.ABORT, command);
		state = TransactionState.ABORT;
		process.config.logger.warning("Transaction aborted: " + reasonToAbort);
		process.notifyTransactionComplete();
		Message msg = new Message(process.processId, MessageType.ABORT, command);
		Process.waitTillDelay();
		process.config.logger.info("Sending Abort messages to all the process.");
		process.controller.sendMsgs(process.upProcess.keySet(), msg.toString(), -1);
		
		processWaitSet.clear();
		positiveResponseSet.clear();
	}
}