package mtchs.backupTracker;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import mtchs.backupTracker.backupEngine.FileHasher;

public class LocalTracker {
    
    public LocalTracker() {
        
    }

    public void trackFile(File file) {
        try {
            String name = file.getName();
            String location = file.getAbsolutePath();
            FileHasher hasher = new FileHasher();
            String hash = hasher.hashFile(file);
            
            JSONObject fileObj = new JSONObject();
            fileObj.put("name", name);
            fileObj.put("location", location);
            fileObj.put("hash", hash);
            
            Path jsonPath = Paths.get("tracked_files.json");
            JSONArray trackedFiles;
            
            if (Files.exists(jsonPath)) {
                String content = Files.readString(jsonPath).trim();
                if (content.isEmpty()) {
                    trackedFiles = new JSONArray();
                } else {
                    try {
                        trackedFiles = new JSONArray(content);
                    } catch (JSONException e) {
                        trackedFiles = new JSONArray();
                    }
                }
            } else {
                trackedFiles = new JSONArray();
            }

            if (trackedFiles.length() == 0 || !(trackedFiles.get(0) instanceof JSONObject)) {
                trackedFiles = new JSONArray();
            }

            for (int i = 0; i < trackedFiles.length(); i++) {
                JSONObject trackedFile = trackedFiles.getJSONObject(i);
                if (trackedFile.getString("location").equals(location)) {
                    System.out.println("File is already being tracked.");
                    return;
                }
            }

            if (trackedFiles.length() > 0 && trackedFiles.get(trackedFiles.length() - 1) instanceof JSONObject) {
                trackedFiles.put(fileObj);
            } else {
                trackedFiles = new JSONArray().put(fileObj);
            }
            
            Files.writeString(jsonPath, trackedFiles.toString(4));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
