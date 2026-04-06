package mtchs.backupTracker.backupEngine;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * FileHasher is responsible for generating a hash of a file's contents. It provides a method to compute the SHA-256 hash of a specified file.
 * 
 * @author Carsen Gafford
 * @version 1.0
 * @since 04-06-2026
 */
public class FileHasher {
    
    public FileHasher() {
        
    }

    /**
     * Generates a SHA-256 hash of the specified file's contents. The method reads the file in chunks to efficiently compute the hash, even for large files. If the file does not exist or an error occurs during reading, the method will return null.
     * @param filePath The path to the file for which to generate a hash.
     * @return The SHA-256 hash of the file's contents, or null if an error occurs.
     */
    public String hashFile(String filePath) {
        filePath = filePath.trim();
        if (filePath.isEmpty()) {
            System.out.println("File path cannot be empty.");
            return null;
        }
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        try (InputStream fis = Files.newInputStream(Paths.get(filePath))) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        StringBuilder hashString = new StringBuilder();
        for (byte b : digest.digest()) {
            hashString.append(String.format("%02x", b));
        }
        return hashString.toString();
    }

    public String hashFile(File file) {
        return hashFile(file.getAbsolutePath());
    }
}
