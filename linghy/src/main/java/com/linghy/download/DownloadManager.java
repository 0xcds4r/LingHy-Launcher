package com.linghy.download;

import com.linghy.model.ProgressCallback;
import com.linghy.model.ProgressUpdate;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.Consumer;

public class DownloadManager
{
    private static final int DEFAULT_BUFFER_SIZE = 32768;
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_RETRY_DELAY_MS = 2000;
    private static final long PROGRESS_UPDATE_INTERVAL_MS = 200;

    private final int bufferSize;
    private final int maxRetries;
    private final long retryDelayMs;

    public DownloadManager()
    {
        this(DEFAULT_BUFFER_SIZE, DEFAULT_MAX_RETRIES, DEFAULT_RETRY_DELAY_MS);
    }

    public DownloadManager(int bufferSize, int maxRetries, long retryDelayMs)
    {
        this.bufferSize = bufferSize;
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
    }

    public void downloadWithHttpURLConnection(String url, Path destination,
                                              ProgressCallback callback) throws Exception
    {
        downloadWithHttpURLConnection(url, destination, null, callback);
    }

    public void downloadWithHttpURLConnection(String url, Path destination,
                                              String expectedSha256,
                                              ProgressCallback callback) throws Exception
    {
        Exception lastException = null;
        Path tempFile = Paths.get(destination.toString() + ".tmp");

        for (int attempt = 1; attempt <= maxRetries; attempt++)
        {
            try
            {
                if (attempt > 1)
                {
                    if (callback != null) {
                        callback.onProgress(new ProgressUpdate(
                                "download", 0,
                                "Retry " + attempt + "/" + maxRetries,
                                destination.getFileName().toString(),
                                "", 0, 0
                        ));
                    }
                    Thread.sleep(retryDelayMs * attempt);
                }

                Files.deleteIfExists(tempFile);

                URL urlObj = new URL(url);
                HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
                connection.setRequestProperty("User-Agent", "LingHy-Launcher/1.0");
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(60000);
                connection.setReadTimeout(60000);

                int responseCode = connection.getResponseCode();

                int redirectCount = 0;
                while ((responseCode == 301 || responseCode == 302 || responseCode == 303) && redirectCount < 5)
                {
                    String newUrl = connection.getHeaderField("Location");
                    connection.disconnect();
                    connection = (HttpURLConnection) new URL(newUrl).openConnection();
                    connection.setRequestProperty("User-Agent", "LingHy-Launcher/1.0");
                    connection.setInstanceFollowRedirects(true);
                    connection.setConnectTimeout(60000);
                    connection.setReadTimeout(60000);
                    responseCode = connection.getResponseCode();
                    redirectCount++;
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("Server returned HTTP " + responseCode);
                }

                long contentLength = connection.getContentLengthLong();

                downloadStream(
                        connection.getInputStream(),
                        tempFile,
                        contentLength,
                        destination.getFileName().toString(),
                        callback
                );

                connection.disconnect();

                if (contentLength > 0 && Files.size(tempFile) != contentLength)
                {
                    throw new IOException("Incomplete download: expected " + contentLength +
                            " bytes, got " + Files.size(tempFile));
                }

                if (expectedSha256 != null && !expectedSha256.isEmpty())
                {
                    if (callback != null)
                    {
                        callback.onProgress(new ProgressUpdate(
                                "download", 95,
                                "Verifying checksum...",
                                destination.getFileName().toString(),
                                "", 0, 0
                        ));
                    }

                    if (!verifySHA256(tempFile, expectedSha256)) {
                        throw new IOException("SHA-256 verification failed");
                    }
                }

                Files.move(tempFile, destination,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);

                if (callback != null)
                {
                    callback.onProgress(new ProgressUpdate(
                            "download", 100,
                            "Download complete",
                            destination.getFileName().toString(),
                            "", 0, 0
                    ));
                }

                return;

            }
            catch (Exception e)
            {
                lastException = e;
                Files.deleteIfExists(tempFile);

                if (attempt == maxRetries) {
                    break;
                }
            }
        }

        throw new IOException("Download failed after " + maxRetries + " attempts", lastException);
    }

