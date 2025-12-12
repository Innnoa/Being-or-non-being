package com.lawnmower.desktop;

import com.google.protobuf.ByteString;
import lawnmower.Messages;

/**
 * Protobuf 消息测试类（登录消息 + Packet 封装）
 * 修复点：
 * 1. 增加消息类型常量，避免硬编码；
 * 2. 增强异常信息输出；
 * 3. 补充 Packet 反序列化测试；
 * 4. 代码注释规范化。
 */
public class ProtobufTest {
    // 消息类型常量（建议统一管理）
    private static final int MSG_TYPE_LOGIN = 1;       // 登录消息
    private static final int MSG_TYPE_HEARTBEAT = 2;   // 心跳消息
    private static final int MSG_TYPE_LOGIN_RESULT = 3;// 登录结果消息

    public static void main(String[] args) {
        try {
            // ========== 1. 构建并序列化登录消息 ==========
            Messages.C2S_Login loginMsg = Messages.C2S_Login.newBuilder()
                    .setPlayerName("测试玩家_001")  // 玩家名
                    .build();

            // 序列化：转为字节数组
            byte[] loginBytes = loginMsg.toByteArray();
            System.out.println("=== 登录消息序列化 ===");
            System.out.println("序列化后字节数: " + loginBytes.length);
            System.out.println("原始玩家名: " + loginMsg.getPlayerName());

            // ========== 2. 反序列化登录消息 ==========
            Messages.C2S_Login parsedLoginMsg = Messages.C2S_Login.parseFrom(loginBytes);
            System.out.println("\n=== 登录消息反序列化 ===");
            System.out.println("解析出的玩家名: " + parsedLoginMsg.getPlayerName());
            // 验证反序列化一致性
            if (loginMsg.getPlayerName().equals(parsedLoginMsg.getPlayerName())) {
                System.out.println("反序列化验证：成功");
            } else {
                System.out.println("反序列化验证：失败");
            }

            // ========== 3. 封装为通用 Packet 消息 ==========
            Messages.Packet packet = Messages.Packet.newBuilder()
                    .setMsgType(MSG_TYPE_LOGIN)  // 消息类型：登录
                    .setPayload(ByteString.copyFrom(loginBytes))  // 消息体
                    .build();

            byte[] packetBytes = packet.toByteArray();
            System.out.println("\n=== Packet 封装 ===");
            System.out.println("Packet 总字节数: " + packetBytes.length);
            System.out.println("Packet 消息类型: " + packet.getMsgType());
            System.out.println("Packet 消息体长度: " + packet.getPayload().size());

            // ========== 4. 解析 Packet 并还原登录消息 ==========
            Messages.Packet parsedPacket = Messages.Packet.parseFrom(packetBytes);
            if (parsedPacket.getMsgType() == MSG_TYPE_LOGIN) {
                // 从 Packet.payload 还原登录消息
                Messages.C2S_Login loginFromPacket = Messages.C2S_Login.parseFrom(parsedPacket.getPayload());
                System.out.println("\n=== 从 Packet 还原登录消息 ===");
                System.out.println("还原的玩家名: " + loginFromPacket.getPlayerName());
            }

        } catch (Exception e) {
            System.err.println("Protobuf 测试异常：");
            System.err.println("异常类型: " + e.getClass().getSimpleName());
            System.err.println("异常信息: " + e.getMessage());
            e.printStackTrace();
        }
    }
}