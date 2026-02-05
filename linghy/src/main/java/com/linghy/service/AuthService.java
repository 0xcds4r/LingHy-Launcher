package com.linghy.service;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.linghy.launcher.UUIDGen;
import com.linghy.model.GameSession;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public class AuthService
{
    private static final String DEFAULT_AUTH_DOMAIN = "sessions.sanasol.ws";
    private static final Gson gson = new Gson();
    private final HttpClient httpClient;
    private String authDomain;

    public AuthService()
    {
        this(DEFAULT_AUTH_DOMAIN);
    }

    public AuthService(String authDomain)
    {
        this.authDomain = authDomain;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public void setAuthDomain(String domain)
    {
        this.authDomain = domain;
        System.out.println("Auth domain set to: " + domain);
    }

    public String getAuthDomain()
    {
        return authDomain;
    }

    public GameSession fetchGameSession(String username) throws Exception
    {
        String uuid = UUIDGen.generateUUID(username);
        String baseUrl = "https://" + authDomain;

        GameSessionRequest requestBody = new GameSessionRequest(
                uuid,
                username,
                List.of("hytale:server", "hytale:client")
        );

        String jsonBody = gson.toJson(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/game-session/new"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("User-Agent", "LingHy-Launcher/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        System.out.println("Requesting game session for: " + username);
        System.out.println("Auth server: " + baseUrl);
        System.out.println("UUID: " + uuid);

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200)
        {
            String errorBody = response.body();
            throw new Exception("Failed to fetch game session: HTTP " +
                    response.statusCode() + " - " + errorBody);
        }

        GameSessionResponse responseObj = gson.fromJson(response.body(),
                GameSessionResponse.class);

        System.out.println("Game session obtained successfully");
        System.out.println("Identity token length: " + responseObj.identityToken.length());
        System.out.println("Session token: " + responseObj.sessionToken);

        return new GameSession(
                username,
                uuid,
                responseObj.identityToken,
                responseObj.sessionToken
        );
    }

    public boolean validateAuthServer() throws Exception
    {
        String baseUrl = "https://" + authDomain;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/health"))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "LingHy-Launcher/1.0")
                .GET()
                .build();

        try
        {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            return response.statusCode() == 200;
        }
        catch (Exception e)
        {
            System.err.println("Auth server validation failed: " + e.getMessage());
            return false;
        }
    }

    private static class GameSessionRequest
    {
        private final String uuid;
        private final String name;
        private final List<String> scopes;

        public GameSessionRequest(String uuid, String name, List<String> scopes)
        {
            this.uuid = uuid;
            this.name = name;
            this.scopes = scopes;
        }
    }

    private static class GameSessionResponse
    {
        @SerializedName("identityToken")
        public String identityToken;

        @SerializedName("sessionToken")
        public String sessionToken;

        @SerializedName("expiresIn")
        public int expiresIn;

        @SerializedName("expiresAt")
        public String expiresAt;

        @SerializedName("tokenType")
        public String tokenType;
    }
}