package com.linghy.launcher;

import com.linghy.env.Environment;
import com.linghy.java.JREDownloader;
import com.linghy.model.ProgressUpdate;
import com.linghy.mods.ModManagerDialog;
import com.linghy.patches.OnlineFix;
import com.linghy.pwr.GameInstaller;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;

import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.linghy.version.GameVersion;
import com.linghy.version.VersionManager;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import static java.awt.Frame.ICONIFIED;

public class LauncherPanel extends JPanel
{
    private final JFrame parent;
    private String username = "";
    private boolean isDownloading = false;

    private JLabel usernameLabel;
    private JTextField usernameField;
    private JButton playButton;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JLabel progressPercentLabel;
    private JLabel speedLabel;
    private BufferedImage backgroundImage;
    private JButton folderButton;
    private JButton fixOnlineButton;

    private double currentProgress = 0;
    private String currentMessage = "Ready to play";

    private static final Path USERNAME_FILE =
            Environment.getDefaultAppDir().resolve("username.txt");

    private VersionManager versionManager;
    private GameVersion selectedVersion;

    public LauncherPanel(JFrame parent)
    {
        this.parent = parent;
        this.versionManager = new VersionManager();
        setLayout(new BorderLayout());
        setOpaque(false);

        loadBackgroundImageAsync();
        initComponents();
        loadSavedUsername();
    }

