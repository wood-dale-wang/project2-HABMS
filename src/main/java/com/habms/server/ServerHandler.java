package com.habms.server;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.Socket;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Base64;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

public class ServerHandler implements Runnable {
    private final Socket socket;
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private String currentUser = null;
    private String currentRole = null;
    private static final ObjectMapper mapper = new ObjectMapper();

    public ServerHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                System.out.println("收到: " + line);
                String reply = handleJson(line);
                out.write(reply);
                out.write("\n");
                out.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getCellString(Row row, int idx) {
        if (row == null) return null;
        Cell c = row.getCell(idx);
        if (c == null) return null;
        try {
            if (c.getCellType() == CellType.STRING) return c.getStringCellValue();
            if (c.getCellType() == CellType.NUMERIC) {
                double d = c.getNumericCellValue();
                long lv = (long) d;
                if (Math.abs(d - lv) < 0.00001) return String.valueOf(lv);
                return String.valueOf(d);
            }
            if (c.getCellType() == CellType.BOOLEAN) return String.valueOf(c.getBooleanCellValue());
            if (c.getCellType() == CellType.FORMULA) {
                try { return c.getStringCellValue(); } catch (Exception ex) { return String.valueOf(c.getNumericCellValue()); }
            }
        } catch (Exception ex) { return null; }
        return null;
    }

