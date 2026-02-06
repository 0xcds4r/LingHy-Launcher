package com.linghy.mods;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class ResourceRenderer extends DefaultListCellRenderer
{
    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                  boolean isSelected, boolean cellHasFocus)
    {
        if (!(value instanceof String zipName)) {
            return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        }

        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setOpaque(true);
        panel.setBackground(isSelected
                ? new Color(255, 168, 69, 100)
                : new Color(26, 26, 32));
        panel.setBorder(new EmptyBorder(10, 15, 10, 15));

        JLabel iconLabel = new JLabel("P");
        iconLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        iconLabel.setForeground(new Color(255, 168, 69, 180));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel iconPanel = new JPanel(new BorderLayout());
        iconPanel.setOpaque(false);
        iconPanel.setPreferredSize(new Dimension(50, 50));
        iconPanel.add(iconLabel, BorderLayout.CENTER);

        panel.add(iconPanel, BorderLayout.WEST);

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);
        textPanel.setBorder(new EmptyBorder(5, 0, 5, 0));

        JLabel nameLabel = new JLabel(zipName);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel descLabel = new JLabel("");
        descLabel.setFont(new Font("Segoe UI", Font.PLAIN, 5));
        descLabel.setForeground(new Color(160, 160, 170));
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        textPanel.add(nameLabel);
        textPanel.add(Box.createVerticalStrut(4));
        textPanel.add(descLabel);

        panel.add(textPanel, BorderLayout.CENTER);

        return panel;
    }
}
