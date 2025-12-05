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
                    boolean booked = Database.bookAppointment(did, currentUser, patientName, time);
                    resp.put("status", booked?"OK":"ERR"); if (!booked) resp.put("message","时间冲突");
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
                    LocalDateTime t = LocalDateTime.parse((String)req.get("time"), fmt);
                    String note = (String) req.getOrDefault("note", "");
                    boolean ok = Database.addSchedule(did, t, note);
                    resp.put("status", ok?"OK":"ERR");
                    break;
                }
                case "list_schedules": {
                    int did = (int)((Number)req.get("doctorId")).intValue();
                    List<Map<String,String>> list = Database.getSchedulesList(did);
                    resp.put("status","OK"); resp.put("data", list);
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
