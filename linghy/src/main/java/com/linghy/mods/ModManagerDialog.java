package com.linghy.mods;

import com.linghy.launcher.SettingsDialog;
import com.linghy.mods.curseforge.CurseForgeAPI;
import com.linghy.version.GameVersion;
import com.linghy.version.VersionManager;
import com.linghy.env.Environment;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ModManagerDialog extends JDialog
{
    private final ModManager modManager;

    private JTabbedPane tabbedPane;
    private JList<ModManager.InstalledMod> installedList;
    private DefaultListModel<ModManager.InstalledMod> installedModel;

    private JButton deleteButton;
    private JButton checkUpdatesButton;

    public JLabel statusLabel;

    private JTextField cfSearchField;
    private DefaultListModel<CurseForgeAPI.Mod> cfModel;
    private JList<CurseForgeAPI.Mod> cfList;
    private JButton cfDownloadButton;
    private JButton cfInfoButton;
    private JComboBox<String> cfSortCombo;
    public JLabel cfStatusLabel;
    public JProgressBar cfProgressBar;
    private volatile boolean isSearching = false;
    private boolean hasLoadedPopularMods = false;
    private GameVersion selectedGameVersion;

    public ModManagerDialog(Frame parent, GameVersion selectedVersion)
    {
        super(parent, "Mod Manager", true);

        this.modManager = new ModManager();
        this.selectedGameVersion = selectedVersion;

        setSize(1100, 700);
        setLocationRelativeTo(parent);
        setUndecorated(true);

        initComponents();
        loadInstalledMods();

        if (isUserInRussia()) {
            SwingUtilities.invokeLater(this::showRussiaWarningDialog);
        }

        tabbedPane.addChangeListener(e ->
        {
            if (tabbedPane.getSelectedIndex() == 1 && !hasLoadedPopularMods) {
                loadPopularMods();
            }
        });
    }

    private void initComponents()
    {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(26, 26, 26));
        mainPanel.setBorder(BorderFactory.createLineBorder(new Color(255, 168, 69, 100), 2));

        JPanel headerPanel = createHeaderPanel();
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(new Color(26, 26, 26));
        tabbedPane.setForeground(Color.WHITE);
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JPanel installedPanel = createInstalledPanel();
        tabbedPane.addTab("Installed Mods", installedPanel);

        JPanel curseForgePanel = createCurseForgePanel();
        tabbedPane.addTab("Browse CurseForge", curseForgePanel);

        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        JPanel bottomPanel = createBottomPanel();
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        JPanel advancedPanel = createAdvancedPanel();
        tabbedPane.addTab("Translations", advancedPanel);

        setContentPane(mainPanel);
        addWindowDragListener();
    }

    private List<String> getPackedResources()
    {
        List<String> zips = new ArrayList<>();

        String[] possible = {
                "russian_translations.packed"
        };

        for (String name : possible) {
            if (getClass().getResource("/" + name) != null) {
                zips.add(name);
            }
        }

        return zips;
    }

    private void loadPackedResourcesFromClasspath(DefaultListModel<String> model)
    {
        model.clear();
        int foundCount = 0;

        for (String fileName : getPackedResources())
        {
            URL resource = getClass().getResource("/" + fileName);
            if (resource != null) {
                model.addElement(fileName);
                foundCount++;
            }
        }

        if (foundCount == 0) {
            model.addElement("EMPTY");
        }
    }

    private JPanel createAdvancedPanel()
    {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(new Color(18, 18, 18));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel topPanel = new JPanel(new BorderLayout(10, 0));
        topPanel.setOpaque(false);

        JTextField searchField = new JTextField();
        searchField.setBackground(new Color(26, 26, 32));
        searchField.setForeground(Color.WHITE);
        searchField.setCaretColor(new Color(255, 168, 69));
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 168, 69, 60), 1),
                new EmptyBorder(10, 15, 10, 15)
        ));
        searchField.setToolTipText("Filter resources by name");

        JButton installButton = createStyledButton("Install");
        installButton.setBackground(new Color(16, 185, 129, 180));
        installButton.setPreferredSize(new Dimension(130, 38));
        installButton.setEnabled(false);

        topPanel.add(searchField, BorderLayout.CENTER);
        topPanel.add(installButton, BorderLayout.EAST);

        panel.add(topPanel, BorderLayout.NORTH);

        DefaultListModel<String> resourceModel = new DefaultListModel<>();
        JList<String> resourceList = new JList<>(resourceModel);
        resourceList.setBackground(new Color(26, 26, 32));
        resourceList.setForeground(Color.WHITE);
        resourceList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        resourceList.setCellRenderer(new ResourceRenderer());
        resourceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resourceList.setFixedCellHeight(60);

        loadPackedResourcesFromClasspath(resourceModel);

        installButton.addActionListener(e -> {
            String selected = resourceList.getSelectedValue();
            if (selected != null && !"EMPTY".equals(selected)) {
                installPackedResource(selected);
            }
        });

        searchField.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyReleased(KeyEvent e) {
                String filter = searchField.getText().toLowerCase().trim();
                resourceModel.clear();

                List<String> allZips = getPackedResources();
                for (String zip : allZips) {
                    if (filter.isEmpty() || zip.toLowerCase().contains(filter)) {
                        resourceModel.addElement(zip);
                    }
                }

                if (resourceModel.isEmpty()) {
                    resourceModel.addElement("EMPTY");
                }

                statusLabel.setText("Showed " + resourceModel.size() + " from " + allZips.size() + " resources");
            }
        });

        resourceList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = resourceList.getSelectedValue();
                installButton.setEnabled(selected != null && !"EMPTY".equals(selected));
            }
        });

        resourceList.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selected = resourceList.getSelectedValue();
                    if (selected != null && !"EMPTY".equals(selected)) {
                        installPackedResource(selected);
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(resourceList);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(255, 168, 69, 60), 1));
        scrollPane.getViewport().setBackground(new Color(26, 26, 32));
        customizeScrollBar(scrollPane);

        panel.add(scrollPane, BorderLayout.CENTER);

        JLabel hintLabel = new JLabel(
                "Select the translation and click Install or double-click. Requires game version to be installed.",
                SwingConstants.CENTER
        );
        hintLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        hintLabel.setForeground(new Color(140, 140, 160));
        hintLabel.setBorder(new EmptyBorder(8, 0, 12, 0));

        panel.add(hintLabel, BorderLayout.SOUTH);

        return panel;
    }

    private void installPackedResource(String resourceName)
    {
        GameVersion selectedVersion = getSelectedGameVersion();

        if (selectedVersion == null)
        {
            JOptionPane.showMessageDialog(this,
                    "Please select a game version first.\n" +
                            "Close this dialog and select a version from the main launcher.",
                    "No Version Selected",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        VersionManager versionManager = new VersionManager();

        if (!versionManager.isVersionInstalled(selectedVersion.getPatchNumber(), selectedVersion.getBranch()))
        {
            JOptionPane.showMessageDialog(this,
                    "Selected version '" + selectedVersion.getName() + "' is not installed.\n" +
                            "Please install the game version first.",
                    "Version Not Installed",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Install '" + resourceName + "' to game version:\n" +
                        selectedVersion.getName() + "\n\n" +
                        "This will replace files in the Client directory.\n" +
                        "A backup will be created automatically.\n\n" +
                        "Continue?",
                "Confirm Installation",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        JDialog progressDialog = new JDialog(this, "Installing Resource", true);
        progressDialog.setSize(450, 180);
        progressDialog.setLocationRelativeTo(this);
        progressDialog.setUndecorated(true);

        JPanel progressPanel = new JPanel(new BorderLayout(10, 10));
        progressPanel.setBackground(new Color(18, 18, 18));
        progressPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 168, 69, 100), 2),
                new EmptyBorder(20, 20, 20, 20)
        ));

        JLabel progressLabel = new JLabel("Preparing installation...");
        progressLabel.setForeground(Color.WHITE);
        progressLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        progressLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setBackground(new Color(30, 30, 30));
        progressBar.setForeground(new Color(255, 168, 69));
        progressBar.setPreferredSize(new Dimension(400, 25));

        progressPanel.add(progressLabel, BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.CENTER);

        progressDialog.setContentPane(progressPanel);

        SwingWorker<String, String> worker = new SwingWorker<>()
        {
            @Override
            protected String doInBackground() throws Exception
            {
                publish("Locating game directory...");

                Path gameDir = versionManager.getVersionDirectory(
                        selectedVersion.getPatchNumber(),
                        selectedVersion.getBranch()
                );

                if (!Files.exists(gameDir))
                {
                    throw new IOException("Client directory not found: " + gameDir);
                }

                publish("Creating backup of Client directory...");
                String timestamp = String.valueOf(System.currentTimeMillis());
                Path backupDir = gameDir.resolve("Client.backup." + timestamp);

                copyDirectory(gameDir, backupDir);
                publish("Backup created: " + backupDir.getFileName());

                try
                {
                    publish("Reading packed resource from jar...");
                    InputStream resourceStream = getClass().getResourceAsStream("/" + resourceName);

                    if (resourceStream == null) {
                        throw new IOException("Resource not found in jar: " + resourceName);
                    }

                    Path tempZip = Files.createTempFile("packed_resource_", ".zip");

                    try
                    {
                        publish("Extracting resource...");
                        Files.copy(resourceStream, tempZip, StandardCopyOption.REPLACE_EXISTING);
                        resourceStream.close();

                        publish("Unpacking files...");

                        Path tempExtractDir = Files.createTempDirectory("packed_extract_");

                        try
                        {
                            extractZipFile(tempZip, tempExtractDir);

                            publish("Installing files to Client directory...");
                            copyDirectoryContents(tempExtractDir, gameDir);

                            publish("Cleaning up temporary files...");
                        }
                        finally
                        {
                            deleteDirectory(tempExtractDir);
                        }
                    }
                    finally
                    {
                        Files.deleteIfExists(tempZip);
                    }

                    return "Installation complete!\nBackup saved: " + backupDir.getFileName();
                }
                catch (Exception e)
                {
                    publish("Error occurred! Restoring from backup...");

                    try {
                        deleteDirectory(gameDir);
                        Files.move(backupDir, gameDir, StandardCopyOption.REPLACE_EXISTING);
                        publish("Backup restored successfully");
                    } catch (Exception restoreError) {
                        throw new IOException("Failed to restore backup: " + restoreError.getMessage(), e);
                    }

                    throw e;
                }
            }

            @Override
            protected void process(List<String> chunks)
            {
                if (!chunks.isEmpty()) {
                    String message = chunks.get(chunks.size() - 1);
                    progressLabel.setText(message);
                    statusLabel.setText(message);
                }
            }

            @Override
            protected void done()
            {
                progressDialog.dispose();

                try
                {
                    String result = get();

                    statusLabel.setText("Resource installed successfully");

                    JOptionPane.showMessageDialog(
                            ModManagerDialog.this,
                            "Successfully installed: " + resourceName + "\n\n" +
                                    "Game version: " + selectedVersion.getName() + "\n" +
                                    result,
                            "Installation Complete",
                            JOptionPane.INFORMATION_MESSAGE);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    statusLabel.setText("Installation failed: " + e.getMessage());

                    String errorMsg = e.getMessage();
                    Throwable cause = e.getCause();
                    if (cause != null && cause.getMessage() != null) {
                        errorMsg += "\n\nDetails: " + cause.getMessage();
                    }

                    JOptionPane.showMessageDialog(
                            ModManagerDialog.this,
                            "Failed to install resource:\n\n" + errorMsg,
                            "Installation Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
    }

    private void extractZipFile(Path zipFile, Path destDir) throws IOException
    {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile.toFile()))
        {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();

            while (entries.hasMoreElements())
            {
                java.util.zip.ZipEntry entry = entries.nextElement();
                Path filePath = destDir.resolve(entry.getName()).normalize();

                if (!filePath.startsWith(destDir)) {
                    throw new IOException("Invalid zip entry (path traversal attempt): " + entry.getName());
                }

                if (entry.isDirectory())
                {
                    Files.createDirectories(filePath);
                }
                else
                {
                    Files.createDirectories(filePath.getParent());

                    try (InputStream in = zip.getInputStream(entry);
                         OutputStream out = Files.newOutputStream(filePath,
                                 StandardOpenOption.CREATE,
                                 StandardOpenOption.TRUNCATE_EXISTING))
                    {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = in.read(buffer)) > 0) {
                            out.write(buffer, 0, len);
                        }
                    }
                }
            }
        }
    }

    private GameVersion getSelectedGameVersion() {
        return selectedGameVersion;
    }

    private void copyDirectory(Path source, Path target) throws IOException
    {
        Files.walk(source).forEach(sourcePath ->
        {
            try {
                Path relative = source.relativize(sourcePath);
                String relativeStr = relative.toString();

                if (relativeStr.startsWith("Client.backup.")) {
                    return;
                }

                Path targetPath = target.resolve(relative);

                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(sourcePath, targetPath,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy: " + sourcePath, e);
            }
        });
    }

    private void copyDirectoryContents(Path source, Path target) throws IOException
    {
        Files.walk(source).forEach(sourcePath ->
        {
            try
            {
                Path relativePath = source.relativize(sourcePath);

                if (relativePath.toString().isEmpty()) {
                    return;
                }

                Path targetPath = target.resolve(relativePath);

                if (Files.isDirectory(sourcePath))
                {
                    Files.createDirectories(targetPath);
                }
                else
                {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(sourcePath, targetPath,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
            catch (IOException e)
            {
                throw new RuntimeException("Failed to copy: " + sourcePath, e);
            }
        });
    }

    private void deleteDirectory(Path directory) throws IOException
    {
        if (!Files.exists(directory)) {
            return;
        }

        Files.walk(directory)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(path ->
                {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        System.err.println("Failed to delete: " + path + " - " + e.getMessage());
                    }
                });
    }

    private void importJarFiles(List<File> files)
    {
        List<String> imported = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        Path modsDir = modManager.getModsDir();

        for (File file : files)
        {
            if (!file.isFile() || !file.getName().toLowerCase().endsWith(".jar"))
            {
                skipped.add(file.getName());
                continue;
            }

            Path source = file.toPath();
            Path target = modsDir.resolve(file.getName());

            try
            {
                if (Files.exists(target))
                {
                    int choice = JOptionPane.showConfirmDialog(
                            this,
                            "Mod \"" + file.getName() + "\" already exists.\nReplace?",
                            "File exists",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE
                    );

                    if (choice != JOptionPane.YES_OPTION)
                    {
                        skipped.add(file.getName());
                        continue;
                    }
                }

                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                imported.add(file.getName());
            }
            catch (IOException e)
            {
                System.err.println("Failed to copy mod: " + file.getName() + " -> " + e.getMessage());
                failed.add(file.getName());
            }
        }

        if (!imported.isEmpty())
        {
            loadInstalledMods();
            statusLabel.setText("Imported " + imported.size() + " mod(s)");
        }

        if (!skipped.isEmpty() || !failed.isEmpty())
        {
            StringBuilder msg = new StringBuilder();
            if (!imported.isEmpty())   msg.append("Imported: ").append(imported.size()).append("\n");
            if (!skipped.isEmpty())    msg.append("Skipped: ").append(skipped.size()).append("\n");
            if (!failed.isEmpty())     msg.append("Failed: ").append(failed.size()).append("\n\n");

            if (!failed.isEmpty())
            {
                msg.append("Failed files:\n").append(String.join("\n", failed));
            }

            JOptionPane.showMessageDialog(this,
                    msg.toString().trim(),
                    "Import Results",
                    failed.isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        }
    }

    private void importJarFileViaChooser()
    {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select mod .jar file(s)");
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Hytale Mod (*.jar)", "jar"));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
        {
            File[] selectedFiles = chooser.getSelectedFiles();
            if (selectedFiles == null || selectedFiles.length == 0) return;

            importJarFiles(Arrays.asList(selectedFiles));
        }
    }

    private void showRussiaWarningDialog()
    {
        if (SettingsDialog.getSettingValue(SettingsDialog.KEY_RKN_WARNING, false)) {
            return;
        }

        String fullMessage = """
        ВНИМАНИЕ! Серьёзные проблемы с CurseForge в России прямо сейчас (февраль 2026)

        Роскомнадзор — это помойное цензурное ведомство — уже больше полугода (с июня 2025) целенаправленно душит Cloudflare, через который работает почти весь CurseForge (скачивание модов, обновления, Forge/CDN и т.д.).

        Что происходит на практике:
        • файлы модов скачиваются максимум 16 килобайт и виснут навсегда
        • таймауты, ошибки соединения каждые 5 секунд
        • CurseForge Launcher либо не запускается, либо не может ничего загрузить
        • обновления модпаков практически невозможны без танцев с бубном

        Это НЕ баг, НЕ проблема CurseForge и НЕ ваш интернет-провайдер.
        Это тупая, вредительская, бессмысленная цензура от РКН, которая ломает нормальную жизнь тысячам геймеров.

        Чтобы хоть как-то играть и собирать моды — БЕЗ ВАРИАНТОВ нужно обходить эту херню:

        1. Запускайте нормальный VPN (ProtonVPN free, Mullvad, Amnezia, Outline — что угодно, лишь бы не российский)
        2. Самый эффективный и лёгкий способ для CurseForge → программа **zapret** (winws.exe):
           - https://github.com/bol-van/zapret
           - добавьте в list-general следующие домены:
           - curseforge.com
           - forgecdn.net
           - mediafilez.forgecdn.net
           - cloudfront.net
           - запустите — и моды полетят на нормальной скорости
        3. GoodbyeDPI + список хостов тоже работает, но zapret сейчас надёжнее

        Без обхода — ничего качаться не будет. Точка.

        Россиянам приходится тратить время и нервы, чтобы обходить идиотские запреты ради того, чтобы просто поиграть в игры.
        Держитесь. И качайте моды в обход этой цензурной помойки.

        Удачи и крепких нервов!
        
        (( ЕСЛИ ВЫ ПОЛУЧИЛИ ЭТО УВЕДОМЛЕНИЕ ПО ОШИБКЕ, ВЕРОЯТНО НАША СИСТЕМА ПОСЧИТАЛО ВАШ РЕГИОН ЗА РОССИЙСКИЙ, ПРОСТО ОТКЛЮЧИТЕ УВЕДОМЛЕНИЕ ))
        """;

        String domainsToCopy = """
        curseforge.com\n
        forgecdn.net\n
        mediafilez.forgecdn.net\n
        cloudfront.net
        """;

        JCheckBox dontShowCheckBox = new JCheckBox("Больше не показывать это сообщение");
        dontShowCheckBox.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        dontShowCheckBox.setForeground(new Color(220, 220, 220));
        dontShowCheckBox.setBackground(new Color(30, 30, 35));
        dontShowCheckBox.setFocusPainted(false);

        JButton copyButton = new JButton("Скопировать домены для zapret");
        copyButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        copyButton.setForeground(Color.WHITE);
        copyButton.setBackground(new Color(16, 185, 129, 180));
        copyButton.setFocusPainted(false);
        copyButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        copyButton.addActionListener(e ->
        {
            StringSelection selection = new StringSelection(domainsToCopy);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);

            String originalText = copyButton.getText();
            copyButton.setText("Скопировано!");
            copyButton.setEnabled(false);

            new javax.swing.Timer(1800, ev -> {
                copyButton.setText(originalText);
                copyButton.setEnabled(true);
            }).start();
        });

        JTextArea textArea = new JTextArea(fullMessage);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        textArea.setBackground(new Color(26, 26, 32));
        textArea.setForeground(new Color(240, 240, 240));
        textArea.setBorder(new EmptyBorder(10, 12, 10, 12));

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(640, 360));
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 90)));

        JPanel bottomPanel = new JPanel(new BorderLayout(12, 0));
        bottomPanel.setOpaque(false);
        bottomPanel.add(dontShowCheckBox, BorderLayout.WEST);
        bottomPanel.add(copyButton, BorderLayout.EAST);

        JPanel mainPanel = new JPanel(new BorderLayout(0, 12));
        mainPanel.setBackground(new Color(26, 26, 32));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        JOptionPane.showOptionDialog(
                this,
                mainPanel,
                "Внимание!",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.ERROR_MESSAGE,
                null,
                new String[]{"Понял"},
                "Понял"
        );

        if (dontShowCheckBox.isSelected()) {
            SettingsDialog.setSettingValue(SettingsDialog.KEY_RKN_WARNING, true);
        }
    }

    private boolean isUserInRussia()
    {
        Locale locale = Locale.getDefault();
        String country = locale.getCountry().toUpperCase();
        if ("RU".equals(country) || "RU".equals(locale.getLanguage().toUpperCase())) {
            return true;
        }

        try {
            URL url = new URL("http://ip-api.com/json/?fields=countryCode,status");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder json = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        json.append(line);
                    }
                    String response = json.toString();
                    if (response.contains("\"status\":\"success\"") && response.contains("\"countryCode\":\"RU\"")) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {

        }

        return false;
    }

    private JPanel createCurseForgePanel()
    {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(new Color(18, 18, 18));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel searchPanel = new JPanel(new BorderLayout(10, 0));
        searchPanel.setOpaque(false);

        cfSearchField = new JTextField();
        cfSearchField.setBackground(new Color(26, 26, 32));
        cfSearchField.setForeground(Color.WHITE);
        cfSearchField.setCaretColor(new Color(255, 168, 69));
        cfSearchField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        cfSearchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 168, 69, 60), 1),
                new EmptyBorder(10, 15, 10, 15)
        ));
        cfSearchField.setToolTipText("Search mods on CurseForge (press Enter or click Search)");

        JButton searchButton = createStyledButton("Search");
        searchButton.setBackground(new Color(255, 168, 69, 180));
        searchButton.setPreferredSize(new Dimension(100, 42));
        searchButton.addActionListener(e -> performSearch());

        cfSearchField.addActionListener(e -> performSearch());

        searchPanel.add(cfSearchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);

        cfSearchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    cfSearchField.setText("");
                }
            }
        });

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        filterPanel.setOpaque(false);

        JLabel sortLabel = new JLabel("Sort by:");
        sortLabel.setForeground(new Color(160, 160, 170));
        sortLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        String[] sortOptions = {"Popularity", "Featured", "Last Updated", "Name", "Author", "Total Downloads"};

        cfSortCombo = new JComboBox<>(sortOptions);
        cfSortCombo.setBackground(new Color(26, 26, 32));
        cfSortCombo.setForeground(Color.WHITE);
        cfSortCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        cfSortCombo.setFocusable(false);
        cfSortCombo.setPreferredSize(new Dimension(150, 30));

        cfSortCombo.addActionListener(e ->
        {
            if (!isSearching)
            {
                String query = cfSearchField.getText().trim();

                if (query.isEmpty())
                {
                    hasLoadedPopularMods = false;
                    loadPopularMods();
                }
                else if (!cfModel.isEmpty())
                {
                    performSearch();
                }
            }
        });

        filterPanel.add(sortLabel);
        filterPanel.add(cfSortCombo);

        JPanel topPanel = new JPanel(new BorderLayout(0, 10));
        topPanel.setOpaque(false);
        topPanel.add(searchPanel, BorderLayout.NORTH);
        topPanel.add(filterPanel, BorderLayout.CENTER);

        panel.add(topPanel, BorderLayout.NORTH);

        cfModel = new DefaultListModel<>();
        cfList = new JList<>(cfModel);
        cfList.setBackground(new Color(26, 26, 32));
        cfList.setForeground(Color.WHITE);
        cfList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        cfList.setCellRenderer(new CurseForgeModRenderer());
        cfList.setFixedCellHeight(100);

        cfList.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    showModDetails();
                }
            }
        });

        cfList.addListSelectionListener(e ->
        {
            if (!e.getValueIsAdjusting()) {
                updateCFButtonStates();
            }
        });

        JScrollPane scrollPane = new JScrollPane(cfList);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(255, 168, 69, 60), 1));
        scrollPane.getViewport().setBackground(new Color(26, 26, 32));
        customizeScrollBar(scrollPane);

        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel cfBottomPanel = new JPanel(new BorderLayout(10, 0));
        cfBottomPanel.setOpaque(false);
        cfBottomPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

        cfStatusLabel = new JLabel("Popular mods will load automatically");
        cfStatusLabel.setForeground(new Color(160, 160, 170));
        cfStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        cfProgressBar = new JProgressBar();
        cfProgressBar.setBackground(new Color(30, 30, 30));
        cfProgressBar.setForeground(new Color(255, 168, 69));
        cfProgressBar.setStringPainted(true);
        cfProgressBar.setVisible(false);
        cfProgressBar.setPreferredSize(new Dimension(300, 25));
        cfProgressBar.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JPanel cfStatusPanel = new JPanel();
        cfStatusPanel.setLayout(new BoxLayout(cfStatusPanel, BoxLayout.Y_AXIS));
        cfStatusPanel.setOpaque(false);
        cfStatusPanel.add(cfStatusLabel);
        cfStatusPanel.add(Box.createVerticalStrut(5));
        cfStatusPanel.add(cfProgressBar);

        JPanel cfButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        cfButtonPanel.setOpaque(false);

        cfInfoButton = createStyledButton("Details");
        cfInfoButton.setBackground(new Color(59, 130, 246, 180));
        cfInfoButton.setPreferredSize(new Dimension(100, 35));
        cfInfoButton.setEnabled(false);
        cfInfoButton.addActionListener(e -> showModDetails());

        cfDownloadButton = createStyledButton("Install");
        cfDownloadButton.setBackground(new Color(16, 185, 129, 180));
        cfDownloadButton.setPreferredSize(new Dimension(100, 35));
        cfDownloadButton.setEnabled(false);
        cfDownloadButton.addActionListener(e -> installSelectedMod());

        cfButtonPanel.add(cfInfoButton);
        cfButtonPanel.add(cfDownloadButton);

        cfBottomPanel.add(cfStatusPanel, BorderLayout.WEST);
        cfBottomPanel.add(cfButtonPanel, BorderLayout.EAST);

        panel.add(cfBottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void performSearch()
    {
        String query = cfSearchField.getText().trim();

        if (isSearching) {
            return;
        }

        isSearching = true;
        cfModel.clear();
        cfDownloadButton.setEnabled(false);
        cfInfoButton.setEnabled(false);

        String displayQuery = query.isEmpty() ? "popular mods" : query;
        cfStatusLabel.setText("Searching for: " + displayQuery + "...");
        cfSearchField.setEnabled(false);
        cfSortCombo.setEnabled(false);

        CurseForgeAPI.SortField sortField = getSortFieldFromCombo();
        CurseForgeAPI.SortOrder sortOrder = CurseForgeAPI.SortOrder.DESCENDING;

        CompletableFuture.supplyAsync(() ->
        {
            try
            {
                return CurseForgeAPI.searchMods(query, sortField, sortOrder);
            }
            catch (Exception ex)
            {
                SwingUtilities.invokeLater(() -> {
                    cfStatusLabel.setText("Search failed: " + ex.getMessage());
                });

                return new ArrayList<CurseForgeAPI.Mod>();
            }
        }).thenAccept(mods -> SwingUtilities.invokeLater(() ->
        {
            if (!mods.isEmpty())
            {
                for (CurseForgeAPI.Mod mod : mods) {
                    cfModel.addElement(mod);
                }

                String sortDesc = (String) cfSortCombo.getSelectedItem();

                if (query.isEmpty())
                {
                    cfStatusLabel.setText("Showing " + mods.size() + " popular mods (sorted by " + sortDesc + ")");
                }
                else
                {
                    cfStatusLabel.setText("Found " + mods.size() + " mods (sorted by " + sortDesc + ")");
                }
            }
            else
            {
                cfStatusLabel.setText("No mods found for: " + displayQuery);
            }

            isSearching = false;

            cfSearchField.setEnabled(true);
            cfSortCombo.setEnabled(true);
        }));
    }

    private CurseForgeAPI.SortField getSortFieldFromCombo()
    {
        String sortBy = (String) cfSortCombo.getSelectedItem();

        if (sortBy == null) {
            return CurseForgeAPI.SortField.POPULARITY;
        }

        return switch (sortBy)
        {
            case "Name" -> CurseForgeAPI.SortField.NAME;
            case "Last Updated" -> CurseForgeAPI.SortField.LAST_UPDATED;
            case "Total Downloads" -> CurseForgeAPI.SortField.TOTAL_DOWNLOADS;
            case "Author" -> CurseForgeAPI.SortField.AUTHOR;
            case "Featured" -> CurseForgeAPI.SortField.FEATURED;
            default -> CurseForgeAPI.SortField.POPULARITY;
        };
    }

    private void loadPopularMods()
    {
        if (isSearching || hasLoadedPopularMods) {
            return;
        }

        hasLoadedPopularMods = true;
        isSearching = true;
        cfModel.clear();
        cfDownloadButton.setEnabled(false);
        cfInfoButton.setEnabled(false);

        cfStatusLabel.setText("Loading popular mods...");

        cfSearchField.setEnabled(false);
        cfSortCombo.setEnabled(false);
        cfProgressBar.setVisible(true);
        cfProgressBar.setIndeterminate(true);

        CurseForgeAPI.SortField sortField = getSortFieldFromCombo();
        CurseForgeAPI.SortOrder sortOrder = CurseForgeAPI.SortOrder.DESCENDING;

        CompletableFuture.supplyAsync(() ->
        {
            try
            {
                return CurseForgeAPI.searchMods("", sortField, sortOrder);
            }
            catch (Exception ex)
            {
                SwingUtilities.invokeLater(() ->
                {
                    cfStatusLabel.setText("Failed to load popular mods: " + ex.getMessage());
                    cfProgressBar.setVisible(false);
                });

                return new ArrayList<CurseForgeAPI.Mod>();
            }
        }).thenAccept(mods -> SwingUtilities.invokeLater(() ->
        {
            cfProgressBar.setVisible(false);

            if (!mods.isEmpty())
            {
                for (CurseForgeAPI.Mod mod : mods) {
                    cfModel.addElement(mod);
                }

                String sortDesc = (String) cfSortCombo.getSelectedItem();
                cfStatusLabel.setText("Showing " + mods.size() + " popular mods (sorted by " + sortDesc + ")");
            }
            else
            {
                cfStatusLabel.setText("Failed to load popular mods");
            }

            isSearching = false;
            cfSearchField.setEnabled(true);
            cfSortCombo.setEnabled(true);
        }));
    }

    private void sortMods(List<CurseForgeAPI.Mod> mods)
    {
        String sortBy = (String) cfSortCombo.getSelectedItem();
        if (sortBy == null) return;

        switch (sortBy)
        {
            case "Name":
                mods.sort(Comparator.comparing(m -> m.name.toLowerCase()));
                break;
            case "Popularity":
                // def
                break;
            case "Last Updated":
                // TODO: need additional API data
                break;
            case "Total Downloads":
                // TODO: need additional API data
                break;
        }
    }

    private void installSelectedMod()
    {
        CurseForgeAPI.Mod selected = cfList.getSelectedValue();
        if (selected == null) {
            return;
        }

        List<ModManager.InstalledMod> installed = modManager.getInstalledMods();

        boolean alreadyInstalled = installed.stream()
                .anyMatch(m -> m.curseForgeId == selected.id);

        if (alreadyInstalled)
        {
            int result = JOptionPane.showConfirmDialog(this,
                    "Mod \"" + selected.name + "\" is already installed.\nReinstall?",
                    "Already Installed",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);

            if (result != JOptionPane.YES_OPTION) return;
        }

        cfDownloadButton.setEnabled(false);
        cfInfoButton.setEnabled(false);
        cfStatusLabel.setText("Fetching mod files...");
        cfProgressBar.setVisible(true);
        cfProgressBar.setIndeterminate(true);
        cfProgressBar.setString("Loading...");

        SwingWorker<CurseForgeAPI.ModFile, Void> worker = new SwingWorker<>()
        {
            @Override
            protected CurseForgeAPI.ModFile doInBackground() throws Exception {
                return CurseForgeAPI.getLatestFile(selected.id);
            }

            @Override
            protected void done()
            {
                try
                {
                    CurseForgeAPI.ModFile file = get();

                    if (file == null)
                    {
                        cfProgressBar.setVisible(false);
                        cfStatusLabel.setText("No files available");
                        cfDownloadButton.setEnabled(true);
                        cfInfoButton.setEnabled(true);
                        return;
                    }

                    startDownload(selected, file);
                }
                catch (Exception ex)
                {
                    cfProgressBar.setVisible(false);
                    cfStatusLabel.setText("Error: " + ex.getMessage());
                    cfDownloadButton.setEnabled(true);
                    cfInfoButton.setEnabled(true);
                }
            }
        };

        worker.execute();
    }

    private void startDownload(CurseForgeAPI.Mod mod, CurseForgeAPI.ModFile file)
    {
        cfProgressBar.setIndeterminate(false);
        cfProgressBar.setValue(0);
        cfProgressBar.setString("0%");
        cfProgressBar.setPreferredSize(new Dimension(300, 25));
        cfStatusLabel.setText("Downloading " + file.fileName + "...");

        SwingWorker<Void, Double> downloadWorker = new SwingWorker<>()
        {
            private volatile String statusMessage = "";

            @Override
            protected Void doInBackground() throws Exception {
                String iconUrl = (mod.logo != null && mod.logo.thumbnailUrl != null)
                        ? mod.logo.thumbnailUrl : null;

                modManager.downloadAndInstall(file, mod.id, iconUrl, (percent, message) -> {
                    statusMessage = message;
                    publish(percent);
                });
                return null;
            }

            @Override
            protected void process(List<Double> chunks)
            {
                if (!chunks.isEmpty()) {
                    double latest = chunks.get(chunks.size() - 1);
                    cfProgressBar.setValue((int) latest);
                    cfProgressBar.setString(String.format("%.0f%% - %s", latest, statusMessage));
                }
            }

            @Override
            protected void done()
            {
                try {
                    get();
                    cfProgressBar.setVisible(false);
                    cfDownloadButton.setEnabled(true);
                    cfInfoButton.setEnabled(true);
                    cfStatusLabel.setText("Installed: " + mod.name);

                    loadInstalledMods();

                    if (tabbedPane.getSelectedIndex() != 0)
                    {
                        int result = JOptionPane.showOptionDialog(
                                ModManagerDialog.this,
                                "Successfully installed:\n" + mod.name + "\n\nView installed mods?",
                                "Success",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.INFORMATION_MESSAGE,
                                null,
                                new String[]{"View", "Stay"},
                                "View");

                        if (result == JOptionPane.YES_OPTION) {
                            tabbedPane.setSelectedIndex(0);
                        }
                    }
                    else
                    {
                        JOptionPane.showMessageDialog(
                                ModManagerDialog.this,
                                "Successfully installed:\n" + mod.name,
                                "Success",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                }
                catch (Exception ex)
                {
                    cfProgressBar.setVisible(false);
                    cfDownloadButton.setEnabled(true);
                    cfInfoButton.setEnabled(true);

                    cfStatusLabel.setText("Failed: " + ex.getMessage());

                    JOptionPane.showMessageDialog(
                            ModManagerDialog.this,
                            "Installation failed:\n" + ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        downloadWorker.execute();
    }

    private void showModDetails()
    {
        CurseForgeAPI.Mod selected = cfList.getSelectedValue();

        if (selected == null) {
            return;
        }

        JDialog detailsDialog = new JDialog(this, "Mod Details - " + selected.name, true);
        detailsDialog.setSize(700, 500);
        detailsDialog.setLocationRelativeTo(this);
        detailsDialog.setUndecorated(true);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(18, 18, 18));
        mainPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 168, 69, 100), 2),
                new EmptyBorder(20, 20, 20, 20)
        ));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(0, 0, 15, 0));

        JLabel titleLabel = new JLabel(selected.name);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titleLabel.setForeground(new Color(255, 168, 69));

        JButton closeButton = createHeaderButton("×");
        closeButton.addActionListener(e -> detailsDialog.dispose());

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(closeButton, BorderLayout.EAST);

        mainPanel.add(headerPanel, BorderLayout.NORTH);

        JPanel detailsPanel = new JPanel();
        detailsPanel.setLayout(new BoxLayout(detailsPanel, BoxLayout.Y_AXIS));
        detailsPanel.setOpaque(false);

        addDetailRow(detailsPanel, "ID:", String.valueOf(selected.id));
        addDetailRow(detailsPanel, "Slug:", selected.slug);

        if (selected.authors != null && !selected.authors.isEmpty())
        {
            String authors = String.join(", ",
                    selected.authors.stream().map(a -> a.name).toArray(String[]::new));

            addDetailRow(detailsPanel, "Authors:", authors);
        }

        if (selected.summary != null && !selected.summary.isEmpty())
        {
            JPanel summaryPanel = new JPanel(new BorderLayout());
            summaryPanel.setOpaque(false);
            summaryPanel.setBorder(new EmptyBorder(10, 0, 10, 0));

            JLabel summaryLabel = new JLabel("Summary:");
            summaryLabel.setForeground(new Color(160, 160, 170));
            summaryLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

            JTextArea summaryText = new JTextArea(selected.summary);
            summaryText.setLineWrap(true);
            summaryText.setWrapStyleWord(true);
            summaryText.setEditable(false);
            summaryText.setBackground(new Color(26, 26, 32));
            summaryText.setForeground(Color.WHITE);
            summaryText.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            summaryText.setBorder(new EmptyBorder(10, 10, 10, 10));

            JScrollPane scrollPane = new JScrollPane(summaryText);
            scrollPane.setBorder(BorderFactory.createLineBorder(new Color(255, 168, 69, 60), 1));
            scrollPane.setPreferredSize(new Dimension(600, 150));

            summaryPanel.add(summaryLabel, BorderLayout.NORTH);
            summaryPanel.add(scrollPane, BorderLayout.CENTER);

            detailsPanel.add(summaryPanel);
        }

        if (selected.categories != null && !selected.categories.isEmpty())
        {
            String categories = String.join(", ",
                    selected.categories.stream().map(c -> c.name).toArray(String[]::new));

            addDetailRow(detailsPanel, "Categories:", categories);
        }

        JScrollPane scrollPane = new JScrollPane(detailsPanel);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        customizeScrollBar(scrollPane);

        mainPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.setBorder(new EmptyBorder(15, 0, 0, 0));

        JButton browserButton = createStyledButton("Open in Browser");
        browserButton.setBackground(new Color(59, 130, 246, 180));
        browserButton.setPreferredSize(new Dimension(150, 35));
        browserButton.setToolTipText("Open mod page on CurseForge");
        browserButton.addActionListener(e ->
        {
            String modUrl = String.format("https://www.curseforge.com/hytale/mods/%s", selected.slug);
            try
            {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                {
                    Desktop.getDesktop().browse(new java.net.URI(modUrl));
                }
                else
                {
                    String os = System.getProperty("os.name").toLowerCase();
                    ProcessBuilder pb;

                    if (os.contains("win")) {
                        pb = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", modUrl);
                    } else if (os.contains("mac")) {
                        pb = new ProcessBuilder("open", modUrl);
                    } else if (os.contains("nix") || os.contains("nux")) {
                        pb = new ProcessBuilder("xdg-open", modUrl);
                    } else {
                        throw new UnsupportedOperationException("Cannot open browser on this OS");
                    }

                    pb.start();
                }

                cfStatusLabel.setText("Opened mod page in browser");
            } catch (Exception ex)
            {
                System.err.println("Failed to open browser: " + ex.getMessage());

                try
                {
                    java.awt.datatransfer.StringSelection selection =
                            new java.awt.datatransfer.StringSelection(modUrl);
                    java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                            .setContents(selection, null);

                    JOptionPane.showMessageDialog(
                            detailsDialog,
                            "Could not open browser.\nURL copied to clipboard:\n" + modUrl,
                            "Browser Error",
                            JOptionPane.WARNING_MESSAGE
                    );
                } catch (Exception clipEx)
                {
                    JOptionPane.showMessageDialog(
                            detailsDialog,
                            "Could not open browser.\nMod URL:\n" + modUrl,
                            "Browser Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        });

        JButton installButton = createStyledButton("Install");
        installButton.setBackground(new Color(16, 185, 129, 180));
        installButton.addActionListener(e ->
        {
            detailsDialog.dispose();
            installSelectedMod();
        });

        JButton closeBtn = createStyledButton("Close");
        closeBtn.setBackground(new Color(60, 60, 70));
        closeBtn.addActionListener(e -> detailsDialog.dispose());

        buttonPanel.add(browserButton);
        buttonPanel.add(installButton);
        buttonPanel.add(closeBtn);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        detailsDialog.setContentPane(mainPanel);

        Point dragOffset = new Point();
        detailsDialog.addMouseListener(new MouseAdapter()
        {
            public void mousePressed(MouseEvent e)
            {
                dragOffset.x = e.getX();
                dragOffset.y = e.getY();
            }
        });

        detailsDialog.addMouseMotionListener(new MouseAdapter()
        {
            public void mouseDragged(MouseEvent e)
            {
                Point location = detailsDialog.getLocation();
                detailsDialog.setLocation(location.x + e.getX() - dragOffset.x,
                        location.y + e.getY() - dragOffset.y);
            }
        });

        detailsDialog.setVisible(true);
    }

    private void addDetailRow(JPanel parent, String label, String value)
    {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(5, 0, 5, 0));

        JLabel labelComp = new JLabel(label);
        labelComp.setForeground(new Color(160, 160, 170));
        labelComp.setFont(new Font("Segoe UI", Font.BOLD, 13));
        labelComp.setPreferredSize(new Dimension(100, 20));

        JLabel valueComp = new JLabel(value);
        valueComp.setForeground(Color.WHITE);
        valueComp.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        row.add(labelComp, BorderLayout.WEST);
        row.add(valueComp, BorderLayout.CENTER);

        parent.add(row);
    }

    private void updateCFButtonStates()
    {
        boolean hasSelection = !cfList.isSelectionEmpty();
        cfDownloadButton.setEnabled(hasSelection && !isSearching);
        cfInfoButton.setEnabled(hasSelection && !isSearching);
    }

    private JPanel createHeaderPanel()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(9, 9, 9));
        panel.setBorder(new EmptyBorder(15, 20, 15, 20));

        JLabel titleLabel = new JLabel("Mod Manager");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(new Color(255, 168, 69));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        controls.setOpaque(false);

        checkUpdatesButton = createHeaderButton("R");
        checkUpdatesButton.setToolTipText("Check for updates / Refresh");
        checkUpdatesButton.addActionListener(e ->
        {
            int selectedTab = tabbedPane.getSelectedIndex();

            // 0 = Installed, 1 = CurseForge, 2 = Translations
            if (selectedTab == 1) {
                refreshCurseForgeResults();
            } else if (selectedTab == 0) {
                checkForUpdates();
            }
        });

        JButton openFolderButton = createHeaderButton("M");
        openFolderButton.setToolTipText("Open mods folder");
        openFolderButton.addActionListener(e -> openModsFolder());

        JButton closeButton = createHeaderButton("×");
        closeButton.setFont(new Font("Arial", Font.PLAIN, 20));
        closeButton.addActionListener(e -> dispose());

        closeButton.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e) {
                closeButton.setForeground(new Color(239, 68, 68));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                closeButton.setForeground(Color.LIGHT_GRAY);
            }
        });

        controls.add(checkUpdatesButton);
        controls.add(openFolderButton);
        controls.add(closeButton);

        panel.add(titleLabel, BorderLayout.WEST);
        panel.add(controls, BorderLayout.EAST);

        return panel;
    }

    private void refreshCurseForgeResults()
    {
        if (isSearching) {
            statusLabel.setText("Search already in progress...");
            return;
        }

        String currentQuery = cfSearchField.getText().trim();

        if (currentQuery.isEmpty())
        {
            hasLoadedPopularMods = false;
            loadPopularMods();
        }
        else
        {
            performSearch();
        }
    }

    private JButton createHeaderButton(String text)
    {
        JButton btn = new JButton(text);
        btn.setForeground(Color.LIGHT_GRAY);
        btn.setBackground(new Color(0, 0, 0, 0));
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setFont(new Font("Arial", Font.BOLD, 16));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(40, 30));

        btn.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                if (!text.equals("×")) {
                    btn.setForeground(new Color(255, 168, 69));
                }
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                if (!text.equals("×")) {
                    btn.setForeground(Color.LIGHT_GRAY);
                }
            }
        });

        return btn;
    }

    private JPanel createInstalledPanel()
    {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(new Color(18, 18, 18));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel topPanel = new JPanel(new BorderLayout(10, 0));
        topPanel.setOpaque(false);

        JTextField installedSearch = new JTextField();
        installedSearch.setBackground(new Color(26, 26, 32));
        installedSearch.setForeground(Color.WHITE);
        installedSearch.setCaretColor(new Color(255, 168, 69));
        installedSearch.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        installedSearch.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 168, 69, 60), 1),
                new EmptyBorder(10, 15, 10, 15)
        ));
        installedSearch.setToolTipText("Filter installed mods");

        JButton importButton = createStyledButton("Import JAR");
        importButton.setBackground(new Color(59, 130, 246, 180));
        importButton.setPreferredSize(new Dimension(130, 38));
        importButton.setToolTipText("Import .jar mod file(s) from your computer");
        importButton.addActionListener(e -> importJarFileViaChooser());

        topPanel.add(installedSearch, BorderLayout.CENTER);
        topPanel.add(importButton, BorderLayout.EAST);

        panel.add(topPanel, BorderLayout.NORTH);

        installedModel = new DefaultListModel<>();
        installedList = new JList<>(installedModel);
        installedList.setBackground(new Color(26, 26, 32));
        installedList.setForeground(Color.WHITE);
        installedList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        installedList.setCellRenderer(new InstalledModRenderer());
        installedList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        installedList.setFixedCellHeight(70);

        ModDropTargetListener dropListener = new ModDropTargetListener();
        installedList.setDropTarget(new DropTarget(installedList, dropListener));

        installedList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateButtonStates();
            }
        });

        installedSearch.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                String filter = installedSearch.getText().toLowerCase().trim();
                installedModel.clear();

                List<ModManager.InstalledMod> allMods = modManager.getInstalledMods();
                for (ModManager.InstalledMod mod : allMods) {
                    if (filter.isEmpty() ||
                            mod.name.toLowerCase().contains(filter) ||
                            mod.author.toLowerCase().contains(filter)) {
                        installedModel.addElement(mod);
                    }
                }

                statusLabel.setText("Showing " + installedModel.size() + " of " + allMods.size() + " mods");
            }
        });

        JScrollPane scrollPane = new JScrollPane(installedList);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(255, 168, 69, 60), 1));
        scrollPane.getViewport().setBackground(new Color(26, 26, 32));
        customizeScrollBar(scrollPane);

        scrollPane.setDropTarget(new DropTarget(scrollPane, dropListener));

        panel.add(scrollPane, BorderLayout.CENTER);

        JLabel dragHintLabel = new JLabel(
                "Drag & drop .jar files here or use «Import JAR» button",
                SwingConstants.CENTER
        );
        dragHintLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        dragHintLabel.setForeground(new Color(140, 140, 160));
        dragHintLabel.setBorder(new EmptyBorder(8, 0, 12, 0));

        panel.add(dragHintLabel, BorderLayout.SOUTH);

        return panel;
    }

    private void customizeScrollBar(JScrollPane scrollPane)
    {
        scrollPane.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI()
        {
            @Override
            protected void configureScrollBarColors()
            {
                this.thumbColor = new Color(255, 168, 69, 150);
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

        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(12, 0));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
    }

    private JPanel createBottomPanel()
    {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(new Color(18, 18, 18));
        panel.setBorder(new EmptyBorder(15, 20, 15, 20));

        statusLabel = new JLabel("Ready");
        statusLabel.setForeground(new Color(160, 160, 170));
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);

        deleteButton = createStyledButton("Delete");
        deleteButton.setBackground(new Color(239, 68, 68, 160));
        deleteButton.addActionListener(e -> deleteSelectedMods());
        deleteButton.setEnabled(false);

        JButton closeButton = createStyledButton("Close");
        closeButton.setBackground(new Color(60, 60, 70));
        closeButton.addActionListener(e -> dispose());

        buttonPanel.add(deleteButton);
        buttonPanel.add(closeButton);

        panel.add(statusLabel, BorderLayout.WEST);
        panel.add(buttonPanel, BorderLayout.EAST);

        return panel;
    }

    private JButton createStyledButton(String text)
    {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(120, 38));

        button.addMouseListener(new MouseAdapter()
        {
            Color originalColor = button.getBackground();

            @Override
            public void mouseEntered(MouseEvent e)
            {
                if (button.isEnabled()) {
                    button.setBackground(originalColor.brighter());
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(originalColor);
            }
        });

        return button;
    }

    private void loadInstalledMods()
    {
        statusLabel.setText("Loading...");

        modManager.getInstalledModsAsync()
                .thenAccept(mods -> SwingUtilities.invokeLater(() ->
                {
                    installedModel.clear();

                    if (mods.isEmpty())
                    {
                        statusLabel.setText("No mods installed");
                    }
                    else
                    {
                        for (ModManager.InstalledMod mod : mods) {
                            installedModel.addElement(mod);
                        }

                        statusLabel.setText(mods.size() + " mod(s)");

                        installedList.revalidate();
                        installedList.repaint();

                        Timer repaintTimer = new Timer(300, e -> {
                            installedList.repaint();
                            ((Timer) e.getSource()).stop();
                        });

                        repaintTimer.setRepeats(false);
                        repaintTimer.start();
                    }
                }))
                .exceptionally(ex ->
                {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Error: " + ex.getMessage());
                    });
                    return null;
                });
    }

    private void deleteSelectedMods()
    {
        int[] selectedIndices = installedList.getSelectedIndices();

        if (selectedIndices.length == 0) {
            return;
        }

        int result = JOptionPane.showConfirmDialog(this,
                "Delete " + selectedIndices.length + " selected mod(s)?",
                "Confirm Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (result == JOptionPane.YES_OPTION)
        {
            List<String> failedDeletions = new ArrayList<>();
            List<String> successfulDeletions = new ArrayList<>();

            for (int index : selectedIndices)
            {
                ModManager.InstalledMod mod = installedModel.getElementAt(index);
                try
                {
                    modManager.uninstallMod(mod.id);
                    successfulDeletions.add(mod.name);
                }
                catch (Exception e)
                {
                    System.err.println("Failed to delete mod: " + e.getMessage());
                    failedDeletions.add(mod.name);
                }
            }

            loadInstalledMods();

            if (!failedDeletions.isEmpty())
            {
                JOptionPane.showMessageDialog(this,
                        "Successfully deleted: " + successfulDeletions.size() + "\n" +
                                "Failed: " + failedDeletions.size() + "\n\n" +
                                "Failed mods:\n" + String.join("\n", failedDeletions),
                        "Deletion Results",
                        JOptionPane.WARNING_MESSAGE);
            }
            else if (!successfulDeletions.isEmpty())
            {
                statusLabel.setText("Deleted " + successfulDeletions.size() + " mod(s)");
            }
        }
    }

    private void checkForUpdates()
    {
        statusLabel.setText("Checking for updates...");
        checkUpdatesButton.setEnabled(false);

        CompletableFuture.supplyAsync(() ->
        {
            List<ModManager.InstalledMod> mods = modManager.getInstalledMods();
            List<String> updates = new ArrayList<>();

            for (ModManager.InstalledMod mod : mods)
            {
                if (mod.curseForgeId > 0 && mod.fileId > 0)
                {
                    try
                    {
                        CurseForgeAPI.ModFile latest = CurseForgeAPI.getLatestFile(mod.curseForgeId);
                        if (latest != null && latest.id != mod.fileId) {
                            updates.add(mod.name);
                        }
                    }
                    catch (Exception e)
                    {
                        // ...
                    }
                }
            }

            return updates;
        }).thenAccept(updates -> SwingUtilities.invokeLater(() ->
        {
            checkUpdatesButton.setEnabled(true);

            if (updates.isEmpty())
            {
                statusLabel.setText("All mods are up to date");

                JOptionPane.showMessageDialog(this,
                        "All mods are up to date!",
                        "Update Check",
                        JOptionPane.INFORMATION_MESSAGE);
            }
            else
            {
                statusLabel.setText(updates.size() + " update(s) available");

                JOptionPane.showMessageDialog(this,
                        "Updates available for:\n" + String.join("\n", updates),
                        "Updates Available",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        }));
    }

    private void openModsFolder()
    {
        try {
            modManager.openModsFolder();
        } catch (Exception e)
        {
            JOptionPane.showMessageDialog(this,
                    "Failed to open mods folder: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateButtonStates()
    {
        boolean hasSelection = !installedList.isSelectionEmpty();
        deleteButton.setEnabled(hasSelection);
    }

    private void addWindowDragListener()
    {
        Point dragOffset = new Point();

        addMouseListener(new MouseAdapter()
        {
            public void mousePressed(MouseEvent e)
            {
                dragOffset.x = e.getX();
                dragOffset.y = e.getY();
            }
        });

        addMouseMotionListener(new MouseAdapter()
        {
            public void mouseDragged(MouseEvent e)
            {
                Point location = getLocation();
                setLocation(location.x + e.getX() - dragOffset.x,
                        location.y + e.getY() - dragOffset.y);
            }
        });
    }

    private static class CurseForgeModRenderer extends DefaultListCellRenderer
    {
        static final Map<Integer, ImageIcon> imageCache = new ConcurrentHashMap<>();
        static final Map<String, ImageIcon> customIconCache = new ConcurrentHashMap<>();
        private static final Map<Integer, Boolean> loadingImages = new ConcurrentHashMap<>();

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
        {
            if (!(value instanceof CurseForgeAPI.Mod mod)) {
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }

            JPanel panel = new JPanel(new BorderLayout(10, 0));
            panel.setOpaque(true);

            panel.setBackground(isSelected
                    ? new Color(255, 168, 69, 100)
                    : new Color(26, 26, 32));

            panel.setBorder(new EmptyBorder(10, 15, 10, 15));

            JPanel imagePanel = new JPanel(new BorderLayout());
            imagePanel.setOpaque(false);
            imagePanel.setPreferredSize(new Dimension(80, 80));
            imagePanel.setMinimumSize(new Dimension(80, 80));

            if (mod.logo != null && mod.logo.thumbnailUrl != null)
            {
                ImageIcon cachedIcon = imageCache.get(mod.id);

                if (cachedIcon != null)
                {
                    JLabel imgLabel = new JLabel(cachedIcon);
                    imgLabel.setHorizontalAlignment(SwingConstants.CENTER);
                    imagePanel.add(imgLabel, BorderLayout.CENTER);
                }
                else if (loadingImages.getOrDefault(mod.id, false))
                {
                    JLabel loadingLabel = new JLabel("...", SwingConstants.CENTER);
                    loadingLabel.setForeground(new Color(255, 168, 69, 100));
                    loadingLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
                    imagePanel.add(loadingLabel, BorderLayout.CENTER);
                }
                else
                {
                    loadingImages.put(mod.id, true);

                    JLabel loadingLabel = new JLabel("...", SwingConstants.CENTER);
                    loadingLabel.setForeground(new Color(255, 168, 69, 100));
                    loadingLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
                    imagePanel.add(loadingLabel, BorderLayout.CENTER);

                    CompletableFuture.runAsync(() ->
                    {
                        try {
                            URL url = new URL(mod.logo.thumbnailUrl);
                            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(url);
                            if (img != null)
                            {
                                java.awt.Image scaled = img.getScaledInstance(80, 80, java.awt.Image.SCALE_SMOOTH);
                                ImageIcon icon = new ImageIcon(scaled);
                                imageCache.put(mod.id, icon);

                                SwingUtilities.invokeLater(() -> {
                                    loadingImages.put(mod.id, false);
                                    list.repaint();
                                });
                            }
                            else
                            {
                                loadingImages.put(mod.id, false);
                            }
                        } catch (Exception e)
                        {
                            loadingImages.put(mod.id, false);
                        }
                    });
                }
            }
            else
            {
                JLabel placeholderLabel = new JLabel("?", SwingConstants.CENTER);
                placeholderLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 40));
                placeholderLabel.setForeground(new Color(255, 168, 69, 80));
                imagePanel.add(placeholderLabel, BorderLayout.CENTER);
            }

            panel.add(imagePanel, BorderLayout.WEST);

            String authors = mod.authors != null && !mod.authors.isEmpty()
                    ? mod.authors.stream().map(a -> a.name).reduce((a, b) -> a + ", " + b).orElse("Unknown")
                    : "Unknown";

            String categories = mod.categories != null && !mod.categories.isEmpty()
                    ? mod.categories.stream().limit(3).map(c -> c.name).reduce((a, b) -> a + ", " + b).orElse("")
                    : "";

            String downloads = mod.downloadCount > 0
                    ? formatDownloads(mod.downloadCount) + " downloads"
                    : "ID: " + mod.id;

            JPanel textPanel = new JPanel();
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
            textPanel.setOpaque(false);
            textPanel.setBorder(new EmptyBorder(5, 0, 5, 0));

            JLabel nameLabel = new JLabel(mod.name);
            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
            nameLabel.setForeground(new Color(255, 168, 69));
            nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel authorLabel = new JLabel("by " + authors);
            authorLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            authorLabel.setForeground(new Color(153, 153, 153));
            authorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            String info = categories.isEmpty() ? downloads : (categories + " • " + downloads);
            JLabel infoLabel = new JLabel(info);
            infoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            infoLabel.setForeground(new Color(102, 102, 102));
            infoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            textPanel.add(nameLabel);
            textPanel.add(Box.createVerticalStrut(4));
            textPanel.add(authorLabel);
            textPanel.add(Box.createVerticalStrut(2));
            textPanel.add(infoLabel);

            panel.add(textPanel, BorderLayout.CENTER);

            return panel;
        }

        private String formatDownloads(long count)
        {
            if (count >= 1_000_000)
            {
                return String.format("%.1fM", count / 1_000_000.0);
            }
            else if (count >= 1_000)
            {
                return String.format("%.1fK", count / 1_000.0);
            }

            return String.valueOf(count);
        }

        private String escapeHtml(String text)
        {
            if (text == null) {
                return "";
            }

            return text.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;");
        }
    }

    private class ModDropTargetListener extends DropTargetAdapter
    {
        @SuppressWarnings("unchecked")
        @Override
        public void drop(DropTargetDropEvent dtde)
        {
            try
            {
                dtde.acceptDrop(DnDConstants.ACTION_COPY);

                Transferable t = dtde.getTransferable();
                if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
                {
                    List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                    importJarFiles(files);
                }
                else
                {
                    dtde.dropComplete(false);
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
                dtde.dropComplete(false);
            }
        }

        @Override
        public void dragEnter(DropTargetDragEvent dtde)
        {
            if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
            {
                dtde.acceptDrag(DnDConstants.ACTION_COPY);
            }
            else
            {
                dtde.rejectDrag();
            }
        }

        @Override
        public void dragOver(DropTargetDragEvent dtde)
        {
            if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
            {
                dtde.acceptDrag(DnDConstants.ACTION_COPY);
            }
            else
            {
                dtde.rejectDrag();
            }
        }
    }

    private static class InstalledModRenderer extends DefaultListCellRenderer
    {
        private static final Map<Integer, ImageIcon> imageCache = CurseForgeModRenderer.imageCache;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
        {
            if (!(value instanceof ModManager.InstalledMod mod)) {
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }

            JPanel panel = new JPanel(new BorderLayout(10, 0));
            panel.setOpaque(true);

            panel.setBackground(isSelected
                    ? new Color(255, 168, 69, 100)
                    : new Color(26, 26, 32));

            panel.setBorder(new EmptyBorder(10, 15, 10, 15));

            JPanel imagePanel = new JPanel(new BorderLayout());
            imagePanel.setOpaque(false);
            imagePanel.setPreferredSize(new Dimension(60, 60));
            imagePanel.setMinimumSize(new Dimension(60, 60));

            if (mod.curseForgeId > 0 && imageCache.containsKey(mod.curseForgeId))
            {
                ImageIcon cachedIcon = imageCache.get(mod.curseForgeId);
                java.awt.Image scaled = cachedIcon.getImage().getScaledInstance(60, 60, java.awt.Image.SCALE_SMOOTH);
                JLabel imgLabel = new JLabel(new ImageIcon(scaled));
                imgLabel.setHorizontalAlignment(SwingConstants.CENTER);
                imagePanel.add(imgLabel, BorderLayout.CENTER);
            }
            else if (mod.iconUrl != null && !mod.iconUrl.isEmpty())
            {
                ImageIcon cached = CurseForgeModRenderer.customIconCache.get(mod.iconUrl);
                if (cached != null)
                {
                    java.awt.Image scaled = cached.getImage().getScaledInstance(60, 60, java.awt.Image.SCALE_SMOOTH);
                    JLabel imgLabel = new JLabel(new ImageIcon(scaled));
                    imgLabel.setHorizontalAlignment(SwingConstants.CENTER);
                    imagePanel.add(imgLabel, BorderLayout.CENTER);
                }
                else
                {
                    JLabel loadingLabel = new JLabel("...", SwingConstants.CENTER);
                    loadingLabel.setForeground(new Color(255, 168, 69, 80));
                    imagePanel.add(loadingLabel, BorderLayout.CENTER);

                    CompletableFuture.runAsync(() ->
                    {
                        try
                        {
                            URL url = new URL(mod.iconUrl);
                            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(url);

                            if (img != null)
                            {
                                ImageIcon icon = new ImageIcon(img);
                                CurseForgeModRenderer.customIconCache.put(mod.iconUrl, icon);

                                SwingUtilities.invokeLater(() -> {
                                    list.repaint();
                                });
                            }
                        } catch (Exception e)
                        {
                            // ...
                        }
                    });
                }
            }
            else
            {
                JLabel placeholderLabel = new JLabel("?", SwingConstants.CENTER);
                placeholderLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 30));
                placeholderLabel.setForeground(new Color(255, 168, 69, 80));
                imagePanel.add(placeholderLabel, BorderLayout.CENTER);
            }

            panel.add(imagePanel, BorderLayout.WEST);

            JPanel textPanel = new JPanel();
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
            textPanel.setOpaque(false);
            textPanel.setBorder(new EmptyBorder(5, 0, 5, 0));

            JLabel nameLabel = new JLabel(mod.name);
            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            nameLabel.setForeground(Color.WHITE);
            nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            String statusText = mod.enabled ? "✓ Enabled" : "✗ Disabled";
            Color statusColor = mod.enabled ? new Color(144, 238, 144) : new Color(255, 107, 107);

            JLabel detailsLabel = new JLabel(String.format("%s • v%s • ", mod.author, mod.version));
            detailsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            detailsLabel.setForeground(new Color(153, 153, 153));
            detailsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel statusLabel = new JLabel(statusText);
            statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            statusLabel.setForeground(statusColor);

            JPanel detailsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            detailsPanel.setOpaque(false);
            detailsPanel.add(detailsLabel);
            detailsPanel.add(statusLabel);
            detailsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

            textPanel.add(nameLabel);
            textPanel.add(Box.createVerticalStrut(3));
            textPanel.add(detailsPanel);

            panel.add(textPanel, BorderLayout.CENTER);

            return panel;
        }

        private String escapeHtml(String text)
        {
            if (text == null) return "";
            return text.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;");
        }
    }
}