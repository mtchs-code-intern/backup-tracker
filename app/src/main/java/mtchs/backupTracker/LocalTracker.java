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
    
    private static final Path JSON_FILE = getJsonPath();
    
    /**
     * Gets the path for the tracked_files.json file.
     * Stores in user's home directory under .backup-tracker/ for consistency across JAR runs.
     */
    private static Path getJsonPath() {
        String homeDir = System.getProperty("user.home");
        Path appDir = Paths.get(homeDir, ".backup-tracker");
        try {
            Files.createDirectories(appDir);
        } catch (Exception e) {
            System.err.println("Could not create application directory: " + appDir);
            e.printStackTrace();
        }
        return appDir.resolve("tracked_files.json");
    }
    
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
            
            JSONArray trackedFiles;
            
            if (Files.exists(JSON_FILE)) {
                String content = Files.readString(JSON_FILE).trim();
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
            
            Files.writeString(JSON_FILE, trackedFiles.toString(4));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
