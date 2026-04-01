package mtchs.backupTracker.backupEngine;

import java.io.IOException;
import java.nio.file.*;

/**
 * BackupEngine is responsible for performing backup operations. It provides a method to copy files and directories from a source folder to a destination folder, creating a backup with a timestamp.
 * 
 * @author Carsen Gafford
 * @version 1.0
 * @since 04-01-2026
 */
public class BackupEngine {

    public BackupEngine() {
        // Constructor
    }

    public String backup(String sourceFolder, String destinationFolder) {
        Path source = Paths.get(sourceFolder);
        final Path destination = Paths.get(destinationFolder).resolve(source.getFileName() + "_backup_" + System.currentTimeMillis());

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
}