    private String handleJson(String json) {
        try {
            Map req = mapper.readValue(json, Map.class);
            String action = (String) req.get("action");
            Map<String, Object> resp = new HashMap<>();
            switch (action) {
                case "login": {
                    String user = (String) req.get("username");
                    String pwd = (String) req.get("password");
                    boolean ok = Database.checkLogin(user, pwd);
                    if (ok) {
                        currentUser = user;
                        currentRole = Database.getUserRole(currentUser);
                        resp.put("status", "OK");
                        resp.put("role", currentRole);
                    } else {
                        resp.put("status", "ERR");
                        resp.put("message", "登录失败");
                    }
                    break;
                }
                case "logout": {
                    currentUser = null; currentRole = null;
                    resp.put("status", "OK");
                    break;
                }
                case "register": {
                    String user = (String) req.get("username");
                    String pwd = (String) req.get("password");
                    String fullname = (String) req.get("fullname");
                    String idcard = (String) req.get("idcard");
                    String phone = (String) req.getOrDefault("phone", "");
                    boolean reg = Database.registerPatient(user, pwd, fullname, idcard, phone);
                    resp.put("status", reg?"OK":"ERR");
                    if (!reg) resp.put("message", "用户名已存在");
                    break;
                }
                case "delete_account": {
                    if (currentUser==null) { resp.put("status","ERR"); resp.put("message","请先登录"); break; }
                    if (!"PATIENT".equals(currentRole)) { resp.put("status","ERR"); resp.put("message","仅患者可注销账户"); break; }
                    boolean del = Database.deletePatient(currentUser);
                    if (del) { currentUser=null; currentRole=null; }
                    resp.put("status", del?"OK":"ERR");
                    break;
                }
                case "update_account": {
                    if (currentUser==null) { resp.put("status","ERR"); resp.put("message","请先登录"); break; }
                    if (!"PATIENT".equals(currentRole)) { resp.put("status","ERR"); resp.put("message","仅患者可修改个人信息"); break; }
                    String pwd = (String) req.getOrDefault("password", "");
                    String fullname = (String) req.getOrDefault("fullname", "");
                    String phone = (String) req.getOrDefault("phone", "");
                    boolean up = Database.updatePatient(currentUser, pwd, fullname, phone);
                    resp.put("status", up?"OK":"ERR");
                    break;
                }
                case "search_name": {
                    String q = (String) req.get("q");
                    String raw = Database.searchDoctorsByName(q);
                    resp.put("status","OK");
                    resp.put("raw", raw);
                    break;
                }
                case "search_dept": {
                    String q = (String) req.get("q");
                    String raw = Database.searchDoctorsByDept(q);
                    resp.put("status","OK");
                    resp.put("raw", raw);
                    break;
                }
                case "list_doctors": {
                    List<Map<String,String>> list = Database.getDoctorsList();
                    resp.put("status","OK");
                    resp.put("data", list);
                    break;
                }
                case "list_appts": {
                    int did = (int) ((Number) req.get("doctorId")).intValue();
                    List<Map<String,String>> list = Database.getAppointmentsList(did);
                    resp.put("status","OK"); resp.put("data", list);
                    break;
                }
                case "book": {
                    if (currentUser==null) { resp.put("status","ERR"); resp.put("message","请先登录"); break; }
                    int did = (int)((Number)req.get("doctorId")).intValue();
                    String patientName = (String) req.get("patientName");
                    LocalDateTime time = LocalDateTime.parse((String)req.get("time"), fmt);
                    int result = Database.bookAppointment(did, currentUser, patientName, time);
                    if (result == 0) { resp.put("status","OK"); }
                    else if (result == 1) { resp.put("status","ERR"); resp.put("message","未找到对应排班"); }
                    else if (result == 2) { resp.put("status","ERR"); resp.put("message","您在该时间段已有预约，不能重复预约"); }
                    else if (result == 3) { resp.put("status","ERR"); resp.put("message","该排班已满，无法预约"); }
                    else { resp.put("status","ERR"); resp.put("message","预约失败"); }
                    break;
                }
                case "cancel": {
                    int apid = (int)((Number)req.get("apptId")).intValue();
                    boolean c = Database.cancelAppointment(apid);
                    resp.put("status", c?"OK":"ERR"); if (!c) resp.put("message","未找到预约");
                    break;
                }
                case "add_doctor": {
                    if (currentUser==null || !"ADMIN".equals(currentRole)) { resp.put("status","ERR"); resp.put("message","需要管理员权限"); break; }
                    String name = (String) req.get("name");
                    String dept = (String) req.get("dept");
                    String info = (String) req.getOrDefault("info", "");
                    boolean ok = Database.addDoctor(name, dept, info);
                    resp.put("status", ok?"OK":"ERR");
                    break;
                }
                case "update_doctor": {
                    if (currentUser==null || !"ADMIN".equals(currentRole)) { resp.put("status","ERR"); resp.put("message","需要管理员权限"); break; }
                    int id = (int)((Number)req.get("id")).intValue();
                    String name = (String) req.get("name");
                    String dept = (String) req.get("dept");
                    String info = (String) req.getOrDefault("info", "");
                    boolean ok = Database.updateDoctor(id, name, dept, info);
                    resp.put("status", ok?"OK":"ERR");
                    break;
                }
                case "add_schedule": {
                    if (currentUser==null || !"ADMIN".equals(currentRole)) { resp.put("status","ERR"); resp.put("message","需要管理员权限"); break; }
                    int did = (int)((Number)req.get("doctorId")).intValue();
                    String startS = (String) req.get("start");
                    String endS = (String) req.get("end");
                    String note = (String) req.getOrDefault("note", "");
                    int capacity = 1;
                    if (req.containsKey("capacity")) {
                        try { capacity = ((Number)req.get("capacity")).intValue(); } catch (Exception ignore) {}
                        if (capacity < 1) capacity = 1;
                    }
                    LocalDateTime start = LocalDateTime.parse(startS, fmt);
                    LocalDateTime end = LocalDateTime.parse(endS, fmt);
                    boolean ok = Database.addSchedule(did, start, end, note, capacity);
                    resp.put("status", ok?"OK":"ERR");
                    break;
                }
                case "list_schedules": {
                    int did = (int)((Number)req.get("doctorId")).intValue();
                    List<Map<String,String>> list = Database.getSchedulesList(did);
                    resp.put("status","OK"); resp.put("data", list);
                    break;
                }
                case "list_my_appts": {
                    if (currentUser==null) { resp.put("status","ERR"); resp.put("message","请先登录"); break; }
                    List<Map<String,String>> list = Database.getAppointmentsForPatient(currentUser);
                    resp.put("status","OK"); resp.put("data", list);
                    break;
                }
                case "import_doctors_xls": {
                    if (currentUser==null || !"ADMIN".equals(currentRole)) { resp.put("status","ERR"); resp.put("message","需要管理员权限"); break; }
                    String content = (String) req.get("content");
                    if (content==null) { resp.put("status","ERR"); resp.put("message","缺少content字段"); break; }
                    byte[] bytes = Base64.getDecoder().decode(content);
                    int addedDoctors = 0; int addedSchedules = 0;
                    try (ByteArrayInputStream bin = new ByteArrayInputStream(bytes)) {
                        Workbook wb = WorkbookFactory.create(bin);
                        for (int si=0; si<wb.getNumberOfSheets(); si++) {
                            Sheet sheet = wb.getSheetAt(si);
                            String sname = sheet.getSheetName();
                            if (sname == null) continue;
                            if (sname.equalsIgnoreCase("Doctors")) {
                                for (int r=1; r<=sheet.getLastRowNum(); r++) {
                                    Row row = sheet.getRow(r); if (row==null) continue;
                                    String dname = getCellString(row,0);
                                    String dept = getCellString(row,1);
                                    String info = getCellString(row,2);
                                    if (dname==null || dname.isEmpty()) continue;
                                    boolean ok = Database.addDoctor(dname, dept==null?"":dept, info==null?"":info);
                                    if (ok) addedDoctors++;
                                }
                            } else if (sname.equalsIgnoreCase("Schedules")) {
                                for (int r=1; r<=sheet.getLastRowNum(); r++) {
                                    Row row = sheet.getRow(r); if (row==null) continue;
                                    String didCell = getCellString(row,0);
                                    String startS = getCellString(row,1);
                                    String endS = getCellString(row,2);
                                    String capS = getCellString(row,3);
                                    String note = getCellString(row,4);
                                    if (didCell==null || startS==null || endS==null) continue;
                                    int did = -1;
                                    try { did = Integer.parseInt(didCell); } catch (Exception ex) { did = Database.findDoctorIdByName(didCell); }
                                    if (did <= 0) continue;
                                    java.time.LocalDateTime start = java.time.LocalDateTime.parse(startS, fmt);
                                    java.time.LocalDateTime end = java.time.LocalDateTime.parse(endS, fmt);
                                    int cap = 1; try { cap = Integer.parseInt(capS); } catch (Exception ignore) {}
                                    boolean ok = Database.addSchedule(did, start, end, note==null?"":note, cap);
                                    if (ok) addedSchedules++;
                                }
                            }
                        }
                        wb.close();
                        resp.put("status","OK"); resp.put("addedDoctors", addedDoctors); resp.put("addedSchedules", addedSchedules);
                    } catch (Exception ife) {
                        resp.put("status","ERR"); resp.put("message","Excel 格式无效或读取失败");
                    }
                    break;
                }
                case "export_appointments_xls": {
                    if (currentUser==null || !"ADMIN".equals(currentRole)) { resp.put("status","ERR"); resp.put("message","需要管理员权限"); break; }
                    List<Map<String,String>> appts = Database.listAllAppointments();
                    try (XSSFWorkbook wb = new XSSFWorkbook()) {
                        Sheet s = wb.createSheet("Appointments");
                        Row header = s.createRow(0);
                        String[] heads = new String[]{"id","doctor_id","doctor_name","dept","patient_username","patient_name","appt_time"};
                        for (int i=0;i<heads.length;i++) header.createCell(i).setCellValue(heads[i]);
                        int r=1;
                        for (Map<String,String> a : appts) {
                            Row row = s.createRow(r++);
                            for (int c=0;c<heads.length;c++) row.createCell(c).setCellValue(a.getOrDefault(heads[c], ""));
                        }
                        ByteArrayOutputStream bout = new ByteArrayOutputStream();
                        wb.write(bout);
                        String b64 = Base64.getEncoder().encodeToString(bout.toByteArray());
                        resp.put("status","OK"); resp.put("content", b64); resp.put("filename","appointments.xlsx");
                    }
                    break;
                }
                case "generate_report_pdf": {
                    if (currentUser==null || !"ADMIN".equals(currentRole)) { resp.put("status","ERR"); resp.put("message","需要管理员权限"); break; }
                    try {
                        java.util.Map<String,Integer> deptCounts = Database.getAppointmentsCountByDept();
                        List<Map<String,String>> workload = Database.getDoctorWorkload();
                        PDDocument doc = new PDDocument();
                        PDPage page = new PDPage(PDRectangle.LETTER);
                        doc.addPage(page);
                        PDPageContentStream cs = new PDPageContentStream(doc, page);
                        // try to load a TTF that supports Chinese on Windows; fallback to Type1 if none
                        PDType0Font cjkFont = null;
                        try {
                            // String[] candidates = new String[]{"C:\\Windows\\Fonts\\msyh.ttc","C:\\Windows\\Fonts\\msyh.ttf","C:\\Windows\\Fonts\\simhei.ttf","C:\\Windows\\Fonts\\simsun.ttc","C:\\Windows\\Fonts\\simsun.ttf"};
                            // 字符加载问题，放弃ttc格式
                            String[] candidates = new String[]{"C:\\Windows\\Fonts\\msyh.ttf","C:\\Windows\\Fonts\\simhei.ttf","C:\\Windows\\Fonts\\simsun.ttf"};
                            for (String p : candidates) {
                                java.io.File ff = new java.io.File(p);
                                if (ff.exists()) { cjkFont = PDType0Font.load(doc, ff); break; }
                            }
                        } catch (Exception ignore) { cjkFont = null; }
                        if (cjkFont != null) {
                            cs.setFont(cjkFont, 14);
                            cs.beginText(); cs.newLineAtOffset(50, 700);
                            cs.showText("医院预约统计报告"); cs.endText();
                            cs.setFont(cjkFont, 12);
                        } else {
                            cs.setFont(PDType1Font.HELVETICA_BOLD, 14);
                            cs.beginText(); cs.newLineAtOffset(50, 700);
                            cs.showText("HABMS Report"); cs.endText();
                            cs.setFont(PDType1Font.HELVETICA, 12);
                        }
                        int y = 660;
                        cs.beginText(); cs.newLineAtOffset(50, y);
                        if (cjkFont != null) cs.showText("各科室预约量:"); else cs.showText("Dept counts:");
                        cs.endText(); y -= 20;
                        for (var e2 : deptCounts.entrySet()) {
                            String text = e2.getKey() + ": " + e2.getValue();
                            if (cjkFont == null) text = text.replaceAll("[^\\x00-\\x7F]","?");
                            cs.beginText(); cs.newLineAtOffset(60, y); cs.showText(text); cs.endText(); y -= 16;
                            if (y < 80) { cs.close(); page = new PDPage(PDRectangle.LETTER); doc.addPage(page); cs = new PDPageContentStream(doc, page); y = 700; }
                        }
                        y -= 10;
                        cs.beginText(); cs.newLineAtOffset(50, y); if (cjkFont != null) cs.showText("医生工作量:"); else cs.showText("Doctor workload:"); cs.endText(); y -= 20;
                        for (Map<String,String> d : workload) {
                            String line = d.get("doctor_name") + " (" + d.get("dept") + ") - " + d.get("appointments");
                            if (cjkFont == null) line = line.replaceAll("[^\\x00-\\x7F]","?");
                            cs.beginText(); cs.newLineAtOffset(60, y); cs.showText(line); cs.endText(); y -= 16;
                            if (y < 80) { cs.close(); page = new PDPage(PDRectangle.LETTER); doc.addPage(page); cs = new PDPageContentStream(doc, page); y = 700; }
                        }
                        cs.close();
                        ByteArrayOutputStream bout = new ByteArrayOutputStream();
                        doc.save(bout); doc.close();
                        String b64 = Base64.getEncoder().encodeToString(bout.toByteArray());
                        resp.put("status","OK"); resp.put("content", b64); resp.put("filename","report.pdf");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        resp.put("status","ERR"); resp.put("message","生成PDF失败");
                    }
                    break;
                }
                default: {
                    resp.put("status","ERR"); resp.put("message","未知命令");
                }
            }
            return mapper.writeValueAsString(resp);
        } catch (SQLException ex) {
            ex.printStackTrace();
            try { return mapper.writeValueAsString(Map.of("status","ERR","message","数据库错误")); } catch (IOException e) { return "{\"status\":\"ERR\",\"message\":\"数据库错误\"}"; }
        } catch (Exception ex) {
            ex.printStackTrace();
            try { return mapper.writeValueAsString(Map.of("status","ERR","message","处理失败")); } catch (IOException e) { return "{\"status\":\"ERR\",\"message\":\"处理失败\"}"; }
        }
    }
}
