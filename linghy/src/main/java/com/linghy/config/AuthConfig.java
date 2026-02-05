package com.linghy.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.linghy.env.Environment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class AuthConfig
{
    private static final String CONFIG_FILE = "auth_config.json";
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private String authDomain = "sanasol.ws";
    private boolean useCustomAuth = true;
    private boolean autoApplyPatches = true;

    public String getAuthDomain()
    {
        return authDomain;
    }

    public void setAuthDomain(String authDomain)
    {
        this.authDomain = authDomain;
    }

    public boolean isUseCustomAuth()
    {
        return useCustomAuth;
    }

    public void setUseCustomAuth(boolean useCustomAuth)
    {
        this.useCustomAuth = useCustomAuth;
    }

    public boolean isAutoApplyPatches()
    {
        return autoApplyPatches;
    }

    public void setAutoApplyPatches(boolean autoApplyPatches)
    {
        this.autoApplyPatches = autoApplyPatches;
    }

    public static AuthConfig load()
    {
        Path configPath = getConfigPath();

        if (Files.exists(configPath))
        {
            try
            {
                String json = Files.readString(configPath, StandardCharsets.UTF_8);
                return gson.fromJson(json, AuthConfig.class);
            }
            catch (IOException e)
            {
                System.err.println("Failed to load auth config: " + e.getMessage());
            }
        }

        return new AuthConfig();
    }

    public void save()
    {
        Path configPath = getConfigPath();

        try
        {
            Files.createDirectories(configPath.getParent());
            String json = gson.toJson(this);
            Files.writeString(configPath, json, StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            System.err.println("Failed to save auth config: " + e.getMessage());
        }
    }

    private static Path getConfigPath()
    {
        return Environment.getDefaultAppDir().resolve(CONFIG_FILE);
    }
}