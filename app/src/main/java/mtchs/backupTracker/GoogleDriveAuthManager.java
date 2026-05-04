package mtchs.backupTracker;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;

public class GoogleDriveAuthManager {

    private static final String CLIENT_ID_ENV = "GOOGLE_DRIVE_CLIENT_ID";
    private static final String CLIENT_SECRET_ENV = "GOOGLE_DRIVE_CLIENT_SECRET";
    private static final String ACCESS_TOKEN_PROPERTY = "google.drive.access.token";
    private static final String ACCESS_TOKEN_ENV = "GOOGLE_DRIVE_ACCESS_TOKEN";
    private static final String AUTH_FILE_NAME = "auth.json";
    private static final String AUTH_DIR_NAME = ".backup-tracker";
    private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String SCOPE = "https://www.googleapis.com/auth/drive.readonly";
    private static final long EXPIRES_BUFFER_MS = 60_000;

    private final Path authFile;
    private JSONObject authData;

    public GoogleDriveAuthManager() {
        this.authFile = getAuthFilePath();
        loadAuthData();
    }

    public String getAccessToken() throws IOException {
        String manualToken = getManualAccessToken();
        if (manualToken != null) {
            return manualToken;
        }

        if (!refreshAccessTokenIfNeeded()) {
            return null;
        }

        String accessToken = authData.optString("accessToken", null);
        if (accessToken == null || accessToken.isBlank()) {
            return null;
        }
        return accessToken.trim();
    }

    public void performLogin() throws IOException {
        String clientId = getClientId();
        String clientSecret = getClientSecret();

        HttpServer server = null;
        int port = 0;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            port = server.getAddress().getPort();
        } catch (IOException e) {
            throw new IOException("Could not open local callback server for OAuth login.", e);
        }

        String redirectUri = "http://127.0.0.1:" + port + "/oauth2callback";
        String authUrl = buildAuthUrl(clientId, redirectUri);
        CompletableFuture<String> codeFuture = new CompletableFuture<>();

        server.createContext("/oauth2callback", new OAuthCallbackHandler(codeFuture));
        server.start();

        System.out.println("Opening browser to authenticate with Google Drive...");
        System.out.println("If the browser does not open automatically, visit this URL:");
        System.out.println(authUrl);
        openBrowser(authUrl);

        String authorizationCode = null;
        try {
            authorizationCode = codeFuture.get(180, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.out.println("Local callback did not receive a response. Please paste the code from the browser prompt.");
            authorizationCode = readAuthorizationCodeFromConsole();
        } finally {
            if (server != null) {
                server.stop(0);
            }
        }

        if (authorizationCode == null || authorizationCode.isBlank()) {
            throw new IOException("Google Drive authentication was not completed.");
        }

        exchangeCodeForTokens(authorizationCode.trim(), clientId, clientSecret, redirectUri);
        System.out.println("Google Drive login complete. Refresh token stored locally.");
    }

    private String getManualAccessToken() {
        String token = System.getProperty(ACCESS_TOKEN_PROPERTY);
        if (token != null && !token.isBlank()) {
            return token.trim();
        }
        token = System.getenv(ACCESS_TOKEN_ENV);
        return (token != null && !token.isBlank()) ? token.trim() : null;
    }

    private boolean refreshAccessTokenIfNeeded() throws IOException {
        if (authData == null) {
            return false;
        }

        String accessToken = authData.optString("accessToken", null);
        long expiresAt = authData.optLong("expiresAt", 0);

        if (accessToken != null && !accessToken.isBlank() && expiresAt > System.currentTimeMillis() + EXPIRES_BUFFER_MS) {
            return true;
        }

        String refreshToken = authData.optString("refreshToken", null);
        if (refreshToken == null || refreshToken.isBlank()) {
            return false;
        }

        refreshAccessToken(refreshToken);
        return true;
    }

