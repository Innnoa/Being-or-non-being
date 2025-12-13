package com.lawnmower.network;

import java.io.*;
import java.net.*;
import java.io.IOException;

public class TcpClient {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
        System.out.println("已连接到 " + host + ":" + port);
    }

    public void send(String message) {
        out.println(message);
    }

    public String receive() throws IOException {
        return in.readLine();
    }

    public void close() throws IOException {
        socket.close();
    }

    public static void main(String[] args) {
        TcpClient client = new TcpClient();
        try {
            client.connect("192.168.45.244", 7777);

            client.send("回声");
            String response = client.receive();
            System.out.println("服务器响应: " + response);

            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
