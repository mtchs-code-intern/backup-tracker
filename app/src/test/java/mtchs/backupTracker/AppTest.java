package mtchs.backupTracker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mtchs.backupTracker.backupEngine.BackupEngine;
import mtchs.backupTracker.backupEngine.FileHasher;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
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

    @Test
    void BackupWithEmptySourcePath() throws IOException {
        BackupEngine backupEngine = new BackupEngine();

        Path destDir = Files.createDirectory(tempDir.resolve("dest"));

        String result = backupEngine.backup(
            "",
            destDir.toString()
        );

        assertNull(result, "Expected null for empty source path");
    }

    @Test
    void FileHasherWithEmptyFilePath() {
        FileHasher hasher = new FileHasher();

        String result = hasher.hashFile("");

        assertNull(result, "Expected null for empty file path");
    }

    @Test
    void FileHasherWithNonExistentFile() {
        FileHasher hasher = new FileHasher();

        String result = hasher.hashFile("nonexistentfile.txt");

        assertNull(result, "Expected null for non-existent file path");
    }

    @Test
    void FileHasherWithValidFile() throws IOException {
        FileHasher hasher = new FileHasher();

        Path testFile = Files.createFile(tempDir.resolve("testFile.txt"));
        Files.writeString(testFile, "test content");

        String result = hasher.hashFile(testFile.toString());

        assertNotNull(result, "Expected a valid hash for an existing file");
        assertEquals(64, result.length(), "Expected SHA-256 hash length of 64 characters");
    }

    @Test
    void FileHasherWithFilePathThatIsADirectory() throws IOException {
        FileHasher hasher = new FileHasher();

        Path testDir = Files.createDirectory(tempDir.resolve("testDir"));

        String result = hasher.hashFile(testDir.toString());

        assertNull(result, "Expected null when file path is a directory, not a file");
    }

    @Test
    void FileHasherWithFilePathThatHasLeadingAndTrailingWhitespace() throws IOException {
        FileHasher hasher = new FileHasher();

        Path testFile = Files.createFile(tempDir.resolve("testFile.txt"));
        Files.writeString(testFile, "test content");

        String result = hasher.hashFile("  " + testFile.toString() + "  ");

        assertNotNull(result, "Expected a valid hash for an existing file path with leading/trailing whitespace");
        assertEquals(64, result.length(), "Expected SHA-256 hash length of 64 characters");
    }

    @Test
    void FileHasherWithFileThatIsEmpty() throws IOException {
        FileHasher hasher = new FileHasher();

        Path emptyFile = Files.createFile(tempDir.resolve("emptyFile.txt"));

        String result = hasher.hashFile(emptyFile.toString());

        assertNotNull(result, "Expected a valid hash for an empty file");
        assertEquals(64, result.length(), "Expected SHA-256 hash length of 64 characters");
    }

    @Test
    void FileHasherWithFileThatIsLarge() throws IOException {
        FileHasher hasher = new FileHasher();

        Path largeFile = Files.createFile(tempDir.resolve("largeFile.txt"));
        // Write 10 MB of data to the file
        byte[] largeData = new byte[10 * 1024 * 1024];
        Files.write(largeFile, largeData);

        String result = hasher.hashFile(largeFile.toString());

        assertNotNull(result, "Expected a valid hash for a large file");
        assertEquals(64, result.length(), "Expected SHA-256 hash length of 64 characters");
    }

    @Test
    void FileHasherWithFileThatHasSpecialCharactersInName() throws IOException {
        FileHasher hasher = new FileHasher();

        Path specialFile = Files.createFile(tempDir.resolve("spécial_fîle.txt"));
        Files.writeString(specialFile, "test content");

        String result = hasher.hashFile(specialFile.toString());

        assertNotNull(result, "Expected a valid hash for a file with special characters in the name");
        assertEquals(64, result.length(), "Expected SHA-256 hash length of 64 characters");
    }
}