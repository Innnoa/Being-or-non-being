package com.lawnmower.network;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.lawnmower.Config;
import lawnmower.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 负责处理客户端到服务器的 UDP 通信。
 *
 * 采用一个轻量自定义帧头以携带身份（playerId、roomId、token），payload 为原有的 {@link Message.Packet}。
 * 首包发送 HELLO 帧，服务器确认后直接广播 PACKET 帧（通常只有 S2C_GameStateSync）。
 */
public class UdpClient {
    private static final Logger log = LoggerFactory.getLogger(UdpClient.class);

    private static final int FRAME_MAGIC = 0x4C4D474D; // 'L''M''G''M'
    private static final byte FRAME_TYPE_HELLO = 1;
    private static final byte FRAME_TYPE_PACKET = 2;

    private final Object sendLock = new Object();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private DatagramSocket socket;
    private InetSocketAddress serverAddress;
    private Thread receiveThread;
    private Consumer<Message.Packet> packetConsumer = packet -> {};
    private Consumer<Throwable> errorConsumer = err -> {};

    private volatile int playerId = -1;
    private volatile int roomId = -1;
    private volatile String sessionToken = "";
    private volatile long lastHelloMillis = 0L;
    private volatile boolean helloAcknowledged = false;

    /**
     * 初始化 UDP socket 并启动接收线程。
     */
    public synchronized void start(String host,
                                   int port,
                                   Consumer<Message.Packet> consumer) throws IOException {
        if (running.get()) {
            return;
        }
        Objects.requireNonNull(consumer, "packetConsumer");
        this.packetConsumer = consumer;
        this.serverAddress = new InetSocketAddress(host, port);
        this.socket = new DatagramSocket();
        this.socket.connect(serverAddress);
        this.socket.setSoTimeout(Config.UDP_RECEIVE_TIMEOUT_MS);
        running.set(true);

        receiveThread = new Thread(this::receiveLoop, "udp-recv");
        receiveThread.setDaemon(true);
        receiveThread.start();
        log.info("UDP socket bound to {} using remote {}", socket.getLocalPort(), serverAddress);
    }

    public synchronized void stop() {
        running.set(false);
        if (socket != null) {
            socket.close();
            socket = null;
        }
        if (receiveThread != null) {
            receiveThread.interrupt();
            receiveThread = null;
        }
        helloAcknowledged = false;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void setErrorConsumer(Consumer<Throwable> consumer) {
        this.errorConsumer = consumer != null ? consumer : err -> {};
    }

    public synchronized void configureSession(int playerId, int roomId, String token) {
        this.playerId = playerId;
        this.roomId = roomId;
        this.sessionToken = token != null ? token : "";
        this.helloAcknowledged = false;
        if (running.get() && playerId > 0 && roomId >= 0) {
            sendHello();
        }
    }

    public boolean sendPlayerInput(Message.C2S_PlayerInput input) {
        Message.Packet packet = Message.Packet.newBuilder()
                .setMsgType(Message.MessageType.MSG_C2S_PLAYER_INPUT)
                .setPayload(input.toByteString())
                .build();
        return sendPacket(packet);
    }

    public boolean sendPacket(Message.Packet packet) {
        if (packet == null || !running.get()) {
            return false;
        }
        byte[] payload = packet.toByteArray();
        return sendFrame(FRAME_TYPE_PACKET, payload);
    }

    private void receiveLoop() {
        byte[] buffer = new byte[Config.UDP_BUFFER_SIZE];
        DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
        while (running.get()) {
            try {
                socket.receive(datagram);
                helloAcknowledged = true;
                handleFrame(buffer, datagram.getLength());
            } catch (SocketTimeoutException timeout) {
                maybeResendHello();
            } catch (IOException e) {
                if (running.get()) {
                    log.warn("UDP receive error: {}", e.getMessage());
                    errorConsumer.accept(e);
                }
                break;
            }
        }
        running.set(false);
    }

    private void handleFrame(byte[] data, int length) {
        if (length <= 0) {
            return;
        }
        ByteBuffer buffer = ByteBuffer.wrap(data, 0, length);
        if (buffer.remaining() < 4 + 1 + 4 + 4 + 2 + 4) {
            return;
        }
        int magic = buffer.getInt();
        if (magic != FRAME_MAGIC) {
            return;
        }
        byte frameType = buffer.get();
        int remotePlayerId = buffer.getInt();
        int remoteRoomId = buffer.getInt();
        int tokenLen = Short.toUnsignedInt(buffer.getShort());
        if (buffer.remaining() < tokenLen + 4) {
            return;
        }
        buffer.position(buffer.position() + tokenLen); // skip token
        int payloadLen = buffer.getInt();
        if (payloadLen <= 0 || payloadLen > buffer.remaining()) {
            return;
        }
        byte[] payload = new byte[payloadLen];
        buffer.get(payload);

        if (frameType == FRAME_TYPE_HELLO) {
            log.debug("Received UDP HELLO ack player={} room={}", remotePlayerId, remoteRoomId);
            return;
        }

        if (frameType != FRAME_TYPE_PACKET) {
            return;
        }

        try {
            Message.Packet packet = Message.Packet.parseFrom(payload);
            packetConsumer.accept(packet);
        } catch (InvalidProtocolBufferException e) {
            log.warn("Failed to parse UDP payload: {}", e.getMessage());
        }
    }

    private boolean sendFrame(byte frameType, byte[] payload) {
        DatagramSocket activeSocket;
        synchronized (this) {
            activeSocket = this.socket;
        }
        if (activeSocket == null || !running.get()) {
            return false;
        }
        if (playerId <= 0 || roomId < 0) {
            return false;
        }
        byte[] tokenBytes = sessionToken.getBytes(StandardCharsets.UTF_8);
        int totalLength = 4 + 1 + 4 + 4 + 2 + tokenBytes.length + 4 + payload.length;
        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        buffer.putInt(FRAME_MAGIC);
        buffer.put(frameType);
        buffer.putInt(playerId);
        buffer.putInt(roomId);
        buffer.putShort((short) tokenBytes.length);
        buffer.put(tokenBytes);
        buffer.putInt(payload.length);
        buffer.put(payload);

        DatagramPacket packet = new DatagramPacket(buffer.array(), buffer.position(), serverAddress);
        synchronized (sendLock) {
            try {
                activeSocket.send(packet);
                if (frameType == FRAME_TYPE_HELLO) {
                    lastHelloMillis = System.currentTimeMillis();
                }
                return true;
            } catch (IOException e) {
                log.error("Failed to send UDP frame", e);
                errorConsumer.accept(e);
                return false;
            }
        }
    }

    private void sendHello() {
        log.info("Sending UDP HELLO player={} room={} tokenLength={}",
                playerId, roomId, sessionToken.length());
        sendFrame(FRAME_TYPE_HELLO, new byte[0]);
    }

    private void maybeResendHello() {
        if (helloAcknowledged || playerId <= 0 || roomId < 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastHelloMillis >= Config.UDP_HELLO_RETRY_MS) {
            sendHello();
        }
    }

    /**
     * 将任意 payload 封装为 {@link Message.Packet} 后再发送，方便传输其它消息。
     */
    public boolean sendPayload(Message.MessageType type, MessageLite payload) {
        Message.Packet packet = Message.Packet.newBuilder()
                .setMsgType(type)
                .setPayload(payload.toByteString())
                .build();
        return sendPacket(packet);
    }
}
