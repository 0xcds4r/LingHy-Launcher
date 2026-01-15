package com.hylauncher.launcher;

import javax.swing.*;
import java.awt.*;

public class LauncherFrame extends JFrame
{
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    public LauncherFrame() {
        setTitle("HyLauncher");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(WIDTH, HEIGHT);
        setResizable(false);
        setUndecorated(true);
        setLocationRelativeTo(null);

        getContentPane().setBackground(new Color(9, 9, 9));

        LauncherPanel panel = new LauncherPanel(this);
        setContentPane(panel);

        addWindowDragListener();
    }

    private void addWindowDragListener() {
        Point dragOffset = new Point();

        addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent e) {
                dragOffset.x = e.getX();
                dragOffset.y = e.getY();
            }
        });

        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            public void mouseDragged(java.awt.event.MouseEvent e) {
                Point location = getLocation();
                setLocation(location.x + e.getX() - dragOffset.x,
                        location.y + e.getY() - dragOffset.y);
            }
        });
    }
}