package com.hylauncher.pwr;

import com.hylauncher.butler.ButlerInstaller;
import com.hylauncher.env.Environment;
import com.hylauncher.model.ProgressCallback;
import com.hylauncher.model.ProgressUpdate;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class GameInstaller
{
    public static void installGame(String version, String fileName,
                                   ProgressCallback callback) throws Exception {
        Path gameLatest = Environment.getDefaultAppDir()
                .resolve("release").resolve("package")
                .resolve("game").resolve("latest");

        String gameClient = Environment.getOS().equals("windows")
                ? "HytaleClient.exe" : "HytaleClient";
        Path clientPath = gameLatest.resolve("Client").resolve(gameClient);

        if (Files.exists(clientPath)) {
            System.out.println("Game already installed, skipping download.");
            callback.onProgress(new ProgressUpdate("game", 100,
                    "Game already installed", "", "", 0, 0));
            return;
        }

        callback.onProgress(new ProgressUpdate("game", 0,
                "Downloading game files...", fileName, "", 0, 0));

        Path pwrPath = PWRDownloader.downloadPWR(version, fileName, callback);

        callback.onProgress(new ProgressUpdate("game", 50,
                "Extracting game files...", "", "", 0, 0));

        applyPWR(pwrPath, callback);
    }

    private static void applyPWR(Path pwrFile, ProgressCallback callback) throws Exception
    {
        Path gameLatest = Environment.getDefaultAppDir()
                .resolve("release").resolve("package")
                .resolve("game").resolve("latest");

        Path butlerPath = ButlerInstaller.installButler(callback);

        Path stagingDir = gameLatest.resolve("staging-temp");
        Files.createDirectories(stagingDir);

        ProcessBuilder pb = new ProcessBuilder(
                butlerPath.toString(),
                "apply",
                "--staging-dir", stagingDir.toString(),
                pwrFile.toString(),
                gameLatest.toString()
        );

        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        System.out.println("Applying .pwr file...");
        callback.onProgress(new ProgressUpdate("game", 60,
                "Applying game patch...", "", "", 0, 0));

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new Exception("Butler apply failed with exit code: " + exitCode);
        }

        deleteRecursively(stagingDir);

        System.out.println("Game extracted successfully");
        callback.onProgress(new ProgressUpdate("game", 100,
                "Game installed successfully", "", "", 0, 0));
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
