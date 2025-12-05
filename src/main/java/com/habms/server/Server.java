package com.habms.server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class Server {
    private static final int PORT = 9090;

    public static void main(String[] args) {
        System.out.println("启动 HABMS 服务器...");
        try {
            Database.init();
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }
        try (ServerSocket ss = new ServerSocket(PORT)) {
            System.out.println("服务器监听端口 " + PORT);
            while (true) {
                Socket s = ss.accept();
                System.out.println("接入客户端: " + s.getRemoteSocketAddress());
                new Thread(new ServerHandler(s)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
