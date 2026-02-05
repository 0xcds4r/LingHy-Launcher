package com.linghy.launcher;

import com.linghy.env.Environment;
import com.linghy.version.GameVersion;
import com.linghy.version.VersionManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class VersionSelectorDialog extends JDialog
{
    private GameVersion selectedVersion;
    private final VersionManager versionManager;
    private JList<GameVersion> versionList;
    private DefaultListModel<GameVersion> listModel;
    private JLabel statusLabel;
    private JButton installButton;
    private JButton deleteButton;
    private JButton refreshButton;
    private JComboBox<String> branchSelector;
    private String currentBranch = "release";
    private GameVersion initialSelectedVersion;

    public VersionSelectorDialog(Frame parent, VersionManager versionManager, GameVersion currentSelected)
    {
        super(parent, "Select game version", true);
        this.versionManager = versionManager;
        this.initialSelectedVersion = currentSelected;

        setSize(800, 600);
        setLocationRelativeTo(parent);
        setResizable(false);
        setUndecorated(true);

        initComponents();
        loadVersions();
    }

    private void initComponents()
    {
        JPanel mainPanel = new JPanel(new BorderLayout(0, 0));
        mainPanel.setBackground(new Color(18, 18, 18));
        mainPanel.setBorder(BorderFactory.createLineBorder(new Color(255, 168, 69, 100), 2));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(9, 9, 9));
        headerPanel.setBorder(new EmptyBorder(15, 20, 15, 20));

        JLabel titleLabel = new JLabel("Select Game Version");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titleLabel.setForeground(new Color(255, 168, 69));

        JPanel windowControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        windowControls.setOpaque(false);

        refreshButton = createWindowButton("R");
        refreshButton.setToolTipText("Refresh versions list");
        refreshButton.addActionListener(e -> refreshVersions());

        JButton closeButton = createWindowButton("×");
        closeButton.setFont(new Font("Arial", Font.PLAIN, 20));
        closeButton.addActionListener(e -> {
            selectedVersion = null;
            dispose();
        });

        closeButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                closeButton.setForeground(new Color(239, 68, 68));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                closeButton.setForeground(Color.LIGHT_GRAY);
            }
        });

        windowControls.add(refreshButton);
        windowControls.add(closeButton);

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(windowControls, BorderLayout.EAST);

        mainPanel.add(headerPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setBackground(new Color(18, 18, 18));
        centerPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JPanel branchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        branchPanel.setOpaque(false);
        branchPanel.setBorder(new EmptyBorder(0, 0, 10, 0));

        JLabel branchLabel = new JLabel("Branch:");
        branchLabel.setForeground(new Color(160, 160, 170));
        branchLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));

        String[] branches = {"Release", "Pre-Release"};
        branchSelector = new JComboBox<>(branches);
        branchSelector.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        branchSelector.setBackground(new Color(26, 26, 32));
        branchSelector.setForeground(Color.WHITE);
        branchSelector.setFocusable(false);
        branchSelector.setPreferredSize(new Dimension(150, 30));
        branchSelector.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        branchSelector.setRenderer(new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
            {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                setBackground(isSelected ? new Color(255, 168, 69, 100) : new Color(26, 26, 32));
                setForeground(Color.WHITE);
                setBorder(new EmptyBorder(5, 10, 5, 10));

                return this;
            }
        });

        if (initialSelectedVersion != null)
        {
            currentBranch = initialSelectedVersion.getBranch();
            branchSelector.setSelectedItem(
                    currentBranch.equals("pre-release") ? "Pre-Release" : "Release"
            );
        }

        branchSelector.addActionListener(e ->
        {
            String selected = (String) branchSelector.getSelectedItem();

            if (selected != null)
            {
                currentBranch = selected.equals("Pre-Release") ? "pre-release" : "release";
                loadVersions();
            }
        });

        branchPanel.add(branchLabel);
        branchPanel.add(branchSelector);

        centerPanel.add(branchPanel, BorderLayout.NORTH);

        listModel = new DefaultListModel<>();
        versionList = new JList<>(listModel);
        versionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        versionList.setBackground(new Color(26, 26, 32));
        versionList.setForeground(Color.WHITE);
        versionList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        versionList.setBorder(new EmptyBorder(10, 10, 10, 10));
        versionList.setCellRenderer(new VersionListCellRenderer());

        versionList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateButtonStates();
            }
        });

        versionList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    selectVersion();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(versionList);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(255, 168, 69, 60), 1));
        scrollPane.getViewport().setBackground(new Color(26, 26, 32));

        scrollPane.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
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

            private JButton createZeroButton() {
                JButton button = new JButton();
                button.setPreferredSize(new Dimension(0, 0));
                button.setMinimumSize(new Dimension(0, 0));
                button.setMaximumSize(new Dimension(0, 0));
                return button;
            }

            @Override
            protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(thumbColor);
                g2.fillRoundRect(thumbBounds.x + 2, thumbBounds.y, thumbBounds.width - 4,
                        thumbBounds.height, 8, 8);
                g2.dispose();
            }

            @Override
            protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(trackColor);
                g2.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
                g2.dispose();
            }
        });

        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(12, 0));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        centerPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(10, 0));
        bottomPanel.setBackground(new Color(18, 18, 18));
        bottomPanel.setBorder(new EmptyBorder(0, 20, 20, 20));

        statusLabel = new JLabel("Loading versions...");
        statusLabel.setForeground(new Color(160, 160, 170));
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        buttonPanel.setOpaque(false);

        deleteButton = createStyledButton("Delete");
        deleteButton.setBackground(new Color(239, 68, 68, 160));
        deleteButton.addActionListener(e -> deleteVersion());
        deleteButton.setEnabled(false);

        installButton = createStyledButton("Select");
        installButton.setBackground(new Color(255, 168, 69, 180));
        installButton.addActionListener(e -> selectVersion());
        installButton.setEnabled(false);

        JButton cancelButton = createStyledButton("Cancel");
        cancelButton.setBackground(new Color(60, 60, 70));
        cancelButton.addActionListener(e -> {
            selectedVersion = null;
            dispose();
        });

        buttonPanel.add(deleteButton);
        buttonPanel.add(installButton);
        buttonPanel.add(cancelButton);

        bottomPanel.add(statusLabel, BorderLayout.WEST);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);

        addWindowDragListener();
    }

    private JButton createWindowButton(String text) {
        JButton btn = new JButton(text);
        btn.setForeground(Color.LIGHT_GRAY);
        btn.setBackground(new Color(0, 0, 0, 0));
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setFont(new Font("Arial", Font.BOLD, 18));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(40, 30));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!text.equals("×")) {
                    btn.setForeground(new Color(255, 168, 69));
                }
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (!text.equals("×")) {
                    btn.setForeground(Color.LIGHT_GRAY);
                }
            }
        });

        return btn;
    }

    private void addWindowDragListener() {
        Point dragOffset = new Point();

        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                dragOffset.x = e.getX();
                dragOffset.y = e.getY();
            }
        });

        addMouseMotionListener(new MouseAdapter() {
            public void mouseDragged(MouseEvent e) {
                Point location = getLocation();
                setLocation(location.x + e.getX() - dragOffset.x,
                        location.y + e.getY() - dragOffset.y);
            }
        });
    }

    private JButton createStyledButton(String text)
    {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(60, 60, 60));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(120, 40));

        button.addMouseListener(new MouseAdapter() {
            Color originalColor = button.getBackground();

            @Override
            public void mouseEntered(MouseEvent e) {
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

    private void loadVersions()
    {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            List<GameVersion> versions;

            @Override
            protected Void doInBackground() {
                versions = versionManager.loadCachedVersions(currentBranch);

                if (versions.isEmpty())
                {
                    try {
                        versions = versionManager.scanAvailableVersions(currentBranch,
                                (percent, message) ->
                                        SwingUtilities.invokeLater(() ->
                                                statusLabel.setText(message + " (" + percent + "%)")));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                return null;
            }

            @Override
            protected void done()
            {
                displayVersions(versions);

                if (initialSelectedVersion != null)
                {
                    for (int i = 0; i < listModel.getSize(); i++)
                    {
                        GameVersion v = listModel.getElementAt(i);

                        if (v.getPatchNumber() == initialSelectedVersion.getPatchNumber() && v.getBranch().equals(initialSelectedVersion.getBranch()))
                        {
                            versionList.setSelectedIndex(i);
                            versionList.ensureIndexIsVisible(i);
                            break;
                        }
                    }
                }
            }
        };

        worker.execute();
    }

    private void refreshVersions()
    {
        refreshButton.setEnabled(false);
        branchSelector.setEnabled(false);
        statusLabel.setText("Updating versions list...");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            List<GameVersion> versions;

            @Override
            protected Void doInBackground() {
                try {
                    versions = versionManager.scanAvailableVersions(currentBranch,
                            (percent, message) ->
                                    SwingUtilities.invokeLater(() ->
                                            statusLabel.setText(message + " (" + percent + "%)")));
                } catch (Exception e) {
                    e.printStackTrace();
                    versions = versionManager.loadCachedVersions(currentBranch);
                }
                return null;
            }

            @Override
            protected void done() {
                displayVersions(versions);
                refreshButton.setEnabled(true);
                branchSelector.setEnabled(true);
            }
        };

        worker.execute();
    }

    private void displayVersions(List<GameVersion> versions)
    {
        listModel.clear();

        for (GameVersion version : versions)
        {
            boolean actuallyInstalled = versionManager.isVersionInstalled(
                    version.getPatchNumber(),
                    version.getBranch()
            );

            GameVersion displayVersion = new GameVersion(
                    version.getName(),
                    version.getFileName(),
                    version.getDownloadUrl(),
                    version.getPatchNumber(),
                    version.getSize(),
                    actuallyInstalled,
                    version.getBranch()
            );

            listModel.addElement(displayVersion);
        }

        String branchName = currentBranch.equals("pre-release") ? "pre-release" : "release";
        statusLabel.setText("Available " + branchName + " versions: " + versions.size());

        if (!versions.isEmpty()) {
            versionList.setSelectedIndex(0);
        }
    }

    private void updateButtonStates()
    {
        GameVersion selected = versionList.getSelectedValue();

        if (selected != null)
        {
            boolean installed = versionManager.isVersionInstalled(
                    selected.getPatchNumber(),
                    selected.getBranch()
            );
            installButton.setEnabled(true);
            installButton.setText("Select");
            deleteButton.setEnabled(installed);
        }
        else
        {
            installButton.setEnabled(false);
            deleteButton.setEnabled(false);
        }
    }

    private void selectVersion()
    {
        selectedVersion = versionList.getSelectedValue();
        if (selectedVersion != null) {
            dispose();
        }
    }

    private void deleteVersion()
    {
        GameVersion version = versionList.getSelectedValue();
        if (version == null) return;

        JDialog confirmDialog = new JDialog(this, "Confirm Deletion", true);
        confirmDialog.setSize(450, 200);
        confirmDialog.setLocationRelativeTo(this);
        confirmDialog.setUndecorated(true);

        JPanel confirmPanel = new JPanel(new BorderLayout(10, 10));
        confirmPanel.setBackground(new Color(18, 18, 18));
        confirmPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(239, 68, 68, 150), 2),
                new EmptyBorder(20, 20, 20, 20)
        ));

        JLabel messageLabel = new JLabel(
                "<html><center><b>Delete " + version.getName() + "?</b><br/><br/>" +
                        "This action cannot be undone.</center></html>"
        );
        messageLabel.setForeground(Color.WHITE);
        messageLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        buttonPanel.setOpaque(false);

        JButton yesButton = createStyledButton("Delete");
        yesButton.setBackground(new Color(239, 68, 68, 180));
        yesButton.setPreferredSize(new Dimension(120, 40));

        JButton noButton = createStyledButton("Cancel");
        noButton.setBackground(new Color(60, 60, 70));
        noButton.setPreferredSize(new Dimension(120, 40));

        final boolean[] confirmed = {false};

        yesButton.addActionListener(e -> {
            confirmed[0] = true;
            confirmDialog.dispose();
        });

        noButton.addActionListener(e -> {
            confirmed[0] = false;
            confirmDialog.dispose();
        });

        buttonPanel.add(yesButton);
        buttonPanel.add(noButton);

        confirmPanel.add(messageLabel, BorderLayout.CENTER);
        confirmPanel.add(buttonPanel, BorderLayout.SOUTH);

        confirmDialog.setContentPane(confirmPanel);
        confirmDialog.setVisible(true);

        if (confirmed[0])
        {
            try {
                versionManager.deleteVersion(version.getPatchNumber(), version.getBranch());

                if (initialSelectedVersion != null && initialSelectedVersion.getPatchNumber() == version.getPatchNumber() && initialSelectedVersion.getBranch().equals(version.getBranch()))
                {
                    Path selectedFile = Environment.getDefaultAppDir().resolve("selected_version.json");
                    Files.deleteIfExists(selectedFile);
                }

                statusLabel.setText("Deleted: " + version.getName());

                int selectedIndex = versionList.getSelectedIndex();
                GameVersion updated = new GameVersion(
                        version.getName(),
                        version.getFileName(),
                        version.getDownloadUrl(),
                        version.getPatchNumber(),
                        version.getSize(),
                        false,
                        version.getBranch()
                );
                listModel.set(selectedIndex, updated);
                updateButtonStates();

            } catch (Exception e) {
                JOptionPane.showMessageDialog(
                        this,
                        "Error while deleting: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    public GameVersion getSelectedVersion() {
        return selectedVersion;
    }

    private static class VersionListCellRenderer extends DefaultListCellRenderer
    {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus)
        {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);

            if (value instanceof GameVersion version)
            {
                String installedBadge = version.isInstalled()
                        ? "<span style='color:#90EE90;'>Installed</span>"
                        : "<span style='color:#666;'>Not installed</span>";

                String branchBadge = version.isPreRelease()
                        ? "<span style='color:#FFA845;'>[Pre-Release]</span>"
                        : "<span style='color:#4A9EFF;'>[Release]</span>";

                String text = String.format(
                        "<html><div style='padding:4px;'>" +
                                "<div style='font-size:15px; font-weight:bold; margin-bottom:6px;'>%s %s</div>" +
                                "<div style='font-size:11px; color:#aaa;'>" +
                                "Size: <b>%s</b> | Patch: <b>#%d</b> | %s" +
                                "</div></div></html>",
                        version.getName(),
                        branchBadge,
                        version.getFormattedSize(),
                        version.getPatchNumber(),
                        installedBadge
                );

                label.setText(text);
                label.setBorder(new EmptyBorder(10, 15, 10, 15));

                if (version.isInstalled())
                {
                    if (!isSelected) {
                        label.setBackground(new Color(40, 50, 40, 100));
                    }
                }
                else
                {
                    if (!isSelected) {
                        label.setBackground(new Color(26, 26, 32));
                    }
                }

                if (isSelected)
                {
                    label.setBackground(new Color(255, 168, 69, 100));
                    label.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(255, 168, 69, 200), 2),
                            new EmptyBorder(8, 13, 8, 13)
                    ));
                }

                label.setForeground(Color.WHITE);
            }

            return label;
        }
    }
}