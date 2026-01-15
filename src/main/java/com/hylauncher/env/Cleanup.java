package com.hylauncher.env;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class Cleanup
{
    public static void cleanupIncompleteDownloads() throws IOException
    {
        Path appDir = Environment.getDefaultAppDir();
        Path cacheDir = appDir.resolve("cache");
        Path gameLatest = appDir.resolve("release").resolve("package")
                .resolve("game").resolve("latest");

        cleanDirectory(cacheDir, new String[]{".pwr", ".zip", ".tar.gz", ".tmp"});
        cleanIncompleteGame(gameLatest);

        Path stagingDir = gameLatest.resolve("staging-temp");
        if (Files.exists(stagingDir)) {
            deleteRecursively(stagingDir);
        }
    }

    private static void cleanDirectory(Path dir, String[] extensions) throws IOException {
        if (!Files.exists(dir)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) continue;

                String fileName = entry.getFileName().toString();
                for (String ext : extensions) {
                    if (fileName.endsWith(ext)) {
                        System.out.println("Removing incomplete download: " + entry);
                        Files.deleteIfExists(entry);
                        break;
                    }
                }
            }
        }
    }

    private static void cleanIncompleteGame(Path gameDir) throws IOException {
        if (!Files.exists(gameDir)) return;

        String gameClient = Environment.getOS().equals("windows")
                ? "HytaleClient.exe" : "HytaleClient";
        Path clientPath = gameDir.resolve("Client").resolve(gameClient);

        if (!Files.exists(clientPath)) {
            System.out.println("Incomplete game installation detected, cleaning up...");
            deleteRecursively(gameDir);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;

        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}