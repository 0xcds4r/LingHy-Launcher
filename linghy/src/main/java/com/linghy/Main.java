package com.linghy;

import com.formdev.flatlaf.FlatDarkLaf;
import com.linghy.env.Cleanup;
import com.linghy.env.Environment;
import com.linghy.launcher.LauncherFrame;
import com.linghy.model.GameSession;
import com.linghy.mods.curseforge.CurseForgeAPI;
import com.linghy.service.AuthService;
import com.linghy.utils.AffinityMgr;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Main
{
    private static final String[] PROCESS_KEYWORDS = {
            "linghy",
            "hytale",
            "hytaleclient"
    };

    private static void killJavaProcesses()
    {
        for (String keyword : PROCESS_KEYWORDS)
        {
            try {
                String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);

                if (os.contains("win")) {
                    new ProcessBuilder("taskkill", "/F", "/IM", keyword + ".exe").start();
                } else {
                    new ProcessBuilder("pkill", "-f", keyword).start();
                }
            } catch (Exception ex) {
                System.err.println("Failed to kill process '" + keyword + "': " + ex.getMessage());
            }
        }
    }

    private static Map<String, String> parseQuery(String query)
    {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;

        for (String pair : query.split("&"))
        {
            int eqIndex = pair.indexOf('=');

            if (eqIndex > 0)
            {
                String key = pair.substring(0, eqIndex);
                String value = pair.substring(eqIndex + 1);

                try {
                    value = URLDecoder.decode(value, StandardCharsets.UTF_8);
                } catch (Exception ignored) {
                    // ...
                }

                map.put(key, value);
            }
        }

        return map;
    }

    private static String getFirstNonNull(String... values)
    {
        for (String v : values)
        {
            if (v != null && !v.trim().isEmpty()) {
                return v;
            }
        }

        return null;
    }

    public static void handleUri(String uri)
    {
        System.out.println("Received URI: " + uri);

        if (!uri.startsWith("curseforge://")) {
            System.out.println("-> Does not start with curseforge:// -> ignoring");
            return;
        }

        try
        {
            String afterScheme = uri.substring("curseforge://".length());

            if (afterScheme.startsWith("install?"))
            {
                String query = afterScheme.substring("install?".length());
                Map<String, String> params = parseQuery(query);

                String addonIdStr = getFirstNonNull(
                        params.get("addonId"),
                        params.get("addon-id"),
                        params.get("projectId"),
                        params.get("id")
                );

                String fileIdStr = getFirstNonNull(
                        params.get("fileId"),
                        params.get("file-id"),
                        params.get("file")
                );

                System.out.println("Query parameters: " + params);

                if (addonIdStr == null || addonIdStr.trim().isEmpty())
                {
                    System.out.println("-> No addonId / modId found");
                    return;
                }

                int modId;

                try {
                    modId = Integer.parseInt(addonIdStr.trim());
                } catch (NumberFormatException e) {
                    System.out.println("-> addonId is not a valid number: " + addonIdStr);
                    return;
                }

                Integer specificFileId = null;

                if (fileIdStr != null && !fileIdStr.trim().isEmpty())
                {
                    try {
                        specificFileId = Integer.parseInt(fileIdStr.trim());
                    } catch (NumberFormatException e)
                    {
                        System.out.println("-> fileId is not a valid number: " + fileIdStr);
                    }
                }

                System.out.printf("-> Processing mod installation -> modId = %d, fileId = %s%n",
                        modId,
                        specificFileId != null ? specificFileId : "latest (most recent)");

                // TODO
            }
            else if (afterScheme.contains("/mods/") && afterScheme.contains("/install"))
            {
                String[] parts = afterScheme.split("/");
                if (parts.length >= 3)
                {
                    try
                    {
                        int modId = Integer.parseInt(parts[1]);
                        System.out.println("-> Old format detected: modId = " + modId + " (latest version)");
                        // TODO
                    } catch (NumberFormatException e)
                    {
                        System.out.println("-> Could not parse ID from /mods/.../install format");
                    }
                }
            }
            else
            {
                System.out.println("-> Unknown URI format: " + afterScheme);
            }
        } catch (Exception e)
        {
            System.err.println("Error processing curseforge:// URI:");
            System.err.println("  URI: " + uri);
            System.err.println("  Error: " + e.getClass().getSimpleName() + " -> " + e.getMessage());

            e.printStackTrace();
        }
    }

    public static void main(String[] args)
    {
        java.security.Security.setProperty("networkaddress.cache.ttl", "60");
        java.security.Security.setProperty("networkaddress.cache.negative.ttl", "10");

        if (args.length > 0)
        {
            String uri = args[0];

            if (uri.startsWith("curseforge://")) {
                handleUri(uri);
            }
        }

        AffinityMgr.init();
        CurseForgeAPI.init();

        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception e) {
            System.err.println("Failed to set look and feel: " + e.getMessage());
        }

        try {
            Environment.createFolders();
            Cleanup.cleanupIncompleteDownloads();
        } catch (Exception e) {
            System.err.println("Warning: cleanup failed: " + e.getMessage());
        }

        SwingUtilities.invokeLater(() ->
        {
            LauncherFrame frame = new LauncherFrame();
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    killJavaProcesses();
                    frame.dispose();
                }
            });
            frame.setVisible(true);
        });
    }
}
