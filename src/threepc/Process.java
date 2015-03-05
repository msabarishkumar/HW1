package threepc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;

import playlist.Playlist;
import util.DTLog;
import util.PlaylistLog;
import util.Queue;
import framework.Config;
import framework.NetController;

public class Process {
	
	public static boolean enableDebug = true;
	
	// Interval after which a process would send isAlive messages. 
	final int HEART_BEAT_PERIOD = 500;
	
	// Instance that would read the configuration file.
	static Config config;

	// Path to the config file.
	String configPath;
	
	// Event queue for storing all the messages from the wire.
	final ConcurrentLinkedQueue<String> queue;

	// Current process Id.
	public int processId;

	// Manage your connections.
	NetController controller;

	// Maintains the playlist.
	public Playlist playlist = new Playlist();

	// Map of the UP Processes. ProcessId to time last updated.
	public Map<Integer, Long> upProcess;

	// Identifier of the coordinator. 
	int coordinatorNumber;

	// delay to be introduced during message processing.
	static int delay;
	
	// Die after information.
	List<Integer> dieAfter = new ArrayList<Integer>();
	
	// Send Partial commit messages to only a few process.
	List<Integer> partialCommit = new ArrayList<Integer>();
	
	// Logger.
	DTLog dtLog;
	
	// current transaction in execution.
	Transaction currentTransaction;
	
	// previous transaction executed.
	Transaction prevTransaction;
	TransactionState prevTransactionState;
		
	boolean hasCoordinatorBeenSelected = false;
	
	Process(int processId) {
		this.configPath = System.getProperty("CONFIG_PATH");
		this.processId = processId;
		this.upProcess = new HashMap<Integer, Long>();
		this.coordinatorNumber = 0;

		delay = Integer.parseInt(System.getProperty("DELAY"));

		try {
			Handler fh = new FileHandler(System.getProperty("LOG_FOLDER") + "/"	+ processId + ".log");
			fh.setLevel(Level.FINEST);

			config = new Config(configPath, fh);
		} catch(Exception e) {
			e.printStackTrace();
		}

		this.queue = new Queue<String>();
		this.controller = new NetController(this.processId, Process.config, this.queue);
	
		this.dtLog = new DTLog(this);
	
		this.playlist = PlaylistLog.readStateFile(this);
		
		String deathAfterString = System.getProperty("DeathAfter");
		Integer message_count = Integer.parseInt(deathAfterString.split("=")[0]);
		Integer process_number = Integer.parseInt(deathAfterString.split("=")[1]);
		dieAfter.add(process_number); dieAfter.add(message_count);
	}
	
	public static void main(String[] args) {
		Process proc = new Process(0);
		
		proc.pumpHeartBeat();
		
		proc.readStateFromLog();
		
		proc.processMsgQueue();
		
		proc.syncUpProcess();
	}
	
	// the return value indicates if the process is recovering or not.
	private boolean readStateFromLog() {
		TransactionState state = this.dtLog.getLoggedState(this.processId);
				
		if (state == null) {
			config.logger.info("Got nothing from the DT log file.");
			prevTransactionState = null;
		} else if (state == TransactionState.COMMIT || state == TransactionState.ABORT) {
			config.logger.info("Transaction completed properly last time.");
			prevTransactionState = state;
		} else if (state == TransactionState.STARTING) {
			String command = this.dtLog.getLoggedCommand(this.processId);
			config.logger.info("Aborting last transaction as I did not take part in it..");
			this.dtLog.write(TransactionState.ABORT, command);
			prevTransactionState = TransactionState.ABORT;
		} else {
			String command = this.dtLog.getLoggedCommand(this.processId);
			
			MessageType type;
			if (command.contains("=")) {
				type = MessageType.EDIT;
			} else {
				type  = MessageType.DELETE;
			}
			
			Message msg = new Message(-1, type, command);
			config.logger.info("DTlog: UNCERTAIN. Going to start recovery transaction.");
			this.coordinatorNumber = -1;
			
			currentTransaction = new RecoveryTransaction(this, msg);
			Thread thread = new Thread(currentTransaction);
			thread.start();
			
			return true;
		}
		
		return false;
	}
	
