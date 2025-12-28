package com.habms.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Map;

/**
 * Thin network service that handles socket lifecycle and JSON request/response.
 */
public class ClientService {
    private final String host;
    private final int port;
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private static final ObjectMapper mapper = new ObjectMapper();

    public ClientService(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public Map send(Map req) throws IOException {
        ensureConnection();
        String payload = mapper.writeValueAsString(req);
        out.write(payload);
        out.write("\n");
        out.flush();
        String line = in.readLine();
        if (line == null) throw new IOException("服务器关闭连接");
        return mapper.readValue(line, Map.class);
    }

    public void close() {
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
    }

    private void ensureConnection() throws IOException {
        if (socket != null && socket.isConnected() && !socket.isClosed()) return;
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
    }
}
