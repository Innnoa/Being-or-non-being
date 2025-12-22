package com.lawnmower;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.lawnmower.screens.GameRoomScreen;
import com.lawnmower.screens.MainMenuScreen;
import com.lawnmower.screens.RoomListScreen;
import com.lawnmower.ui.PvzSkin;
import com.lawnmower.network.TcpClient;
import lawnmower.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main extends Game {
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private Skin skin;
    private TcpClient tcpClient;
    private String playerName = "Player";
    private int playerId = -1; // 未登录时为 -1

    private final AtomicBoolean networkRunning = new AtomicBoolean(false);
    private Thread networkThread;

    @Override
    public void create() {
        //使用自定义 PVZ 风格皮肤
        skin = PvzSkin.create();

        // 初始化 TCP 客户端（连接本地服务器）
        try {
            tcpClient = new TcpClient();
            tcpClient.connect(Config.SERVER_HOST, Config.SERVER_PORT);
            log.info("Connected to server {}:{}", Config.SERVER_HOST, Config.SERVER_PORT);
            setScreen(new RoomListScreen(Main.this, skin));
            // 启动网络监听线程
            startNetworkThread();
        } catch (IOException e) {
            log.error("Failed to connect to server", e);
            // 可选：弹出错误对话框或进入离线模式
        }

        // 设置初始屏幕为主菜单
        setScreen(new MainMenuScreen(this, skin));
    }

    private void startNetworkThread() {
        if (networkRunning.get()) return;

        networkRunning.set(true);
        networkThread = new Thread(() -> {
            while (networkRunning.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    // 阻塞等待服务器消息
                    Message.Packet packet = tcpClient.receivePacket();
                    if (packet == null) break; // 连接关闭

                    // 解析消息类型并分发
                    Message.MessageType type = packet.getMsgType();
                    Object payload = null;

                    switch (type) {
                        case MSG_S2C_LOGIN_RESULT:
                            payload = Message.S2C_LoginResult.parseFrom(packet.getPayload());
                            break;
                        case MSG_S2C_ROOM_LIST:
                            payload = Message.S2C_RoomList.parseFrom(packet.getPayload());
                            break;
                        case MSG_S2C_ROOM_UPDATE:
                            payload = Message.S2C_RoomUpdate.parseFrom(packet.getPayload());
                            break;
                        // TODO: 添加其他消息类型
                        default:
                            Gdx.app.log("NET", "Unknown message type: " + type);
                            continue;
                    }

                    // 通知主线程处理（UI 操作必须在渲染线程）
                    handleNetworkMessage(type, payload);

                } catch (IOException e) {
                    if (networkRunning.get()) {
                        Gdx.app.log("NET", "Network error: " + e.getMessage());
                    }
                    break;
                } catch (Exception e) {
                    Gdx.app.log("NET", "Error parsing packet", e);
                    break;
                }
            }

            // 线程退出
            networkRunning.set(false);
            Gdx.app.postRunnable(() -> {
                // 可选：提示连接断开，返回主菜单等
                if (!(getScreen() instanceof MainMenuScreen)) {
                    setScreen(new MainMenuScreen(Main.this, skin));
                }
            });
        }, "NetworkThread");

        networkThread.setDaemon(true); // 随主线程退出而终止
        networkThread.start();
    }

    // ———————— 公共访问方法 ————————

    public Skin getSkin() {
        return skin;
    }

    public TcpClient getTcpClient() {
        return tcpClient;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String name) {
        this.playerName = name;
    }

    public int getPlayerId() {
        return playerId;
    }

    public void setPlayerId(int id) {
        this.playerId = id;
    }

    // ———————— 网络消息处理入口（由网络线程调用） ————————

    public void handleNetworkMessage(Message.MessageType type, Object message) {
        Gdx.app.postRunnable(() -> {
            switch (type) {
                case MSG_S2C_LOGIN_RESULT:
                    Message.S2C_LoginResult result = (Message.S2C_LoginResult) message;
                    if (result.getSuccess()) {
                        setPlayerId(result.getPlayerId());
                        setPlayerName(result.getMessageLogin());
                        // 登录成功，跳转到房间列表
                        setScreen(new com.lawnmower.screens.RoomListScreen(Main.this, skin));
                    } else {
                        // 登录失败：返回主菜单并提示
                        if (getScreen() instanceof com.lawnmower.screens.MainMenuScreen mainMenu) {
                            mainMenu.showError("登录失败: " + result.getMessageLogin());
                        } else {
                            setScreen(new MainMenuScreen(Main.this, skin));
                            ((MainMenuScreen) getScreen()).showError("登录失败: " + result.getMessageLogin());
                        }
                    }
                    break;

                case MSG_S2C_ROOM_LIST:
                    if (getScreen() instanceof com.lawnmower.screens.RoomListScreen roomList) {
                        Message.S2C_RoomList list = (Message.S2C_RoomList) message;
                        roomList.onRoomListReceived(list.getRoomsList());
                    }
                    break;

                case MSG_S2C_ROOM_UPDATE:
                    Message.S2C_RoomUpdate update = (Message.S2C_RoomUpdate) message;
                    if (!(getScreen() instanceof com.lawnmower.screens.GameRoomScreen)) {
                        // 自动进入游戏房间界面
                        setScreen(new com.lawnmower.screens.GameRoomScreen(Main.this, skin));
                    }
                    if (getScreen() instanceof com.lawnmower.screens.GameRoomScreen gameRoom) {
                        gameRoom.onRoomUpdate(update.getRoomId(), update.getPlayersList());
                    }
                    break;

                default:
                    Gdx.app.log("NET", "Unhandled message type: " + type);
            }
        });
    }

    @Override
    public void dispose() {
        // 停止网络线程
        if (networkRunning.get()) {
            networkRunning.set(false);
            if (networkThread != null) {
                networkThread.interrupt();
            }
        }

        if (tcpClient != null) {
            try {
                tcpClient.close();
            } catch (IOException e) {
                log.warn("Error closing TCP client", e);
            }
        }

        if (skin != null) skin.dispose();
        super.dispose();
    }
}