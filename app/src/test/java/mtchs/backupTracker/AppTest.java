package mtchs.backupTracker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mtchs.backupTracker.backupEngine.BackupEngine;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * AppTest contains unit tests
 * 
 * @author Carsen Gafford
 */
class AppTest {

    @TempDir
    Path tempDir;

    @Test
    void BackupWithInvalidDestinationPath() throws IOException {
        BackupEngine backupEngine = new BackupEngine();

        Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
        Path invalidDest = tempDir.resolve("nonexistent").resolve("dest"); // not created

        String result = backupEngine.backup(
            sourceDir.toString(),
            invalidDest.toString()
        );

        assertNull(result, "Expected null for invalid destination path");
    }

    @Test
    void BackupWithNonExistentSourcePath() {
        BackupEngine backupEngine = new BackupEngine();

        Path nonExistentSource = tempDir.resolve("doesNotExist");
        Path destDir = tempDir.resolve("dest");

        String result = backupEngine.backup(
            nonExistentSource.toString(),
            destDir.toString()
        );

        assertNull(result, "Expected null for non-existent source path");
    }

    @Test
    void BackupWithValidPaths() throws IOException {
        BackupEngine backupEngine = new BackupEngine();

        Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
        Path destDir = Files.createDirectory(tempDir.resolve("dest"));

        // create a dummy file to back up
        Files.writeString(sourceDir.resolve("test.txt"), "test content");

        String result = backupEngine.backup(
            sourceDir.toString(),
            destDir.toString()
        );

        assertNotNull(result, "Expected a valid backup path for valid source and destination");

        Path backupPath = Path.of(result);
        assertTrue(Files.exists(backupPath), "Backup folder should exist");
    }

    @Test
    void BackupWithSourcePathThatIsAFile() throws IOException {
        BackupEngine backupEngine = new BackupEngine();

        Path sourceFile = Files.createFile(tempDir.resolve("sourceFile.txt"));
        Path destDir = Files.createDirectory(tempDir.resolve("dest"));

        String result = backupEngine.backup(
            sourceFile.toString(),
            destDir.toString()
        );

        assertNull(result, "Expected null when source path is a file, not a directory");
    }

    @Test
    void BackupWithDestinationPathThatIsAFile() throws IOException {
        BackupEngine backupEngine = new BackupEngine();

        Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
        Path destFile = Files.createFile(tempDir.resolve("destFile.txt"));

        String result = backupEngine.backup(
            sourceDir.toString(),
            destFile.toString()
        );

        assertNull(result, "Expected null when destination path is a file, not a directory");
    }

    @Test
    void BackupWithSourceAndDestinationBeingTheSame() throws IOException {
        BackupEngine backupEngine = new BackupEngine();

        Path sourceDir = Files.createDirectory(tempDir.resolve("source"));

        String result = backupEngine.backup(
            sourceDir.toString(),
            sourceDir.toString()
        );

        assertNull(result, "Expected null when source and destination paths are the same");
    }
}