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
		this.DECISION_TIMEOUT = process.delay + this.BUFFER_TIMEOUT;
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
				dieIfNMessagesReceived();
				
				process.dtLog.write(TransactionState.STARTING, command);
				if (!decision) {
					process.dtLog.write(TransactionState.ABORT, command);
					state = TransactionState.ABORT;
					process.notifyTransactionComplete();
					process.config.logger.warning("Transaction aborted.");
					
					if (sendAbort) {
						process.config.logger.info("Received: " + message.toString());
						Message msg = new Message(process.processId, MessageType.NO, " ");
						Process.waitTillDelay();
						process.config.logger.info("Sending No.");
						process.controller.sendMsg(process.coordinatorNumber, msg.toString());
					}
					break;
				} else {
					process.dtLog.write(TransactionState.UNCERTAIN, command);
					state = TransactionState.UNCERTAIN;
					process.config.logger.info("Received: "	+ message.toString());
					Message msg = new Message(process.processId, MessageType.YES, " ");
					Process.waitTillDelay();
					process.config.logger.info("Sending Yes.");
					process.controller.sendMsg(process.coordinatorNumber, msg.toString());
					process.config.logger.info("Waiting to receive either PRE_COMMIT or ABORT.");

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
								process.config.logger.warning("Did not receive either PRE_COMMIT or ABORT from coordinator. It must be dead.");
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
					process.config.logger.info("Transaction aborted. Co-ordinator sent an abort.");
					break;
				} else if (message.type == MessageType.PRE_COMMIT) {
					stateRequestResponseReceived = true;
					process.config.logger.info("Received: " + message.toString());
					process.config.logger.info("Updated state to COMMITABLE.");
					state = TransactionState.COMMITABLE;

					Message msg = new Message(process.processId, MessageType.ACK, " ");
					Process.waitTillDelay();
					process.config.logger.info("Sending Acknowledgment.");
					process.controller.sendMsg(process.coordinatorNumber, msg.toString());

					Thread th = new Thread() {
						public void run() {
							try {
								Thread.sleep(DECISION_TIMEOUT);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							lock.lock();
							if (state == TransactionState.COMMITABLE) {
								process.config.logger.warning("Did not receive COMMIT from the coordinator. It must be dead.");
								electCordinator();
							}
							lock.unlock();
						}
					};
					th.start();
				} else {
					process.config.logger.warning("Was expecting either an ABORT or PRE_COMMIT." + "However, received a: " + message.type);
					break;
				}
			} else if (state == TransactionState.COMMITABLE) {
				process.config.logger.info("Received: " + message.toString());
				if (message.type == MessageType.COMMIT) {
					stateRequestResponseReceived = true;
					process.dtLog.write(TransactionState.COMMIT, command);
					state = TransactionState.COMMIT;
					process.config.logger.info("Transaction Committed.");
					process.notifyTransactionComplete();
					break; // STOP THE LOOP
				} else {
					process.config.logger .warning("Was expecting only a COMMIT message." + "However, received a: " + message.type);
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
		Integer[] keys = (Integer[]) process.upProcess.keySet().toArray(new Integer[0]);
		Arrays.sort(keys);

		if (keys[0] > process.processId) {
			keys[0] = process.processId;
		}

		int temp = process.coordinatorNumber;
		while ((keys[0] - temp) > 1) {
			temp = temp + 1;
			process.config.logger.info("As per round robin, Selecting: " + temp);
			process.config.logger.info("Discarding: " + temp + ", as it is down.");
		}

		process.config.logger.info("Elected new coordinator: " + keys[0]);

		// Sending UR_SELECTED message to the new coordinator.
		Message msg = new Message(process.processId, MessageType.UR_SELECTED, command);
		Process.waitTillDelay();
		process.config.logger.info("Sending: " + msg + " to: " + keys[0]);
		process.controller.sendMsg(keys[0], msg.toString());

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
						process.config.logger.info("Reelect coordinator.");
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

		dieIfNMessagesReceived();

		this.message = message;
		if (message.type == MessageType.STATE_REQ) {
			stateRequestReceived = true;
			process.coordinatorNumber = message.process_id;
			process.config.logger.info("Received: " + message.toString());
			Process.waitTillDelay();
			if (state == TransactionState.STARTING) {
				state = TransactionState.ABORT;
			}
			
			Message response = new Message(process.processId, MessageType.STATE_VALUE, state.toString());
			process.config.logger.info("Sending: " + response.toString());
			stateRequestResponseReceived = false;
			process.controller.sendMsg(message.process_id, response.toString());

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
							process.config.logger.info("Going to reelect the cordinator because no response is received after sending TransactionState.");
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

	private void dieIfNMessagesReceived() {
		if(process.dieAfter.size() < 2) {
			return ;
		}
		
		int pidToWaitFor = process.dieAfter.get(0);
		int numMsg = process.dieAfter.get(1);
		
		if (pidToWaitFor == message.process_id) {
			numMsg = numMsg - 1;
			if (numMsg == 0) {
				process.config.logger.info("Received n messages from: " + message.process_id);
				process.config.logger.warning("Killing  myself");
				System.exit(1);
			}
			process.dieAfter.set(1, numMsg);
		}
	}
}