    public void downloadWithHttpClient(String url, Path destination,
                                       ProgressCallback callback) throws Exception
    {
        downloadWithHttpClient(url, destination, null, callback);
    }

    public void downloadWithHttpClient(String url, Path destination,
                                       String expectedSha256,
                                       ProgressCallback callback) throws Exception
    {
        Exception lastException = null;
        Path tempFile = Paths.get(destination.toString() + ".tmp");

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(60))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        for (int attempt = 1; attempt <= maxRetries; attempt++)
        {
            try
            {
                if (attempt > 1)
                {
                    if (callback != null) {
                        callback.onProgress(new ProgressUpdate(
                                "download", 0,
                                "Retry " + attempt + "/" + maxRetries,
                                destination.getFileName().toString(),
                                "", 0, 0
                        ));
                    }
                    Thread.sleep(retryDelayMs * attempt);
                }

                Files.deleteIfExists(tempFile);

                System.out.println("Attempting download from: " + url);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofMinutes(5))
                        .header("User-Agent", "LingHy-Launcher/1.0")
                        .header("Accept", "*/*")
                        .GET()
                        .build();

                HttpResponse<InputStream> response = client.send(request,
                        HttpResponse.BodyHandlers.ofInputStream());

                System.out.println("Response status: " + response.statusCode());

                if (response.statusCode() != 200) {
                    throw new IOException("Server returned HTTP " + response.statusCode());
                }

                long contentLength = response.headers()
                        .firstValueAsLong("Content-Length")
                        .orElse(-1);

                System.out.println("Content length: " + (contentLength > 0 ? contentLength + " bytes" : "unknown"));

                downloadStream(
                        response.body(),
                        tempFile,
                        contentLength,
                        destination.getFileName().toString(),
                        callback
                );

                long actualSize = Files.size(tempFile);
                System.out.println("Downloaded: " + actualSize + " bytes");

                if (contentLength > 0 && actualSize != contentLength) {
                    throw new IOException("Incomplete download: expected " + contentLength +
                            " bytes, got " + actualSize);
                }

                if (expectedSha256 != null && !expectedSha256.isEmpty())
                {
                    if (callback != null) {
                        callback.onProgress(new ProgressUpdate(
                                "download", 95,
                                "Verifying checksum...",
                                destination.getFileName().toString(),
                                "", 0, 0
                        ));
                    }

                    if (!verifySHA256(tempFile, expectedSha256)) {
                        throw new IOException("SHA-256 verification failed");
                    }
                }

                Files.move(tempFile, destination,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);

                if (callback != null) {
                    callback.onProgress(new ProgressUpdate(
                            "download", 100,
                            "Download complete",
                            destination.getFileName().toString(),
                            "", 0, 0
                    ));
                }

                System.out.println("Download successful");
                return;

            }
            catch (Exception e)
            {
                System.err.println("Attempt " + attempt + " failed: " + e.getMessage());
                lastException = e;
                Files.deleteIfExists(tempFile);

                if (attempt == maxRetries) {
                    break;
                }
            }
        }

        throw new IOException("Download failed after " + maxRetries + " attempts", lastException);
    }

    private void downloadStream(InputStream inputStream, Path destination,
                                long totalBytes, String fileName,
                                ProgressCallback callback) throws IOException
    {
        long downloaded = 0;
        long startTime = System.currentTimeMillis();
        long lastUpdate = startTime;

        try (InputStream in = inputStream;
             OutputStream out = Files.newOutputStream(destination,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING))
        {
            byte[] buffer = new byte[bufferSize];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1)
            {
                out.write(buffer, 0, bytesRead);
                downloaded += bytesRead;

                long now = System.currentTimeMillis();

                if (callback != null && (now - lastUpdate > PROGRESS_UPDATE_INTERVAL_MS))
                {
                    double percent = totalBytes > 0 ? (downloaded * 100.0 / totalBytes) : 0;
                    double elapsed = (now - startTime) / 1000.0;
                    String speed = elapsed > 0
                            ? String.format("%.2f MB/s", downloaded / 1024.0 / 1024.0 / elapsed)
                            : "";

                    callback.onProgress(new ProgressUpdate(
                            "download", percent,
                            "Downloading...", fileName,
                            speed, downloaded, totalBytes
                    ));

                    lastUpdate = now;
                }
            }
        }
    }

    public void downloadWithNIO(String url, Path destination,
                                ProgressCallback callback) throws Exception
    {
        Exception lastException = null;
        Path tempFile = Paths.get(destination.toString() + ".tmp");

        for (int attempt = 1; attempt <= maxRetries; attempt++)
        {
            try
            {
                if (attempt > 1)
                {
                    if (callback != null)
                    {
                        callback.onProgress(new ProgressUpdate(
                                "download", 0,
                                "Retry " + attempt + "/" + maxRetries,
                                destination.getFileName().toString(),
                                "", 0, 0
                        ));
                    }

                    Thread.sleep(retryDelayMs * attempt);
                }

                Files.deleteIfExists(tempFile);

                URL urlObj = new URL(url);
                HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
                connection.setRequestProperty("User-Agent", "Linghy/1.0");
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(60000);
                connection.setReadTimeout(60000);

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP " + responseCode);
                }

                long contentLength = connection.getContentLengthLong();

                downloadWithChannels(
                        connection.getInputStream(),
                        tempFile,
                        contentLength,
                        destination.getFileName().toString(),
                        callback
                );

                connection.disconnect();

                if (contentLength > 0 && Files.size(tempFile) != contentLength) {
                    throw new IOException("Incomplete download");
                }

                Files.move(tempFile, destination,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);

                if (callback != null)
                {
                    callback.onProgress(new ProgressUpdate(
                            "download", 100,
                            "Download complete",
                            destination.getFileName().toString(),
                            "", 0, 0
                    ));
                }

                return;

            }
            catch (Exception e)
            {
                lastException = e;
                Files.deleteIfExists(tempFile);

                if (attempt == maxRetries) {
                    break;
                }
            }
        }

        throw new IOException("Download failed after " + maxRetries + " attempts", lastException);
    }

    private void downloadWithChannels(InputStream inputStream, Path destination,
                                      long contentLength, String fileName,
                                      ProgressCallback callback) throws IOException
    {
        long totalRead = 0;
        long startTime = System.currentTimeMillis();
        long lastUpdate = startTime;
        long chunkSize = 1024 * 1024;

        try (ReadableByteChannel rbc = Channels.newChannel(inputStream);
             FileChannel fc = FileChannel.open(destination,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.WRITE,
                     StandardOpenOption.TRUNCATE_EXISTING))
        {
            while (totalRead < contentLength || contentLength <= 0)
            {
                long transferred = fc.transferFrom(rbc, totalRead, chunkSize);
                if (transferred <= 0) break;

                totalRead += transferred;

                long now = System.currentTimeMillis();

                if (callback != null && (now - lastUpdate > PROGRESS_UPDATE_INTERVAL_MS))
                {
                    double percent = contentLength > 0 ? (totalRead * 100.0) / contentLength : 0;
                    double elapsed = (now - startTime) / 1000.0;
                    String speed = elapsed > 0
                            ? String.format("%.2f MB/s", totalRead / 1024.0 / 1024.0 / elapsed)
                            : "";

                    callback.onProgress(new ProgressUpdate(
                            "download", percent,
                            "Downloading...", fileName,
                            speed, totalRead, contentLength
                    ));

                    lastUpdate = now;
                }
            }
        }
    }

    public boolean verifySHA256(Path file, String expectedHash) throws Exception
    {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream in = Files.newInputStream(file))
        {
            byte[] buffer = new byte[bufferSize];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        String calculatedHash = HexFormat.of().formatHex(digest.digest());
        return calculatedHash.equalsIgnoreCase(expectedHash);
    }

    public static void download(String url, Path destination) throws Exception {
        new DownloadManager().downloadWithHttpClient(url, destination, null);
    }

    public static void download(String url, Path destination, ProgressCallback callback) throws Exception {
        new DownloadManager().downloadWithHttpClient(url, destination, callback);
    }

    public static void download(String url, Path destination, String sha256,
                                ProgressCallback callback) throws Exception {
        new DownloadManager().downloadWithHttpClient(url, destination, sha256, callback);
    }
}