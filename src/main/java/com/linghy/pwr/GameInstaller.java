package com.linghy.pwr;

import com.linghy.butler.ButlerInstaller;
import com.linghy.env.Environment;
import com.linghy.model.ProgressCallback;
import com.linghy.model.ProgressUpdate;
import com.linghy.version.GameVersion;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class GameInstaller
{
    public static Path installGameVersion(GameVersion version, ProgressCallback callback) throws Exception
    {
        int patchNumber = version.getPatchNumber();
        String branch = version.getBranch();

        String dirName = branch.equals("pre-release")
                ? "patch-pre-" + patchNumber
                : "patch-" + patchNumber;

        Path gameDir = Environment.getDefaultAppDir()
                .resolve("release").resolve("package")
                .resolve("game").resolve(dirName);

        String gameClient = Environment.getOS().equals("windows")
                ? "HytaleClient.exe" : "HytaleClient";
        Path clientPath = gameDir.resolve("Client").resolve(gameClient);

        if (Files.exists(clientPath))
        {
            System.out.println("Version " + version.getName() + " already installed");
            callback.onProgress(new ProgressUpdate("game", 100,
                    "Version already installed", "", "", 0, 0));
            return gameDir;
        }

        callback.onProgress(new ProgressUpdate("game", 0,
                "Download " + version.getName() + "...", version.getFileName(), "", 0, 0));

        Path pwrPath = PWRDownloader.downloadPWRFromUrl(
                version.getDownloadUrl(),
                version.getFileName(),
                callback
        );

        callback.onProgress(new ProgressUpdate("game", 50,
                "Installing " + version.getName() + "...", "", "", 0, 0));

        applyPWRToDirectory(pwrPath, gameDir, callback);

        return gameDir;
    }

    public static void installGame(String version, String fileName,
                                   ProgressCallback callback) throws Exception
    {
        Path gameLatest = Environment.getDefaultAppDir()
                .resolve("release").resolve("package")
                .resolve("game").resolve("latest");

        String gameClient = Environment.getOS().equals("windows")
                ? "HytaleClient.exe" : "HytaleClient";
        Path clientPath = gameLatest.resolve("Client").resolve(gameClient);

        if (Files.exists(clientPath))
        {
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

        applyPWRToDirectory(pwrPath, gameLatest, callback);
    }

    private static void applyPWRToDirectory(Path pwrFile, Path targetDir,
                                            ProgressCallback callback) throws Exception
    {
        Path butlerPath = ButlerInstaller.installButler(callback);

        Files.createDirectories(targetDir);
        Path stagingDir = targetDir.resolve("staging-temp");
        Files.createDirectories(stagingDir);

        ProcessBuilder pb = new ProcessBuilder(
                butlerPath.toString(),
                "apply",
                "--staging-dir", stagingDir.toString(),
                pwrFile.toString(),
                targetDir.toString()
        );

        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        System.out.println("Applying .pwr file to: " + targetDir);
        callback.onProgress(new ProgressUpdate("game", 60,
                "Applying game patch...", "", "", 0, 0));

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new Exception("Butler apply failed with exit code: " + exitCode);
        }

        deleteRecursively(stagingDir);

        System.out.println("Game extracted successfully to: " + targetDir);
        callback.onProgress(new ProgressUpdate("game", 100,
                "Game installed successfully", "", "", 0, 0));
    }

    private static void deleteRecursively(Path path) throws IOException
    {
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