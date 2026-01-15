package com.hylauncher;

import com.formdev.flatlaf.FlatDarkLaf;
import com.hylauncher.env.Cleanup;
import com.hylauncher.env.Environment;
import com.hylauncher.launcher.LauncherFrame;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Paths;
import java.util.Locale;

public class Main {

    private static final String[] PROCESS_KEYWORDS = {
            "hylauncher",
            "hytale",
            "hytaleclient"
    };

    private static void killJavaProcesses() {
        for (String keyword : PROCESS_KEYWORDS) {
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

    public static void main(String[] args)
    {
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

        SwingUtilities.invokeLater(() -> {
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
