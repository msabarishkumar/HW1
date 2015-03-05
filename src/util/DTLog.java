package util;

import java.io.*;
import java.util.*;
import java.sql.Timestamp;

import threepc.Process;
import threepc.TransactionState;

public class DTLog {
	public static final String Log_SEPARATOR = " %% ";
	public static final String UpSet_SEPARATOR = ", ";

	Process process;
	String folder;

	public DTLog(Process process) {
		this.process = process;
		this.folder = System.getProperty("LOG_FOLDER");
	}

	// Function To write log to the file
	public void write(TransactionState state, String command) {
		int process_id = process.processId;
		Map<Integer, Long> upProcess = process.upProcess;

		final File Log_folder = new File(folder);

		if (!Log_folder.exists()) {

			if (Log_folder.mkdirs() && Process.enableDebug) {
				System.out.println("first process to log and create directory");
			} else {
				System.out.println("Failed to create a Log directory");
			}
		}

		// Write log to the process file.
		if (Log_folder.canWrite()) {
			try {
				final File myFile = new File(Log_folder + "/" + process_id+ ".DTlog");

				if (!myFile.exists()) {
					myFile.createNewFile();
				}

				FileWriter log_writer = new FileWriter(myFile.getAbsoluteFile(), true);
				BufferedWriter log_buf = new BufferedWriter(log_writer);
				StringBuilder log_str = new StringBuilder();

				// Get the time stamp.
				java.util.Date date = new java.util.Date();
				Timestamp timeStamp = new Timestamp(date.getTime());
				log_str.append(timeStamp + Log_SEPARATOR);

				Set<Integer> set_of_upProcess = upProcess.keySet();
				log_str.append("[");
				for (Integer i : set_of_upProcess) {
					log_str.append(i + UpSet_SEPARATOR);
				}
				log_str.append("]");
				log_str.append(Log_SEPARATOR);

				log_str.append(state.toString() + Log_SEPARATOR);

				log_str.append(command + Log_SEPARATOR);

				log_buf.write(log_str + "\n");
				
				log_buf.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		} else {
			System.out.println("DTLog: Permission Denied.");
		}
	}

	private String readLastLogCommand(int process_id) {
		final File Log_folder = new File(folder);
		final File myFile = new File(Log_folder + "/" + process_id + ".DTlog");
		if (myFile.exists() && myFile.length() != 0) {
			if (myFile.canRead()) {
				try {
					FileReader log_reader = new FileReader(myFile);
					@SuppressWarnings("resource")
					BufferedReader log_buf = new BufferedReader(log_reader);
					String str = null, temp;
					while ((temp = log_buf.readLine()) != null) {
						str = temp;
					}
					
					return str;
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		}
		
		return null;
	}
	
	// Read DTLogger.
	public String getLoggedCommand(int process_id) {
		String lastCommand = readLastLogCommand(process_id);
		if(lastCommand != null) {
			String[] msg_splits = lastCommand.split(Log_SEPARATOR);
			return msg_splits[3]; 
		}
		
		return null;
	}


	public TransactionState getLoggedState(int process_id) {
		String lastCommand = readLastLogCommand(process_id);
		if (lastCommand != null) {
			String[] msg_splits = lastCommand.split(Log_SEPARATOR);
			String msg = msg_splits[2];

			if (msg != null) {
				if (msg.equals(TransactionState.STARTING.toString())) {
					return TransactionState.STARTING;
				} else if (msg.equals(TransactionState.ABORT.toString())) {
					return TransactionState.ABORT;
				} else if (msg.equals(TransactionState.COMMIT.toString())) {
					return TransactionState.COMMIT;
				} else if (msg.equals(TransactionState.UNCERTAIN.toString())) {
					return TransactionState.UNCERTAIN;
				}
			}
		}
		
		return null;
	}

	public Set<Integer> getLastUpProcessSet(int process_id) {
		String lastCommand = readLastLogCommand(process_id);
		if (lastCommand == null) {
			return new TreeSet<Integer>();
		}
		
		String[] msg_splits = readLastLogCommand(process_id).split(Log_SEPARATOR);
		String upProcess_str = msg_splits[1];

		Set<Integer> up_set = new TreeSet<Integer>();
		
		upProcess_str = upProcess_str.substring(1, upProcess_str.length() - 1);
		String[] upStr_split = upProcess_str.split(UpSet_SEPARATOR);
		for (String upStr : upStr_split) {
			try {
				up_set.add(Integer.parseInt(upStr));
			} catch (java.lang.NumberFormatException ex) {
				return new TreeSet<Integer>();
			}
		}

		return up_set;
	}
}