    private void loadBackgroundImageAsync()
    {
        SwingWorker<BufferedImage, Void> worker = new SwingWorker<>()
        {
            @Override
            protected BufferedImage doInBackground()
            {
                try {
                    InputStream is = getClass().getResourceAsStream("/bg01.png");

                    if (is == null)
                    {
                        System.err.println("Resource not found: bg01.png");
                        return null;
                    }

                    return ImageIO.read(is);
                } catch (Exception e) {
                    System.err.println("Failed to load background: " + e.getMessage());
                    return null;
                }
            }

            @Override
            protected void done()
            {
                try {
                    backgroundImage = get();
                    if (backgroundImage != null) {
                        repaint();
                    }
                } catch (Exception e) {
                    System.err.println("Error setting background: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    private void loadSavedUsername()
    {
        try {
            if (Files.exists(USERNAME_FILE))
            {
                String savedName = Files.readString(USERNAME_FILE, StandardCharsets.UTF_8).trim();

                if (!savedName.isEmpty() && savedName.length() <= 16)
                {
                    usernameField.setText(savedName);
                    return;
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load username: " + e.getMessage());
        }

        usernameField.setText("");
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);

        if (backgroundImage != null)
        {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), null);

            g2d.setColor(new Color(0, 0, 0, 102));
            g2d.fillRect(0, 0, getWidth(), getHeight());

            GradientPaint gradient = new GradientPaint(
                    0, 0, new Color(0, 0, 0, 0),
                    0, getHeight(), new Color(9, 9, 9, 255)
            );
            g2d.setPaint(gradient);
            g2d.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    private void initComponents()
    {
        add(createTitleBar(), BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout(20, 20));
        centerPanel.setOpaque(false);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));

        JPanel leftPanel = createUsernameSection();
        leftPanel.setPreferredSize(new Dimension(350, 180));
        centerPanel.add(leftPanel, BorderLayout.WEST);

        JPanel newsPanel = createNewsSection();

        JScrollPane scrollPane = new JScrollPane(newsPanel);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 0, 0, 0), 0, false),
                BorderFactory.createEmptyBorder(45, 10, 45, 10)
        ));
        scrollPane.setBackground(new Color(0, 0, 0, 0));

        scrollPane.getVerticalScrollBar().setUnitIncrement(20);
        scrollPane.getVerticalScrollBar().setBlockIncrement(100);

        scrollPane.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI()
        {
            @Override
            protected void configureScrollBarColors()
            {
                this.thumbColor = new Color(255, 168, 69, 120);
                this.trackColor = new Color(26, 26, 32, 100);
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return createZeroButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return createZeroButton();
            }

            private JButton createZeroButton() {
                JButton button = new JButton();
                button.setPreferredSize(new Dimension(0, 0));
                button.setMinimumSize(new Dimension(0, 0));
                button.setMaximumSize(new Dimension(0, 0));
                return button;
            }

            @Override
            protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(thumbColor);
                g2.fillRoundRect(thumbBounds.x + 2, thumbBounds.y, thumbBounds.width - 4,
                        thumbBounds.height, 8, 8);
                g2.dispose();
            }

            @Override
            protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(trackColor);
                g2.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
                g2.dispose();
            }
        });

        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        centerPanel.add(scrollPane, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        add(createBottomControls(), BorderLayout.SOUTH);
    }

    private JPanel createTitleBar()
    {
        JPanel titleBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        titleBar.setOpaque(false);
        titleBar.setPreferredSize(new Dimension(0, 40));

        JButton minimizeBtn = createTitleBarButton("-");
        minimizeBtn.addActionListener(e -> parent.setState(ICONIFIED));

        JButton closeBtn = createTitleBarButton("×");
        closeBtn.addActionListener(e -> System.exit(0));
        closeBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { closeBtn.setForeground(new Color(239, 68, 68)); }
            public void mouseExited(MouseEvent e) { closeBtn.setForeground(Color.LIGHT_GRAY); }
        });

        titleBar.add(minimizeBtn);
        titleBar.add(closeBtn);
        return titleBar;
    }

    private JButton createTitleBarButton(String text)
    {
        JButton btn = new JButton(text);
        btn.setForeground(Color.LIGHT_GRAY);
        btn.setBackground(new Color(0, 0, 0, 0));
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setFont(new Font("Arial", Font.PLAIN, 16));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JPanel createUsernameSection()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 0, 16, 0));

        JLabel label = new JLabel("Enter username");
        label.setForeground(new Color(160, 160, 170));
        label.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

        usernameField = new RoundTextField("");
        usernameField.setFont(new Font("Segoe UI", Font.BOLD, 17));
        usernameField.setForeground(Color.WHITE);
        usernameField.setBackground(new Color(26, 26, 32));
        usernameField.setCaretColor(new Color(255, 168, 69));
        usernameField.setSelectionColor(new Color(255, 168, 69, 140));
        usernameField.setSelectedTextColor(Color.BLACK);
        usernameField.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        usernameField.setPreferredSize(new Dimension(280, 32));
        usernameField.setMaximumSize(new Dimension(340, 32));

        usernameField.addActionListener(e ->
        {
            saveUsername();
            usernameField.transferFocus();
        });

        usernameField.addFocusListener(new FocusAdapter()
        {
            @Override
            public void focusGained(FocusEvent e) {
                usernameField.setBorder(BorderFactory.createLineBorder(new Color(255, 168, 69, 160), 1));
            }

            @Override
            public void focusLost(FocusEvent e)
            {
                usernameField.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
                saveUsername();
                usernameField.setCaretPosition(usernameField.getText().length());
            }
        });

        panel.add(label);
        panel.add(usernameField);

        panel.add(Box.createVerticalStrut(8));

        JLabel infoLine1 = new JLabel("LingHy Launcher (v" + Environment.getVersion() +")");
        infoLine1.setForeground(new Color(120, 120, 130));
        infoLine1.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        infoLine1.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(infoLine1);

        JLabel infoLine2 = new JLabel("by 0xcds4r");
        infoLine2.setForeground(new Color(120, 120, 130));
        infoLine2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        infoLine2.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(infoLine2);

        return panel;
    }

    private void saveUsername()
    {
        String name = usernameField.getText().trim();

        if (name.isEmpty())
        {
            name = "";
            usernameField.setText(name);
        }

        if (name.length() > 16)
        {
            name = name.substring(0, 16);
            usernameField.setText(name);
        }

        try {
            Files.createDirectories(USERNAME_FILE.getParent());
            Files.writeString(USERNAME_FILE, name, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to save username: " + e.getMessage());
        }
    }

    @Deprecated
    private void startEditingUsername() {}

    @Deprecated
    private void finishEditingUsername() {}

    private List<NewsItem> fetchHytaleNews()
    {
        List<NewsItem> news = new ArrayList<>();

        try {
            Document doc = Jsoup.connect("https://hytale.com/news")
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(8000)
                    .get();

            Elements wrappers = doc.select(".postWrapper");

            for (Element wrapper : wrappers)
            {
                if (news.size() >= 6) break;

                Element postLink = wrapper.selectFirst("a.post");
                if (postLink == null) continue;

                String title = getSafeText(wrapper, "h4.post__details__heading", "No Title");

                Element bodyEl = wrapper.selectFirst("span.post__details__body");
                String rawDesc = "";

                if (bodyEl != null) {
                    rawDesc = bodyEl.html()
                            .replaceAll("(?i)<br\\s*/?>", "\n")
                            .replaceAll("<[^>]+>", "")
                            .trim();
                }

                String desc = rawDesc.length() > 120 ? rawDesc.substring(0, 120) + "..." : rawDesc;

                String meta = getSafeText(wrapper, "span.post__details__meta", "");

                String date = "Unknown date";

                Pattern p = Pattern.compile(
                        "(January|February|March|April|May|June|July|August|September|October|November|December)\\s+" +
                                "\\d{1,2}(st|nd|rd|th)\\s+\\d{4}"
                );

                Matcher m = p.matcher(meta);
                if (m.find()) {
                    date = m.group();
                }

                String url = postLink.absUrl("href");

                String imgUrl = "";
                Element img = wrapper.selectFirst("span.post__image img");
                if (img != null) {
                    imgUrl = img.absUrl("src");
                }

                news.add(new NewsItem(title, date, desc, url, imgUrl));
            }

        } catch (Exception e) {
            System.err.println("Hytale News Loading Error: " + e.getMessage());
        }

        if (news.isEmpty())
        {
            news.add(new NewsItem(
                    "News unavailable",
                    LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                    "Failed to load news. You may not have an internet connection.",
                    "https://hytale.com/news",
                    ""
            ));
        }

        return news;
    }

    private String getSafeText(Element parent, String selector, String fallback)
    {
        Element el = parent.selectFirst(selector);
        return (el != null) ? el.text() : fallback;
    }

    private String escapeHtml(String input)
    {
        if (input == null) return "";
        return input.replace("&amp;", "")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
                .replace("-", "&nbsp;");
    }

    private JPanel createNewsCard(NewsItem item)
    {
        JPanel newsCard = new JPanel(new BorderLayout(16, 0));

        newsCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 168, 69, 100), 1, false),
                BorderFactory.createEmptyBorder(18, 18, 18, 18)
        ));

        newsCard.setBackground(new Color(15, 15, 20, 200));
        newsCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));

        int textWidth = 440;

        String htmlText =
                "<html>" +
                        "<div style='width:" + textWidth + "px;'>" +
                        "<div style='color:#ffffff; font-size:17px; font-weight:bold; margin-bottom:8px;'>"
                        + escapeHtml(item.title) + "</div>" +
                        "<div style='color:#FFA845; font-size:12px; margin-bottom:12px;'>"
                        + item.date + "</div>" +
                        "<div style='color:#e0e0e0; font-size:9px; line-height:1.6;'>" +
                        escapeHtml(item.description) +
                        "</div>" +
                        "</div>" +
                        "</html>";

        JLabel newsLabel = new JLabel(htmlText);
        newsLabel.setVerticalAlignment(SwingConstants.TOP);

        newsCard.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        newsCard.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (!item.url.isEmpty())
                {
                    try {
                        Desktop.getDesktop().browse(new URI(item.url));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                newsCard.setBackground(new Color(20, 20, 28, 220));
                newsCard.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(255, 168, 69, 180), 2, false),
                        BorderFactory.createEmptyBorder(18, 18, 18, 18)
                ));
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                newsCard.setBackground(new Color(15, 15, 20, 200));
                newsCard.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(255, 168, 69, 100), 1, false),
                        BorderFactory.createEmptyBorder(18, 18, 18, 18)
                ));
            }
        });

        JPanel imgPanel = new JPanel(new BorderLayout());
        imgPanel.setBackground(new Color(9, 9, 9, 0));
        imgPanel.setPreferredSize(new Dimension(180, 120));
        imgPanel.setMinimumSize(new Dimension(180, 120));
        imgPanel.setMaximumSize(new Dimension(180, 120));

        if (!item.imageUrl.isEmpty())
        {
            SwingWorker<ImageIcon, Void> imgWorker = new SwingWorker<>()
            {
                @Override
                protected ImageIcon doInBackground() throws Exception
                {
                    try {
                        BufferedImage original = ImageIO.read(new URL(item.imageUrl));
                        if (original == null) return null;

                        int targetWidth = 180;
                        int targetHeight = 120;

                        double imgRatio = (double) original.getWidth() / original.getHeight();
                        double panelRatio = (double) targetWidth / targetHeight;

                        int newWidth, newHeight;
                        int cropX = 0, cropY = 0;

                        if (imgRatio > panelRatio)
                        {
                            newHeight = targetHeight;
                            newWidth = (int) (targetHeight * imgRatio);
                            cropX = (newWidth - targetWidth) / 2;
                        }
                        else
                        {
                            newWidth = targetWidth;
                            newHeight = (int) (targetWidth / imgRatio);
                            cropY = (newHeight - targetHeight) / 2;
                        }

                        Image scaled = original.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
                        BufferedImage buffered = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g2d = buffered.createGraphics();
                        g2d.drawImage(scaled, 0, 0, null);
                        g2d.dispose();

                        BufferedImage cropped = buffered.getSubimage(cropX, cropY, targetWidth, targetHeight);

                        return new ImageIcon(cropped);
                    } catch (Exception e) {
                        return null;
                    }
                }

                @Override
                protected void done()
                {
                    try {
                        ImageIcon icon = get(3, TimeUnit.SECONDS);
                        if (icon != null)
                        {
                            JLabel imgLabel = new JLabel(icon);
                            imgLabel.setHorizontalAlignment(SwingConstants.CENTER);
                            imgLabel.setVerticalAlignment(SwingConstants.CENTER);
                            imgLabel.setPreferredSize(new Dimension(180, 120));
                            imgLabel.setMinimumSize(new Dimension(180, 120));
                            imgLabel.setMaximumSize(new Dimension(180, 120));

                            imgLabel.setBorder(BorderFactory.createLineBorder(
                                    new Color(255, 168, 69, 60), 1, true));

                            imgPanel.removeAll();
                            imgPanel.add(imgLabel, BorderLayout.CENTER);
                            imgPanel.revalidate();
                            imgPanel.repaint();
                        }
                        else
                        {
                            showPlaceholder(imgPanel);
                        }
                    } catch (Exception e) {
                        showPlaceholder(imgPanel);
                    }
                }
            };
            imgWorker.execute();

            JLabel loadingLabel = new JLabel("Loading...", SwingConstants.CENTER);
            loadingLabel.setForeground(new Color(255, 168, 69, 100));
            loadingLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            imgPanel.add(loadingLabel, BorderLayout.CENTER);
        }
        else
        {
            showPlaceholder(imgPanel);
        }

        newsCard.add(newsLabel, BorderLayout.CENTER);
        newsCard.add(imgPanel, BorderLayout.EAST);

        return newsCard;
    }

    private void showPlaceholder(JPanel imgPanel)
    {
        imgPanel.removeAll();
        JLabel placeholder = new JLabel("Hytale", SwingConstants.CENTER);
        placeholder.setForeground(new Color(255, 168, 69, 120));
        placeholder.setFont(new Font("Segoe UI", Font.BOLD, 14));
        imgPanel.add(placeholder, BorderLayout.CENTER);
        imgPanel.revalidate();
        imgPanel.repaint();
    }

    private JPanel createNewsSection()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JLabel loadingLabel = new JLabel("Loading news...", SwingConstants.CENTER);
        loadingLabel.setForeground(new Color(255, 168, 69));
        loadingLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        panel.add(loadingLabel);

        SwingWorker<List<NewsItem>, Void> newsWorker = new SwingWorker<>()
        {
            @Override
            protected List<NewsItem> doInBackground() {
                return fetchHytaleNews();
            }

            @Override
            protected void done()
            {
                try {
                    List<NewsItem> newsItems = get();
                    panel.removeAll();

                    for (NewsItem item : newsItems) {
                        JPanel newsCard = createNewsCard(item);
                        panel.add(newsCard);
                        panel.add(Box.createVerticalStrut(14));
                    }

                    panel.revalidate();
                    panel.repaint();
                } catch (Exception e)
                {
                    System.err.println("Error loading news: " + e.getMessage());
                    panel.removeAll();

                    JLabel errorLabel = new JLabel("Error loading news", SwingConstants.CENTER);
                    errorLabel.setForeground(new Color(239, 68, 68));
                    errorLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                    panel.add(errorLabel);

                    panel.revalidate();
                    panel.repaint();
                }
            }
        };

        newsWorker.execute();
        return panel;
    }

    private void openGameFolder()
    {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                "Select folder type", true);
        dialog.setSize(400, 200);
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(false);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(new Color(18, 18, 18));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel titleLabel = new JLabel("Choose folder to open:");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        mainPanel.add(titleLabel);
        mainPanel.add(Box.createVerticalStrut(20));

        JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 10, 10));
        buttonPanel.setOpaque(false);

        JButton dataButton = createFolderDialogButton("Game Files");
        dataButton.setToolTipText("Open game installation folder");
        dataButton.addActionListener(e -> {
            dialog.dispose();
            openDataFolder();
        });

        JButton userButton = createFolderDialogButton("Mods / Saves");
        userButton.setToolTipText("Open UserData folder (Mods, Saves & etc.)");
        userButton.addActionListener(e -> {
            dialog.dispose();
            openUserDataFolder();
        });

        buttonPanel.add(dataButton);
        buttonPanel.add(userButton);

        mainPanel.add(buttonPanel);

        dialog.setContentPane(mainPanel);
        dialog.setVisible(true);
    }

    private void openDataFolder()
    {
        try {
            Path gameFolder;

            if (selectedVersion != null)
            {
                gameFolder = versionManager.getVersionDirectory(
                        selectedVersion.getPatchNumber(),
                        selectedVersion.getBranch()
                );
            }
            else
            {
                gameFolder = Environment.getDefaultAppDir()
                        .resolve("release").resolve("package").resolve("game");

                Path latestFolder = gameFolder.resolve("latest");
                if (Files.exists(latestFolder)) {
                    gameFolder = latestFolder;
                }
            }

            Files.createDirectories(gameFolder);
            openFolderInSystem(gameFolder);

        } catch (Exception e)
        {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Failed to open folder: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openUserDataFolder()
    {
        try {
            Path userDataFolder = Environment.getDefaultAppDir().resolve("UserData");
            Files.createDirectories(userDataFolder);

            Files.createDirectories(userDataFolder.resolve("Mods"));
            Files.createDirectories(userDataFolder.resolve("Saves"));

            openFolderInSystem(userDataFolder);

        } catch (Exception e)
        {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Failed to open UserData folder: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openFolderInSystem(Path folder) throws Exception
    {
        if (Desktop.isDesktopSupported())
        {
            Desktop.getDesktop().open(folder.toFile());
        }
        else
        {
            String os = Environment.getOS();
            ProcessBuilder pb;

            switch (os)
            {
                case "windows" -> pb = new ProcessBuilder("explorer", folder.toString());
                case "darwin" -> pb = new ProcessBuilder("open", folder.toString());
                case "linux" -> pb = new ProcessBuilder("xdg-open", folder.toString());
                default -> {
                    JOptionPane.showMessageDialog(this,
                            "Folder path: " + folder.toString(),
                            "Folder Location",
                            JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
            }

            pb.start();
        }

        System.out.println("Opened folder: " + folder);
    }

    private JButton createFolderDialogButton(String text)
    {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(60, 60, 70));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(350, 45));

        button.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(new Color(80, 80, 90));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(new Color(60, 60, 70));
            }
        });

        return button;
    }

    private void applyOnlineFix()
    {
        if (selectedVersion == null)
        {
            JOptionPane.showMessageDialog(this,
                    "Select game version first",
                    "No Version Selected",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int result = JOptionPane.showConfirmDialog(this,
                "This will patch the HytaleClient binary to fix online mode.\n" +
                        "A backup will be created automatically.\n\n" +
                        "Continue?",
                "Apply Online Fix",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        Path gameDir = versionManager.getVersionDirectory(
                selectedVersion.getPatchNumber(),
                selectedVersion.getBranch()
        );

        Path clientDir = gameDir.resolve("Client");
        Path clientPath = clientDir.resolve("HytaleClient");

        if (!Files.exists(clientPath))
        {
            JOptionPane.showMessageDialog(this,
                    "HytaleClient not found in:\n" + clientPath,
                    "Client Not Found",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (fixOnlineButton != null) {
            fixOnlineButton.setEnabled(false);
            fixOnlineButton.setText("<html><center>Patching...</center></html>");
        }

        SwingWorker<OnlineFix.PatchResult, String> worker = new SwingWorker<>()
        {
            @Override
            protected OnlineFix.PatchResult doInBackground() throws Exception
            {
                return OnlineFix.applyPatch(clientPath, this::publish);
            }

            @Override
            protected void process(java.util.List<String> chunks)
            {
                if (!chunks.isEmpty()) {
                    String message = chunks.get(chunks.size() - 1);
                    statusLabel.setText(message);
                }
            }

            @Override
            protected void done()
            {
                try
                {
                    OnlineFix.PatchResult patchResult = get();

                    if (patchResult.success)
                    {
                        statusLabel.setText("Online fix applied successfully");

                        JOptionPane.showMessageDialog(
                                LauncherPanel.this,
                                "Online fix applied successfully!\n\n" +
                                        "Backup saved to:\n" +
                                        (patchResult.backupFile != null ? patchResult.backupFile.toString() : "N/A"),
                                "Success",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                    }
                    else
                    {
                        statusLabel.setText("Patch failed: " + patchResult.message);

                        JOptionPane.showMessageDialog(
                                LauncherPanel.this,
                                "Failed to apply patch:\n" + patchResult.message,
                                "Patch Failed",
                                JOptionPane.ERROR_MESSAGE
                        );
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    statusLabel.setText("Error: " + e.getMessage());

                    JOptionPane.showMessageDialog(
                            LauncherPanel.this,
                            "Error applying patch:\n" + e.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
                finally
                {
                    if (fixOnlineButton != null)
                    {
                        fixOnlineButton.setEnabled(true);
                        fixOnlineButton.setText("<html><center>Fix<br>Online</center></html>");
                    }
                }
            }
        };

        worker.execute();
    }

    private JPanel createBottomControls()
    {
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftPanel.setOpaque(false);

        JButton versionButton = new JButton("Select version");
        versionButton.setFont(new Font("Arial", Font.BOLD, 14));
        versionButton.setForeground(Color.WHITE);
        versionButton.setBackground(new Color(60, 60, 70));
        versionButton.setPreferredSize(new Dimension(160, 90));
        versionButton.setFocusPainted(false);
        versionButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        versionButton.addActionListener(e -> openVersionSelector());

        folderButton = new JButton("Folder");
        folderButton.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        folderButton.setForeground(Color.WHITE);
        folderButton.setBackground(new Color(60, 60, 70));
        folderButton.setPreferredSize(new Dimension(140, 90));
        folderButton.setFocusPainted(false);
        folderButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        folderButton.setToolTipText("Open game folder");
        folderButton.addActionListener(e -> openGameFolder());

        folderButton.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e) {
                folderButton.setBackground(new Color(80, 80, 90));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                folderButton.setBackground(new Color(60, 60, 70));
            }
        });

        playButton = new JButton("PLAY");
        playButton.setFont(new Font("Arial", Font.BOLD, 36));
        playButton.setForeground(Color.WHITE);
        playButton.setBackground(new Color(255, 168, 69, 180));
        playButton.setPreferredSize(new Dimension(300, 90));
        playButton.setFocusPainted(false);
        playButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        playButton.addActionListener(e -> handlePlay());

        JButton modsButton = new JButton("Mods");
        modsButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        modsButton.setForeground(Color.WHITE);
        modsButton.setBackground(new Color(80, 60, 120));
        modsButton.setPreferredSize(new Dimension(140, 90));
        modsButton.setFocusPainted(false);
        modsButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        modsButton.addActionListener(e -> openModManager());

        leftPanel.add(modsButton);
        leftPanel.add(versionButton);
        leftPanel.add(folderButton);

        if (Environment.getOS().equals("linux"))
        {
            fixOnlineButton = new JButton("<html><center>Fix<br>Online</center></html>");
            fixOnlineButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
            fixOnlineButton.setForeground(Color.WHITE);
            fixOnlineButton.setBackground(new Color(220, 38, 38));
            fixOnlineButton.setPreferredSize(new Dimension(90, 90));
            fixOnlineButton.setFocusPainted(false);
            fixOnlineButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            fixOnlineButton.setToolTipText("Apply online fix patch (Linux only)");
            fixOnlineButton.addActionListener(e -> applyOnlineFix());

            fixOnlineButton.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseEntered(MouseEvent e) {
                    fixOnlineButton.setBackground(new Color(239, 68, 68));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    fixOnlineButton.setBackground(new Color(220, 38, 38));
                }
            });

            leftPanel.add(fixOnlineButton);
        }

        leftPanel.add(playButton);

        bottom.add(leftPanel, BorderLayout.WEST);

        JPanel progressPanel = createProgressSection();
        bottom.add(progressPanel, BorderLayout.EAST);

        SwingWorker<GameVersion, Void> versionWorker = new SwingWorker<>()
        {
            @Override
            protected GameVersion doInBackground()
            {
                GameVersion savedVersion = versionManager.loadSelectedVersion();
                if (savedVersion != null)
                {
                    if (versionManager.isVersionInstalled(savedVersion.getPatchNumber(), savedVersion.getBranch())) {
                        return savedVersion;
                    }
                }

                List<GameVersion> versions = versionManager.loadCachedVersions("release");
                if (versions.isEmpty()) {
                    versions = versionManager.loadCachedVersions("pre-release");
                }

                return versions.isEmpty() ? null : versions.get(0);
            }

            @Override
            protected void done()
            {
                try {
                    GameVersion version = get();
                    if (version != null && selectedVersion == null)
                    {
                        selectedVersion = version;
                        updateFolderButtonText();
                    }
                } catch (Exception e) {
                    System.err.println("Error loading version: " + e.getMessage());
                }
            }
        };
        versionWorker.execute();

        return bottom;
    }

    private void openModManager()
    {
        if (selectedVersion == null) {
            JOptionPane.showMessageDialog(this,
                    "Select game version",
                    "Game version is not selected",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        ModManagerDialog dialog = new ModManagerDialog(
                (Frame) SwingUtilities.getWindowAncestor(this)
        );
        dialog.setVisible(true);
    }

    private void updateFolderButtonText()
    {
        if (folderButton == null) return;

        if (selectedVersion != null)
        {
            String branchLabel = selectedVersion.isPreRelease() ? "Pre-" : "";
            folderButton.setText(String.format(
                    "%sPatch %d",
                    branchLabel,
                    selectedVersion.getPatchNumber()
            ));
            folderButton.setToolTipText("Open game folder - " + selectedVersion.getName());
        }
        else
        {
            folderButton.setText("Folder");
            folderButton.setToolTipText("Open game folder");
        }
    }

    private void openVersionSelector()
    {
        VersionSelectorDialog dialog = new VersionSelectorDialog(
                (Frame) SwingUtilities.getWindowAncestor(this),
                versionManager,
                selectedVersion
        );

        dialog.setVisible(true);

        GameVersion selected = dialog.getSelectedVersion();
        if (selected != null)
        {
            selectedVersion = selected;
            versionManager.saveSelectedVersion(selected);
            playButton.setText("PLAY");
            updateFolderButtonText();
            System.out.println("Selected version: " + selected.getName());
        }
    }

    @Deprecated
    private JButton createNavButton(String text) {
        return null;
    }

    private JPanel createProgressSection()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);

        progressPercentLabel = new JLabel("0%");
        progressPercentLabel.setFont(new Font("Arial", Font.BOLD | Font.ITALIC, 48));
        progressPercentLabel.setForeground(Color.WHITE);
        progressPercentLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        statusLabel = new JLabel("READY TO PLAY");
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        statusLabel.setForeground(Color.LIGHT_GRAY);
        statusLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        speedLabel = new JLabel("Ready");
        speedLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        speedLabel.setForeground(Color.GRAY);
        speedLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setForeground(new Color(255, 168, 69));
        progressBar.setBackground(new Color(30, 30, 30));
        progressBar.setPreferredSize(new Dimension(300, 10));
        progressBar.setAlignmentX(Component.RIGHT_ALIGNMENT);

        panel.add(progressPercentLabel);
        panel.add(Box.createVerticalStrut(5));
        panel.add(statusLabel);
        panel.add(Box.createVerticalStrut(5));
        panel.add(speedLabel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(progressBar);

        return panel;
    }

    private void handlePlay()
    {
        if (isDownloading) return;

        String playerName = usernameField.getText().trim();

        if (playerName.isEmpty()) {
            playerName = "";
            usernameField.setText(playerName);
            saveUsername();
        }

        if (playerName.length() > 16)
        {
            playerName = playerName.substring(0, 16);
            usernameField.setText(playerName);
            saveUsername();
            JOptionPane.showMessageDialog(this, "username must be < 16 symbols",
                    "WARNING!", JOptionPane.WARNING_MESSAGE);
        }

        if (selectedVersion == null)
        {
            List<GameVersion> versions = versionManager.loadCachedVersions("release");
            if (versions.isEmpty()) {
                versions = versionManager.loadCachedVersions("pre-release");
            }

            if (versions.isEmpty())
            {
                JOptionPane.showMessageDialog(this,
                        "Press 'Select version' for select game version",
                        "Version not selected",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            selectedVersion = versions.get(0);
        }

        isDownloading = true;
        playButton.setText("Loading...");
        playButton.setEnabled(false);

        String finalPlayerName = playerName;
        GameVersion versionToInstall = selectedVersion;

        SwingWorker<Path, ProgressUpdate> worker = new SwingWorker<>()
        {
            @Override
            protected Path doInBackground() throws Exception
            {
                try {
                    publish(new ProgressUpdate("jre", 0, "Checking JRE...", "", "", 0, 0));
                    JREDownloader.downloadJRE(this::publish);

                    publish(new ProgressUpdate("game", 0, "Installing " +
                            versionToInstall.getName() + "...", "", "", 0, 0));

                    Path gameDir = GameInstaller.installGameVersion(versionToInstall, this::publish);

                    versionManager.markVersionInstalled(versionToInstall);

                    publish(new ProgressUpdate("launch", 100, "Launching game...", "", "", 0, 0));
                    return gameDir;

                } catch (Exception e)
                {
                    e.printStackTrace();
                    throw e;
                }
            }

            @Override
            protected void process(java.util.List<ProgressUpdate> chunks)
            {
                for (ProgressUpdate update : chunks) {
                    updateProgress(update);
                }
            }

            @Override
            protected void done()
            {
                try {
                    Path gameDir = get();

                    launchGameFromDirectory(gameDir, finalPlayerName);

                    playButton.setText("PLAY");
                    playButton.setEnabled(true);
                    isDownloading = false;

                    progressBar.setValue(0);
                    progressPercentLabel.setText("0%");
                    statusLabel.setText("READY TO PLAY");
                    speedLabel.setText("Ready");

                } catch (Exception e)
                {
                    e.printStackTrace();
                    playButton.setText("ERROR - RETRY");
                    playButton.setEnabled(true);
                    isDownloading = false;

                    JOptionPane.showMessageDialog(LauncherPanel.this,
                            "Error while loading/installing:\n" + e.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
    }

    private void updateProgress(ProgressUpdate update)
    {
        currentProgress = update.getProgress();
        currentMessage = update.getMessage();

        progressBar.setValue((int) currentProgress);
        progressPercentLabel.setText(String.format("%.0f%%", currentProgress));
        statusLabel.setText(currentMessage.toUpperCase());

        if (!update.getSpeed().isEmpty())
        {
            speedLabel.setText(update.getSpeed());
        }
        else if (currentProgress >= 100)
        {
            speedLabel.setText("Complete");
        }
    }

    private String findClientExecutable(Path clientDir) throws IOException
    {
        if (!Files.exists(clientDir)) {
            throw new IOException("Client directory not found: " + clientDir);
        }

        String[] candidates = {"HytaleClient", "Hytale", "HytaleClient.bin.x86_64"};

        for (String name : candidates)
        {
            Path candidate = clientDir.resolve(name);

            if (Files.exists(candidate) && Files.isExecutable(candidate)) {
                return name;
            }
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(clientDir))
        {
            for (Path entry : stream)
            {
                if (Files.isRegularFile(entry) && Files.isExecutable(entry)) {
                    return entry.getFileName().toString();
                }
            }
        }

        throw new IOException("No executable found in Client directory");
    }

    private void launchGameFromDirectory(Path gameDir, String playerName) throws Exception
    {
        if (playerName == null || playerName.trim().isEmpty())
        {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(
                        this,
                        "Enter username!",
                        "Error while running",
                        JOptionPane.WARNING_MESSAGE
                );
            });
            return;
        }

        Path clientDir = gameDir.resolve("Client");
        String clientName = findClientExecutable(clientDir);
        Path clientPath = clientDir.resolve(clientName);

        if (!Files.exists(clientPath) || !Files.isExecutable(clientPath)) {
            throw new Exception("Game client executable not found or not executable: " + clientPath);
        }

        Path userDataDir = Environment.getDefaultAppDir().resolve("UserData");
        Files.createDirectories(userDataDir);

        if (!Environment.getOS().equals("windows")) {
            clientPath.toFile().setExecutable(true, false);
        }

        ProcessBuilder pb = new ProcessBuilder(
                clientPath.toAbsolutePath().toString(),
                "--app-dir", gameDir.toAbsolutePath().toString(),
                "--user-dir", userDataDir.toAbsolutePath().toString(),
                "--java-exec", JREDownloader.getJavaExec(),
                "--auth-mode", "offline",
                "--uuid", UUIDGen.generateUUID(playerName),
                "--name", playerName
                //,
//                "--identity-token", UUIDGen.generateIdentityToken(playerName),
//                "--session-token", UUIDGen.generateSessionToken()
        );

        pb.directory(gameDir.toFile());
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        System.out.println("Launching from: " + gameDir);
        System.out.println("Command: " + pb.command());

        Process process = pb.start();

        SwingUtilities.invokeLater(() -> {
            parent.setExtendedState(ICONIFIED);
        });
    }

    @Deprecated
    private void launchGame(String playerName) throws Exception {
        throw new Exception("This method is deprecated");
    }
}