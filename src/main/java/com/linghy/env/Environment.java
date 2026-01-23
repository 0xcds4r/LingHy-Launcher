package com.linghy.env;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Environment
{
    private static final String APP_NAME = "hytale";

    public static String getOS()
    {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "darwin";
        if (os.contains("nix") || os.contains("nux")) return "linux";
        return "unknown";
    }

    public static String getArch()
    {
        String arch = System.getProperty("os.arch").toLowerCase();
        if (arch.contains("amd64") || arch.contains("x86_64")) return "amd64";
        if (arch.contains("aarch64") || arch.contains("arm64")) return "arm64";
        return "unknown";
    }

    public static Path getDefaultAppDir()
    {
        String home = System.getProperty("user.home");
        String os = getOS();

        return switch (os)
        {
            case "windows" -> Paths.get(home, "AppData", "Local", APP_NAME);
            case "darwin" -> Paths.get(home, "Library", "Application Support", APP_NAME);
            case "linux" -> Paths.get(home, "." + APP_NAME.toLowerCase());
            default -> Paths.get(home, APP_NAME);
        };
    }

    public static void createFolders() throws IOException
    {
        Path basePath = getDefaultAppDir();
        Path packagePath = basePath.resolve("release").resolve("package");

        Path[] paths = {
                basePath,
                basePath.resolve("cache"),
                basePath.resolve("tools").resolve("butler"),
                packagePath.resolve("jre"),
                packagePath.resolve("game"),
                packagePath.resolve("game").resolve("latest")
        };

        for (Path path : paths) {
            Files.createDirectories(path);
        }
    }
}