package mtchs.backupTracker.backupEngine;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import mtchs.backupTracker.GoogleDriveAuthManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class BackupEngine {

    private final GoogleDriveAuthManager authManager = new GoogleDriveAuthManager();

    public BackupEngine() {
    }

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
            if (isGoogleSheetsShortcut(source)) {
                backupPath = destinationRoot.resolve(replaceGsheetExtension(source.getFileName().toString()));
                try {
                    backupGoogleSheetFile(source, backupPath);
                    System.out.println("Converted and backed up .gsheet successfully.");
                    return backupPath.toString();
                } catch (IOException e) {
                    System.err.println("Failed to backup .gsheet file: " + source + " -> " + e.getMessage());
                    return null;
                }
            } else if (isGoogleDocsShortcut(source)) {
                backupPath = destinationRoot.resolve(replaceGdocExtension(source.getFileName().toString()));
                try {
                    backupGoogleDocFile(source, backupPath);
                    System.out.println("Converted and backed up .gdoc successfully.");
                    return backupPath.toString();
                } catch (IOException e) {
                    System.err.println("Failed to backup .gdoc file: " + source + " -> " + e.getMessage());
                    return null;
                }
            } else if (isGoogleSlidesShortcut(source)) {
                backupPath = destinationRoot.resolve(replaceGslidesExtension(source.getFileName().toString()));
                try {
                    backupGoogleSlidesFile(source, backupPath);
                    System.out.println("Converted and backed up .gslides successfully.");
                    return backupPath.toString();
                } catch (IOException e) {
                    System.err.println("Failed to backup .gslides file: " + source + " -> " + e.getMessage());
                    return null;
                }
            }

            backupPath = destinationRoot.resolve(source.getFileName());
            try {
                Files.createDirectories(backupPath.getParent());
                Files.copy(source, backupPath, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("File backup completed successfully.");
                return backupPath.toString();
            } catch (IOException e) {
                if (source.toString().toLowerCase().endsWith(".gsheet")) {
                    try {
                        Path alt = destinationRoot.resolve(replaceGsheetExtension(source.getFileName().toString()));
                        backupGoogleSheetFile(source, alt);
                        return alt.toString();
                    } catch (IOException ex) {
                        System.err.println("Fallback .gsheet export failed: " + ex.getMessage());
                    }
                } else if (source.toString().toLowerCase().endsWith(".gdoc")) {
                    try {
                        Path alt = destinationRoot.resolve(replaceGdocExtension(source.getFileName().toString()));
                        backupGoogleDocFile(source, alt);
                        return alt.toString();
                    } catch (IOException ex) {
                        System.err.println("Fallback .gdoc export failed: " + ex.getMessage());
                    }
                } else if (source.toString().toLowerCase().endsWith(".gslides")) {
                    try {
                        Path alt = destinationRoot.resolve(replaceGslidesExtension(source.getFileName().toString()));
                        backupGoogleSlidesFile(source, alt);
                        return alt.toString();
                    } catch (IOException ex) {
                        System.err.println("Fallback .gslides export failed: " + ex.getMessage());
                    }
                }
                System.err.println("Failed to backup file: " + e.getMessage());
                return null;
            }
        } else {
            backupPath = destinationRoot.resolve(source.getFileName() + "_backup_" + System.currentTimeMillis());

            AtomicBoolean hadErrors = new AtomicBoolean(false);

            try {
                long totalFiles = Files.walk(source).filter(Files::isRegularFile).count();
                System.out.println("Backing up " + totalFiles + " files...");

                Files.walk(source).forEach(path -> {
                    try {
                        Path target = backupPath.resolve(source.relativize(path));

                        if (Files.isDirectory(path)) {
                            Files.createDirectories(target);
                        } else {
                            Files.createDirectories(target.getParent());

                            try {
                                if (isGoogleSheetsShortcut(path)) {
                                    target = target.resolveSibling(replaceGsheetExtension(target.getFileName().toString()));
                                    backupGoogleSheetFile(path, target);
                                } else if (isGoogleDocsShortcut(path)) {
                                    target = target.resolveSibling(replaceGdocExtension(target.getFileName().toString()));
                                    backupGoogleDocFile(path, target);
                                } else if (isGoogleSlidesShortcut(path)) {
                                    target = target.resolveSibling(replaceGslidesExtension(target.getFileName().toString()));
                                    backupGoogleSlidesFile(path, target);
                                } else {
                                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                                }
                            } catch (IOException copyError) {
                                if (path.toString().toLowerCase().endsWith(".gsheet")) {
                                    try {
                                        Path alt = target.resolveSibling(replaceGsheetExtension(target.getFileName().toString()));
                                        backupGoogleSheetFile(path, alt);
                                    } catch (IOException ex) {
                                        hadErrors.set(true);
                                        System.err.println("Failed to export .gsheet: " + path + " -> " + ex.getMessage());
                                    }
                                } else if (path.toString().toLowerCase().endsWith(".gdoc")) {
                                    try {
                                        Path alt = target.resolveSibling(replaceGdocExtension(target.getFileName().toString()));
                                        backupGoogleDocFile(path, alt);
                                    } catch (IOException ex) {
                                        hadErrors.set(true);
                                        System.err.println("Failed to export .gdoc: " + path + " -> " + ex.getMessage());
                                    }
                                } else if (path.toString().toLowerCase().endsWith(".gslides")) {
                                    try {
                                        Path alt = target.resolveSibling(replaceGslidesExtension(target.getFileName().toString()));
                                        backupGoogleSlidesFile(path, alt);
                                    } catch (IOException ex) {
                                        hadErrors.set(true);
                                        System.err.println("Failed to export .gslides: " + path + " -> " + ex.getMessage());
                                    }
                                } else {
                                    throw copyError;
                                }
                            }
                        }

                    } catch (IOException e) {
                        hadErrors.set(true);
                        if (path.toString().toLowerCase().endsWith(".lock")) {
                            System.out.println("Skipping locked file: " + path);
                        } else {
                            System.err.println("Failed to copy: " + path + " -> " + e.getMessage());
                        }
                    }
                });

                if (hadErrors.get()) {
                    System.out.println("Backup completed with errors.");
                } else {
                    System.out.println("Backup completed successfully.");
                }

                return backupPath.toString();

            } catch (IOException e) {
                System.err.println("Error walking file tree: " + e.getMessage());
            }
            return null;
        }
    }

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

    private boolean isGoogleSheetsShortcut(Path path) {
        return path != null
                && Files.isRegularFile(path)
                && path.getFileName().toString().toLowerCase().endsWith(".gsheet");
    }

    private boolean isGoogleDocsShortcut(Path path) {
        return path != null
                && Files.isRegularFile(path)
                && path.getFileName().toString().toLowerCase().endsWith(".gdoc");
    }

    private boolean isGoogleSlidesShortcut(Path path) {
        return path != null
                && Files.isRegularFile(path)
                && path.getFileName().toString().toLowerCase().endsWith(".gslides");
    }

    private String replaceGsheetExtension(String fileName) {
        if (fileName == null) {
            return null;
        }
        return fileName.replaceAll("(?i)\\.gsheet$", ".xlsx");
    }

    private String replaceGdocExtension(String fileName) {
        if (fileName == null) {
            return null;
        }
        return fileName.replaceAll("(?i)\\.gdoc$", ".docx");
    }

    private String replaceGslidesExtension(String fileName) {
        if (fileName == null) {
            return null;
        }
        return fileName.replaceAll("(?i)\\.gslides$", ".pptx");
    }

    private Path resolveSourcePathForBackup(Path sourceDir, Path backupFile, Path relative) {
        Path sourceFile = sourceDir.resolve(relative);
        if (Files.exists(sourceFile)) {
            return sourceFile;
        }

        if (backupFile.toString().toLowerCase().endsWith(".xlsx")) {
            Path alternateSource = sourceDir.resolve(relative.toString().replaceAll("(?i)\\.xlsx$", ".gsheet"));
            if (Files.exists(alternateSource)) {
                return alternateSource;
            }
        }

        if (backupFile.toString().toLowerCase().endsWith(".docx")) {
            Path alternateSource = sourceDir.resolve(relative.toString().replaceAll("(?i)\\.docx$", ".gdoc"));
            if (Files.exists(alternateSource)) {
                return alternateSource;
            }
        }

        if (backupFile.toString().toLowerCase().endsWith(".pptx")) {
            Path alternateSource = sourceDir.resolve(relative.toString().replaceAll("(?i)\\.pptx$", ".gslides"));
            if (Files.exists(alternateSource)) {
                return alternateSource;
            }
        }

        return sourceFile;
    }

    public static String extractGoogleSheetDocId(String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }

        try {
            JSONObject json = new JSONObject(content.trim());
            String docId = json.optString("doc_id", null);
            if (docId == null || docId.isEmpty()) {
                docId = json.optString("id", null);
            }

            if (docId == null || docId.isEmpty()) {
                String url = json.optString("url", null);
                if (url != null && !url.isBlank()) {
                    docId = extractGoogleSheetDocIdFromUrl(url);
                }
            }

            return (docId == null || docId.isEmpty()) ? null : docId;
        } catch (JSONException e) {
            return null;
        }
    }

    private static String extractGoogleSheetDocIdFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        String normalized = url.trim();
        int index = normalized.indexOf("/d/");
        if (index >= 0) {
            int start = index + 3;
            int end = normalized.indexOf('/', start);
            if (end < 0) {
                end = normalized.length();
            }
            String candidate = normalized.substring(start, end);
            if (!candidate.isBlank()) {
                return candidate;
            }
        }

        return null;
    }

    private String getGoogleAccessToken() throws IOException {
        String token = System.getProperty("google.drive.access.token");
        if (token != null && !token.isBlank()) {
            return token.trim();
        }
        token = System.getenv("GOOGLE_DRIVE_ACCESS_TOKEN");
        if (token != null && !token.isBlank()) {
            return token.trim();
        }

        return authManager.getAccessToken();
    }

    private String requireGoogleAccessToken() throws IOException {
        String accessToken = getGoogleAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            throw new IOException("Please authenticate with Google Drive.\nRun: backuptracker --login");
        }
        return accessToken;
    }

    private void backupGoogleSheetFile(Path source, Path target) throws IOException {
        String docId = null;
        try {
            String json = Files.readString(source);
            docId = extractGoogleSheetDocId(json);
        } catch (IOException e) {
            String fileName = source.getFileName().toString();
            if (fileName.toLowerCase().endsWith(".gsheet")) {
                fileName = fileName.substring(0, fileName.length() - 7); // remove .gsheet
            }
            docId = lookupDocIdByName(fileName);
        }
        if (docId == null) {
            throw new IOException("Unable to extract or lookup doc_id for .gsheet file.");
        }

        System.out.println("Exporting Google Sheets document id: " + docId);
        byte[] excelData = downloadGoogleSheetAsXlsx(docId);
        Files.createDirectories(target.getParent());
        Files.write(target, excelData);
        System.out.println("Converted .gsheet to .xlsx: " + target);
    }

    private void backupGoogleDocFile(Path source, Path target) throws IOException {
        String docId = null;
        try {
            String json = Files.readString(source);
            docId = extractGoogleSheetDocId(json);
        } catch (IOException e) {
            String fileName = source.getFileName().toString();
            if (fileName.toLowerCase().endsWith(".gdoc")) {
                fileName = fileName.substring(0, fileName.length() - 5);
            }
            docId = lookupDocIdByName(fileName);
        }
        if (docId == null) {
            throw new IOException("Unable to extract or lookup doc_id for .gdoc file.");
        }

        System.out.println("Exporting Google Docs document id: " + docId);
        byte[] docxData = downloadGoogleDocAsDocx(docId);
        Files.createDirectories(target.getParent());
        Files.write(target, docxData);
        System.out.println("Converted .gdoc to .docx: " + target);
    }

    private void backupGoogleSlidesFile(Path source, Path target) throws IOException {
        String docId = null;
        try {
            String json = Files.readString(source);
            docId = extractGoogleSheetDocId(json);
        } catch (IOException e) {
            String fileName = source.getFileName().toString();
            if (fileName.toLowerCase().endsWith(".gslides")) {
                fileName = fileName.substring(0, fileName.length() - 8);
            }
            docId = lookupDocIdByName(fileName);
        }
        if (docId == null) {
            throw new IOException("Unable to extract or lookup doc_id for .gslides file.");
        }

        System.out.println("Exporting Google Slides document id: " + docId);
        byte[] pptxData = downloadGoogleSlidesAsPptx(docId);
        Files.createDirectories(target.getParent());
        Files.write(target, pptxData);
        System.out.println("Converted .gslides to .pptx: " + target);
    }

    private byte[] downloadGoogleSheetAsXlsx(String docId) throws IOException {
        String accessToken = requireGoogleAccessToken();

        String url = "https://www.googleapis.com/drive/v3/files/"
            + URLEncoder.encode(docId, StandardCharsets.UTF_8)
            + "/export?mimeType="
            + URLEncoder.encode("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();

        try {
            HttpResponse<byte[]> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                throw new IOException("Google export failed: " + response.statusCode());
            }
            return response.body();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted.", e);
        }
    }

    private byte[] downloadGoogleDocAsDocx(String docId) throws IOException {
        String accessToken = requireGoogleAccessToken();

        String url = "https://www.googleapis.com/drive/v3/files/"
            + URLEncoder.encode(docId, StandardCharsets.UTF_8)
            + "/export?mimeType="
            + URLEncoder.encode("application/vnd.openxmlformats-officedocument.wordprocessingml.document", StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();

        try {
            HttpResponse<byte[]> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                throw new IOException("Google export failed: " + response.statusCode());
            }
            return response.body();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted.", e);
        }
    }

    private byte[] downloadGoogleSlidesAsPptx(String docId) throws IOException {
        String accessToken = requireGoogleAccessToken();

        String url = "https://www.googleapis.com/drive/v3/files/"
            + URLEncoder.encode(docId, StandardCharsets.UTF_8)
            + "/export?mimeType="
            + URLEncoder.encode("application/vnd.openxmlformats-officedocument.presentationml.presentation", StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();

        try {
            HttpResponse<byte[]> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                throw new IOException("Google export failed: " + response.statusCode());
            }
            return response.body();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted.", e);
        }
    }

    private String lookupDocIdByName(String fileName) throws IOException {
        String accessToken = requireGoogleAccessToken();

        String query = "name contains '" + fileName.replace("'", "\\'") + "' and trashed = false";

        String url = "https://www.googleapis.com/drive/v3/files"
            + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
            + "&fields=" + URLEncoder.encode("files(id,name,mimeType,createdTime,modifiedTime)", StandardCharsets.UTF_8)
            + "&spaces=drive";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();

        try {
            HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("Drive API search failed: " + response.statusCode() + " " + response.body());
            }

            JSONObject json = new JSONObject(response.body());
            JSONArray files = json.optJSONArray("files");

            if (files == null || files.length() == 0) {
                return null;
            }

            // Prefer exact match first
            for (int i = 0; i < files.length(); i++) {
                JSONObject file = files.getJSONObject(i);
                if (fileName.equals(file.optString("name"))) {
                    return file.optString("id", null);
                }
            }

            // fallback: first result
            return files.getJSONObject(0).optString("id", null);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted.", e);
        }
    }

        public void updateBackup(String type, String sourcePath, String backupPath, FileHasher hasher, boolean noDelete) {
        if ("file".equals(type)) {
            Path source = Paths.get(sourcePath);
            Path backup = Paths.get(backupPath);
            if (!Files.exists(source)) {
                if (noDelete) {
                    System.out.println("Preserving deleted source backup file: " + backupPath);
                } else {
                    try {
                        Files.deleteIfExists(backup);
                        System.out.println("Deleted backup file: " + backupPath);
                    } catch (IOException e) {
                        System.err.println("Failed to delete backup file: " + e.getMessage());
                    }
                }
            } else if (isGoogleSheetsShortcut(source)) {
                try {
                    backupGoogleSheetFile(source, backup);
                } catch (IOException e) {
                    System.err.println("Failed to update .gsheet file: " + sourcePath + " -> " + e.getMessage());
                }
            } else if (isGoogleDocsShortcut(source)) {
                try {
                    backupGoogleDocFile(source, backup);
                } catch (IOException e) {
                    System.err.println("Failed to update .gdoc file: " + sourcePath + " -> " + e.getMessage());
                }
            } else if (isGoogleSlidesShortcut(source)) {
                try {
                    backupGoogleSlidesFile(source, backup);
                } catch (IOException e) {
                    System.err.println("Failed to update .gslides file: " + sourcePath + " -> " + e.getMessage());
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
                long totalSourceFiles = Files.walk(sourceDir).filter(Files::isRegularFile).count();
                System.out.println("Updating " + totalSourceFiles + " source files...");

                Files.walk(sourceDir).filter(Files::isRegularFile).forEach(sourceFile -> {
                    try {
                        Path relative = sourceDir.relativize(sourceFile);
                        Path backupFile = backupDir.resolve(relative);
                        if (isGoogleSheetsShortcut(sourceFile)) {
                            try {
                                Path sheetBackupFile = backupFile.resolveSibling(replaceGsheetExtension(backupFile.getFileName().toString()));
                                Files.createDirectories(sheetBackupFile.getParent());
                                backupGoogleSheetFile(sourceFile, sheetBackupFile);
                                System.out.println("Updated/Copied: " + relative);
                            } catch (IOException e) {
                                System.err.println("Failed to update .gsheet file: " + sourceFile + " -> " + e.getMessage());
                            }
                            return;
                        }

                        if (isGoogleDocsShortcut(sourceFile)) {
                            try {
                                Path docBackupFile = backupFile.resolveSibling(replaceGdocExtension(backupFile.getFileName().toString()));
                                Files.createDirectories(docBackupFile.getParent());
                                backupGoogleDocFile(sourceFile, docBackupFile);
                                System.out.println("Updated/Copied: " + relative);
                            } catch (IOException e) {
                                System.err.println("Failed to update .gdoc file: " + sourceFile + " -> " + e.getMessage());
                            }
                            return;
                        }

                        if (isGoogleSlidesShortcut(sourceFile)) {
                            try {
                                Path slidesBackupFile = backupFile.resolveSibling(replaceGslidesExtension(backupFile.getFileName().toString()));
                                Files.createDirectories(slidesBackupFile.getParent());
                                backupGoogleSlidesFile(sourceFile, slidesBackupFile);
                                System.out.println("Updated/Copied: " + relative);
                            } catch (IOException e) {
                                System.err.println("Failed to update .gslides file: " + sourceFile + " -> " + e.getMessage());
                            }
                            return;
                        }

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

                Files.walk(backupDir).filter(Files::isRegularFile).forEach(backupFile -> {
                    try {
                        Path relative = backupDir.relativize(backupFile);
                        Path sourceFile = resolveSourcePathForBackup(sourceDir, backupFile, relative);
                        if (!Files.exists(sourceFile)) {
                            if (noDelete) {
                                System.out.println("Preserving deleted source backup file: " + relative);
                            } else {
                                Files.delete(backupFile);
                                System.out.println("Deleted: " + relative);
                            }
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