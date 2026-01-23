package com.linghy.java;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class JREExtractor
{

    public static void extractJRE(Path archive, Path destDir) throws IOException
    {
        if (Files.exists(destDir)) {
            deleteRecursively(destDir);
        }
        Files.createDirectories(destDir);

        String fileName = archive.getFileName().toString().toLowerCase();

        if (fileName.endsWith(".zip")) {
            extractZip(archive, destDir);
        } else if (fileName.endsWith(".tar.gz")) {
            extractTarGz(archive, destDir);
        } else {
            throw new IOException("Unsupported archive format: " + fileName);
        }

        flattenJREDir(destDir);
    }

    private static void extractZip(Path zipFile, Path destDir) throws IOException
    {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile)))
        {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null)
            {
                Path filePath = destDir.resolve(entry.getName()).normalize();

                if (!filePath.startsWith(destDir)) {
                    throw new IOException("Illegal file path: " + entry.getName());
                }

                if (entry.isDirectory())
                {
                    Files.createDirectories(filePath);
                }
                else
                {
                    Files.createDirectories(filePath.getParent());
                    Files.copy(zis, filePath, StandardCopyOption.REPLACE_EXISTING);
                }

                zis.closeEntry();
            }
        }
    }

    private static void extractTarGz(Path tarGzFile, Path destDir) throws IOException
    {
        try (InputStream fi = Files.newInputStream(tarGzFile);
             GzipCompressorInputStream gzi = new GzipCompressorInputStream(fi);
             TarArchiveInputStream tai = new TarArchiveInputStream(gzi)) {

            TarArchiveEntry entry;
            while ((entry = tai.getNextEntry()) != null)
            {
                Path filePath = destDir.resolve(entry.getName()).normalize();

                if (!filePath.startsWith(destDir)) {
                    throw new IOException("Illegal file path: " + entry.getName());
                }

                if (entry.isDirectory())
                {
                    Files.createDirectories(filePath);
                }
                else
                {
                    Files.createDirectories(filePath.getParent());
                    Files.copy(tai, filePath, StandardCopyOption.REPLACE_EXISTING);

                    if ((entry.getMode() & 0100) != 0) {
                        filePath.toFile().setExecutable(true);
                    }
                }
            }
        }
    }

    private static void flattenJREDir(Path jreDir) throws IOException
    {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(jreDir))
        {
            Path[] entries = StreamSupport.stream(stream.spliterator(), false)
                    .toArray(Path[]::new);

            if (entries.length == 1 && Files.isDirectory(entries[0])) {
                Path nested = entries[0];

                try (DirectoryStream<Path> nestedStream = Files.newDirectoryStream(nested)) {
                    for (Path file : nestedStream) {
                        Files.move(file, jreDir.resolve(file.getFileName()),
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                }

                deleteRecursively(nested);
            }
        }
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