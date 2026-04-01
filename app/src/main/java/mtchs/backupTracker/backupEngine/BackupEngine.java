package mtchs.backupTracker.backupEngine;

import java.io.IOException;
import java.nio.file.*;

/**
 * BackupEngine is responsible for performing backup operations. It provides a method to copy files and directories from a source folder to a destination folder, creating a backup with a timestamp.
 * 
 * @author Carsen Gafford
 * @version 1.2
 * @since 04-01-2026
 */
public class BackupEngine {

    public BackupEngine() {
        // Constructor
    }

    /**
     * Performs a backup of the specified source folder to the destination folder. The backup will be created with a timestamp in the name.
     * @param sourceFolder The path to the folder to be backed up.
     * @param destinationFolder The path to the folder where the backup will be stored.
     * @return The path to the created backup folder, or null if the backup failed.
     */
    public String backup(String sourceFolder, String destinationFolder) {

        if (sourceFolder == null || destinationFolder == null) {
            System.out.println("Source and destination paths cannot be null.");
            return null;
        }

        if (Files.isRegularFile(Paths.get(sourceFolder))) {
            System.out.println("Source path is a file. Please provide a directory.");
            return null;
        }

        Path destinationRoot = Paths.get(destinationFolder);

        if (!Files.exists(destinationRoot) || !Files.isDirectory(destinationRoot)) {
            System.out.println("Destination folder does not exist.");
            return null;
        }

        Path source = Paths.get(sourceFolder);
        final Path destination = destinationRoot.resolve(source.getFileName() + "_backup_" + System.currentTimeMillis());

        if (!Files.exists(source)) {
            System.out.println("Source folder does not exist.");
            return null;
        }


        try {
            Files.walk(source).forEach(path -> {
                try {
                    Path target = destination.resolve(source.relativize(path));

                    if (Files.isDirectory(path)) {
                        Files.createDirectories(target);
                    } else {
                        Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                    }

                } catch (IOException e) {
                    System.err.println("Failed to copy: " + path + " -> " + e.getMessage());
                }
            });

            System.out.println("Backup completed successfully.");
            return destination.toString();

        } catch (IOException e) {
            System.err.println("Error walking file tree: " + e.getMessage());
        }
        return null;
    }

    /**
     * Replaces the destination file with the source file. Both files must exist for the operation to succeed.
     * @param sourceFile The path to the source file that will replace the destination file.
     * @param destinationFile The path to the destination file that will be replaced.
     */
    public void replaceFile(String sourceFile, String destinationFile) {
        Path source = Paths.get(sourceFile);
        Path destination = Paths.get(destinationFile);

        if (!Files.exists(source)) {
            System.out.println("Source file does not exist.");
            return;
        }

        if (!Files.exists(destination)) {
            System.out.println("Destination file does not exist.");
            return;
        }
        
        try {
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("File replaced successfully.");
        } catch (IOException e) {
            System.err.println("Failed to replace file: " + e.getMessage());
        }
    }
}