package com.habms.server;

import java.io.*;
import java.net.Socket;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ServerHandler implements Runnable {
    private final Socket socket;
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    public ServerHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("收到: " + line);
                String reply = handle(line);
                out.write(reply);
                out.write("\n<<END>>\n");
                out.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String handle(String cmd) {
        try {
            String[] parts = cmd.split("\\|", -1);
            switch (parts[0]) {
                case "LOGIN":
                    boolean ok = Database.checkLogin(parts[1], parts[2]);
                    return ok ? "OK" : "ERR|登录失败";
                case "LIST_DOCTORS":
                    return "OK|" + Database.listDoctors();
                case "LIST_APPTS":
                    int docId = Integer.parseInt(parts[1]);
                    return "OK|" + Database.listAppointments(docId);
                case "BOOK":
                    int dId = Integer.parseInt(parts[1]);
                    String patient = parts[2];
                    LocalDateTime t = LocalDateTime.parse(parts[3], fmt);
                    boolean booked = Database.bookAppointment(dId, patient, t);
                    return booked ? "OK" : "ERR|时间冲突";
                case "CANCEL":
                    int apid = Integer.parseInt(parts[1]);
                    boolean c = Database.cancelAppointment(apid);
                    return c ? "OK" : "ERR|未找到预约";
                default:
                    return "ERR|未知命令";
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            return "ERR|数据库错误";
        } catch (Exception ex) {
            ex.printStackTrace();
            return "ERR|处理失败";
        }
    }
}
