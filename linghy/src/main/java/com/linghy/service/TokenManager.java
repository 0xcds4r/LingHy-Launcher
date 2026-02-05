package com.linghy.service;

import com.linghy.model.GameSession;

import java.util.concurrent.ConcurrentHashMap;

public class TokenManager
{
    private static final long TOKEN_CACHE_DURATION_MS = 50 * 60 * 1000;
    private static final ConcurrentHashMap<String, CachedSession> tokenCache = new ConcurrentHashMap<>();

    private static class CachedSession
    {
        final GameSession session;
        final long cacheTime;

        CachedSession(GameSession session)
        {
            this.session = session;
            this.cacheTime = System.currentTimeMillis();
        }

        boolean isExpired()
        {
            return System.currentTimeMillis() - cacheTime > TOKEN_CACHE_DURATION_MS;
        }
    }

    public static GameSession getOrFetchSession(String username, AuthService authService) throws Exception
    {
        CachedSession cached = tokenCache.get(username);

        if (cached != null && !cached.isExpired())
        {
            System.out.println("Using cached session for: " + username);
            return cached.session;
        }

        System.out.println("Fetching new session for: " + username);
        GameSession newSession = authService.fetchGameSession(username);

        tokenCache.put(username, new CachedSession(newSession));

        return newSession;
    }

    public static GameSession refreshSession(String username, AuthService authService) throws Exception
    {
        System.out.println("Force refreshing session for: " + username);
        tokenCache.remove(username);
        return getOrFetchSession(username, authService);
    }

    public static void clearSession(String username)
    {
        tokenCache.remove(username);
        System.out.println("Cleared session cache for: " + username);
    }

    public static void clearAll()
    {
        tokenCache.clear();
        System.out.println("Cleared all session cache");
    }

    public static boolean hasValidSession(String username)
    {
        CachedSession cached = tokenCache.get(username);
        return cached != null && !cached.isExpired();
    }
}