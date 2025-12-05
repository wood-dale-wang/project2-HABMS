package com.habms.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ClientApp {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 9090;
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private JFrame frame;
    private JTextArea ta;
    private JTextField userField, passField;
    private DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientApp().createAndShowGUI());
    }

    private void createAndShowGUI() {
        frame = new JFrame("医院预约系统 客户端");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700, 500);
        JPanel p = new JPanel(new BorderLayout());

        JPanel top = new JPanel();
        userField = new JTextField(8);
        passField = new JTextField(8);
        JButton loginBtn = new JButton("登录");
        loginBtn.addActionListener(this::onLogin);
        top.add(new JLabel("用户名"));top.add(userField);
        top.add(new JLabel("密码"));top.add(passField);
        top.add(loginBtn);

        ta = new JTextArea();
        ta.setEditable(false);

        JPanel bottom = new JPanel();
        JButton listDoc = new JButton("查看医生列表");
        listDoc.addActionListener(e -> sendAndShow("LIST_DOCTORS"));
        JButton listAppts = new JButton("查看某医生预约");
        listAppts.addActionListener(e -> {
            String id = JOptionPane.showInputDialog(frame, "输入医生ID");
            if (id!=null) sendAndShow("LIST_APPTS|"+id);
        });
        JButton book = new JButton("预约");
        book.addActionListener(e -> {
            String docId = JOptionPane.showInputDialog(frame, "医生ID");
            String name = JOptionPane.showInputDialog(frame, "病人姓名");
            String time = JOptionPane.showInputDialog(frame, "时间 (yyyy-MM-dd'T'HH:mm)");
            if (docId!=null && name!=null && time!=null) sendAndShow("BOOK|"+docId+"|"+name+"|"+time);
        });
        JButton cancel = new JButton("取消预约");
        cancel.addActionListener(e -> {
            String apid = JOptionPane.showInputDialog(frame, "预约ID");
            if (apid!=null) sendAndShow("CANCEL|"+apid);
        });
        bottom.add(listDoc);bottom.add(listAppts);bottom.add(book);bottom.add(cancel);

        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(ta), BorderLayout.CENTER);
        p.add(bottom, BorderLayout.SOUTH);

        frame.getContentPane().add(p);
        frame.setVisible(true);
    }

    private void onLogin(ActionEvent e) {
        String user = userField.getText();
        String pass = passField.getText();
        ensureConnection();
        String res = sendCommand("LOGIN|" + user + "|" + pass);
        if (res.startsWith("OK")) {
            ta.append("登录成功。\n");
        } else {
            ta.append("登录失败: " + res + "\n");
        }
    }

    private void ensureConnection() {
        if (socket!=null && socket.isConnected()) return;
        try {
            socket = new Socket(HOST, PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "无法连接服务器: " + ex.getMessage());
        }
    }

    private void sendAndShow(String cmd) {
        ensureConnection();
        String res = sendCommand(cmd);
        ta.append("> " + cmd + "\n" + res + "\n");
    }

    private String sendCommand(String cmd) {
        try {
            out.write(cmd);
            out.write("\n");
            out.flush();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                if ("<<END>>".equals(line)) break;
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (IOException ex) {
            ex.printStackTrace();
            return "ERR|通信失败";
        }
    }
}
