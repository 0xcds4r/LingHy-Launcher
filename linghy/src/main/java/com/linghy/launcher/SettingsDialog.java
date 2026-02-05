package com.linghy.launcher;

import javax.swing.*;
import java.awt.*;
import java.util.prefs.Preferences;

public class SettingsDialog extends JDialog
{
    private static final String PREF_NODE = LauncherPanel.class.getPackageName();
    public static final String KEY_RKN_WARNING = "curseforge_rkn_warning_dont_show_v1";

    private JCheckBox cbRknWarning;

    public SettingsDialog(Frame owner)
    {
        super(owner, "LingHy Launcher Settings", true);
        setSize(1280, 720);
        setMinimumSize(new Dimension(480, 340));
        setLocationRelativeTo(owner);
        setResizable(true);

        initComponents();
    }

    private void initComponents()
    {
        getContentPane().setBackground(new Color(18, 18, 22));
        setLayout(new BorderLayout());

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(26, 26, 34));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 60, 70)));

        JLabel title = new JLabel("  ⚙️  Settings");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(new Color(255, 168, 69));
        title.setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        header.add(title, BorderLayout.WEST);

        add(header, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));

        Preferences prefs = Preferences.userNodeForPackage(LauncherPanel.class);

        content.add(createSectionLabel("Notifications & Warnings"));
        content.add(Box.createVerticalStrut(8));

        cbRknWarning = new JCheckBox("Do not show warning about CurseForge blocking in Russia (RKN / Cloudflare)");
        cbRknWarning.setSelected(prefs.getBoolean(KEY_RKN_WARNING, false));
        styleCheckBox(cbRknWarning);
        cbRknWarning.setToolTipText("When checked — the CurseForge warning will no longer appear");
        content.add(cbRknWarning);
        content.add(Box.createVerticalStrut(16));

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        add(scroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 16));
        footer.setBackground(new Color(22, 22, 28));
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(60, 60, 70)));

        JButton btnSave = new JButton("Save");
        styleButton(btnSave, new Color(16, 185, 129, 220), false);
        btnSave.addActionListener(e -> {
            saveSettings(prefs);
            dispose();
        });

        JButton btnCancel = new JButton("Cancel");
        styleButton(btnCancel, new Color(80, 80, 90, 180), false);
        btnCancel.addActionListener(e -> dispose());

        footer.add(btnSave);
        footer.add(btnCancel);

        add(footer, BorderLayout.SOUTH);
    }

    private JLabel createSectionLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 15));
        label.setForeground(new Color(180, 180, 200));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        return label;
    }

    private void styleCheckBox(JCheckBox cb)
    {
        cb.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        cb.setForeground(new Color(230, 230, 235));
        cb.setBackground(new Color(30, 30, 38));
        cb.setFocusPainted(false);
        cb.setBorderPainted(true);
        cb.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        cb.setAlignmentX(Component.LEFT_ALIGNMENT);
        cb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void styleButton(JButton btn, Color baseColor, boolean danger)
    {
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setForeground(Color.WHITE);
        btn.setBackground(baseColor);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(260, 44));
        btn.setOpaque(true);

        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.setBackground(brighter(baseColor, danger ? 40 : 50));
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setBackground(baseColor);
            }
        });
    }

    private Color brighter(Color c, int amount)
    {
        int r = Math.min(255, c.getRed() + amount);
        int g = Math.min(255, c.getGreen() + amount);
        int b = Math.min(255, c.getBlue() + amount);
        return new Color(r, g, b, c.getAlpha());
    }

    private void saveSettings(Preferences prefs) {
        prefs.putBoolean(KEY_RKN_WARNING, cbRknWarning.isSelected());
    }

    public static String getSettingValue(String key, String defaultValue) {
        Preferences prefs = Preferences.userNodeForPackage(LauncherPanel.class);
        return prefs.get(key, defaultValue);
    }

    public static boolean getSettingValue(String key, boolean defaultValue) {
        Preferences prefs = Preferences.userNodeForPackage(LauncherPanel.class);
        return prefs.getBoolean(key, defaultValue);
    }

    public static int getSettingValue(String key, int defaultValue) {
        Preferences prefs = Preferences.userNodeForPackage(LauncherPanel.class);
        return prefs.getInt(key, defaultValue);
    }

    public static void setSettingValue(String key, String value) {
        Preferences prefs = Preferences.userNodeForPackage(LauncherPanel.class);
        prefs.put(key, value);
    }

    public static void setSettingValue(String key, boolean value) {
        Preferences prefs = Preferences.userNodeForPackage(LauncherPanel.class);
        prefs.putBoolean(key, value);
    }

    public static void setSettingValue(String key, int value) {
        Preferences prefs = Preferences.userNodeForPackage(LauncherPanel.class);
        prefs.putInt(key, value);
    }

    public static void removeSetting(String key) {
        Preferences prefs = Preferences.userNodeForPackage(LauncherPanel.class);
        prefs.remove(key);
    }
}