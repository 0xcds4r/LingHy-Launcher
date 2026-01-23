package com.linghy.launcher;

import javax.swing.*;
import java.awt.*;

public class LauncherFrame extends JFrame
{
    public LauncherFrame()
    {
        setTitle("LingHy v1.5");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();

        int w = (int) (screen.width  * 0.70);
        int h = (int) (screen.height * 0.673);

        double ratio = 16.0 / 9.0;
        if ((double) w / h > ratio)
        {
            w = (int) (h * ratio);
        }
        else
        {
            h = (int) (w / ratio);
        }

        setSize(w, h);
        setResizable(false);
        setUndecorated(true);
        setLocationRelativeTo(null);

        getContentPane().setBackground(new Color(9, 9, 9));

        LauncherPanel panel = new LauncherPanel(this);
        setContentPane(panel);

        addWindowDragListener();
    }

    private void addWindowDragListener()
    {
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