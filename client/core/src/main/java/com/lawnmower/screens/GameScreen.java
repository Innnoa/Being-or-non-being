package com.lawnmower.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.FitViewport;

import com.lawnmower.Main;
import lawnmower.Message;


import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class GameScreen implements Screen {

    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;
    private static final float PLAYER_SPEED = 200f; // pixels per second

    private Main game;
    private OrthographicCamera camera;
    private FitViewport viewport;
    private SpriteBatch batch;
    private Texture playerTexture;

    // 所有玩家状态（key: player_id）
    private final Map<Integer, Message.PlayerState> serverPlayerStates = new HashMap<>();
    // 本地预测状态（仅用于本玩家渲染，提升响应速度）
    private Vector2 localPosition = new Vector2();
    private float localRotation = 0f;

    // 输入缓存
    private Vector2 moveDirection = new Vector2();
    private boolean isAttacking = false;
    private int inputSequence = 0;

    public GameScreen(Main game) {
        this.game = Objects.requireNonNull(game);
    }


    @Override
    public void show() {
        camera = new OrthographicCamera();
        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);
        batch = new SpriteBatch();

        try {
            // 假设使用统一角色图（后续可按 role_id 区分）
            playerTexture = new Texture(Gdx.files.internal("player/bbb1.png"));
        } catch (Exception e) {
            Gdx.app.error("GameScreen", "Failed to load player texture", e);
            Pixmap pixmap = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
            pixmap.setColor(Color.GREEN);
            pixmap.fill();
            playerTexture = new Texture(pixmap);
            pixmap.dispose();
        }

        // 初始化本地位置（等待服务器同步或设为中心）
        localPosition.set(WORLD_WIDTH / 2, WORLD_HEIGHT / 2);
    }

    private Vector2 getMovementInput() {
        Vector2 input = new Vector2();
        if (Gdx.input.isKeyPressed(Input.Keys.W)) input.y += 1;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) input.y -= 1;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) input.x -= 1;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) input.x += 1;
        return input.len2() > 0 ? input.nor() : input;
    }

    @Override
    public void render(float delta) {
        // === 1. 采集输入 ===
        moveDirection.set(getMovementInput());
        isAttacking = Gdx.input.isKeyJustPressed(Input.Keys.SPACE) ||
                Gdx.input.isButtonJustPressed(Input.Buttons.LEFT);

        // === 2. 客户端预测：仅更新本玩家位置（提升响应）===
        if (serverPlayerStates.containsKey(game.getPlayerId())) {
            Message.PlayerState self = serverPlayerStates.get(game.getPlayerId());
            if (self.getIsAlive()) {
                localPosition.x = self.getPosition().getX() + moveDirection.x * PLAYER_SPEED * delta;
                localPosition.y = self.getPosition().getY() + moveDirection.y * PLAYER_SPEED * delta;
                // 可选：根据方向计算 rotation（面向移动方向）
                if (moveDirection.len2() > 0.1f) {
                    localRotation = (float) Math.toDegrees(Math.atan2(moveDirection.y, moveDirection.x));
                }
            }
        }

        // === 3. 发送输入到服务器 ===
        sendPlayerInputToServer();

        // === 4. 渲染所有玩家 ===
        Gdx.gl.glClearColor(0.1f, 0.2f, 0.1f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.update();
        batch.setProjectionMatrix(camera.combined);
    }

    private void sendPlayerInputToServer() {
        if (game.getTcpClient() == null || game.getPlayerId() <= 0) return;

        Message.Vector2 pbVec = Message.Vector2.newBuilder()
                .setX(moveDirection.x)
                .setY(moveDirection.y)
                .build();

        Message.Timestamp timestamp = Message.Timestamp.newBuilder()
                .setServerTime(System.currentTimeMillis())
                .setTick(0)
                .build();

        Message.C2S_PlayerInput inputMsg = Message.C2S_PlayerInput.newBuilder()
                .setPlayerId(game.getPlayerId())
                .setMoveDirection(pbVec)
                .setIsAttacking(isAttacking)
                .setInputTime(timestamp)
                .build();

        try {
            game.getTcpClient().sendPlayerInput(inputMsg);
        } catch (Exception e) {
            Gdx.app.error("GameScreen", "Failed to send player input", e);
        }
    }
    // —————— 供 Main.java 调用的网络事件入口 ——————

    public void onGameStateReceived(Message.S2C_GameStateSync sync) {
        // 清空旧状态（简单全量更新）
        serverPlayerStates.clear();

        // 更新所有玩家状态
        for (Message.PlayerState player : sync.getPlayersList()) {
            serverPlayerStates.put((int) player.getPlayerId(), player);
        }

        // TODO: 后续可增量更新敌人、道具等
    }

    public void onGameEvent(Message.MessageType type, Object message) {
        // 根据消息类型做不同处理
        switch (type) {
            case MSG_S2C_PLAYER_HURT:
                Message.S2C_PlayerHurt hurt = (Message.S2C_PlayerHurt) message;
                Gdx.app.log("GameEvent", "Player " + hurt.getPlayerId() + " hurt, HP: " + hurt.getRemainingHealth());
                break;
            case MSG_S2C_ENEMY_DIED:
                Message.S2C_EnemyDied died = (Message.S2C_EnemyDied) message;
                Gdx.app.log("GameEvent", "Enemy " + died.getEnemyId() + " died at (" + died.getPosition().getX() + ", " + died.getPosition().getY() + ")");
                break;
            case MSG_S2C_PLAYER_LEVEL_UP:
                Message.S2C_PlayerLevelUp levelUp = (Message.S2C_PlayerLevelUp) message;
                Gdx.app.log("GameEvent", "Player " + levelUp.getPlayerId() + " leveled up to " + levelUp.getNewLevel());
                break;
            case MSG_S2C_GAME_OVER:
                Gdx.app.log("GameEvent", "Game Over!");
                // 可在此切换到结算界面
                break;
            default:
                Gdx.app.log("GameEvent", "Unhandled game event: " + type);
        }
    }

    @Override
    public void resize(int i, int i1) {

    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void hide() {

    }

    @Override
    public void dispose() {

    }
}