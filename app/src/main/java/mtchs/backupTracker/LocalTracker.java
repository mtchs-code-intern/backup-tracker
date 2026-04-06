package mtchs.backupTracker;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import mtchs.backupTracker.backupEngine.FileHasher;

/**
 * LocalTracker is responsible for tracking files locally by storing their name, location, and hash in a JSON file.
 * 
 * @author Carsen Gafford
 * @version 1.0
 * @since 04-06-2026
 */
public class LocalTracker {
    
    private static final Path JSON_FILE = getJsonPath();
    
    /**
     * Gets the path to the JSON file used for tracking files. The file is stored in a hidden directory named ".backup-tracker" in the user's home directory. If the directory does not exist, it will be created. If there is an error creating the directory, an error message will be printed and the method will return a path to a non-existent file.
     * 
     * @return The path to the JSON file used for tracking files.
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

    /**
     * Tracks a folder by recursively tracking all files within the folder and its subfolders.
     * @param folder The folder to be tracked.
     */
    public void trackFolder(File folder) {
        if (folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        trackFile(file);
                    } else if (file.isDirectory()) {
                        trackFolder(file);
                    }
                }
            }
        } else {
            System.out.println("Provided file is not a directory: " + folder.getAbsolutePath());
        }
    }

    /**
     * Tracks a file by storing its name, location, and hash in a JSON file.
     * 
     * @param file The file to be tracked.
     */
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

    /**
     * Untracks a file by removing its entry from the JSON file.
     * 
     * @param file The file to be untracked.
     */
    public void untrackFile(File file) {
        try {
            if (!Files.exists(JSON_FILE)) {
                System.out.println("No files are currently being tracked.");
                return;
            }

            String content = Files.readString(JSON_FILE).trim();
            if (content.isEmpty()) {
                System.out.println("No files are currently being tracked.");
                return;
            }

            JSONArray trackedFiles = new JSONArray(content);
            JSONArray updatedTrackedFiles = new JSONArray();

            for (int i = 0; i < trackedFiles.length(); i++) {
                JSONObject trackedFile = trackedFiles.getJSONObject(i);
                if (!trackedFile.getString("location").equals(file.getAbsolutePath())) {
                    updatedTrackedFiles.put(trackedFile);
                }
            }

            Files.writeString(JSON_FILE, updatedTrackedFiles.toString(4));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updateFile(File file) {
        untrackFile(file);
        trackFile(file);
    }
}
