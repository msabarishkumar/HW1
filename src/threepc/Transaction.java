package threepc;

import java.util.Arrays;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Transaction implements Runnable {
	public int DECISION_TIMEOUT;
	public int BUFFER_TIMEOUT;

	Process process;

	String command;

	Message message;

	boolean stateRequestReceived = false;
	boolean stateRequestResponseReceived = false;

	TransactionState state = TransactionState.STARTING;

	protected final Lock lock = new ReentrantLock();
	protected final Condition nextMessageArrived = lock.newCondition();

	// Should the process accept the current transaction.
	public boolean decision = true;

	// This is to determine whether to send an abort decision to coordinator or
	// not.
	public boolean sendAbort = true;

	public Transaction(Process process, Message message) {
		this.process = process;
		this.command = message.payLoad;
		this.message = message;
		
		this.BUFFER_TIMEOUT = 4000;
		this.DECISION_TIMEOUT = Process.delay + this.BUFFER_TIMEOUT;
	}

	public TransactionState getState() {
		if (state == TransactionState.COMMITABLE) {
			return TransactionState.UNCERTAIN;
		}
		
		return this.state;
	}

	@Override
	public void run() {
		lock.lock();
		
		while (state != TransactionState.COMMIT && state != TransactionState.ABORT) {

			if (state == TransactionState.STARTING) {
				///dieIfNMessagesReceived();
				
				process.dtLog.write(TransactionState.STARTING, command);
				System.out.println("Starting the transaction");
				
				if (!decision) {
					process.dtLog.write(TransactionState.ABORT, command);
					state = TransactionState.ABORT;
					process.notifyTransactionComplete();
					Process.config.logger.warning("Transaction aborted.");
					
					if (sendAbort) {
						Process.config.logger.info("Received: " + message.toString());
						Message msg = new Message(process.processId, MessageType.NO, " ");
						Process.waitTillDelay();
						Process.config.logger.info("Sending No.");
						sendMsg(process.coordinatorNumber, msg);
					}
					break;
				} else {
					process.dtLog.write(TransactionState.UNCERTAIN, command);
					state = TransactionState.UNCERTAIN;
					System.out.println("Received: "	+ message.toString());
					Process.config.logger.info("Received: "	+ message.toString());
					Message msg = new Message(process.processId, MessageType.YES, " ");
					Process.waitTillDelay();
					Process.config.logger.info("Sending Yes.");
					System.out.println("Sending Yes");
					sendMsg(process.coordinatorNumber, msg);
					Process.config.logger.info("Waiting to receive either PRE_COMMIT or ABORT.");

					// Timeout if coordinator doesn't say anything.
					Thread th = new Thread() {
						public void run() {
							try {
								Thread.sleep(DECISION_TIMEOUT);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							lock.lock();
							if (state == TransactionState.UNCERTAIN) {
								Process.config.logger.warning("Did not receive either PRE_COMMIT or ABORT from coordinator. It must be dead.");
								electCordinator();
							}
							lock.unlock();
						}
					};
					th.start();
				}
			} else if (state == TransactionState.UNCERTAIN) {
				if (message.type == MessageType.ABORT) {
					stateRequestResponseReceived = true;
					process.dtLog.write(TransactionState.ABORT, command);
					state = TransactionState.ABORT;
					process.notifyTransactionComplete();
					Process.config.logger.info("Transaction aborted. Co-ordinator sent an abort.");
					break;
				} else if (message.type == MessageType.PRE_COMMIT) {
					stateRequestResponseReceived = true;
					Process.config.logger.info("Received: " + message.toString());
					Process.config.logger.info("Updated state to COMMITABLE.");
					System.out.println("Updated state to COMMITABLE.");
					state = TransactionState.COMMITABLE;

					Message msg = new Message(process.processId, MessageType.ACK, " ");
					System.out.println("Sending Acknowledgment.");
					Process.waitTillDelay();
					Process.config.logger.info("Sending Acknowledgment.");
					sendMsg(process.coordinatorNumber, msg);

					Thread th = new Thread() {
						public void run() {
							try {
								Thread.sleep(DECISION_TIMEOUT);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							lock.lock();
							if (state == TransactionState.COMMITABLE) {
								Process.config.logger.warning("Did not receive COMMIT from the coordinator. It must be dead.");
								electCordinator();
							}
							lock.unlock();
						}
					};
					th.start();
				} else {
					Process.config.logger.warning("Was expecting either an ABORT or PRE_COMMIT." + "However, received a: " + message.type);
					break;
				}
			} else if (state == TransactionState.COMMITABLE) {
				Process.config.logger.info("Received: " + message.toString());
				if (message.type == MessageType.COMMIT) {
					stateRequestResponseReceived = true;
					process.dtLog.write(TransactionState.COMMIT, command);
					state = TransactionState.COMMIT;
					System.out.println("Transaction Committed.");
					Process.config.logger.info("Transaction Committed.");
					process.notifyTransactionComplete();
					break; // STOP THE LOOP
				} else {
					Process.config.logger .warning("Was expecting only a COMMIT message." + "However, received a: " + message.type);
					break;
				}
			}

			// Start waiting for the next message to come.
			try {
				nextMessageArrived.await();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		lock.unlock();
	}

	private void electCordinator() {
		stateRequestReceived = false;
		
		Integer[] keys = new Integer[process.upProcess.keySet().size() + 1]; 
		keys[0] = process.processId;
		
		int counter = 1;
		for(Integer x : process.upProcess.keySet()) {
			keys[counter++] =  x;
		}
		
		Arrays.sort(keys);
		
		if (keys[0] > process.processId) {
			keys[0] = process.processId;
		}
		
		int temp = process.coordinatorNumber;
		while ((keys[0] - temp) > 1) {
			temp = temp + 1;
			Process.config.logger.info("As per round robin, Selecting: " + temp);
			Process.config.logger.info("Discarding: " + temp + ", as it is down.");
		}

		Process.config.logger.info("Elected new coordinator: " + keys[0]);

		// Sending UR_SELECTED message to the new coordinator.
		Message msg = new Message(process.processId, MessageType.UR_SELECTED, command);
		Process.waitTillDelay();
		Process.config.logger.info("Sending: " + msg + " to: " + keys[0]);
		sendMsg(keys[0], msg);

		// If I am not the elected coordinator then update the new coordinator
		// number.
		if (keys[0] != process.processId) {
			process.coordinatorNumber = keys[0];

			// Start waiting for the new STATE_REQ message.
			Thread th = new Thread() {
				public void run() {
					try {
						Thread.sleep(DECISION_TIMEOUT * 2);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					if (!stateRequestReceived) {
						lock.lock();
						Process.config.logger.info("Reelect coordinator.");
						electCordinator();
						lock.unlock();
					}
				}
			};

			th.start();
		}
	}

	public void enforceStop() {
		lock.lock();

		state = TransactionState.COMMIT;
		nextMessageArrived.signal();

		lock.unlock();
	}

	public void update(Message message) {
		lock.lock();

		///dieIfNMessagesReceived();

		this.message = message;
		if (message.type == MessageType.STATE_REQ) {
			stateRequestReceived = true;
			process.coordinatorNumber = message.process_id;
			Process.config.logger.info("Received: " + message.toString());
			Process.waitTillDelay();
			if (state == TransactionState.STARTING) {
				state = TransactionState.ABORT;
			}
			
			Message response = new Message(process.processId, MessageType.STATE_VALUE, state.toString());
			Process.config.logger.info("Sending: " + response.toString());
			stateRequestResponseReceived = false;
			sendMsg(message.process_id, response);

			if (state == TransactionState.UNCERTAIN	|| state == TransactionState.COMMITABLE) {
				Thread th = new Thread() {
					public void run() {
						try {
							// Increasing this time because Coordinator might have had to send to uncertain process also.
							Thread.sleep(DECISION_TIMEOUT * 3);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						if (stateRequestResponseReceived == false) {
							lock.lock();
							Process.config.logger.info("Going to reelect the cordinator.");
							electCordinator();
							lock.unlock();
						}
					}
				};
				th.start();
			}
		} else {
			nextMessageArrived.signal();
		}

		lock.unlock();
	}
	
	private void sendMsg(int toWho, Message msg){
		if(process.processKiller.check(FailType.BEFORE_SEND, msg.type, toWho)){
			process.die();
		}
		process.controller.sendMsg(toWho, msg.toString());
		
		if(process.processKiller.check(FailType.AFTER_SEND, msg.type, toWho)){
			process.die();
		}
	}

	
	protected void dieIfNMessagesReceived() {
		if(process.dieAfter.size() < 2) {
			return ;
		}
		
		int pidToWaitFor = process.dieAfter.get(0);
		int numMsg = process.dieAfter.get(1);
		
		if (pidToWaitFor == message.process_id) {
			numMsg = numMsg - 1;
			if (numMsg == 0) {
				Process.config.logger.info("Received n messages from: " + message.process_id);
				Process.config.logger.warning("Killing  myself");
				System.exit(1);
			}
			process.dieAfter.set(1, numMsg);
		}
	} 
	
	public String getUpStates() {
		return "";
	}
}