	public void pumpHeartBeat() {
		final Message heartBeatMsg = new Message(this.processId, MessageType.HEARTBEAT, "EMPTY");

		Thread th = new Thread() {
			public void run() {
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
				
				while (true) {
					controller.broadCastMsgs(heartBeatMsg.toString());
					try {
						Thread.sleep(HEART_BEAT_PERIOD);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		};

		th.start();
	}
	
	private void processMsgQueue() {
		Thread th = new Thread() {
			public void run() {
				while (true) {
					String msg = queue.poll();
					Message message = Message.parseMsg(msg);

					switch (message.type) {
					
					case HEARTBEAT: {
						updateProcessList(message);
						break;
					}
					
					case ADD:
					case DELETE:
					case EDIT: {
						if (coordinatorNumber == processId) {
							if (currentTransaction != null) {
								config.logger.warning("A transaction is already running. Ignoring this request.");
								break;
							}
							if (message.type == MessageType.EDIT && !playlist.containsKey(message.payLoad.split("=")[0])) {
    							System.out.println("No entry found to EDIT.");
    							config.logger.warning("No entry found to EDIT.");
    							break;
    						}
    						if (message.type == MessageType.DELETE && !playlist.containsKey(message.payLoad.trim())) {
    							System.out.println("No entry found to DELETE.");
    							config.logger.warning("No entry found to DELETE.");
    							break;
    						}    						
							if (upProcess.size() != config.numProcesses - 1) {
								config.logger.warning("Not starting a transaction as all the process are not up.");
								break;
							}
							
							startNewTransaction(message);
						} else {
							config.logger.warning("I am not the coordinator");
						}
						break;
					}
					
					case VOTE_REQ: {
						if (coordinatorNumber == processId) {
							config.logger.warning(message.type + " sent by: " + message.process_id);
							config.logger.warning("Coordiantor is not supposed to get " + message.type);
						} else {
							if (currentTransaction == null) {
								coordinatorNumber = message.process_id;
								startNewTransaction(message);
							} else {
								config.logger.warning(message.type + " sent by: " + message.process_id);
								config.logger.warning("shouldn't get a vote-req");
							}
						}
						break;
					}
					case PRE_COMMIT:
					case COMMIT:
					case ABORT:
					case STATE_REQ: {
						if (coordinatorNumber == processId) {
							config.logger.warning(message.type + " sent by: " + message.process_id);
							config.logger.warning("There is something wrong. Coordiantor is not supposed to get " + message.type);
							break;
						} else {
							if (currentTransaction != null) {
								currentTransaction.update(message);
							} else {
								config.logger.warning(message.type + " sent by: " + message.process_id);
								config.logger.warning("I should not get a " + message.type);
							}
							break;
						}
					} 

					case YES:
					case NO:
					case ACK:
					case STATE_VALUE: {
						if (coordinatorNumber != processId) {
							config.logger.warning(message.type + " sent by: " + message.process_id);
							config.logger.warning("Coordinator should get " + message.type);
							break;
						} else {
							if (currentTransaction != null) {
								currentTransaction.update(message);
							} else {
								config.logger.warning(message.type + " sent by: " + message.process_id);
								config.logger.warning("Txn. running: I should not get a " + message.type);
							}
						}
						break;
					}
					case UR_SELECTED: {
						if (coordinatorNumber == processId) {
							config.logger.info("Ignoring: " + message.toString());
							break;
						}
						config.logger.info("Received: " + message.toString());
						startCoordinatorRecoveryTransaction(message);
						break;
					}
					case STATE_ENQUIRY: {
						config.logger.info("Received: " + message.toString());
						MessageType type;
						if (currentTransaction == null) {
							if (prevTransactionState != null) {
								if (prevTransactionState == TransactionState.COMMIT) {
									type = MessageType.STATE_COMMIT;
								} else if (prevTransactionState == TransactionState.ABORT) {
									type = MessageType.STATE_ABORT;
								} else {break;
								}
								Message stateReply = new Message(processId, type, " ");
								config.logger.info("Sending: " + stateReply.toString() + " to: " + message.process_id);
								controller.sendMsg(message.process_id, stateReply.toString());
							}
							break;
						}
						String command = currentTransaction.command;
						if (currentTransaction.state == TransactionState.COMMIT) {
							type = MessageType.STATE_COMMIT;
						} else if (currentTransaction.state == TransactionState.ABORT) {
							type = MessageType.STATE_ABORT;
						} else if (currentTransaction.state == TransactionState.RECOVERING) {
							type = MessageType.STATE_RECOVERING;
							command = currentTransaction.getUpStates();
						} else {
							type = MessageType.STATE_UNDECIDED;
						}

						Message stateReply = new Message(processId, type, command);
						config.logger.info("Sending: " + stateReply.toString()	+ " to: " + message.process_id);
						controller.sendMsg(message.process_id, stateReply.toString());
						break;
					}
					case STATE_COMMIT:
					case STATE_ABORT:
					case STATE_RECOVERING:
					case STATE_UNDECIDED: {
						if (currentTransaction != null) {
							currentTransaction.update(message);
						} else {
							config.logger.warning(message.type + " sent by: " + message.process_id);
							config.logger.warning("Txn. running: Should not get a " + message.type);
						}
						break;
					}
					case PRINT_STATE: {
						if (currentTransaction == null) {
							System.out.println("No transaction is going on.");
						} else {
							System.out.println("STATE is " + currentTransaction.state);
						}

						System.out.println("Current Playlist contains: \n");
						Map<String, String>  songs = playlist.clone();
						if (!songs.isEmpty()) {
							for(String song : songs.keySet()) {
								System.out.println(song + " : " + songs.get(song));
							}
						} else {
							System.out.println("No Songs");
						}
						break;
					}
					case DIE: {
						// Also remove the coordinator from upProcess map and then die.
						config.logger.warning("Received: " + message.toString());
						upProcess.remove(message.process_id);
						dtLog.write(currentTransaction.getState(), currentTransaction.command);
						System.exit(1);
					}
					case GIVE_COORDINATOR: {
						config.logger.info("Received: " + message.toString());
						String to_return = "false";
						if (coordinatorNumber == processId) {
							to_return = "true";
						}
						Message co_msg = new Message(processId, MessageType.COORDINATOR_NO, to_return);
						config.logger.info("Sending: " + co_msg);
						controller.sendMsg(message.process_id, co_msg.toString());
						break;
					}
					case COORDINATOR_NO: {
						if (message.payLoad.trim().equals("true")) {
							hasCoordinatorBeenSelected = true;
						}
					}
					}
				}
			}
		};

		th.start();
	}

	protected void startCoordinatorRecoveryTransaction(Message message) {
		coordinatorNumber = processId;
		
		TransactionState state = prevTransactionState;
		if (currentTransaction != null) {
			state = currentTransaction.state;
			currentTransaction.enforceStop();
		}
		RecoveryCoordinatorTransaction newTransaction = new RecoveryCoordinatorTransaction(this, message, state);
		
		currentTransaction = newTransaction;
		
		config.logger.info("New coordinator");
		Thread thread = new Thread(currentTransaction);
		thread.start();
	}

	public void updateProcessList(Message message) {
		if (!upProcess.containsKey(message.process_id)) {
			if (currentTransaction != null && !(currentTransaction instanceof RecoveryTransaction)) {
				dtLog.write(currentTransaction.getState(), currentTransaction.command);
			}
		}
		upProcess.put(message.process_id, System.currentTimeMillis());
	}
	
	public void syncUpProcess() {
        Thread th = new Thread() {
        	public void run() {		
        		while(true) {
	        		try {
						Thread.sleep(HEART_BEAT_PERIOD);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
	        		for (Iterator<Map.Entry<Integer, Long>> i = upProcess.entrySet().iterator(); i.hasNext(); ) {
	        	        Map.Entry<Integer, Long> entry = i.next();
	        	        
	        	        if (System.currentTimeMillis() - entry.getValue() > (HEART_BEAT_PERIOD + 200)) {
	        	            i.remove();
	        	            if (currentTransaction != null && !(currentTransaction instanceof RecoveryTransaction)) {
	        					dtLog.write(currentTransaction.getState(), currentTransaction.command);
	        				}
	        	        }
	        	    }
        		}
        	}
        };
        
        th.start();
	}
	
	public void notifyTransactionComplete() {
		if (currentTransaction.state == TransactionState.COMMIT) {
			if (!currentTransaction.command.contains("=")) {
				playlist.removeSong(currentTransaction.command.trim());	
			} else {
				String[] str = currentTransaction.command.split("=");
				if(str.length == 3) {
					playlist.addSong(str[0], str[1]);
				} else {
					playlist.editSong(str[0], str[1], str[2]);
				}
			}
			
			PlaylistLog.updateStateFile(this);
		}
		
		if ((currentTransaction instanceof RecoveryTransaction) && processId == 0) {
			Message msg = new Message(processId, MessageType.GIVE_COORDINATOR, "-");
			controller.sendMsgs(upProcess.keySet(), msg.toString(), -1);
			
			Thread th = new Thread() {
				public void run() {
					try {
						Thread.sleep(2000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					
					if (!hasCoordinatorBeenSelected) {
						coordinatorNumber = 0;
					}
				}
			};
			
			th.start();
		}
		
		prevTransactionState = currentTransaction.state;
		config.logger.info("Transaction is complete. State: " + currentTransaction.state);
		System.out.println("Process: " + processId + "'s transaction is complete. State: " + currentTransaction.state);
		currentTransaction = null;
	}
	
	public void startNewTransaction(Message message) {
		if (coordinatorNumber == processId) {
			currentTransaction = new CoordinatorTransaction(this, message);
		} else {
			currentTransaction = new Transaction(this, message);
		}
		
		Thread thread = new Thread(currentTransaction);
		thread.start();
	}
	
	public static void waitTillDelay() {
		try {
			config.logger.info("Waiting for " + Process.delay / 1000 + " secs.");
			Thread.sleep(Process.delay);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
