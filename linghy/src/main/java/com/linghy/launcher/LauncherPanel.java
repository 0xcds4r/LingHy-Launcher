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
import java.lang.ref.SoftReference;
import java.net.URI;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    private static final Map<String, SoftReference<ImageIcon>> imageCache = new ConcurrentHashMap<>();

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

        JPanel centerPanel = new JPanel(new BorderLayout(0, 0));
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

            private JButton createZeroButton()
            {
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

        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(0, 0));
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
        panel.setBorder(BorderFactory.createEmptyBorder(32, 20, 40, 20));

        JLabel titleLabel = new JLabel("LingHy Launcher");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(new Color(245, 245, 255));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JLabel hintLabel = new JLabel("Enter username — max 16 characters");
        hintLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        hintLabel.setForeground(new Color(175, 180, 190));
        hintLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        hintLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));

        usernameField = new RoundTextField("");
        usernameField.setFont(new Font("Segoe UI", Font.BOLD, 18));
        usernameField.setForeground(Color.WHITE);
        usernameField.setBackground(new Color(32, 32, 42, 240));
        usernameField.setCaretColor(new Color(255, 168, 69));
        usernameField.setSelectionColor(new Color(255, 168, 69, 80));
        usernameField.setSelectedTextColor(new Color(20, 20, 25));

        usernameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 75), 1, true),
                BorderFactory.createEmptyBorder(14, 20, 14, 20)
        ));

        usernameField.setPreferredSize(new Dimension(360, 56));
        usernameField.setMaximumSize(new Dimension(420, 56));
        usernameField.setMinimumSize(new Dimension(320, 56));

        usernameField.addFocusListener(new FocusAdapter()
        {
            @Override public void focusGained(FocusEvent e)
            {
                usernameField.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(255, 168, 69, 240), 2, true),
                        BorderFactory.createEmptyBorder(13, 19, 13, 19)
                ));
            }

            @Override public void focusLost(FocusEvent e)
            {
                usernameField.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(60, 60, 75), 1, true),
                        BorderFactory.createEmptyBorder(14, 20, 14, 20)
                ));
                saveUsername();
                usernameField.setCaretPosition(usernameField.getText().length());
            }
        });

        usernameField.addMouseListener(new MouseAdapter()
        {
            @Override public void mouseEntered(MouseEvent e)
            {
                if (!usernameField.hasFocus())
                {
                    usernameField.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(255, 168, 69, 140), 1, true),
                            BorderFactory.createEmptyBorder(14, 20, 14, 20)
                    ));
                }
            }

            @Override public void mouseExited(MouseEvent e)
            {
                if (!usernameField.hasFocus())
                {
                    usernameField.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(60, 60, 75), 1, true),
                            BorderFactory.createEmptyBorder(14, 20, 14, 20)
                    ));
                }
            }
        });

        usernameField.addActionListener(e -> {
            saveUsername();
            usernameField.transferFocus();
        });

        JPanel footerPanel = new JPanel();
        footerPanel.setLayout(new BoxLayout(footerPanel, BoxLayout.Y_AXIS));
        footerPanel.setOpaque(false);
        footerPanel.setBorder(BorderFactory.createEmptyBorder(24, 0, 0, 0));

        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        infoPanel.setOpaque(false);

        JLabel versionLabel = new JLabel("v" + Environment.getVersion());
        versionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        versionLabel.setForeground(new Color(140, 145, 160));

        JLabel separator = new JLabel(" — by ");
        separator.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        separator.setForeground(new Color(140, 145, 160));

        JLabel authorLabel = new JLabel("0xcds4r");
        authorLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        authorLabel.setForeground(Color.YELLOW);
        authorLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        authorLabel.addMouseListener(new MouseAdapter()
        {
            @Override public void mouseClicked(MouseEvent e)
            {
                if (Desktop.isDesktopSupported())
                {
                    try {
                        Desktop.getDesktop().browse(new URI("https://github.com/0xcds4r?from=linghy"));
                    } catch (Exception ignored) {}
                }
            }
        });

        infoPanel.add(versionLabel);
        infoPanel.add(separator);
        infoPanel.add(authorLabel);

        Font playFont = new Font("Segoe UI", Font.BOLD, 16);
        Dimension btnSize = new Dimension(180, 45);
        Font btnFont = new Font("Segoe UI", Font.BOLD, 15);

        playButton = createStyledButton("PLAY", new Color(255, 168, 69), btnSize, playFont);
        playButton.setForeground(new Color(30, 30, 35));
        playButton.addActionListener(e -> handlePlay());

        JButton modsButton = createStyledButton("Mods", new Color(90, 70, 140), btnSize, btnFont);
        modsButton.addActionListener(e -> openModManager());

        JButton versionButton = createStyledButton("Versions", new Color(70, 70, 85), btnSize, btnFont);
        versionButton.addActionListener(e -> openVersionSelector());

        folderButton = createStyledButton("Folder", new Color(70, 70, 85), btnSize, btnFont);
        folderButton.addActionListener(e -> openGameFolder());

        fixOnlineButton = createStyledButton("Fix Online", new Color(200, 50, 50), btnSize, btnFont);
        fixOnlineButton.addActionListener(e -> applyOnlineFix());

        JButton bugReportButton = createStyledButton(
                "Bug report",
                new Color(220, 43, 0),
                new Dimension(140, 36),
                new Font("Segoe UI", Font.BOLD, 13)
        );
        bugReportButton.setToolTipText("Open GitHub Issues");
        bugReportButton.addActionListener(e -> {
            if (Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().browse(
                            new URI("https://github.com/0xcds4r/LingHy-Launcher/issues")
                    );
                } catch (Exception ignored) {}
            }
        });

        JPanel buttonsPanel = new JPanel(new GridLayout(0, 2, 14, 14));
        buttonsPanel.setOpaque(false);
        buttonsPanel.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));

        buttonsPanel.add(playButton);
        buttonsPanel.add(modsButton);
        buttonsPanel.add(versionButton);
        buttonsPanel.add(folderButton);
        buttonsPanel.add(fixOnlineButton);
        buttonsPanel.add(bugReportButton);

        footerPanel.add(infoPanel);
        footerPanel.add(buttonsPanel);

        panel.add(titleLabel);
        panel.add(hintLabel);
        panel.add(usernameField);
        panel.add(footerPanel);

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

    private List<NewsItem> fetchHytaleNews()
    {
        List<NewsItem> news = new ArrayList<>();

        try
        {
            Document doc = Jsoup.connect("https://hytale.com/news")
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                    .timeout(15000)
                    .get();

            Elements wrappers = doc.select("div.postWrapper");
            System.out.println("Found " + wrappers.size() + " news items on hytale.com");

            for (Element wrapper : wrappers)
            {
                if (news.size() >= 16) break;

                Element postLink = wrapper.selectFirst("a.post");
                if (postLink == null) continue;

                Element titleEl = wrapper.selectFirst("h4.post__details__heading");
                String title = titleEl != null ? titleEl.text().trim() : "No Title";

                Element bodyEl = wrapper.selectFirst("span.post__details__body");
                String desc = "";
                if (bodyEl != null)
                {
                    String rawDesc = bodyEl.text().trim();

                    if (rawDesc.startsWith(title)) {
                        rawDesc = rawDesc.substring(title.length()).trim();
                    }

                    if (rawDesc.length() > 180)
                    {
                        desc = rawDesc.substring(0, 180) + "...";
                    }
                    else
                    {
                        desc = rawDesc;
                    }
                }

                String date = "Unknown date";
                Element metaEl = wrapper.selectFirst("span.post__details__meta");

                if (metaEl != null)
                {
                    String metaText = metaEl.text();

                    Pattern p = Pattern.compile(
                            "(January|February|March|April|May|June|July|August|September|October|November|December)\\s+" +
                                    "\\d{1,2}(?:st|nd|rd|th)?\\s+\\d{4}"
                    );

                    Matcher m = p.matcher(metaText);
                    if (m.find()) {
                        date = m.group();
                    }
                }

                String url = postLink.absUrl("href");

                String imgUrl = "";
                Element img = wrapper.selectFirst("span.post__image__frame img");
                if (img != null)
                {
                    imgUrl = img.absUrl("src");

                    if (imgUrl.startsWith("./")) {
                        imgUrl = "https://hytale.com" + imgUrl.substring(1);
                    } else if (!imgUrl.startsWith("http")) {
                        imgUrl = "https://hytale.com" + imgUrl;
                    }
                }

                news.add(new NewsItem(title, date, desc, url, imgUrl));
            }
        } catch (Exception e)
        {
            System.err.println("Hytale News Loading Error: " + e.getMessage());
            e.printStackTrace();
        }

        if (news.isEmpty())
        {
            news.add(new NewsItem(
                    "News unavailable",
                    LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM dd yyyy")),
                    "Failed to load news from hytale.com. Check your internet connection or try again later.",
                    "https://hytale.com/news",
                    ""
            ));
        }

        return news;
    }

    private void loadNewsImageAsync(String urlStr, JLabel targetLabel, JPanel container)
    {
        new SwingWorker<ImageIcon, Void>() {
            @Override
            protected ImageIcon doInBackground() throws Exception {
                SoftReference<ImageIcon> cached = imageCache.get(urlStr);
                if (cached != null && cached.get() != null) {
                    return cached.get();
                }

                URL url = new URL(urlStr);
                BufferedImage img = ImageIO.read(url);
                if (img == null) return null;

                BufferedImage scaled = scaleAndCrop(img, 168, 94);
                ImageIcon icon = new ImageIcon(scaled);
                imageCache.put(urlStr, new SoftReference<>(icon));
                return icon;
            }

            @Override
            protected void done() {
                try {
                    ImageIcon icon = get();
                    if (icon != null) {
                        targetLabel.setIcon(icon);
                        targetLabel.setText("");
                    } else {
                        targetLabel.setText("Failed");
                    }
                } catch (Exception e) {
                    targetLabel.setText("Error");
                }
                container.revalidate();
                container.repaint();
            }
        }.execute();
    }

    private BufferedImage scaleAndCrop(BufferedImage src, int tw, int th) {
        double ratio = (double) src.getWidth() / src.getHeight();
        double tRatio = (double) tw / th;

        int nw, nh;
        if (ratio > tRatio) {
            nh = th;
            nw = (int)(th * ratio);
        } else {
            nw = tw;
            nh = (int)(tw / ratio);
        }

        Image scaled = src.getScaledInstance(nw, nh, Image.SCALE_SMOOTH);
        BufferedImage result = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(scaled, (tw - nw)/2, (th - nh)/2, null);
        g.dispose();
        return result;
    }

    private JPanel createNewsCard(NewsItem item)
    {
        var card = new JPanel(new GridBagLayout())
        {
            private Color bgColor = new Color(22, 22, 28, 200);

            {
                setOpaque(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }

            public void setHoverBackground(boolean hover)
            {
                bgColor = hover
                        ? new Color(32, 32, 42, 250)
                        : new Color(22, 22, 28, 200);
                repaint();
            }

            @Override
            protected void paintComponent(Graphics g)
            {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(bgColor);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };

        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 0, 0, 0), 0),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)
        ));

        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        MouseAdapter hover = new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                card.setHoverBackground(true);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                card.setHoverBackground(false);
            }

            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (Desktop.isDesktopSupported() && !item.url.isEmpty())
                {
                    try
                    {
                        Desktop.getDesktop().browse(new URI(item.url));
                    }
                    catch (Exception ex)
                    {
                        System.err.println("Failed to open news URL: " + ex.getMessage());
                    }
                }
            }
        };

        card.addMouseListener(hover);

        JPanel imgContainer = new JPanel(new BorderLayout());
        imgContainer.setPreferredSize(new Dimension(168, 94));
        imgContainer.setMinimumSize(new Dimension(168, 94));
        imgContainer.setBackground(new Color(15, 15, 22));
        imgContainer.setBorder(BorderFactory.createLineBorder(new Color(50, 50, 60), 1));

        JLabel imgLabel = new JLabel("Loading...", SwingConstants.CENTER);
        imgLabel.setForeground(new Color(100, 100, 110));
        imgContainer.add(imgLabel, BorderLayout.CENTER);

        if (!item.imageUrl.isEmpty())
        {
            loadNewsImageAsync(item.imageUrl, imgLabel, imgContainer);
        }
        else
        {
            imgLabel.setText("No image");
        }

        imgContainer.addMouseListener(hover);

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);
        textPanel.setBorder(BorderFactory.createEmptyBorder(4, 16, 4, 8));

        JLabel title = new JLabel(item.title);
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(new Color(255, 178, 89));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel meta = new JLabel(item.date + " • Hytale Team");
        meta.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        meta.setForeground(new Color(140, 140, 155));
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea desc = new JTextArea(item.description);
        desc.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        desc.setForeground(new Color(210, 210, 225));
        desc.setLineWrap(true);
        desc.setWrapStyleWord(true);
        desc.setEditable(false);
        desc.setFocusable(false);
        desc.setOpaque(false);
        desc.setAlignmentX(Component.LEFT_ALIGNMENT);

        desc.setMaximumSize(new Dimension(480, Integer.MAX_VALUE));
        desc.setPreferredSize(null);
        desc.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 8));

        textPanel.add(title);
        textPanel.add(Box.createVerticalStrut(4));
        textPanel.add(meta);
        textPanel.add(Box.createVerticalStrut(10));
        textPanel.add(desc);

        textPanel.addMouseListener(hover);
        title.addMouseListener(hover);
        meta.addMouseListener(hover);
        desc.addMouseListener(hover);

        GridBagConstraints gbc = new GridBagConstraints();

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridheight = 2;
        gbc.insets = new Insets(0, 0, 0, 16);
        card.add(imgContainer, gbc);

        gbc.gridx = 1;
        gbc.gridheight = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        card.add(textPanel, gbc);

        return card;
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

        JLabel titleLabel = new JLabel("Hytale News");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(new Color(235, 235, 245));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));
        panel.add(titleLabel);

        JLabel loading = new JLabel("Loading news...", SwingConstants.CENTER);
        loading.setFont(new Font("Segoe UI", Font.ITALIC, 14));
        loading.setForeground(new Color(140, 140, 155));
        panel.add(loading);

        new SwingWorker<List<NewsItem>, Void>()
        {
            @Override protected List<NewsItem> doInBackground() {
                return fetchHytaleNews();
            }

            @Override protected void done()
            {
                try {
                    List<NewsItem> items = get(8, TimeUnit.SECONDS);
                    panel.removeAll();
                    panel.add(titleLabel);

                    if (items.isEmpty() || items.get(0).title.equals("News unavailable"))
                    {
                        JLabel err = new JLabel("Could not load news • " + LocalDate.now());
                        err.setForeground(new Color(220, 80, 80));
                        panel.add(err);
                    }
                    else
                    {
                        for (int i = 0; i < items.size(); i++)
                        {
                            panel.add(createNewsCard(items.get(i)));

                            if (i < items.size() - 1) {
                                panel.add(Box.createVerticalStrut(15));
                            }
                        }
                    }

                    panel.revalidate();
                    panel.repaint();
                } catch (Exception e)
                {
                    panel.removeAll();
                    JLabel err = new JLabel("Failed to load news • Try again later");
                    err.setForeground(new Color(220, 80, 80));
                    panel.add(err);
                    panel.revalidate();
                    panel.repaint();
                }
            }
        }.execute();

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
        String clientName = Environment.getOS().equals("windows")
                ? "HytaleClient.exe" : "HytaleClient";
        Path clientPath = clientDir.resolve(clientName);

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

        String os = Environment.getOS();

        SwingWorker<Object, String> worker = new SwingWorker<>()
        {
            @Override
            protected Object doInBackground() throws Exception
            {
                if (os.equals("windows"))
                {
                    return com.linghy.patches.OnlineFixWin.applyPatch(clientPath, this::publish);
                }
                else
                {
                    return OnlineFix.applyPatch(clientPath, this::publish);
                }
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
                    Object result = get();
                    boolean success;
                    String message;
                    Path backupFile;

                    if (result instanceof OnlineFix.PatchResult)
                    {
                        OnlineFix.PatchResult patchResult = (OnlineFix.PatchResult) result;
                        success = patchResult.success;
                        message = patchResult.message;
                        backupFile = patchResult.backupFile;
                    }
                    else if (result instanceof com.linghy.patches.OnlineFixWin.PatchResult)
                    {
                        com.linghy.patches.OnlineFixWin.PatchResult patchResult =
                                (com.linghy.patches.OnlineFixWin.PatchResult) result;
                        success = patchResult.success;
                        message = patchResult.message;
                        backupFile = patchResult.backupFile;
                    }
                    else
                    {
                        throw new Exception("Unknown patch result type");
                    }

                    if (success)
                    {
                        statusLabel.setText("Online fix applied successfully");

                        JOptionPane.showMessageDialog(
                                LauncherPanel.this,
                                "Online fix applied successfully!\n\n" +
                                        "Backup saved to:\n" +
                                        (backupFile != null ? backupFile.toString() : "N/A"),
                                "Success",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                    }
                    else
                    {
                        statusLabel.setText("Patch failed: " + message);

                        JOptionPane.showMessageDialog(
                                LauncherPanel.this,
                                "Failed to apply patch:\n" + message,
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
        JPanel bottom = new JPanel(new BorderLayout(20, 0));
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 32, 8, 32));

        JPanel progressPanel = createProgressSection();
        bottom.add(progressPanel, BorderLayout.SOUTH);

        SwingWorker<GameVersion, Void> versionWorker = new SwingWorker<>()
        {
            @Override protected GameVersion doInBackground() {
                GameVersion saved = versionManager.loadSelectedVersion();
                if (saved != null && versionManager.isVersionInstalled(saved.getPatchNumber(), saved.getBranch())) {
                    return saved;
                }

                List<GameVersion> versions = versionManager.loadCachedVersions("release");
                if (versions.isEmpty()) versions = versionManager.loadCachedVersions("pre-release");

                return versions.isEmpty() ? null : versions.get(0);
            }

            @Override protected void done()
            {
                try {
                    GameVersion v = get();
                    if (v != null && selectedVersion == null) {
                        selectedVersion = v;
                        updateFolderButtonText();
                    }
                } catch (Exception ex) {
                    System.err.println("Version load error: " + ex.getMessage());
                }
            }
        };
        versionWorker.execute();

        return bottom;
    }

    private JButton createStyledButton(String text, Color bgColor, Dimension size, Font font)
    {
        JButton btn = new JButton(text);
        btn.setFont(font);
        btn.setForeground(Color.WHITE);
        btn.setBackground(bgColor);
        btn.setPreferredSize(size);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(true);
        btn.setHorizontalAlignment(SwingConstants.CENTER);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setOpaque(true);

        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                btn.setBackground(brighter(bgColor, 25));
            }

            @Override public void mouseExited(MouseEvent e) {
                btn.setBackground(bgColor);
            }
        });

        return btn;
    }

    private Color brighter(Color c, int factor)
    {
        int r = Math.min(255, c.getRed()   + factor);
        int g = Math.min(255, c.getGreen() + factor);
        int b = Math.min(255, c.getBlue()  + factor);
        return new Color(r, g, b, c.getAlpha());
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

        String os = Environment.getOS();
        String[] candidates;

        if (os.equals("windows"))
        {
            candidates = new String[]{"HytaleClient.exe"};
        }
        else
        {
            candidates = new String[]{"HytaleClient", "Hytale", "HytaleClient.bin.x86_64"};
        }

        for (String name : candidates)
        {
            Path candidate = clientDir.resolve(name);

            if (Files.exists(candidate))
            {
                if (os.equals("windows") || Files.isExecutable(candidate)) {
                    System.out.println("Found game executable: " + name);
                    return name;
                }
            }
        }

        System.out.println("Known executables not found, scanning directory...");

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(clientDir))
        {
            for (Path entry : stream)
            {
                if (!Files.isRegularFile(entry)) {
                    continue;
                }

                String fileName = entry.getFileName().toString();

                if (os.equals("windows"))
                {
                    if (fileName.toLowerCase().endsWith(".exe")) {
                        System.out.println("Found .exe file: " + fileName);
                        return fileName;
                    }
                }
                else
                {
                    if (Files.isExecutable(entry))
                    {
                        System.out.println("Found executable file: " + fileName);
                        return fileName;
                    }
                }
            }
        }

        throw new IOException("No executable found in Client directory: " + clientDir);
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