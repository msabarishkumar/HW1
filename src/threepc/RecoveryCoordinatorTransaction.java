package threepc;

import java.util.HashSet;

public class RecoveryCoordinatorTransaction extends Transaction {

	boolean isRecoveryComplete = false;
	TransactionState recoveryState;

	boolean abortFlag = false;
	boolean commitFlag = false;
	HashSet<Integer> committableSet = new HashSet<Integer>();
	HashSet<Integer> uncertainSet = new HashSet<Integer>();

	HashSet<Integer> ackSet = new HashSet<Integer>();

	public RecoveryCoordinatorTransaction(Process process, Message message,
			TransactionState state) {
		super(process, message);
		this.state = state;
		this.recoveryState = TransactionState.STARTING;

		this.BUFFER_TIMEOUT = 2000;
		this.DECISION_TIMEOUT = Process.delay + this.BUFFER_TIMEOUT;
	}

	public TransactionState getState() {
		if (state != TransactionState.COMMIT && state != TransactionState.ABORT) {
			return TransactionState.UNCERTAIN;
		} else {
			return state;
		}
	}

	public void run() {
		lock.lock();

		while (!isRecoveryComplete) {
			if (recoveryState == TransactionState.STARTING) {
				recoveryState = TransactionState.WAIT_DECISION;

				Message msg = new Message(process.processId, MessageType.STATE_REQ, command);
				Process.waitTillDelay();
				Process.config.logger.info("Sending: " + msg);
				process.controller.sendMsgs(process.upProcess.keySet(), msg.toString(), -1);

				Thread th = new Thread() {
					public void run() {
						try {
							Thread.sleep(DECISION_TIMEOUT);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						lock.lock();
						recoveryState = TransactionState.DECISION_RECEIVED;
						if (state == TransactionState.COMMIT) {
							commitFlag = true;
						}
						if (state == TransactionState.ABORT) {
							abortFlag = true;
						}
						if (state == TransactionState.COMMITABLE) {
							committableSet.add(process.processId);
						}
						nextMessageArrived.signal();
						lock.unlock();
					}
				};
				th.start();
			}
			else if (recoveryState == TransactionState.WAIT_DECISION) {
				if (message.type == MessageType.STATE_VALUE) {
					Process.config.logger.info("Recieved: " + message.toString());
					if (message.payLoad.equals(TransactionState.COMMIT.toString())) {
						commitFlag = true;
					} else if (message.payLoad.equals(TransactionState.ABORT.toString())) {
						abortFlag = true;
					} else if (message.payLoad.equals(TransactionState.COMMITABLE.toString())) {
						committableSet.add(message.process_id);
					} else if (message.payLoad.equals(TransactionState.UNCERTAIN.toString())) {
						uncertainSet.add(message.process_id);
					} else {
						Process.config.logger.warning("Unexpected State = " + message.payLoad + " received from: " + message.process_id);
						break;
					}
				} else {
					Process.config.logger.warning("Was expecting a STATE value and got: " + message.toString());
					break;
				}
			} else if (recoveryState == TransactionState.DECISION_RECEIVED) {
				if (commitFlag && abortFlag) {
					Process.config.logger.warning("ALERT !! SOMETHING IS WRONG. 3-PC has failed.");
					break;
				}
				if (commitFlag) {
					Process.config.logger.info("Some process has already committed. Let us all commit.");
					if (state != TransactionState.COMMIT) {
						process.dtLog.write(TransactionState.COMMIT, command);
						state = TransactionState.COMMIT;
						process.notifyTransactionComplete();
					}
					Process.waitTillDelay();
					isRecoveryComplete = true;
					Message msg = new Message(process.processId,MessageType.COMMIT, command);
					Process.config.logger.info("Sending COMMIT to all the active processes.");

					int partial_count = -1;
					if (!System.getProperty("PartialCommit").equals("-1")) {
						partial_count = Integer.parseInt(System.getProperty("PartialCommit"));
					}
					process.controller.sendMsgs(process.upProcess.keySet(), msg.toString(), partial_count);
					break;
				} else if (abortFlag) {
					Process.config.logger.info("Some process has already aborted. Let us all abort.");
					if (state != TransactionState.ABORT) {
						process.dtLog.write(TransactionState.ABORT, command);
						state = TransactionState.ABORT;
						process.notifyTransactionComplete();
					}
					Process.waitTillDelay();
					isRecoveryComplete = true;
					Message msg = new Message(process.processId, MessageType.ABORT, command);
					Process.config.logger.info("Sending ABORT to all the active processes.");
					process.controller.sendMsgs(process.upProcess.keySet(), msg.toString(), -1);
					break;
				} else if (committableSet.size() == 0) {
					Process.config.logger.info("All the process are uncertain. Let us all abort.");
					Process.waitTillDelay();
					process.dtLog.write(TransactionState.ABORT, command);
					state = TransactionState.ABORT;
					isRecoveryComplete = true;
					Message msg = new Message(process.processId, MessageType.ABORT, command);
					Process.config.logger.info("Sending ABORT to all the active processes.");
					process.controller.sendMsgs(process.upProcess.keySet(), msg.toString(), -1);
					process.notifyTransactionComplete();
					break;
				} else {
					recoveryState = TransactionState.WAIT_ACK;
					Process.config.logger.info("Some process may be uncertain and some are committable.");
					Process.waitTillDelay();
					state = TransactionState.COMMITABLE;
					Message msg = new Message(process.processId,MessageType.PRE_COMMIT, command);
					Process.config.logger.info("Sending PRE_COMMIT to uncertain processes.");

					int partial_count = -1;
					if (!System.getProperty("PartialPreCommit").equals("-1")) {
						partial_count = Integer.parseInt(System.getProperty("PartialPreCommit"));
					}
					process.controller.sendMsgs(uncertainSet, msg.toString(), partial_count);

					Thread th = new Thread() {
						public void run() {
							try {
								Thread.sleep(DECISION_TIMEOUT);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							lock.lock();
							recoveryState = TransactionState.ACK_RECEIVED;
							// We don't have to check for acks here.
							nextMessageArrived.signal();
							lock.unlock();
						}
					};
					th.start();
				}
			} else if (recoveryState == TransactionState.WAIT_ACK) {
				if (message.type == MessageType.ACK) {
					Process.config.logger.info("Received: " + message.toString());
					ackSet.add(message.process_id);
				} else {
					Process.config.logger .warning("Was expecting a ACK and got: "	+ message.toString());
					break;
				}
			} else if (recoveryState == TransactionState.ACK_RECEIVED) {
				Process.config.logger.info("COMMIT");
				process.dtLog.write(TransactionState.COMMIT, command);
				state = TransactionState.COMMIT;
				isRecoveryComplete = true;
				Message msg = new Message(process.processId, MessageType.COMMIT, command);
				Process.waitTillDelay();
				Process.config.logger.info("Sending COMMIT to all the active processes.");
				process.notifyTransactionComplete();

				int partial_count = -1;
				if (!System.getProperty("PartialCommit").equals("-1")) {
					partial_count = Integer.parseInt(System.getProperty("PartialCommit"));
				}
				process.controller.sendMsgs(process.upProcess.keySet(), msg.toString(), partial_count);
				break;
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
}