package com.habms.client;

import javax.swing.*;
import java.awt.*;

/**
 * Simple login view hosting username/password inputs and action buttons.
 */
public class LoginPanel extends JPanel {
    public interface Listener {
        void onLogin(String username, String password);
        void onExit();
        void onRegister();
    }

    private final JTextField usernameField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private Listener listener;

    public LoginPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(24, 36, 24, 36));

        JLabel title = new JLabel("飞马星医院预约服务", SwingConstants.CENTER);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 28f));
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 24, 0));

        JPanel form = new JPanel(new GridLayout(2, 2, 10, 10));
        form.setMaximumSize(new Dimension(360, 100));
        form.add(new JLabel("用户名"));
        form.add(usernameField);
        form.add(new JLabel("密码"));
        form.add(passwordField);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        JButton loginBtn = new JButton("登录");
        loginBtn.setPreferredSize(new Dimension(120, 36));
        loginBtn.addActionListener(e -> {
            if (listener != null) listener.onLogin(getUsername(), getPassword());
        });
        JButton exitBtn = new JButton("退出");
        exitBtn.setPreferredSize(new Dimension(120, 36));
        exitBtn.addActionListener(e -> {
            if (listener != null) listener.onExit();
        });
        JButton registerBtn = new JButton("注册");
        registerBtn.setPreferredSize(new Dimension(120, 36));
        registerBtn.addActionListener(e -> {
            if (listener != null) listener.onRegister();
        });
        btnBar.add(loginBtn);
        btnBar.add(exitBtn);
        btnBar.add(registerBtn);

        add(title);
        add(form);
        add(Box.createVerticalStrut(16));
        add(btnBar);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public String getUsername() {
        return usernameField.getText();
    }

    public String getPassword() {
        return new String(passwordField.getPassword());
    }

    public void clearPassword() {
        passwordField.setText("");
    }
}
