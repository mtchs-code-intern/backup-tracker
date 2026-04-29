package mtchs.backupTracker;

import java.io.File;
import java.nio.file.Paths;

import org.json.JSONArray;
import org.json.JSONObject;

import mtchs.backupTracker.backupEngine.BackupEngine;
import mtchs.backupTracker.backupEngine.FileHasher;

/**
 * App is the main entry point for the Backup Tracker application.
 * 
 * @author Carsen Gafford
 * @version 1.2.0
 * @since 04-13-2026
 */
public class App {

    public static void main(String[] args) {

        if (args.length == 0) {
            System.out.println("No arguments provided. Use -help or -h for usage information.");
            return;
        }

        String command = args[0];

        switch (command) {

            case "-v":
            case "-version":
                handleVersion(args);
                break;

            case "-track":
            case "-t":
                handleTrack(args);
                break;

            case "-update":
            case "-u":
                handleUpdate(args);
                break;

            case "-backup":
            case "-b":
                handleBackup(args);
                break;

            case "-list":
            case "-l":
                handleList(args);
                break;

            case "-stoptrack":
            case "-st":
                handleStopTrack(args);
                break;

            case "-help":
            case "-h":
                printHelp();
                break;

            case "--login":
            case "-login":
                handleLogin(args);
                break;

            default:
                System.out.println("Invalid arguments. Use -help or -h for usage information.");
        }
    }

    private static void handleVersion(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: -v | -version");
            return;
        }
        System.out.println("Backup Tracker Version 1.2.0");
    }

    private static void handleTrack(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: -track | -t <source> <dest>");
            return;
        }

        BackupEngine backupEngine = new BackupEngine();
        LocalTracker tracker = new LocalTracker();
        File fileToTrack = new File(args[1]);

        if (fileToTrack.isDirectory()) {
            tracker.trackFolder(fileToTrack);
        } else if (fileToTrack.isFile()) {
            tracker.trackFile(fileToTrack);
        } else {
            System.out.println("Invalid source path.");
            return;
        }

        String backupPath = backupEngine.backup(args[1], args[2]);
        if (backupPath != null) {
            tracker.setBackupPath(args[1], backupPath);
            System.out.println("File/Folder tracked and backed up successfully");
        } else {
            System.out.println("Backup failed.");
        }
    }

    private static void handleUpdate(String[] args) {
        BackupEngine backupEngine = new BackupEngine();
        LocalTracker tracker = new LocalTracker();
        FileHasher hasher = new FileHasher();

        if (args.length == 1) {
            JSONArray trackedItems = tracker.getTrackedItems();

            System.out.println("Updating backups... [0%]");

            for (int i = 0; i < trackedItems.length(); i++) {
                JSONObject item = trackedItems.getJSONObject(i);

                String type = item.getString("type");
                String sourcePath = item.getString("sourcePath");

                if (item.isNull("backupPath")) {
                    System.out.println("No backup path for " + sourcePath + ", skipping.");
                    continue;
                }

                String backupPath = item.getString("backupPath");

                System.out.println("Updating item " + (i + 1) + "/" + trackedItems.length() + ": " + sourcePath);
                backupEngine.updateBackup(type, sourcePath, backupPath, hasher);

                int percent = (int) (((i + 1) * 100) / trackedItems.length());
                System.out.print("\rUpdating backups... [" + percent + "%]");
            }

            System.out.println("\rUpdating backups... [100%]");
            System.out.println("Update completed.");
            return;
        }

        if (args.length == 2) {
            String sourcePath = normalizePath(args[1]);

            JSONArray trackedItems = tracker.getTrackedItems();
            JSONObject itemToUpdate = null;

            for (int i = 0; i < trackedItems.length(); i++) {
                JSONObject item = trackedItems.getJSONObject(i);
                if (item.getString("sourcePath").equals(sourcePath)) {
                    itemToUpdate = item;
                    break;
                }
            }

            if (itemToUpdate == null) {
                System.out.println("No tracked item found with source path: " + sourcePath);
                return;
            }

            if (itemToUpdate.isNull("backupPath")) {
                System.out.println("No backup path for " + sourcePath + ", cannot update.");
                return;
            }

            String type = itemToUpdate.getString("type");
            String backupPath = itemToUpdate.getString("backupPath");

            System.out.println("Updating item: " + sourcePath);
            backupEngine.updateBackup(type, sourcePath, backupPath, hasher);
            System.out.println("Update completed for: " + sourcePath);
            return;
        }

        System.out.println("Usage: -update | -u [source]");
    }

    private static void handleBackup(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: -backup | -b <source> <dest>");
            return;
        }

        BackupEngine backupEngine = new BackupEngine();
        String backupPath = backupEngine.backup(args[1], args[2]);

        if (backupPath != null) {
            System.out.println("Backup completed successfully: " + backupPath);
        } else {
            System.out.println("Backup failed.");
        }
    }

    private static void handleLogin(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: --login");
            return;
        }

        try {
            GoogleDriveAuthManager authManager = new GoogleDriveAuthManager();
            authManager.performLogin();
        } catch (Exception e) {
            System.err.println("Failed to complete Google Drive login: " + e.getMessage());
        }
    }

    private static void handleList(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: -list | -l");
            return;
        }

        LocalTracker tracker = new LocalTracker();
        JSONArray trackedItems = tracker.getTrackedItems();

        if (trackedItems.length() == 0) {
            System.out.println("No items are currently being tracked.");
            return;
        }

        System.out.println("Currently tracked items:");
        for (int i = 0; i < trackedItems.length(); i++) {
            JSONObject item = trackedItems.getJSONObject(i);

            String type = item.getString("type");
            String sourcePath = item.getString("sourcePath");
            String backupPath = item.isNull("backupPath")
                    ? "No backup path set"
                    : item.getString("backupPath");

            System.out.println((i + 1) + ". [" + type + "] " + sourcePath + " -> " + backupPath);
        }
    }

    private static void handleStopTrack(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: -stoptrack | -st <source | -all>");
            return;
        }

        LocalTracker tracker = new LocalTracker();

        if (args[1].equals("-all") || args[1].equals("-a")) {
            JSONArray items = tracker.getTrackedItems();

            for (int i = 0; i < items.length(); i++) {
                String sourcePath = items.getJSONObject(i).getString("sourcePath");
                tracker.stopTracking(sourcePath);
            }

            System.out.println("Stopped tracking all items.");
            return;
        }

        tracker.stopTracking(args[1]);
        System.out.println("Stopped tracking: " + args[1]);
    }

    private static void printHelp() {
        System.out.println("Usage:");
        System.out.println("  -v, -version                Display the application version");
        System.out.println("  -track, -t <source> <dest>   Track and back up a file/folder");
        System.out.println("  -update, -u [source]         Update all or one tracked item");
        System.out.println("  -backup, -b <source> <dest>  Perform a backup");
        System.out.println("  -list, -l                    List tracked items");
        System.out.println("  -stoptrack, -st <source>     Stop tracking one item");
        System.out.println("  -stoptrack, -st -all         Stop tracking all items");
        System.out.println("  --login                      Authenticate with Google Drive");
        System.out.println("  -help, -h                    Show this help message");
    }

    private static String normalizePath(String path) {
        path = Paths.get(path).toAbsolutePath().normalize().toString();

        if (path.endsWith(File.separator)) {
            path = path.substring(0, path.length() - File.separator.length());
        }

        return path;
    }
}