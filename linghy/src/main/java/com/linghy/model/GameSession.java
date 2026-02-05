package com.linghy.model;

public class GameSession
{
    private final String username;
    private final String uuid;
    private final String identityToken;
    private final String sessionToken;

    public GameSession(String username, String uuid, String identityToken, String sessionToken)
    {
        this.username = username;
        this.uuid = uuid;
        this.identityToken = identityToken;
        this.sessionToken = sessionToken;
    }

    public String getUsername() {
        return username;
    }

    public String getUuid() {
        return uuid;
    }

    public String getIdentityToken() {
        return identityToken;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    @Override
    public String toString() {
        return String.format("GameSession{username='%s', uuid='%s'}", username, uuid);
    }
}
