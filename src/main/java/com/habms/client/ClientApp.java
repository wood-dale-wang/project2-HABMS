package com.habms.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class ClientApp {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 9090;
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private JFrame frame;
    private JPanel centerPanel;
    private JPanel displayPanel;
    private JTextArea ta;
    private JTextField userField, passField;
    private DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private String currentUser = null;
    private String currentRole = null;
    // UI controls referenced for role enable/disable
    private JButton addDoctorBtn, updateDoctorBtn, addScheduleBtn, listSchedulesBtn;
    private JButton updateAccountBtn, deleteAccountBtn, logoutBtn;
    // additional buttons
    private JButton listApptsBtn;
    private JButton myApptsBtn;
    // file/report buttons
    private JButton importXlsBtn;
    private JButton exportApptsBtn;
    private JButton genReportBtn;

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientApp().createAndShowGUI());
    }

    private void createAndShowGUI() {
        frame = new JFrame("医院预约系统 客户端");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(900, 600);
        JPanel p = new JPanel(new BorderLayout());

        JPanel top = new JPanel();
        userField = new JTextField(10);
        passField = new JTextField(10);
        JButton loginBtn = new JButton("登录");
        loginBtn.addActionListener(this::onLogin);
        JButton registerBtn = new JButton("注册");
        registerBtn.addActionListener(e -> onRegister());
        top.add(new JLabel("用户名"));top.add(userField);
        top.add(new JLabel("密码"));top.add(passField);
        top.add(loginBtn);
        top.add(registerBtn);

        ta = new JTextArea();
        ta.setEditable(false);
        centerPanel = new JPanel(new BorderLayout());
        displayPanel = new JPanel(new BorderLayout());
        centerPanel.add(displayPanel, BorderLayout.CENTER);
        JScrollPane logScroll = new JScrollPane(ta);
        logScroll.setPreferredSize(new Dimension(0, 140));
        centerPanel.add(logScroll, BorderLayout.SOUTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton listDoc = new JButton("查看医生列表");
        listDoc.addActionListener(e -> doListDoctors());
        listApptsBtn = new JButton("查看某医生预约");
        listApptsBtn.addActionListener(e -> {
            String id = JOptionPane.showInputDialog(frame, "输入医生ID");
            if (id!=null) doListAppts(id);
        });
        JButton book = new JButton("预约");
        book.addActionListener(e -> {
            if (currentUser==null) { JOptionPane.showMessageDialog(frame, "请先登录或注册"); return; }
            String docId = JOptionPane.showInputDialog(frame, "医生ID");
            if (docId==null) return;
            JPanel panel = new JPanel(new GridLayout(0,2));
            JTextField dateField = new JTextField();
            JTextField hField = new JTextField();
            JTextField mField = new JTextField();
            JTextField nameField = new JTextField();
            panel.add(new JLabel("日期 (yyyy-MM-dd):")); panel.add(dateField);
            panel.add(new JLabel("小时 (0-23):")); panel.add(hField);
            panel.add(new JLabel("分钟 (0-59):")); panel.add(mField);
            panel.add(new JLabel("病人姓名:")); panel.add(nameField);
            int res = JOptionPane.showConfirmDialog(frame, panel, "预约", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (res != JOptionPane.OK_OPTION) return;
            try {
                java.time.LocalDate date = java.time.LocalDate.parse(dateField.getText().trim());
                int hh = Integer.parseInt(hField.getText().trim()); int mm = Integer.parseInt(mField.getText().trim());
                if (hh<0||hh>23||mm<0||mm>59) { JOptionPane.showMessageDialog(frame, "时间不合法"); return; }
                java.time.LocalDateTime time = date.atTime(hh, mm);
                String pname = nameField.getText().trim();
                doBookWithDatetime(docId, pname, time);
            } catch (Exception ex) { JOptionPane.showMessageDialog(frame, "输入格式错误: " + ex.getMessage()); }
        });
        JButton cancel = new JButton("取消预约");
        cancel.addActionListener(e -> {
            String apid = JOptionPane.showInputDialog(frame, "预约ID");
            if (apid!=null) doCancel(apid);
        });

        JButton searchName = new JButton("按姓名查医生");
        searchName.addActionListener(e -> {
            String q = JOptionPane.showInputDialog(frame, "医生姓名关键字");
            if (q!=null) doSearchName(q);
        });
        JButton searchDept = new JButton("按科室查医生");
        searchDept.addActionListener(e -> {
            String q = JOptionPane.showInputDialog(frame, "科室关键字");
            if (q!=null) doSearchDept(q);
        });

        // Admin buttons (initially disabled)
        addDoctorBtn = new JButton("添加医生");
        addDoctorBtn.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(frame, "医生姓名");
            String dept = JOptionPane.showInputDialog(frame, "科室");
            String info = JOptionPane.showInputDialog(frame, "简介");
            if (name!=null && dept!=null) doAddDoctor(name, dept, info==null?"":info);
        });
        updateDoctorBtn = new JButton("修改医生");
        updateDoctorBtn.addActionListener(e -> {
            String id = JOptionPane.showInputDialog(frame, "医生ID");
            String name = JOptionPane.showInputDialog(frame, "新姓名");
            String dept = JOptionPane.showInputDialog(frame, "新科室");
            String info = JOptionPane.showInputDialog(frame, "新简介");
            if (id!=null) doUpdateDoctor(id, name==null?"":name, dept==null?"":dept, info==null?"":info);
        });
        addScheduleBtn = new JButton("添加排班");
        addScheduleBtn.addActionListener(e -> {
            String did = JOptionPane.showInputDialog(frame, "医生ID");
            if (did==null) return;
            // build a panel to collect date, start hour/min, end hour/min, capacity, note
            JPanel panel = new JPanel(new GridLayout(0,2));
            JTextField dateField = new JTextField(); // yyyy-MM-dd
            JTextField shField = new JTextField(); // start hour
            JTextField smField = new JTextField(); // start minute
            JTextField ehField = new JTextField(); // end hour
            JTextField emField = new JTextField(); // end minute
            JTextField capField = new JTextField("1");
            JTextField noteField = new JTextField();
            panel.add(new JLabel("日期 (yyyy-MM-dd):")); panel.add(dateField);
            panel.add(new JLabel("开始小时 (0-23):")); panel.add(shField);
            panel.add(new JLabel("开始分钟 (0-59):")); panel.add(smField);
            panel.add(new JLabel("结束小时 (0-23):")); panel.add(ehField);
            panel.add(new JLabel("结束分钟 (0-59):")); panel.add(emField);
            panel.add(new JLabel("容量 (整数):")); panel.add(capField);
            panel.add(new JLabel("备注:")); panel.add(noteField);
            int res = JOptionPane.showConfirmDialog(frame, panel, "添加排班", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (res != JOptionPane.OK_OPTION) return;
            String date = dateField.getText().trim();
            String sh = shField.getText().trim(); String sm = smField.getText().trim();
            String eh = ehField.getText().trim(); String em = emField.getText().trim();
            String cap = capField.getText().trim(); String note = noteField.getText().trim();
            // basic validation
            try {
                int sh_i = Integer.parseInt(sh); int sm_i = Integer.parseInt(sm); int eh_i = Integer.parseInt(eh); int em_i = Integer.parseInt(em); int capacity = Integer.parseInt(cap);
                if (sh_i<0||sh_i>23||eh_i<0||eh_i>23||sm_i<0||sm_i>59||em_i<0||em_i>59) { JOptionPane.showMessageDialog(frame, "时间部分不合法"); return; }
                java.time.LocalDate startDate = java.time.LocalDate.parse(date);
                java.time.LocalDateTime start = startDate.atTime(sh_i, sm_i);
                java.time.LocalDateTime end = startDate.atTime(eh_i, em_i);
                if (!end.isAfter(start)) { JOptionPane.showMessageDialog(frame, "结束时间必须晚于开始时间"); return; }
                doAddScheduleWithParts(did, start, end, note, capacity);
            } catch (Exception ex) { JOptionPane.showMessageDialog(frame, "输入格式错误: " + ex.getMessage()); return; }
        });
        listSchedulesBtn = new JButton("查看排班");
        listSchedulesBtn.addActionListener(e -> {
            String id = JOptionPane.showInputDialog(frame, "医生ID");
            if (id!=null) doListSchedules(id);
        });

        importXlsBtn = new JButton("导入医生/排班 (XLS)");
        importXlsBtn.addActionListener(e -> doImportXls());
        exportApptsBtn = new JButton("导出预约 (XLS)");
        exportApptsBtn.addActionListener(e -> doExportAppointmentsXls());
        genReportBtn = new JButton("生成统计报表 (PDF)");
        genReportBtn.addActionListener(e -> doGenerateReportPdf());

        // Account management
        updateAccountBtn = new JButton("修改个人信息");
        updateAccountBtn.addActionListener(e -> onUpdateAccount());
        deleteAccountBtn = new JButton("注销账户");
        deleteAccountBtn.addActionListener(e -> onDeleteAccount());
        logoutBtn = new JButton("退出登录");
        logoutBtn.addActionListener(e -> onLogout());

        // add buttons to panel
        buttonPanel.add(listDoc); buttonPanel.add(listApptsBtn); buttonPanel.add(book); buttonPanel.add(cancel);
        buttonPanel.add(searchName); buttonPanel.add(searchDept);
        buttonPanel.add(addDoctorBtn); buttonPanel.add(updateDoctorBtn); buttonPanel.add(addScheduleBtn); buttonPanel.add(listSchedulesBtn);
        buttonPanel.add(importXlsBtn); buttonPanel.add(exportApptsBtn); buttonPanel.add(genReportBtn);
        myApptsBtn = new JButton("查看本人预约");
        myApptsBtn.addActionListener(e -> doListMyAppts());
        buttonPanel.add(updateAccountBtn); buttonPanel.add(deleteAccountBtn); buttonPanel.add(myApptsBtn); buttonPanel.add(logoutBtn);

        JScrollPane buttonScroll = new JScrollPane(buttonPanel, JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        buttonScroll.setPreferredSize(new Dimension(900, 140));

        p.add(top, BorderLayout.NORTH);
        p.add(centerPanel, BorderLayout.CENTER);
        p.add(buttonScroll, BorderLayout.SOUTH);

        frame.getContentPane().add(p);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // try to notify server
                try { if (currentUser!=null) sendJsonRequest(Map.of("action","logout")); } catch (Exception ex) {}
                try { if (socket!=null) socket.close(); } catch (Exception ex) {}
                frame.dispose();
                System.exit(0);
            }
        });
        frame.setVisible(true);
        updateUIForRole();
    }

    private void doListDoctors() {
        try {
            Map resp = sendJsonRequest(Map.of("action","list_doctors"));
            if ("OK".equals(resp.get("status"))) {
                Object data = resp.get("data");
                if (data instanceof List) showTable((List) data);
            } else {
                ta.append("ERROR: " + resp.get("message") + "\n");
            }
        } catch (Exception ex) { ta.append("通信错误: " + ex.getMessage() + "\n"); }
    }

    private void doListAppts(String id) {
        try {
            Map req = Map.of("action","list_appts", "doctorId", Integer.parseInt(id));
            Map resp = sendJsonRequest(req);
            if ("OK".equals(resp.get("status"))) { showTable((List)resp.get("data")); }
            else ta.append("ERROR: " + resp.get("message") + "\n");
        } catch (Exception ex) { ta.append("错误: " + ex.getMessage() + "\n"); }
    }

    private void doBookWithDatetime(String docId, String name, java.time.LocalDateTime time) {
        try {
            Map req = Map.of("action","book","doctorId", Integer.parseInt(docId), "patientName", name, "time", time.format(fmt));
            Map resp = sendJsonRequest(req);
            if (resp.containsKey("message")) ta.append(resp.get("message") + "\n");
            else ta.append(String.valueOf(resp) + "\n");
        } catch (Exception ex) { ta.append("错误: " + ex.getMessage() + "\n"); }
    }

    private void doListMyAppts() {
        try {
            Map resp = sendJsonRequest(Map.of("action","list_my_appts"));
            if ("OK".equals(resp.get("status"))) showTable((List)resp.get("data"));
            else ta.append("ERROR: " + resp.get("message") + "\n");
        } catch (Exception ex) { ta.append("错误: " + ex.getMessage() + "\n"); }
    }

    private void doCancel(String apid) {
        try {
            Map req = Map.of("action","cancel","apptId", Integer.parseInt(apid));
            Map resp = sendJsonRequest(req);
            ta.append(String.valueOf(resp) + "\n");
        } catch (Exception ex) { ta.append("错误: " + ex.getMessage() + "\n"); }
    }

    private void doSearchName(String q) {
        try {
            Map req = Map.of("action","search_name","q", q);
            Map resp = sendJsonRequest(req);
            if ("OK".equals(resp.get("status"))) {
                if (resp.containsKey("data")) showTable((List)resp.get("data"));
                else if (resp.containsKey("raw")) ta.append((String)resp.get("raw") + "\n");
            } else ta.append("ERROR: " + resp.get("message") + "\n");
        } catch (Exception ex) { ta.append("错误: " + ex.getMessage() + "\n"); }
    }

    private void doSearchDept(String q) {
        try {
            Map req = Map.of("action","search_dept","q", q);
            Map resp = sendJsonRequest(req);
            if ("OK".equals(resp.get("status"))) {
                if (resp.containsKey("data")) showTable((List)resp.get("data"));
                else if (resp.containsKey("raw")) ta.append((String)resp.get("raw") + "\n");
            } else ta.append("ERROR: " + resp.get("message") + "\n");
        } catch (Exception ex) { ta.append("错误: " + ex.getMessage() + "\n"); }
    }

    private void doAddDoctor(String name, String dept, String info) {
        try {
            Map req = Map.of("action","add_doctor","name",name,"dept",dept,"info",info);
            Map resp = sendJsonRequest(req);
            ta.append(String.valueOf(resp) + "\n");
        } catch (Exception ex) { ta.append("错误: " + ex.getMessage() + "\n"); }
    }

    private void doUpdateDoctor(String id, String name, String dept, String info) {
        try {
            Map req = Map.of("action","update_doctor","id", Integer.parseInt(id), "name",name,"dept",dept,"info",info);
            Map resp = sendJsonRequest(req);
            ta.append(String.valueOf(resp) + "\n");
        } catch (Exception ex) { ta.append("错误: " + ex.getMessage() + "\n"); }
    }

    private void doAddScheduleWithParts(String did, java.time.LocalDateTime start, java.time.LocalDateTime end, String note, int capacity) {
        try {
            Map req = Map.of("action","add_schedule","doctorId", Integer.parseInt(did), "start", start.format(fmt), "end", end.format(fmt), "note", note==null?"":note, "capacity", capacity);
            Map resp = sendJsonRequest(req);
            ta.append(String.valueOf(resp) + "\n");
        } catch (Exception ex) { ta.append("错误: " + ex.getMessage() + "\n"); }
    }

    private void doListSchedules(String id) {
        try {
            Map req = Map.of("action","list_schedules","doctorId", Integer.parseInt(id));
            Map resp = sendJsonRequest(req);
            if ("OK".equals(resp.get("status"))) showTable((List)resp.get("data"));
            else ta.append("ERROR: " + resp.get("message") + "\n");
        } catch (Exception ex) { ta.append("错误: " + ex.getMessage() + "\n"); }
    }

    private void onLogin(ActionEvent e) {
        String user = userField.getText();
        String pass = passField.getText();
        try {
            Map req = Map.of("action","login","username",user,"password",pass);
            Map resp = sendJsonRequest(req);
            if ("OK".equals(resp.get("status"))) {
                String role = (String) resp.getOrDefault("role","PATIENT");
                currentUser = user; currentRole = role;
                ta.append("登录成功。角色: " + role + "\n");
                updateUIForRole();
            } else {
                ta.append("登录失败: " + resp.get("message") + "\n");
            }
        } catch (Exception ex) { ta.append("登录错误: " + ex.getMessage() + "\n"); }
    }

    private void onRegister() {
        String username = JOptionPane.showInputDialog(frame, "用户名");
        String password = JOptionPane.showInputDialog(frame, "密码");
        String fullname = JOptionPane.showInputDialog(frame, "姓名");
        String idcard = JOptionPane.showInputDialog(frame, "身份证号（注册后不可修改）");
        String phone = JOptionPane.showInputDialog(frame, "电话");
        // basic validation
        if (username==null || password==null || fullname==null || idcard==null) return;
        if (username.length() < 3) { JOptionPane.showMessageDialog(frame, "用户名需至少3字符"); return; }
        if (password.length() < 6) { JOptionPane.showMessageDialog(frame, "密码需至少6字符"); return; }
        if (idcard.length() < 6) { JOptionPane.showMessageDialog(frame, "身份证号格式不正确"); return; }
        try {
            Map req = Map.of("action","register","username",username,"password",password,"fullname",fullname,"idcard",idcard,"phone",phone==null?"":phone);
            Map resp = sendJsonRequest(req);
            if ("OK".equals(resp.get("status"))) ta.append("注册成功，请使用用户名和密码登录。\n");
            else ta.append("注册失败: " + resp.get("message") + "\n");
        } catch (Exception ex) { ta.append("注册错误: " + ex.getMessage() + "\n"); }
    }

    private void onLogout() {
        try {
            if (currentUser!=null) sendJsonRequest(Map.of("action","logout"));
        } catch (Exception ignored) {}
        currentUser = null; currentRole = null;
        updateUIForRole();
        ta.append("已退出登录。\n");
        try { if (socket!=null) socket.close(); } catch (Exception ignored) {}
    }

    private void onUpdateAccount() {
        if (currentUser==null) { JOptionPane.showMessageDialog(frame, "请先登录"); return; }
        String password = JOptionPane.showInputDialog(frame, "新密码(空则不改)");
        String fullname = JOptionPane.showInputDialog(frame, "新姓名");
        String phone = JOptionPane.showInputDialog(frame, "新电话");
        if (password==null) password = "";
        if (password.length()>0 && password.length()<6) { JOptionPane.showMessageDialog(frame, "密码需至少6字符"); return; }
        try {
            Map req = Map.of("action","update_account","password",password,"fullname",fullname==null?"":fullname,"phone",phone==null?"":phone);
            Map resp = sendJsonRequest(req);
            ta.append(String.valueOf(resp) + "\n");
        } catch (Exception ex) { ta.append("错误: " + ex.getMessage() + "\n"); }
    }

    private void onDeleteAccount() {
        if (currentUser==null) { JOptionPane.showMessageDialog(frame, "请先登录"); return; }
        int ok = JOptionPane.showConfirmDialog(frame, "确认要注销当前账户吗？此操作不可恢复","确认", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;
        try {
            Map resp = sendJsonRequest(Map.of("action","delete_account"));
            ta.append(String.valueOf(resp) + "\n");
            if ("OK".equals(resp.get("status"))) { currentUser=null; currentRole=null; updateUIForRole(); }
        } catch (Exception ex) { ta.append("错误: " + ex.getMessage() + "\n"); }
    }

    private void updateUIForRole() {
        boolean isAdmin = "ADMIN".equals(currentRole);
        addDoctorBtn.setEnabled(isAdmin);
        updateDoctorBtn.setEnabled(isAdmin);
        addScheduleBtn.setEnabled(isAdmin);
        importXlsBtn.setEnabled(isAdmin);
        exportApptsBtn.setEnabled(isAdmin);
        genReportBtn.setEnabled(isAdmin);
        listSchedulesBtn.setEnabled(true);
        updateAccountBtn.setEnabled(currentUser!=null && "PATIENT".equals(currentRole));
        deleteAccountBtn.setEnabled(currentUser!=null && "PATIENT".equals(currentRole));
        logoutBtn.setEnabled(currentUser!=null);
    }

    private void doImportXls() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Excel files","xls","xlsx"));
        int res = chooser.showOpenDialog(frame);
        if (res != JFileChooser.APPROVE_OPTION) return;
        File f = chooser.getSelectedFile();
        try {
            byte[] data = java.nio.file.Files.readAllBytes(f.toPath());
            String b64 = java.util.Base64.getEncoder().encodeToString(data);
            Map resp = sendJsonRequest(Map.of("action","import_doctors_xls","content", b64));
            ta.append(String.valueOf(resp) + "\n");
        } catch (Exception ex) { ta.append("错误: " + ex.getMessage() + "\n"); }
    }

    private void doExportAppointmentsXls() {
        try {
            Map resp = sendJsonRequest(Map.of("action","export_appointments_xls"));
            if (!"OK".equals(resp.get("status"))) { ta.append("ERROR: " + resp.get("message") + "\n"); return; }
            String b64 = (String) resp.get("content");
            byte[] data = java.util.Base64.getDecoder().decode(b64);
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File((String)resp.getOrDefault("filename","appointments.xlsx")));
            int res = chooser.showSaveDialog(frame);
            if (res != JFileChooser.APPROVE_OPTION) return;
            java.nio.file.Files.write(chooser.getSelectedFile().toPath(), data);
            ta.append("已保存: " + chooser.getSelectedFile().getAbsolutePath() + "\n");
        } catch (Exception ex) { ta.append("错误: " + ex.getMessage() + "\n"); }
    }

    private void doGenerateReportPdf() {
        try {
            Map resp = sendJsonRequest(Map.of("action","generate_report_pdf"));
            if (!"OK".equals(resp.get("status"))) { ta.append("ERROR: " + resp.get("message") + "\n"); return; }
            String b64 = (String) resp.get("content");
            byte[] data = java.util.Base64.getDecoder().decode(b64);
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File((String)resp.getOrDefault("filename","report.pdf")));
            int res = chooser.showSaveDialog(frame);
            if (res != JFileChooser.APPROVE_OPTION) return;
            java.nio.file.Files.write(chooser.getSelectedFile().toPath(), data);
            ta.append("已保存: " + chooser.getSelectedFile().getAbsolutePath() + "\n");
        } catch (Exception ex) { ta.append("错误: " + ex.getMessage() + "\n"); }
    }

    private void ensureConnection() throws IOException {
        if (socket!=null && socket.isConnected() && !socket.isClosed()) return;
        // cleanup old socket if any
        try { if (socket!=null) socket.close(); } catch (Exception ignore) {}
        socket = new Socket(HOST, PORT);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
    }

    private Map sendJsonRequest(Map req) throws IOException {
        ensureConnection();
        try {
            String s = mapper.writeValueAsString(req);
            out.write(s);
            out.write("\n");
            out.flush();
            String line = in.readLine();
            if (line == null) throw new IOException("服务器关闭连接");
            Map resp = mapper.readValue(line, Map.class);
            return resp;
        } catch (IOException ex) {
            throw ex;
        }
    }

    private void showTable(List<Map> data) {
        if (data == null || data.isEmpty()) {
            ta.append("无数据\n");
            return;
        }
        Map first = data.get(0);
        Object[] cols = first.keySet().toArray();
        DefaultTableModel model = new DefaultTableModel();
        for (Object c : cols) model.addColumn(c.toString());
        for (Map row : data) {
            Object[] r = new Object[cols.length];
            for (int i=0;i<cols.length;i++) r[i] = row.get(cols[i]);
            model.addRow(r);
        }
        JTable table = new JTable(model);
        displayPanel.removeAll();
        displayPanel.add(new JScrollPane(table), BorderLayout.CENTER);
        displayPanel.revalidate(); displayPanel.repaint();
    }
}
