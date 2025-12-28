package com.habms.client;

import javax.swing.*;
import java.awt.*;

/**
 * Main application shell containing top bar, center display and bottom action bar.
 */
public class AppPanel extends JPanel {
    private final JLabel topUserLabel = new JLabel("未登录");
    private final JLabel topTimeLabel = new JLabel("--:--:--");
    private final JButton logoutButton = new JButton("退出登录");
    private final JButton deleteAccountButton = new JButton("注销账户");
    private final JPanel displayPanel = new JPanel(new BorderLayout());
    private final DefaultListModel<String> messageModel = new DefaultListModel<>();
    private final JList<String> messageList = new JList<>(messageModel);
    private final JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
    private final JScrollPane buttonScroll;

    public AppPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // top bar
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        topUserLabel.setFont(topUserLabel.getFont().deriveFont(Font.BOLD, 16f));
        topTimeLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(topUserLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        right.add(deleteAccountButton);
        right.add(logoutButton);
        right.add(topTimeLabel);

        topBar.add(left, BorderLayout.WEST);
        topBar.add(right, BorderLayout.EAST);

        // center area
        displayPanel.add(new JLabel("请先登录以查看数据", SwingConstants.CENTER), BorderLayout.CENTER);
        messageList.setVisibleRowCount(6);
        messageList.setFont(messageList.getFont().deriveFont(13f));
        JScrollPane logScroll = new JScrollPane(messageList);
        logScroll.setPreferredSize(new Dimension(0, 140));
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(displayPanel, BorderLayout.CENTER);
        centerPanel.add(logScroll, BorderLayout.SOUTH);

        // bottom action bar
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        buttonScroll = new JScrollPane(buttonPanel, JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        buttonScroll.setBorder(BorderFactory.createTitledBorder("功能"));
        buttonScroll.setPreferredSize(new Dimension(0, 110));
        buttonScroll.setVisible(false);

        add(topBar, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(buttonScroll, BorderLayout.SOUTH);
    }

    public JLabel getTopUserLabel() {
        return topUserLabel;
    }

    public JLabel getTopTimeLabel() {
        return topTimeLabel;
    }

    public JButton getLogoutButton() {
        return logoutButton;
    }

    public JButton getDeleteAccountButton() {
        return deleteAccountButton;
    }

    public JPanel getDisplayPanel() {
        return displayPanel;
    }

    public DefaultListModel<String> getMessageModel() {
        return messageModel;
    }

    public JList<String> getMessageList() {
        return messageList;
    }

    public JPanel getButtonPanel() {
        return buttonPanel;
    }

    public JScrollPane getButtonScroll() {
        return buttonScroll;
    }

    public void showLoginPlaceholder() {
        displayPanel.removeAll();
        displayPanel.add(new JLabel("请先登录以查看数据", SwingConstants.CENTER), BorderLayout.CENTER);
        displayPanel.revalidate(); displayPanel.repaint();
    }
}
