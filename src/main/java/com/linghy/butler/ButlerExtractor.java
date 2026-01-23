package com.linghy.butler;

import com.linghy.env.Environment;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.*;
import java.nio.file.*;

public class ButlerExtractor
{
    public static void extractButler(Path zipFile, Path destDir) throws IOException
    {
        try (ZipFile zip = new ZipFile(zipFile.toFile()))
        {
            var entries = zip.getEntries();

            while (entries.hasMoreElements())
            {
                ZipArchiveEntry entry = entries.nextElement();
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

                    try (InputStream in = zip.getInputStream(entry);
                         OutputStream out = Files.newOutputStream(filePath)) {
                        in.transferTo(out);
                    }

                    if (!Environment.getOS().equals("windows"))
                    {
                        int unixMode = entry.getUnixMode();
                        if ((unixMode & 0100) != 0) {
                            filePath.toFile().setExecutable(true);
                        }
                    }
                }
            }
        }
    }
}