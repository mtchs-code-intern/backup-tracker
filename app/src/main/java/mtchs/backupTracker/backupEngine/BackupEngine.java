package mtchs.backupTracker.backupEngine;

import java.io.IOException;
import java.nio.file.*;

/**
 * BackupEngine is responsible for performing backup operations. It provides a method to copy files and directories from a source folder to a destination folder, creating a backup with a timestamp.
 * 
 * @author Carsen Gafford
 */
public class BackupEngine {

    public BackupEngine() {
        
    }

    /**
     * Performs a backup of the specified source to the destination folder. If source is a file, copies it to destination with the same name. If source is a directory, creates a timestamped backup folder.
     * @param sourcePath The path to the file or folder to be backed up.
     * @param destinationFolder The path to the folder where the backup will be stored.
     * @return The path to the created backup, or null if the backup failed.
     */
    public String backup(String sourcePath, String destinationFolder) {

        if (sourcePath == null || destinationFolder == null) {
            System.out.println("Source and destination paths cannot be null.");
            return null;
        }

        if (sourcePath.trim().isEmpty() || destinationFolder.trim().isEmpty()) {
            System.out.println("Source and destination paths cannot be empty.");
            return null;
        }

        if (sourcePath.equals(destinationFolder)) {
            System.out.println("Source and destination paths cannot be the same.");
            return null;
        }

        Path destinationRoot = Paths.get(destinationFolder);

        if (!Files.exists(destinationRoot) || !Files.isDirectory(destinationRoot)) {
            System.out.println("Destination folder does not exist.");
            return null;
        }

        Path source = Paths.get(sourcePath);

        if (!Files.exists(source)) {
            System.out.println("Source does not exist.");
            return null;
        }

        Path backupPath;
        if (Files.isRegularFile(source)) {
            backupPath = destinationRoot.resolve(source.getFileName());
            try {
                Files.copy(source, backupPath, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("File backup completed successfully.");
                return backupPath.toString();
            } catch (IOException e) {
                System.err.println("Failed to backup file: " + e.getMessage());
                return null;
            }
        } else {
            // Directory
            backupPath = destinationRoot.resolve(source.getFileName() + "_backup_" + System.currentTimeMillis());

            try {
                // Count total files
                long totalFiles = Files.walk(source).filter(Files::isRegularFile).count();
                System.out.println("Backing up " + totalFiles + " files...");

                Files.walk(source).forEach(path -> {
                    try {
                        Path target = backupPath.resolve(source.relativize(path));

                        if (Files.isDirectory(path)) {
                            Files.createDirectories(target);
                        } else {
                            Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                        }

                    } catch (IOException e) {
                        if (path.toString().toLowerCase().endsWith(".lock")) {
                            System.out.println("Skipping locked file: " + path);
                        } else {
                            System.err.println("Failed to copy: " + path + " -> " + e.getMessage());
                        }
                    }
                });

                System.out.println("Backup completed successfully.");
                return backupPath.toString();

            } catch (IOException e) {
                System.err.println("Error walking file tree: " + e.getMessage());
            }
            return null;
        }
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
            System.out.println("File " + sourceFile + " replaced " + destinationFile);
        } catch (IOException e) {
            System.err.println("Failed to replace file: " + e.getMessage());
        }
    }

    /**
     * Updates the backup for a tracked item.
     * @param type The type of the item ("file" or "directory").
     * @param sourcePath The source path.
     * @param backupPath The backup path.
     * @param hasher The FileHasher instance.
     */
    public void updateBackup(String type, String sourcePath, String backupPath, FileHasher hasher) {
        if ("file".equals(type)) {
            Path source = Paths.get(sourcePath);
            Path backup = Paths.get(backupPath);
            if (!Files.exists(source)) {
                try {
                    Files.deleteIfExists(backup);
                    System.out.println("Deleted backup file: " + backupPath);
                } catch (IOException e) {
                    System.err.println("Failed to delete backup file: " + e.getMessage());
                }
            } else {
                String sourceHash = hasher.hashFile(sourcePath);
                if (sourceHash == null) {
                    System.err.println("Skipping update; source file cannot be read: " + sourcePath);
                    return;
                }
                String backupHash = hasher.hashFile(backupPath);
                if (backupHash == null || !sourceHash.equals(backupHash)) {
                    replaceFile(sourcePath, backupPath);
                }
            }
        } else if ("directory".equals(type)) {
            Path sourceDir = Paths.get(sourcePath);
            Path backupDir = Paths.get(backupPath);
            try {
                // Count total source files for progress
                long totalSourceFiles = Files.walk(sourceDir).filter(Files::isRegularFile).count();
                System.out.println("Updating " + totalSourceFiles + " source files...");

                // Update/add files
                Files.walk(sourceDir).filter(Files::isRegularFile).forEach(sourceFile -> {
                    try {
                        Path relative = sourceDir.relativize(sourceFile);
                        Path backupFile = backupDir.resolve(relative);
                        String sourceHash = hasher.hashFile(sourceFile.toString());
                        if (sourceHash == null) {
                            System.err.println("Skipping locked or unreadable source file: " + sourceFile);
                            return;
                        }
                        String backupHash = hasher.hashFile(backupFile.toString());
                        if (!Files.exists(backupFile) || (backupHash != null && !sourceHash.equals(backupHash))) {
                            Files.createDirectories(backupFile.getParent());
                            Files.copy(sourceFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
                            System.out.println("Updated/Copied: " + relative);
                        } else if (backupHash == null && Files.exists(backupFile)) {
                            System.out.println("Skipping update for locked or unreadable backup file: " + backupFile);
                        }
                    } catch (IOException e) {
                        if (sourceFile.toString().toLowerCase().endsWith(".lock")) {
                            System.out.println("Skipping locked file: " + sourceFile);
                        } else {
                            System.err.println("Failed to update: " + sourceFile + " -> " + e.getMessage());
                        }
                    }
                });

                System.out.println("Checking for deleted files...");
                // Count total backup files for progress
                long totalBackupFiles = Files.walk(backupDir).filter(Files::isRegularFile).count();

                // Remove deleted files
                Files.walk(backupDir).filter(Files::isRegularFile).forEach(backupFile -> {
                    try {
                        Path relative = backupDir.relativize(backupFile);
                        Path sourceFile = sourceDir.resolve(relative);
                        if (!Files.exists(sourceFile)) {
                            Files.delete(backupFile);
                            System.out.println("Deleted: " + relative);
                        }
                    } catch (IOException e) {
                        System.err.println("Failed to delete: " + backupFile + " -> " + e.getMessage());
                    }
                });
                System.out.println();
            } catch (IOException e) {
                System.err.println("Error updating directory: " + e.getMessage());
            }
        }
    }
}