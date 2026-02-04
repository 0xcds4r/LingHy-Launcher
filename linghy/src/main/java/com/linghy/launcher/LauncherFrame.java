package com.linghy.launcher;

import com.linghy.env.Environment;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class LauncherFrame extends JFrame
{
    public LauncherFrame()
    {
        setTitle("LingHy v" + Environment.getVersion());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setIconImages(loadAppIcons());

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

    private List<Image> loadAppIcons()
    {
        List<Image> icons = new ArrayList<>();
        String[] sizes = {"16", "32", "64", "128", "256", "512"};

        for (String size : sizes)
        {
            try
            {
                InputStream is = getClass().getResourceAsStream("/icons/logo_" + size + ".png");
                if (is != null)
                {
                    BufferedImage img = ImageIO.read(is);
                    icons.add(img);
                    System.out.println("loading logo: " + "/icons/logo_" + size + ".png");
                }
            }
            catch (Exception e)
            {
                System.err.println("Failed to load icon " + size + "x" + size);
            }
        }

        return icons;
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