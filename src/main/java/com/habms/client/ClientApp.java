package com.habms.client;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.text.JTextComponent;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class ClientApp {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 9090;
    private ClientService service;
    private ClientController controller;
    private JFrame frame;
    private CardLayout viewLayout;
    private JPanel viewPanel;
    private AppPanel appPanel;
    private LoginPanel loginPanel;
    private JLabel topUserLabel;
    private JLabel topTimeLabel;
    private JPanel displayPanel;
    private DefaultListModel<String> messageModel;
    private JList<String> messageList;
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

    // container of action buttons, toggled by login state
    private JScrollPane buttonScroll;

    private javax.swing.Timer clockTimer;

    public static void main(String[] args) {
        // use system look and feel for better native appearance
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignore) {}
        SwingUtilities.invokeLater(() -> new ClientApp().createAndShowGUI());
    }

    private void createAndShowGUI() {
        frame = new JFrame("医院预约系统 客户端");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(1000, 640);

        service = new ClientService(HOST, PORT);
        controller = new ClientController(service);

        viewLayout = new CardLayout();
        viewPanel = new JPanel(viewLayout);

        loginPanel = new LoginPanel();
        loginPanel.setListener(new LoginPanel.Listener() {
            @Override
            public void onLogin(String username, String password) {
                ClientApp.this.onLogin(username, password);
            }

            @Override
            public void onExit() { ClientApp.this.onExit(); }

            @Override
            public void onRegister() { ClientApp.this.onRegister(); }
        });

        appPanel = new AppPanel();
        messageModel = appPanel.getMessageModel();
        messageList = appPanel.getMessageList();
        displayPanel = appPanel.getDisplayPanel();
        buttonScroll = appPanel.getButtonScroll();
        JPanel buttonPanel = appPanel.getButtonPanel();
        topUserLabel = appPanel.getTopUserLabel();
        topTimeLabel = appPanel.getTopTimeLabel();
        JButton topLogoutBtn = appPanel.getLogoutButton();
        JButton topDeleteBtn = appPanel.getDeleteAccountButton();
        topLogoutBtn.addActionListener(e -> onLogout());
        topDeleteBtn.addActionListener(e -> onDeleteAccount());
        JButton listDoc = new JButton("查看医生列表");
        listDoc.addActionListener(e -> doListDoctors());
        listApptsBtn = new JButton("查看某医生预约");
        listApptsBtn.addActionListener(e -> {
            JTextField idField = new JTextField();
            JPanel pId = FormFactory.singleFieldPanel("医生ID", idField);
            FormDialog fd = new FormDialog(frame, "查看医生预约", pId);
            int r = fd.showDialog();
            if (r != FormDialog.OK) return;
            String id = idField.getText().trim();
            if (!id.isEmpty()) doListAppts(id);
        });
        JButton book = new JButton("预约");
        book.addActionListener(e -> {
            if (currentUser==null) { addMessage("请先登录或注册"); return; }
            JTextField docIdField = new JTextField();
            JSpinner dateTimeSpinner = new JSpinner(new SpinnerDateModel(new Date(), null, null, Calendar.MINUTE));
            dateTimeSpinner.setEditor(new JSpinner.DateEditor(dateTimeSpinner, "yyyy-MM-dd HH:mm"));
            JTextField nameField = new JTextField();
            JPanel panel = new JPanel(new GridLayout(0,2,6,6));
            FormFactory.addBookingFields(panel, docIdField, dateTimeSpinner, nameField);
            while (true) {
                FormDialog fd = new FormDialog(frame, "预约", panel);
                int res = fd.showDialog();
                if (res != FormDialog.OK) return;
                // reset borders
                docIdField.setBorder(UIManager.getLookAndFeel().getDefaults().getBorder("TextField.border"));
                nameField.setBorder(UIManager.getLookAndFeel().getDefaults().getBorder("TextField.border"));
                boolean ok = true;
                String docId = docIdField.getText().trim();
                String pname = nameField.getText().trim();
                if (docId.isEmpty()) { docIdField.setBorder(new LineBorder(Color.RED,1)); ok=false; }
                if (pname.isEmpty()) { nameField.setBorder(new LineBorder(Color.RED,1)); ok=false; }
                if (!ok) { addMessage("请修正红色字段后重试（保留输入）。"); continue; }
                try {
                    Date dt = (Date) dateTimeSpinner.getValue();
                    Calendar cal = Calendar.getInstance(); cal.setTime(dt);
                    LocalDateTime ldt = LocalDateTime.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH)+1, cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
                    doBookWithDatetime(docId, pname, ldt);
                    return;
                } catch (Exception ex) { addMessage("预约错误: " + ex.getMessage()); return; }
            }
        });
        JButton cancel = new JButton("取消预约");
        cancel.addActionListener(e -> {
            JTextField apidField = new JTextField();
            JPanel pId = FormFactory.singleFieldPanel("预约ID", apidField);
            FormDialog fd = new FormDialog(frame, "取消预约", pId);
            int r = fd.showDialog();
            if (r != FormDialog.OK) return;
            String apid = apidField.getText().trim();
            if (!apid.isEmpty()) doCancel(apid);
        });

        JButton searchName = new JButton("按姓名查医生");
        searchName.addActionListener(e -> {
            JTextField qField = new JTextField();
            JPanel pQ = FormFactory.singleFieldPanel("医生姓名关键字", qField);
            FormDialog fd = new FormDialog(frame, "按姓名查医生", pQ);
            int r = fd.showDialog(); if (r!=FormDialog.OK) return;
            String q = qField.getText().trim(); if (!q.isEmpty()) doSearchName(q);
        });
        JButton searchDept = new JButton("按科室查医生");
        searchDept.addActionListener(e -> {
            JTextField qField = new JTextField();
            JPanel pQ = FormFactory.singleFieldPanel("科室关键字", qField);
            FormDialog fd = new FormDialog(frame, "按科室查医生", pQ);
            int r = fd.showDialog(); if (r!=FormDialog.OK) return;
            String q = qField.getText().trim(); if (!q.isEmpty()) doSearchDept(q);
        });

        // Admin buttons (initially disabled)
        addDoctorBtn = new JButton("添加医生");
        addDoctorBtn.addActionListener(e -> {
            JTextField nameF = new JTextField();
            JTextField deptF = new JTextField();
            JTextField infoF = new JTextField();
            JPanel panel = new JPanel(new GridLayout(0,2,6,6));
            FormFactory.addDoctorFields(panel, nameF, deptF, infoF);
            while (true) {
                FormDialog fd = new FormDialog(frame, "添加医生", panel);
                int res = fd.showDialog();
                if (res != FormDialog.OK) return;
                nameF.setBorder(UIManager.getLookAndFeel().getDefaults().getBorder("TextField.border"));
                deptF.setBorder(UIManager.getLookAndFeel().getDefaults().getBorder("TextField.border"));
                boolean ok = true;
                String name = nameF.getText().trim();
                String dept = deptF.getText().trim();
                String info = infoF.getText().trim();
                if (name.isEmpty()) { nameF.setBorder(new LineBorder(Color.RED,1)); ok=false; }
                if (dept.isEmpty()) { deptF.setBorder(new LineBorder(Color.RED,1)); ok=false; }
                if (!ok) { addMessage("请修正红色字段后重试（保留输入）。"); continue; }
                doAddDoctor(name, dept, info);
                return;
            }
        });
        updateDoctorBtn = new JButton("修改医生");
        updateDoctorBtn.addActionListener(e -> {
            JTextField idF = new JTextField();
            JTextField nameF = new JTextField();
            JTextField deptF = new JTextField();
            JTextField infoF = new JTextField();
            JPanel panel = new JPanel(new GridLayout(0,2,6,6));
            panel.add(new JLabel("医生ID")); panel.add(idF);
            FormFactory.addDoctorFields(panel, nameF, deptF, infoF);
            while (true) {
                FormDialog fd = new FormDialog(frame, "修改医生", panel);
                int res = fd.showDialog();
                if (res != FormDialog.OK) return;
                idF.setBorder(UIManager.getLookAndFeel().getDefaults().getBorder("TextField.border"));
                boolean ok = true;
                String id = idF.getText().trim();
                String name = nameF.getText().trim();
                String dept = deptF.getText().trim();
                String info = infoF.getText().trim();
                if (id.isEmpty()) { idF.setBorder(new LineBorder(Color.RED,1)); ok=false; }
                if (!ok) { addMessage("请修正红色字段后重试（保留输入）。"); continue; }
                doUpdateDoctor(id, name, dept, info);
                return;
            }
        });
        addScheduleBtn = new JButton("添加排班");
        addScheduleBtn.addActionListener(e -> {
            JTextField didField = new JTextField();
            JSpinner dateSpinner = new JSpinner(new SpinnerDateModel(new Date(), null, null, Calendar.DAY_OF_MONTH));
            dateSpinner.setEditor(new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd"));
            JSpinner startSpinner = new JSpinner(new SpinnerDateModel(new Date(), null, null, Calendar.MINUTE));
            startSpinner.setEditor(new JSpinner.DateEditor(startSpinner, "HH:mm"));
            JSpinner endSpinner = new JSpinner(new SpinnerDateModel(new Date(), null, null, Calendar.MINUTE));
            endSpinner.setEditor(new JSpinner.DateEditor(endSpinner, "HH:mm"));
            JSpinner capacitySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1000, 1));
            JTextField noteField = new JTextField();
            JPanel panel = new JPanel(new GridLayout(0,2,6,6));
            FormFactory.addScheduleFields(panel, didField, dateSpinner, startSpinner, endSpinner, capacitySpinner, noteField);
            while (true) {
                FormDialog fd = new FormDialog(frame, "添加排班", panel);
                int res = fd.showDialog();
                if (res != FormDialog.OK) return;
                // reset borders
                didField.setBorder(UIManager.getLookAndFeel().getDefaults().getBorder("TextField.border"));
                boolean ok = true;
                String did = didField.getText().trim();
                if (did.isEmpty()) { didField.setBorder(new LineBorder(Color.RED,1)); ok=false; }
                if (!ok) { addMessage("请修正红色字段后重试（保留输入）。"); continue; }
                try {
                    Date dateVal = (Date) dateSpinner.getValue();
                    Date sVal = (Date) startSpinner.getValue();
                    Date eVal = (Date) endSpinner.getValue();
                    Calendar cd = Calendar.getInstance(); cd.setTime(dateVal);
                    Calendar cs = Calendar.getInstance(); cs.setTime(sVal);
                    Calendar ce = Calendar.getInstance(); ce.setTime(eVal);
                    LocalDateTime start = LocalDateTime.of(cd.get(Calendar.YEAR), cd.get(Calendar.MONTH)+1, cd.get(Calendar.DAY_OF_MONTH), cs.get(Calendar.HOUR_OF_DAY), cs.get(Calendar.MINUTE));
                    LocalDateTime end = LocalDateTime.of(cd.get(Calendar.YEAR), cd.get(Calendar.MONTH)+1, cd.get(Calendar.DAY_OF_MONTH), ce.get(Calendar.HOUR_OF_DAY), ce.get(Calendar.MINUTE));
                    if (!end.isAfter(start)) { addMessage("结束时间必须晚于开始时间"); return; }
                    int capacity = (Integer) capacitySpinner.getValue();
                    String note = noteField.getText();
                    doAddScheduleWithParts(did, start, end, note, capacity);
                    return;
                } catch (Exception ex) { addMessage("添加排班错误: " + ex.getMessage()); return; }
            }
        });
        listSchedulesBtn = new JButton("查看排班");
        listSchedulesBtn.addActionListener(e -> {
            JTextField idField = new JTextField();
            JPanel pId = new JPanel(new GridLayout(0,1,6,6));
            pId.add(new JLabel("医生ID")); pId.add(idField);
            FormDialog fd = new FormDialog(frame, "查看排班", pId);
            int r = fd.showDialog();
            if (r != FormDialog.OK) return;
            String id = idField.getText().trim();
            if (!id.isEmpty()) doListSchedules(id);
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

        // add buttons to bar (bottom)
        addActionButton(buttonPanel, listDoc);
        addActionButton(buttonPanel, listApptsBtn);
        addActionButton(buttonPanel, book);
        addActionButton(buttonPanel, cancel);
        addActionButton(buttonPanel, searchName);
        addActionButton(buttonPanel, searchDept);
        addActionButton(buttonPanel, addDoctorBtn);
        addActionButton(buttonPanel, updateDoctorBtn);
        addActionButton(buttonPanel, addScheduleBtn);
        addActionButton(buttonPanel, listSchedulesBtn);
        addActionButton(buttonPanel, importXlsBtn);
        addActionButton(buttonPanel, exportApptsBtn);
        addActionButton(buttonPanel, genReportBtn);
        myApptsBtn = new JButton("查看本人预约");
        myApptsBtn.addActionListener(e -> doListMyAppts());
        addActionButton(buttonPanel, updateAccountBtn);
        addActionButton(buttonPanel, deleteAccountBtn);
        addActionButton(buttonPanel, myApptsBtn);
        addActionButton(buttonPanel, logoutBtn);

        viewPanel.add(loginPanel, "login");
        viewPanel.add(appPanel, "app");
        frame.getContentPane().add(viewPanel);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // try to notify server
                try { if (currentUser!=null) service.send(Map.of("action","logout")); } catch (Exception ex) {}
                try { service.close(); } catch (Exception ex) {}
                frame.dispose();
                System.exit(0);
            }
        });
        frame.setVisible(true);
        showLoginPage();
    }

    private void doListDoctors() {
        try {
            Map resp = controller.listDoctors();
            if ("OK".equals(resp.get("status"))) {
                Object data = resp.get("data");
                if (data instanceof List) showTable((List) data);
            } else {
                addMessage("ERROR: " + resp.get("message"));
            }
        } catch (Exception ex) { addMessage("通信错误: " + ex.getMessage()); }
    }

    private void doListAppts(String id) {
        try {
            Map resp = controller.listAppts(Integer.parseInt(id));
            if ("OK".equals(resp.get("status"))) { showTable((List)resp.get("data")); }
            else addMessage("ERROR: " + resp.get("message"));
        } catch (Exception ex) { addMessage("错误: " + ex.getMessage()); }
    }

    private void doBookWithDatetime(String docId, String name, java.time.LocalDateTime time) {
        try {
            Map resp = controller.book(Integer.parseInt(docId), name, time.format(fmt));
            if (resp.containsKey("message")) addMessage((String)resp.get("message"));
            else addMessage(String.valueOf(resp));
        } catch (Exception ex) { addMessage("错误: " + ex.getMessage()); }
    }

    private void doListMyAppts() {
        try {
            Map resp = controller.listMyAppts();
            if ("OK".equals(resp.get("status"))) showTable((List)resp.get("data"));
            else addMessage("ERROR: " + resp.get("message"));
        } catch (Exception ex) { addMessage("错误: " + ex.getMessage()); }
    }

    private void doCancel(String apid) {
        try {
            Map resp = controller.cancel(Integer.parseInt(apid));
            addMessage(String.valueOf(resp));
        } catch (Exception ex) { addMessage("错误: " + ex.getMessage()); }
    }

    private void doSearchName(String q) {
        try {
            Map resp = controller.searchName(q);
            if ("OK".equals(resp.get("status"))) {
                if (resp.containsKey("data")) showTable((List)resp.get("data"));
            } else addMessage("ERROR: " + resp.get("message"));
        } catch (Exception ex) { addMessage("错误: " + ex.getMessage()); }
    }

    private void doSearchDept(String q) {
        try {
            Map resp = controller.searchDept(q);
            if ("OK".equals(resp.get("status"))) {
                if (resp.containsKey("data")) showTable((List)resp.get("data"));
            } else addMessage("ERROR: " + resp.get("message"));
        } catch (Exception ex) { addMessage("错误: " + ex.getMessage()); }
    }

    private void doAddDoctor(String name, String dept, String info) {
        try {
            Map resp = controller.addDoctor(name, dept, info);
            addMessage(String.valueOf(resp));
        } catch (Exception ex) { addMessage("错误: " + ex.getMessage()); }
    }

    private void doUpdateDoctor(String id, String name, String dept, String info) {
        try {
            Map resp = controller.updateDoctor(Integer.parseInt(id), name, dept, info);
            addMessage(String.valueOf(resp));
        } catch (Exception ex) { addMessage("错误: " + ex.getMessage()); }
    }

    private void doAddScheduleWithParts(String did, java.time.LocalDateTime start, java.time.LocalDateTime end, String note, int capacity) {
        try {
            Map resp = controller.addSchedule(Integer.parseInt(did), start.format(fmt), end.format(fmt), note, capacity);
            addMessage(String.valueOf(resp));
        } catch (Exception ex) { addMessage("错误: " + ex.getMessage()); }
    }

    private void doListSchedules(String id) {
        try {
            Map resp = controller.listSchedules(Integer.parseInt(id));
            if ("OK".equals(resp.get("status"))) showTable((List)resp.get("data"));
            else addMessage("ERROR: " + resp.get("message"));
        } catch (Exception ex) { addMessage("错误: " + ex.getMessage()); }
    }

    private void onLogin(ActionEvent e) {
        onLogin(loginPanel.getUsername(), loginPanel.getPassword());
    }

    private void onLogin(String user, String pass) {
        try {
            Map resp = controller.login(user, pass);
            if ("OK".equals(resp.get("status"))) {
                String role = (String) resp.getOrDefault("role","PATIENT");
                currentUser = user; currentRole = role;
                addMessage("登录成功。角色: " + role);
                showAppPage();
                updateUIForRole();
            } else {
                addMessage("登录失败: " + resp.get("message"));
            }
        } catch (Exception ex) { addMessage("登录错误: " + ex.getMessage()); }
    }

    private void onRegister() {
        // combined registration form with inline validation
        JTextField usernameF = new JTextField();
        JPasswordField passwordF = new JPasswordField();
        JTextField fullnameF = new JTextField();
        JTextField idcardF = new JTextField();
        JTextField phoneF = new JTextField();
        JPanel panel = new JPanel(new GridLayout(0,2,6,6));
        FormFactory.addRegistrationFields(panel, usernameF, passwordF, fullnameF, idcardF, phoneF);
            while (true) {
                FormDialog fd = new FormDialog(frame, "注册", panel);
                int res = fd.showDialog();
                if (res != FormDialog.OK) return;
                // clear previous borders
                usernameF.setBorder(UIManager.getLookAndFeel().getDefaults().getBorder("TextField.border"));
                passwordF.setBorder(UIManager.getLookAndFeel().getDefaults().getBorder("TextField.border"));
                idcardF.setBorder(UIManager.getLookAndFeel().getDefaults().getBorder("TextField.border"));
                boolean ok = true;
                String username = usernameF.getText();
                String password = new String(passwordF.getPassword());
                String fullname = fullnameF.getText();
                String idcard = idcardF.getText();
                String phone = phoneF.getText();
                if (username == null || username.length() < 3) { usernameF.setBorder(new LineBorder(Color.RED,1)); ok=false; }
                if (password == null || password.length() < 6) { passwordF.setBorder(new LineBorder(Color.RED,1)); ok=false; }
                if (idcard == null || idcard.length() < 6) { idcardF.setBorder(new LineBorder(Color.RED,1)); ok=false; }
                if (!ok) { addMessage("注册校验未通过，请修正红色字段后重试。保留输入以便修改。"); continue; }
                try {
                    Map resp = controller.register(username, password, fullname, idcard, phone);
                    if ("OK".equals(resp.get("status"))) addMessage("注册成功，请使用用户名和密码登录。");
                    else addMessage("注册失败: " + resp.get("message"));
                    return;
                } catch (Exception ex) { addMessage("注册错误: " + ex.getMessage()); return; }
            }
    }

    private void onLogout() {
        try {
            if (currentUser!=null) controller.logout();
        } catch (Exception ignored) {}
        currentUser = null; currentRole = null;
        updateUIForRole();
        addMessage("已退出登录。");
        try { service.close(); } catch (Exception ignored) {}
        showLoginPage();
    }

    private void onUpdateAccount() {
        if (currentUser==null) { addMessage("请先登录"); return; }
        JPasswordField passwordF = new JPasswordField();
        JTextField fullnameF = new JTextField();
        JTextField phoneF = new JTextField();
        JPanel p = new JPanel(new GridLayout(0,2,6,6));
        p.add(new JLabel("新密码(空则不改)")); p.add(passwordF);
        p.add(new JLabel("新姓名")); p.add(fullnameF);
        p.add(new JLabel("新电话")); p.add(phoneF);
        FormDialog fd = new FormDialog(frame, "更新账户", p);
        int r = fd.showDialog(); if (r!=FormDialog.OK) return;
        String password = new String(passwordF.getPassword());
        String fullname = fullnameF.getText();
        String phone = phoneF.getText();
        if (password==null) password = "";
        if (password.length()>0 && password.length()<6) { addMessage("密码需至少6字符"); return; }
        try {
            Map resp = controller.updateAccount(password, fullname, phone);
            addMessage(String.valueOf(resp));
        } catch (Exception ex) { addMessage("错误: " + ex.getMessage()); }
    }

    private void onDeleteAccount() {
        if (currentUser==null) { addMessage("请先登录"); return; }
        JPanel cp = new JPanel(new BorderLayout()); cp.add(new JLabel("确认要注销当前账户吗？此操作不可恢复"), BorderLayout.CENTER);
        FormDialog fd = new FormDialog(frame, "确认注销", cp);
        int r = fd.showDialog(); if (r!=FormDialog.OK) return;
        try {
            Map resp = controller.deleteAccount();
            addMessage(String.valueOf(resp));
            if ("OK".equals(resp.get("status"))) { currentUser=null; currentRole=null; updateUIForRole(); }
        } catch (Exception ex) { addMessage("错误: " + ex.getMessage()); }
    }

    private void updateUIForRole() {
        boolean logged = currentUser != null;
        boolean isAdmin = "ADMIN".equals(currentRole);
        boolean isPatient = "PATIENT".equals(currentRole);

        addDoctorBtn.setVisible(logged && isAdmin);
        updateDoctorBtn.setVisible(logged && isAdmin);
        addScheduleBtn.setVisible(logged && isAdmin);
        importXlsBtn.setVisible(logged && isAdmin);
        exportApptsBtn.setVisible(logged && isAdmin);
        genReportBtn.setVisible(logged && isAdmin);

        listSchedulesBtn.setVisible(logged);
        listApptsBtn.setVisible(logged);
        updateAccountBtn.setVisible(logged && isPatient);
        deleteAccountBtn.setVisible(logged && isPatient); // bottom bar copy hidden for admins
        logoutBtn.setVisible(logged);
        myApptsBtn.setVisible(logged && isPatient);
        // top bar actions: logout always visible when logged; delete hidden for admin
        appPanel.getLogoutButton().setVisible(logged);
        appPanel.getDeleteAccountButton().setVisible(logged && isPatient);
        if (topUserLabel != null) {
            String userText = logged ? "当前用户: " + currentUser + " (" + currentRole + ")" : "未登录";
            topUserLabel.setText(userText);
        }

        if (buttonScroll != null) {
            SwingUtilities.invokeLater(() -> {
                buttonScroll.setVisible(logged);
                if (frame != null) { frame.validate(); frame.repaint(); }
            });
        }
    }

    private void addMessage(String m) {
        if (m == null) return;
        SwingUtilities.invokeLater(() -> {
            messageModel.addElement(java.time.LocalTime.now().withNano(0) + " - " + m);
            int last = messageModel.getSize()-1; if (last>=0) messageList.ensureIndexIsVisible(last);
        });
    }

    private void addActionButton(JPanel panel, JButton b) {
        b.setPreferredSize(new Dimension(170, 32));
        b.setFocusable(false);
        panel.add(b);
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
            Map resp = controller.importDoctorsXls(b64);
            addMessage(String.valueOf(resp));
        } catch (Exception ex) { addMessage("错误: " + ex.getMessage()); }
    }

    private void doExportAppointmentsXls() {
        try {
            Map resp = controller.exportAppointmentsXls();
            if (!"OK".equals(resp.get("status"))) { addMessage("ERROR: " + resp.get("message")); return; }
            String b64 = (String) resp.get("content");
            byte[] data = java.util.Base64.getDecoder().decode(b64);
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File((String)resp.getOrDefault("filename","appointments.xlsx")));
            int res = chooser.showSaveDialog(frame);
            if (res != JFileChooser.APPROVE_OPTION) return;
            java.nio.file.Files.write(chooser.getSelectedFile().toPath(), data);
            addMessage("已保存: " + chooser.getSelectedFile().getAbsolutePath());
        } catch (Exception ex) { addMessage("错误: " + ex.getMessage()); }
    }

    private void doGenerateReportPdf() {
        try {
            Map resp = controller.generateReportPdf();
            if (!"OK".equals(resp.get("status"))) { addMessage("ERROR: " + resp.get("message")); return; }
            String b64 = (String) resp.get("content");
            byte[] data = java.util.Base64.getDecoder().decode(b64);
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File((String)resp.getOrDefault("filename","report.pdf")));
            int res = chooser.showSaveDialog(frame);
            if (res != JFileChooser.APPROVE_OPTION) return;
            java.nio.file.Files.write(chooser.getSelectedFile().toPath(), data);
            addMessage("已保存: " + chooser.getSelectedFile().getAbsolutePath());
        } catch (Exception ex) { addMessage("错误: " + ex.getMessage()); }
    }

    private void showTable(List<Map> data) {
        if (data == null || data.isEmpty()) {
            addMessage("无数据");
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

    // show a single-column table with provided text (used when server returns a raw string)
    private void showTextAsTable(String title, String text) {
        DefaultTableModel model = new DefaultTableModel();
        model.addColumn(title);
        model.addRow(new Object[]{text});
        JTable table = new JTable(model);
        displayPanel.removeAll();
        displayPanel.add(new JScrollPane(table), BorderLayout.CENTER);
        displayPanel.revalidate(); displayPanel.repaint();
    }

    // helper: reset borders and validate required fields (returns true if all non-empty)
    private boolean validateRequired(JTextComponent[] fields) {
        boolean ok = true;
        for (JTextComponent f : fields) {
            String s = f.getText();
            if (s == null || s.trim().isEmpty()) {
                f.setBorder(new LineBorder(Color.RED,1));
                ok = false;
            } else {
                f.setBorder(UIManager.getLookAndFeel().getDefaults().getBorder("TextField.border"));
            }
        }
        return ok;
    }

    private void showAppPage() {
        if (viewLayout != null) {
            viewLayout.show(viewPanel, "app");
        }
        startClock();
    }

    private void showLoginPage() {
        if (viewLayout != null) {
            viewLayout.show(viewPanel, "login");
        }
        stopClock();
        if (displayPanel != null) {
            displayPanel.removeAll();
            displayPanel.add(new JLabel("请先登录以查看数据", SwingConstants.CENTER), BorderLayout.CENTER);
            displayPanel.revalidate(); displayPanel.repaint();
        }
    }

    private void startClock() {
        if (clockTimer != null) clockTimer.stop();
        clockTimer = new javax.swing.Timer(1000, evt -> {
            if (topTimeLabel != null) {
                topTimeLabel.setText(java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }
        });
        clockTimer.start();
    }

    private void stopClock() {
        if (clockTimer != null) clockTimer.stop();
        if (topTimeLabel != null) topTimeLabel.setText("--:--:--");
    }

    private void onExit() {
        try { if (currentUser!=null) controller.logout(); } catch (Exception ignored) {}
        try { service.close(); } catch (Exception ignored) {}
        System.exit(0);
    }
}
