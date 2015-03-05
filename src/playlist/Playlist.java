package playlist;

import java.util.Map;
import java.util.HashMap;

public class Playlist {
	private Map<String, String> songToURLMap = new HashMap<String, String>();

	public boolean containsKey(String key) {
		return songToURLMap.containsKey(key.trim());
	}
	
	public boolean addSong(String song, String url) {
		songToURLMap.put(song, url);
		
		return true;
	}

	public boolean removeSong(String song) {
		if (songToURLMap.containsKey(song)) {
			songToURLMap.remove(song);

			return true;
		}

		return false;
	}

	public void editSong(String song, String newSong, String url) {
		if(songToURLMap.containsKey(song)) {
			String oldUrl = songToURLMap.get(song);
			songToURLMap.remove(song);
			songToURLMap.put(newSong, oldUrl);
		} else {
			addSong(newSong, url);
		}
	}

	public Map<String, String> clone() {
		Playlist newPlaylist = new Playlist();
		for (String song : songToURLMap.keySet()) {
			newPlaylist.addSong(song, songToURLMap.get(song));
		}

		return newPlaylist.songToURLMap;
	}
	
	public String toString() {
		StringBuffer sb = new StringBuffer();
		for (String song : songToURLMap.keySet()) {
			sb.append(song + "\t" + songToURLMap.get(song) + "\n");
		}

		return new String(sb);
	}
}