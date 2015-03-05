package util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;

import playlist.Playlist;
import threepc.Process;

public class PlaylistLog {

	public static void updateStateFile(Process process) {
		final File Log_folder = new File(System.getProperty("LOG_FOLDER"));
		
		if(!Log_folder.exists()){
			if(!Log_folder.mkdirs()){
			   System.out.println("Failed to create a Log directory.");
			}
		 }
		
		if(Log_folder.canWrite()) {
			final File myFile = new File(Log_folder + "/" + process.processId + ".Songs");
			
			try {
				FileOutputStream fos = new FileOutputStream(myFile);
				ObjectOutputStream oos = new ObjectOutputStream(fos);

				oos.writeObject(process.playlist.clone());
				oos.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public static Playlist readStateFile(Process process) {
		final File Log_folder = new File(System.getProperty("LOG_FOLDER"));

		Playlist temp = null;		
		try {
			final File myFile = new File(Log_folder + "/" + process.processId + ".Songs");
			
			FileInputStream fos = new FileInputStream(myFile);
			ObjectInputStream oos = new ObjectInputStream(fos);

			HashMap<String, String> tempMap = (HashMap<String, String>)  oos.readObject();
			temp = new Playlist(tempMap);
			
			oos.close();
		} catch (Exception e) {}
		
		if (temp == null) {
			return new Playlist();
		} else {
			return temp;
		}
	}
}