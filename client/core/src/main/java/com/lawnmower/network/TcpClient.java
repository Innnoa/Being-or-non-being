package com.lawnmower.network;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;

import com.lawnmower.Config;
import lawnmower.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.io.IOException;

public class TcpClient {
    private static final Logger log = LoggerFactory.getLogger(TcpClient.class);
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

    public void sendCreateRoom(String roomName, int maxPlayers) throws IOException {
        var msg = Message.C2S_CreateRoom.newBuilder()
                .setRoomName(roomName)
                .setMaxPlayers(maxPlayers)
                .build();
        sendPacket(Message.MessageType.MSG_C2S_CREATE_ROOM, msg);
    }

    public void sendGetRoomList() throws IOException {
        var msg = Message.C2S_GetRoomList.newBuilder().build();
        sendPacket(Message.MessageType.MSG_C2S_GET_ROOM_LIST, msg);
    }

    public void sendJoinRoom(int roomId) throws IOException {
        var msg = Message.C2S_JoinRoom.newBuilder()
                .setRoomId(roomId)
                .build();
        sendPacket(Message.MessageType.MSG_C2S_JOIN_ROOM, msg);
    }

    public void sendLeaveRoom() throws IOException {
        var msg = Message.C2S_LeaveRoom.newBuilder().build();
        sendPacket(Message.MessageType.MSG_C2S_LEAVE_ROOM, msg);
    }

    public void sendSetReady(boolean ready) throws IOException {
        var msg = Message.C2S_SetReady.newBuilder()
                .setIsReady(ready)
                .build();
        sendPacket(Message.MessageType.MSG_C2S_SET_READY, msg);
    }

    public void sendStartGame() throws IOException {
        var msg = Message.C2S_StartGame.newBuilder().build();
        sendPacket(Message.MessageType.MSG_C2S_START_GAME, msg);
    }


    public void sendPacket(Message.Packet packet) throws IOException {
        byte[] data = packet.toByteArray();

        // 写长度（4字节）
        DataOutputStream dos = new DataOutputStream(
                socket.getOutputStream());
        dos.writeInt(data.length);
        dos.write(data);
        dos.flush();
    }
    // ====== 新增方法：发送玩家输入 ======
    public void sendPlayerInput(Message.C2S_PlayerInput input) throws IOException {
        // 注意：input 已包含 player_id、方向、攻击状态等
        sendPacket(Message.MessageType.MSG_C2S_PLAYER_INPUT, input);
    }

    public void sendPacket(Message.MessageType type, MessageLite payload) throws IOException {
        Message.Packet packet = Message.Packet.newBuilder()
                .setMsgType(type)
                .setPayload(payload.toByteString())
                .build();

        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
        byte[] data = packet.toByteArray();
        dos.writeInt(data.length);
        dos.write(data);
        dos.flush();
    }

    public Message.Packet receivePacket() throws IOException {
        DataInputStream dis = new DataInputStream(
                socket.getInputStream());

        int len = dis.readInt();
        byte[] data = new byte[len];
        dis.readFully(data);

        return Message.Packet.parseFrom(data);
    }


    public void close() throws IOException {
        Message.Packet packet = Message.Packet.newBuilder()
                        .setMsgType(Message.MessageType.MSG_C2S_REQUEST_QUIT)
                        .setPayload(Config.byteString)
                        .build();
        sendPacket(packet);
        socket.close();
    }

}
