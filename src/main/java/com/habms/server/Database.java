package com.habms.server;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Database {
    private static final String DB_URL = "jdbc:derby:habmsdb;create=true";

    public static void init() throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            Statement st = conn.createStatement();
            // users: role (ADMIN/PATIENT), fullname, idcard, phone
            st.executeUpdate("CREATE TABLE users (id INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1), username VARCHAR(50) UNIQUE, password VARCHAR(100), role VARCHAR(20), fullname VARCHAR(100), idcard VARCHAR(50), phone VARCHAR(50))");
        } catch (SQLException e) {
            if (!tableExists(e)) throw e;
        }
        // If users table existed previously with smaller password column, try to enlarge it
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            Statement st = conn.createStatement();
            st.executeUpdate("ALTER TABLE users ALTER COLUMN password SET DATA TYPE VARCHAR(100)");
        } catch (SQLException ignored) {
            // ignore if cannot alter (e.g., table didn't exist or already correct)
        }
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            Statement st = conn.createStatement();
            // doctors
            st.executeUpdate("CREATE TABLE doctors (id INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1), name VARCHAR(100), dept VARCHAR(100), info VARCHAR(255))");
        } catch (SQLException e) {
            if (!tableExists(e)) throw e;
        }
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            Statement st = conn.createStatement();
            // appointments
            st.executeUpdate("CREATE TABLE appointments (id INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1), doctor_id INT, patient_username VARCHAR(50), patient_name VARCHAR(100), appt_time TIMESTAMP)");
        } catch (SQLException e) {
            if (!tableExists(e)) throw e;
        }
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            Statement st = conn.createStatement();
            // schedules: doctor_id + start_time + end_time + note + capacity
            st.executeUpdate("CREATE TABLE schedules (id INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1), doctor_id INT, start_time TIMESTAMP, end_time TIMESTAMP, note VARCHAR(255), capacity INT)");
        } catch (SQLException e) {
            if (!tableExists(e)) throw e;
        }
        // Try to migrate old schema: if 'slot_time' exists, add start_time/end_time and copy slot_time -> start_time, end_time = slot_time + 1 hour
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            Statement st = conn.createStatement();
            // attempt to add start_time/end_time if missing
            try { st.executeUpdate("ALTER TABLE schedules ADD COLUMN start_time TIMESTAMP"); } catch (SQLException ignore) {}
            try { st.executeUpdate("ALTER TABLE schedules ADD COLUMN end_time TIMESTAMP"); } catch (SQLException ignore) {}
            try { st.executeUpdate("ALTER TABLE schedules ADD COLUMN capacity INT"); } catch (SQLException ignore) {}
            // if slot_time exists, migrate rows
            try {
                ResultSet rs = st.executeQuery("SELECT id, slot_time FROM schedules");
                java.util.List<Integer> ids = new java.util.ArrayList<>();
                java.util.List<Timestamp> slots = new java.util.ArrayList<>();
                while (rs.next()) { ids.add(rs.getInt(1)); slots.add(rs.getTimestamp(2)); }
                PreparedStatement upd = conn.prepareStatement("UPDATE schedules SET start_time=?, end_time=?, capacity=? WHERE id=?");
                for (int i=0;i<ids.size();i++) {
                    Timestamp s = slots.get(i);
                    if (s==null) continue;
                    Timestamp end = new Timestamp(s.getTime() + 3600L*1000L); // +1 hour
                    upd.setTimestamp(1, s);
                    upd.setTimestamp(2, end);
                    upd.setInt(3, 1);
                    upd.setInt(4, ids.get(i));
                    upd.executeUpdate();
                }
            } catch (SQLException ignore) {
                // slot_time might not exist, ignore
            }
        } catch (SQLException ignored) {
            // ignore migration failures
        }
        // insert sample data and admin if empty
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM doctors");
            rs.next();
            if (rs.getInt(1) == 0) {
                st.executeUpdate("INSERT INTO doctors(name, dept, info) VALUES('王医生','内科','擅长内科常见病')");
                st.executeUpdate("INSERT INTO doctors(name, dept, info) VALUES('李医生','外科','擅长普外手术')");
            }
            rs = st.executeQuery("SELECT COUNT(*) FROM users WHERE role='ADMIN'");
            rs.next();
            if (rs.getInt(1) == 0) {
                String hashed = hashPassword("admin");
                st.executeUpdate("INSERT INTO users(username,password,role,fullname,idcard,phone) VALUES('admin','"+hashed+"','ADMIN','系统管理员','000000000000000000','')");
            }
        }
    }

    public static List<Map<String, String>> getDoctorsList() throws SQLException {
        List<Map<String, String>> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("SELECT id,name,dept,info FROM doctors");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, String> m = new HashMap<>();
                m.put("id", String.valueOf(rs.getInt("id")));
                m.put("name", rs.getString("name"));
                m.put("dept", rs.getString("dept"));
                m.put("info", rs.getString("info"));
                list.add(m);
            }
        }
        return list;
    }

    public static List<Map<String, String>> getAppointmentsList(int doctorId) throws SQLException {
        List<Map<String, String>> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("SELECT id,patient_username,patient_name,appt_time FROM appointments WHERE doctor_id=? ORDER BY appt_time");
            ps.setInt(1, doctorId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, String> m = new HashMap<>();
                m.put("id", String.valueOf(rs.getInt("id")));
                m.put("patient_username", rs.getString("patient_username"));
                m.put("patient_name", rs.getString("patient_name"));
                m.put("appt_time", rs.getTimestamp("appt_time").toString());
                list.add(m);
            }
        }
        return list;
    }

    public static List<Map<String, String>> getSchedulesList(int doctorId) throws SQLException {
        List<Map<String, String>> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("SELECT id,start_time,end_time,note,capacity FROM schedules WHERE doctor_id=? ORDER BY start_time");
            ps.setInt(1, doctorId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, String> m = new HashMap<>();
                m.put("id", String.valueOf(rs.getInt("id")));
                Timestamp start = rs.getTimestamp("start_time");
                Timestamp end = rs.getTimestamp("end_time");
                m.put("start_time", start==null?"":start.toString());
                m.put("end_time", end==null?"":end.toString());
                m.put("note", rs.getString("note"));
                int capacity = rs.getInt("capacity");
                // compute current booked count in interval [start, end)
                PreparedStatement pc = conn.prepareStatement("SELECT COUNT(*) FROM appointments WHERE doctor_id=? AND appt_time>=? AND appt_time<?");
                pc.setInt(1, doctorId);
                pc.setTimestamp(2, start);
                pc.setTimestamp(3, end);
                ResultSet rc = pc.executeQuery(); rc.next();
                int booked = rc.getInt(1);
                m.put("capacity", String.valueOf(capacity));
                m.put("booked", String.valueOf(booked));
                m.put("available", String.valueOf(Math.max(0, capacity - booked)));
                list.add(m);
            }
        }
        return list;
    }

    // in-memory locks per schedule id to avoid race conditions in this JVM
    private static final java.util.concurrent.ConcurrentHashMap<Integer, Object> scheduleLocks = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean tableExists(SQLException e) {
        // Derby throws X0Y32: Table/View already exists in schema
        return e.getSQLState() != null && e.getSQLState().startsWith("X0Y");
    }

    public static boolean checkLogin(String username, String password) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("SELECT password FROM users WHERE username=?");
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return false;
            String stored = rs.getString(1);
            if (stored == null) return false;
            String candidateHash = hashPassword(password);
            // If stored looks like a sha256 hex (64 chars) compare hashes
            if (stored.length() == 64) {
                return stored.equalsIgnoreCase(candidateHash);
            } else {
                // legacy plaintext: compare and upgrade to hashed
                if (stored.equals(password)) {
                    PreparedStatement upd = conn.prepareStatement("UPDATE users SET password=? WHERE username=?");
                    upd.setString(1, candidateHash);
                    upd.setString(2, username);
                    upd.executeUpdate();
                    return true;
                }
                return false;
            }
        }
    }

    public static String getUserRole(String username) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("SELECT role FROM users WHERE username=?");
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString(1);
            return null;
        }
    }

    public static boolean registerPatient(String username, String password, String fullname, String idcard, String phone) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement check = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE username=?");
            check.setString(1, username);
            ResultSet rs = check.executeQuery(); rs.next();
            if (rs.getInt(1) > 0) return false;
            PreparedStatement ps = conn.prepareStatement("INSERT INTO users(username,password,role,fullname,idcard,phone) VALUES(?,?,?,?,?,?)");
            ps.setString(1, username); ps.setString(2, hashPassword(password)); ps.setString(3, "PATIENT"); ps.setString(4, fullname); ps.setString(5, idcard); ps.setString(6, phone);
            ps.executeUpdate();
            return true;
        }
    }

    public static boolean deletePatient(String username) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE username=? AND role='PATIENT'");
            ps.setString(1, username);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean updatePatient(String username, String newPassword, String newFullname, String newPhone) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("UPDATE users SET password=?, fullname=?, phone=? WHERE username=? AND role='PATIENT'");
            ps.setString(1, hashPassword(newPassword)); ps.setString(2, newFullname); ps.setString(3, newPhone); ps.setString(4, username);
            return ps.executeUpdate() > 0;
        }
    }

    private static String hashPassword(String pwd) {
        if (pwd == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(pwd.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String listDoctors() throws SQLException {
        StringBuilder sb = new StringBuilder();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("SELECT id,name,dept,info FROM doctors");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                sb.append(rs.getInt("id")).append("|")
                  .append(rs.getString("name")).append("|")
                  .append(rs.getString("dept")).append("|")
                  .append(rs.getString("info")).append("\n");
            }
        }
        return sb.toString();
    }

    public static String listAppointments(int doctorId) throws SQLException {
        StringBuilder sb = new StringBuilder();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("SELECT id,patient_username,patient_name,appt_time FROM appointments WHERE doctor_id=? ORDER BY appt_time");
            ps.setInt(1, doctorId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Timestamp t = rs.getTimestamp("appt_time");
                sb.append(rs.getInt("id")).append("|")
                  .append(rs.getString("patient_username")).append("|")
                  .append(rs.getString("patient_name")).append("|")
                  .append(t.toString()).append("\n");
            }
        }
        return sb.toString();
    }

    public static List<Map<String, String>> getAppointmentsForPatient(String patientUsername) throws SQLException {
        List<Map<String, String>> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("SELECT id,doctor_id,patient_username,patient_name,appt_time FROM appointments WHERE patient_username=? ORDER BY appt_time");
            ps.setString(1, patientUsername);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, String> m = new HashMap<>();
                m.put("id", String.valueOf(rs.getInt("id")));
                m.put("doctor_id", String.valueOf(rs.getInt("doctor_id")));
                m.put("patient_username", rs.getString("patient_username"));
                m.put("patient_name", rs.getString("patient_name"));
                m.put("appt_time", rs.getTimestamp("appt_time").toString());
                list.add(m);
            }
        }
        return list;
    }

    public static int findDoctorIdByName(String name) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("SELECT id FROM doctors WHERE name = ?");
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
            return -1;
        }
    }

    public static List<Map<String, String>> listAllAppointments() throws SQLException {
        List<Map<String, String>> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("SELECT a.id,a.doctor_id,d.name as doctor_name,d.dept,a.patient_username,a.patient_name,a.appt_time FROM appointments a LEFT JOIN doctors d ON a.doctor_id=d.id ORDER BY a.appt_time");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, String> m = new HashMap<>();
                m.put("id", String.valueOf(rs.getInt("id")));
                m.put("doctor_id", String.valueOf(rs.getInt("doctor_id")));
                m.put("doctor_name", rs.getString("doctor_name"));
                m.put("dept", rs.getString("dept"));
                m.put("patient_username", rs.getString("patient_username"));
                m.put("patient_name", rs.getString("patient_name"));
                m.put("appt_time", rs.getTimestamp("appt_time").toString());
                list.add(m);
            }
        }
        return list;
    }

    public static java.util.Map<String, Integer> getAppointmentsCountByDept() throws SQLException {
        java.util.Map<String, Integer> map = new java.util.HashMap<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("SELECT d.dept, COUNT(*) as cnt FROM appointments a JOIN doctors d ON a.doctor_id=d.id GROUP BY d.dept");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                map.put(rs.getString("dept"), rs.getInt("cnt"));
            }
        }
        return map;
    }

    public static List<Map<String, String>> getDoctorWorkload() throws SQLException {
        List<Map<String, String>> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("SELECT d.id,d.name,d.dept, COUNT(a.id) as cnt FROM doctors d LEFT JOIN appointments a ON a.doctor_id=d.id GROUP BY d.id,d.name,d.dept ORDER BY cnt DESC");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, String> m = new HashMap<>();
                m.put("doctor_id", String.valueOf(rs.getInt("id")));
                m.put("doctor_name", rs.getString("name"));
                m.put("dept", rs.getString("dept"));
                m.put("appointments", String.valueOf(rs.getInt("cnt")));
                list.add(m);
            }
        }
        return list;
    }

    /**
     * Try to book an appointment.
     * Return codes:
     * 0 = success
     * 1 = no matching schedule for that time
     * 2 = patient already has appointment in that interval
     * 3 = schedule full
     */
    public static int bookAppointment(int doctorId, String patientUsername, String patientName, LocalDateTime time) throws SQLException {
        // find schedule for doctor where time is inside [start_time, end_time)
        try (Connection conn0 = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn0.prepareStatement("SELECT id, capacity, start_time, end_time FROM schedules WHERE doctor_id=? AND start_time<=? AND end_time>?");
            ps.setInt(1, doctorId);
            ps.setTimestamp(2, Timestamp.valueOf(time));
            ps.setTimestamp(3, Timestamp.valueOf(time));
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return 1; // no matching schedule
            int scheduleId = rs.getInt("id");
            int capacity = rs.getInt("capacity");
            Timestamp start = rs.getTimestamp("start_time");
            Timestamp end = rs.getTimestamp("end_time");
            Object lock = scheduleLocks.computeIfAbsent(scheduleId, k -> new Object());
            synchronized (lock) {
                Connection conn = DriverManager.getConnection(DB_URL);
                try {
                    conn.setAutoCommit(false);
                    // check patient already has appointment in this interval
                    PreparedStatement pCheckPatient = conn.prepareStatement("SELECT COUNT(*) FROM appointments WHERE patient_username=? AND appt_time>=? AND appt_time<?");
                    pCheckPatient.setString(1, patientUsername);
                    pCheckPatient.setTimestamp(2, start);
                    pCheckPatient.setTimestamp(3, end);
                    ResultSet rcp = pCheckPatient.executeQuery(); rcp.next();
                    if (rcp.getInt(1) > 0) { conn.rollback(); conn.setAutoCommit(true); return 2; }

                    // count current bookings for this schedule interval
                    PreparedStatement pCount = conn.prepareStatement("SELECT COUNT(*) FROM appointments WHERE doctor_id=? AND appt_time>=? AND appt_time<?");
                    pCount.setInt(1, doctorId);
                    pCount.setTimestamp(2, start);
                    pCount.setTimestamp(3, end);
                    ResultSet rc = pCount.executeQuery(); rc.next();
                    int booked = rc.getInt(1);
                    if (booked >= capacity) { conn.rollback(); conn.setAutoCommit(true); return 3; }

                    PreparedStatement ins = conn.prepareStatement("INSERT INTO appointments(doctor_id,patient_username,patient_name,appt_time) VALUES(?,?,?,?)");
                    ins.setInt(1, doctorId);
                    ins.setString(2, patientUsername);
                    ins.setString(3, patientName);
                    ins.setTimestamp(4, Timestamp.valueOf(time));
                    ins.executeUpdate();
                    conn.commit(); conn.setAutoCommit(true);
                    return 0;
                } catch (SQLException ex) {
                    try { conn.rollback(); } catch (SQLException ignore) {}
                    try { conn.setAutoCommit(true); } catch (SQLException ignore) {}
                    throw ex;
                } finally {
                    try { conn.close(); } catch (SQLException ignore) {}
                }
            }
        }
    }

    public static boolean cancelAppointment(int apptId) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("DELETE FROM appointments WHERE id=?");
            ps.setInt(1, apptId);
            return ps.executeUpdate() > 0;
        }
    }

    // search doctors
    public static List<Map<String, String>> searchDoctorsByName(String name) throws SQLException {
        List<Map<String, String>> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("SELECT id,name,dept,info FROM doctors WHERE name LIKE ?");
            ps.setString(1, "%" + name + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, String> m = new HashMap<>();
                m.put("id", String.valueOf(rs.getInt("id")));
                m.put("name", rs.getString("name"));
                m.put("dept", rs.getString("dept"));
                m.put("info", rs.getString("info"));
                list.add(m);
            }
        }
        return list;
    }

    public static List<Map<String, String>> searchDoctorsByDept(String dept) throws SQLException {
        List<Map<String, String>> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("SELECT id,name,dept,info FROM doctors WHERE dept LIKE ?");
            ps.setString(1, "%" + dept + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, String> m = new HashMap<>();
                m.put("id", String.valueOf(rs.getInt("id")));
                m.put("name", rs.getString("name"));
                m.put("dept", rs.getString("dept"));
                m.put("info", rs.getString("info"));
                list.add(m);
            }
        }
        return list;
    }

    public static boolean addDoctor(String name, String dept, String info) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("INSERT INTO doctors(name,dept,info) VALUES(?,?,?)");
            ps.setString(1, name); ps.setString(2, dept); ps.setString(3, info);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean updateDoctor(int id, String name, String dept, String info) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("UPDATE doctors SET name=?, dept=?, info=? WHERE id=?");
            ps.setString(1, name); ps.setString(2, dept); ps.setString(3, info); ps.setInt(4, id);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean addSchedule(int doctorId, LocalDateTime slot, String note) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("INSERT INTO schedules(doctor_id,start_time,end_time,note,capacity) VALUES(?,?,?,?,?)");
            ps.setInt(1, doctorId); ps.setTimestamp(2, Timestamp.valueOf(slot)); ps.setTimestamp(3, Timestamp.valueOf(slot.plusHours(1))); ps.setString(4, note); ps.setInt(5, 1);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean addSchedule(int doctorId, LocalDateTime slot, String note, int capacity) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("INSERT INTO schedules(doctor_id,start_time,end_time,note,capacity) VALUES(?,?,?,?,?)");
            ps.setInt(1, doctorId); ps.setTimestamp(2, Timestamp.valueOf(slot)); ps.setTimestamp(3, Timestamp.valueOf(slot.plusHours(1))); ps.setString(4, note); ps.setInt(5, capacity);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean addSchedule(int doctorId, LocalDateTime start, LocalDateTime end, String note, int capacity) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("INSERT INTO schedules(doctor_id,start_time,end_time,note,capacity) VALUES(?,?,?,?,?)");
            ps.setInt(1, doctorId); ps.setTimestamp(2, Timestamp.valueOf(start)); ps.setTimestamp(3, Timestamp.valueOf(end)); ps.setString(4, note); ps.setInt(5, capacity);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean updateSchedule(int scheduleId, LocalDateTime slot, String note) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("UPDATE schedules SET slot_time=?, note=? WHERE id=?");
            ps.setTimestamp(1, Timestamp.valueOf(slot)); ps.setString(2, note); ps.setInt(3, scheduleId);
            return ps.executeUpdate() > 0;
        }
    }

    public static String listSchedules(int doctorId) throws SQLException {
        StringBuilder sb = new StringBuilder();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("SELECT id,start_time,end_time,note,capacity FROM schedules WHERE doctor_id=? ORDER BY start_time");
            ps.setInt(1, doctorId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Timestamp start = rs.getTimestamp("start_time");
                Timestamp end = rs.getTimestamp("end_time");
                int capacity = rs.getInt("capacity");
                PreparedStatement pc = conn.prepareStatement("SELECT COUNT(*) FROM appointments WHERE doctor_id=? AND appt_time>=? AND appt_time<?");
                pc.setInt(1, doctorId);
                pc.setTimestamp(2, start);
                pc.setTimestamp(3, end);
                ResultSet rc = pc.executeQuery(); rc.next();
                int booked = rc.getInt(1);
                sb.append(rs.getInt("id")).append("|")
                  .append(start.toString()).append(" to ").append(end.toString()).append("|")
                  .append(rs.getString("note")).append("|")
                  .append(capacity).append("|")
                  .append(booked).append("|")
                  .append(Math.max(0, capacity-booked)).append("\n");
            }
        }
        return sb.toString();
    }

    public static void shutdown() {
        try {
            DriverManager.getConnection("jdbc:derby:;shutdown=true");
        } catch (SQLException se) {
            // Derby throws SQL exception with SQLState XJ015 on successful shutdown
            String state = se.getSQLState();
            if (state != null && (state.equals("XJ015") || state.equals("08006"))) {
                System.out.println("Derby shut down normally.");
            } else {
                System.err.println("Derby shutdown error: " + se.getMessage());
            }
        }
    }
}
