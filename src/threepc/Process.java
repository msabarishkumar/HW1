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
import util.Queue;
import framework.Config;
import framework.NetController;

public class Process {
	// Interval after which a process would send isAlive messages. 
	final int HEART_BEAT_PERIOD = 1000;
	
	// Instance that would read the configuration file.
	Config config;

	// Path to the config file.
	String configPath;
	
	// Event queue for storing all the messages from the wire.
	final ConcurrentLinkedQueue<String> queue;

	// Current process Id.
	int processId;

	// Manage your connections.
	NetController controller;

	// Maintains the playlist.
	Playlist playlist;

	// Map of the UP Processes. ProcessId to time last updated.
	Map<Integer, Long> upProcess;

	// Identifier of the coordinator. 
	int coordinatorNumber;

	// delay to be introduced during message processing.
	int delay;
	
	// Die after information.
	List<Integer> dieAfter = new ArrayList<Integer>();
	
	// Send Partial commit messages to only a few process.
	List<Integer> partialCommit = new ArrayList<Integer>();
	
	Process(int processId, String configPath) {
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
		this.controller = new NetController(this.processId, this.config, this.queue);
	}
	
	public static void main(String[] args) {
		Process proc = new Process(0, "/home/sabar/Desktop/DC1/src/config0");
		
		proc.pumpHeartBeat();
		
		proc.readStateFromLog();
		
		proc.processMsgQueue();
		
		proc.syncUpProcess();
	}
	
	public void readStateFromLog() {
		
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
		
	}
	
	public void syncUpProcess() {
		Thread th = new Thread() {
			public void run() {
				while (true) {
					try {
						Thread.sleep(HEART_BEAT_PERIOD);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

					for (Iterator<Map.Entry<Integer, Long>> i = upProcess.entrySet().iterator(); i.hasNext();) {
						Map.Entry<Integer, Long> entry = i.next();

						if (System.currentTimeMillis() - entry.getValue() > 2 * HEART_BEAT_PERIOD) {
							i.remove();
						}
					}
				}
			}
		};

		th.start();
	}
}
