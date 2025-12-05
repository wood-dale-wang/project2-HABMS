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
        ServerSocket ss = null;
        try {
            ss = new ServerSocket(PORT);
            ServerSocket finalSs = ss;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("服务器正在关闭...");
                try {
                    if (finalSs != null && !finalSs.isClosed()) finalSs.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                Database.shutdown();
            }));
            System.out.println("服务器监听端口 " + PORT);
            while (true) {
                try {
                    Socket s = ss.accept();
                    System.out.println("接入客户端: " + s.getRemoteSocketAddress());
                    new Thread(new ServerHandler(s)).start();
                } catch (IOException ex) {
                    if (ss.isClosed()) {
                        System.out.println("Server socket closed, 退出监听循环。");
                        break;
                    } else {
                        ex.printStackTrace();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
