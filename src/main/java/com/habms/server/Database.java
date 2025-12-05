package com.habms.server;

import java.sql.*;
import java.time.LocalDateTime;

public class Database {
    private static final String DB_URL = "jdbc:derby:habmsdb;create=true";

    public static void init() throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            Statement st = conn.createStatement();
            // users
            st.executeUpdate("CREATE TABLE users (id INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1), username VARCHAR(50), password VARCHAR(50))");
        } catch (SQLException e) {
            if (!tableExists(e)) throw e;
        }
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            Statement st = conn.createStatement();
            // doctors
            st.executeUpdate("CREATE TABLE doctors (id INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1), name VARCHAR(100), dept VARCHAR(100))");
        } catch (SQLException e) {
            if (!tableExists(e)) throw e;
        }
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            Statement st = conn.createStatement();
            // appointments
            st.executeUpdate("CREATE TABLE appointments (id INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1), doctor_id INT, patient_name VARCHAR(100), appt_time TIMESTAMP)");
        } catch (SQLException e) {
            if (!tableExists(e)) throw e;
        }
        // insert sample data if empty
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM doctors");
            rs.next();
            if (rs.getInt(1) == 0) {
                st.executeUpdate("INSERT INTO doctors(name, dept) VALUES('王医生','内科')");
                st.executeUpdate("INSERT INTO doctors(name, dept) VALUES('李医生','外科')");
            }
            rs = st.executeQuery("SELECT COUNT(*) FROM users");
            rs.next();
            if (rs.getInt(1) == 0) {
                st.executeUpdate("INSERT INTO users(username,password) VALUES('admin','admin')");
            }
        }
    }

    private static boolean tableExists(SQLException e) {
        // Derby throws X0Y32: Table/View already exists in schema
        return e.getSQLState() != null && e.getSQLState().startsWith("X0Y");
    }

    public static boolean checkLogin(String username, String password) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE username=? AND password=?");
            ps.setString(1, username);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt(1) > 0;
        }
    }

    public static String listDoctors() throws SQLException {
        StringBuilder sb = new StringBuilder();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("SELECT id,name,dept FROM doctors");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                sb.append(rs.getInt("id")).append("|")
                  .append(rs.getString("name")).append("|")
                  .append(rs.getString("dept")).append("\n");
            }
        }
        return sb.toString();
    }

    public static String listAppointments(int doctorId) throws SQLException {
        StringBuilder sb = new StringBuilder();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("SELECT id,patient_name,appt_time FROM appointments WHERE doctor_id=? ORDER BY appt_time");
            ps.setInt(1, doctorId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Timestamp t = rs.getTimestamp("appt_time");
                sb.append(rs.getInt("id")).append("|")
                  .append(rs.getString("patient_name")).append("|")
                  .append(t.toString()).append("\n");
            }
        }
        return sb.toString();
    }

    public static boolean bookAppointment(int doctorId, String patientName, LocalDateTime time) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // check conflict
            PreparedStatement check = conn.prepareStatement("SELECT COUNT(*) FROM appointments WHERE doctor_id=? AND appt_time=?");
            check.setInt(1, doctorId);
            check.setTimestamp(2, Timestamp.valueOf(time));
            ResultSet rs = check.executeQuery();
            rs.next();
            if (rs.getInt(1) > 0) return false;
            PreparedStatement ins = conn.prepareStatement("INSERT INTO appointments(doctor_id,patient_name,appt_time) VALUES(?,?,?)");
            ins.setInt(1, doctorId);
            ins.setString(2, patientName);
            ins.setTimestamp(3, Timestamp.valueOf(time));
            ins.executeUpdate();
            return true;
        }
    }

    public static boolean cancelAppointment(int apptId) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement("DELETE FROM appointments WHERE id=?");
            ps.setInt(1, apptId);
            return ps.executeUpdate() > 0;
        }
    }
}