    private void refreshAccessToken(String refreshToken) throws IOException {
        String clientId = getClientId();
        String clientSecret = getClientSecret();

        Map<String, String> form = new HashMap<>();
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        form.put("refresh_token", refreshToken);
        form.put("grant_type", "refresh_token");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(TOKEN_ENDPOINT))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(buildForm(form)))
            .build();

        HttpResponse<String> response;
        try {
            response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Google Drive token refresh interrupted.", e);
        }
        if (response.statusCode() != 200) {
            String message = "Failed to refresh Google Drive access token: " + response.statusCode();
            if (response.body() != null && !response.body().isBlank()) {
                message += " " + response.body();
            }
            throw new IOException(message);
        }

        JSONObject json = new JSONObject(response.body());
        String newAccessToken = json.optString("access_token", null);
        if (newAccessToken == null || newAccessToken.isBlank()) {
            throw new IOException("Google Drive refresh response did not contain an access token.");
        }

        authData.put("accessToken", newAccessToken);
        authData.put("expiresAt", System.currentTimeMillis() + (json.optLong("expires_in", 3600) * 1000));
        if (json.has("refresh_token")) {
            String newRefreshToken = json.optString("refresh_token", null);
            if (newRefreshToken != null && !newRefreshToken.isBlank()) {
                authData.put("refreshToken", newRefreshToken);
            }
        }
        saveAuthData();
    }

    private void exchangeCodeForTokens(String code, String clientId, String clientSecret, String redirectUri) throws IOException {
        Map<String, String> form = new HashMap<>();
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        form.put("code", code);
        form.put("redirect_uri", redirectUri);
        form.put("grant_type", "authorization_code");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(TOKEN_ENDPOINT))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(buildForm(form)))
            .build();

        HttpResponse<String> response;
        try {
            response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Google Drive token exchange interrupted.", e);
        }
        if (response.statusCode() != 200) {
            throw new IOException("Google Drive token exchange failed: " + response.statusCode() + " " + response.body());
        }

        JSONObject json = new JSONObject(response.body());
        String accessToken = json.optString("access_token", null);
        String refreshToken = json.optString("refresh_token", null);
        long expiresIn = json.optLong("expires_in", 3600);

        if (accessToken == null || accessToken.isBlank()) {
            throw new IOException("Google Drive token exchange did not return an access token.");
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IOException("Google Drive token exchange did not return a refresh token. Ensure prompt=consent and access_type=offline are enabled.");
        }

        authData.put("accessToken", accessToken);
        authData.put("refreshToken", refreshToken);
        authData.put("expiresAt", System.currentTimeMillis() + (expiresIn * 1000));
        saveAuthData();
    }

    private String buildAuthUrl(String clientId, String redirectUri) {
        return AUTH_ENDPOINT
            + "?response_type=code"
            + "&client_id=" + urlEncode(clientId)
            + "&redirect_uri=" + urlEncode(redirectUri)
            + "&scope=" + urlEncode(SCOPE)
            + "&access_type=offline"
            + "&prompt=consent"
            + "&include_granted_scopes=true";
    }

    private void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception e) {
            System.out.println("Unable to open browser automatically.");
        }
    }

    private String readAuthorizationCodeFromConsole() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Enter the authorization code: ");
        return reader.readLine();
    }

    private String getClientId() throws IOException {
        String clientId = System.getenv(CLIENT_ID_ENV);
        if (clientId != null && !clientId.isBlank()) {
            return clientId.trim();
        }

        System.out.print("Enter your Google Drive OAuth Client ID: ");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        clientId = reader.readLine();

        if (clientId == null || clientId.isBlank()) {
            throw new IOException("Google Drive client ID is required. Set the " + CLIENT_ID_ENV + " environment variable or provide it interactively.");
        }
        return clientId.trim();
    }

    private String getClientSecret() throws IOException {
        String clientSecret = System.getenv(CLIENT_SECRET_ENV);
        if (clientSecret != null && !clientSecret.isBlank()) {
            return clientSecret.trim();
        }

        System.out.print("Enter your Google Drive OAuth Client Secret: ");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        clientSecret = reader.readLine();

        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IOException("Google Drive client secret is required. Set the " + CLIENT_SECRET_ENV + " environment variable or provide it interactively.");
        }
        return clientSecret.trim();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String buildForm(Map<String, String> values) {
        return values.entrySet().stream()
            .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
            .collect(Collectors.joining("&"));
    }

    private void loadAuthData() {
        try {
            if (Files.exists(authFile)) {
                String content = Files.readString(authFile).trim();
                if (!content.isBlank()) {
                    authData = new JSONObject(content);
                    return;
                }
            }
        } catch (Exception ignored) {
        }
        authData = new JSONObject();
    }

    private void saveAuthData() {
        try {
            Files.writeString(authFile, authData.toString(4));
        } catch (IOException e) {
            throw new RuntimeException("Unable to store Google Drive auth data: " + e.getMessage(), e);
        }
    }

    private static Path getAuthFilePath() {
        String homeDir = System.getProperty("user.home");
        Path authDir = Paths.get(homeDir, AUTH_DIR_NAME);
        try {
            Files.createDirectories(authDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create auth directory: " + authDir, e);
        }
        return authDir.resolve(AUTH_FILE_NAME);
    }

    private static class OAuthCallbackHandler implements HttpHandler {
        private final CompletableFuture<String> codeFuture;

        OAuthCallbackHandler(CompletableFuture<String> codeFuture) {
            this.codeFuture = codeFuture;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            String code = null;
            String message;

            if (query != null && query.contains("code=")) {
                for (String part : query.split("&")) {
                    if (part.startsWith("code=")) {
                        code = part.substring("code=".length());
                        break;
                    }
                }
            }

            if (code != null && !code.isBlank()) {
                message = "Authentication successful. You can close this window.";
                codeFuture.complete(code);
            } else {
                message = "Authentication failed. Please return to the application and try again.";
                if (!codeFuture.isDone()) {
                    codeFuture.complete(null);
                }
            }

            byte[] bytes = ("<html><body><h1>" + message + "</h1></body></html>").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }
}
