package com.lawnmower.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.FitViewport;

import com.lawnmower.Main;
import com.lawnmower.players.PlayerInputCommand;
import com.lawnmower.players.PlayerStateSnapshot;
import lawnmower.Message;


import java.util.*;

public class GameScreen implements Screen {

    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;
    private static final float PLAYER_SPEED = 200f; // pixels per second

    private Main game;
    private OrthographicCamera camera;
    private FitViewport viewport;
    private SpriteBatch batch;
    private Texture playerTexture;
    private long lastInputTimeMs = System.currentTimeMillis(); // 上次发送时间（用于 delta_ms）
    private int inputSequence = 0;                            // 输入序列号（从0开始递增）
    private float sendInterval = 0.05f; // 1秒
    private float timeSinceLastSend = 0f;

    // 所有玩家状态（key: player_id）
    private final Map<Integer, Message.PlayerState> serverPlayerStates = new HashMap<>();
    // 本地预测状态（仅用于本玩家渲染，提升响应速度）
    private Vector2 predictedPosition = new Vector2();     // 当前预测位置（用于渲染）
    private float predictedRotation = 0f;                 // 当前预测朝向
    private final Map<Integer, PlayerInputCommand> unconfirmedInputs = new LinkedHashMap<>();
    private final Queue<PlayerStateSnapshot> snapshotHistory = new ArrayDeque<>();
    private boolean hasReceivedInitialState = false;

    private TextureRegion playerTextureRegion;

    // 输入缓存
    private Vector2 moveDirection = new Vector2();
    private boolean isAttacking = false;
    private Texture backgroundTexture;

    // 新增：记录最后一次从服务器收到的位置和时间
    private Vector2 lastServerPosition = new Vector2();
    private long lastServerTimeMs = 0L;

    // 标记是否已收到过服务器状态（避免初始渲染错误）
    private boolean hasReceivedServerState = false;


    public GameScreen(Main game) {
        this.game = Objects.requireNonNull(game);
    }


    @Override
    public void show() {
        camera = new OrthographicCamera();
        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);
        batch = new SpriteBatch();

        // === 加载背景图 ===
        try {
            backgroundTexture = new Texture(Gdx.files.internal("background/roomListBackground.png")); // 路径可自定义
            Gdx.app.log("GameScreen", "Loaded background: " + backgroundTexture.getWidth() + "x" + backgroundTexture.getHeight());
        } catch (Exception e) {
            Gdx.app.error("GameScreen", "Failed to load background texture", e);
            // 创建一个绿色渐变或纯色背景作为 fallback
            Pixmap bgPixmap = new Pixmap((int)WORLD_WIDTH, (int)WORLD_HEIGHT, Pixmap.Format.RGBA8888);
            bgPixmap.setColor(0.05f, 0.15f, 0.05f, 1f); // 深绿色草地感
            bgPixmap.fill();
            backgroundTexture = new Texture(bgPixmap);
            bgPixmap.dispose();
        }

        try {
            // 假设使用统一角色图（后续可按 role_id 区分）
            playerTexture = new Texture(Gdx.files.internal("player/bbb1.png"));
            playerTextureRegion = new TextureRegion(playerTexture);
            Gdx.app.log("GameScreen", "Loaded texture: " + playerTexture.getWidth() + "x" + playerTexture.getHeight());
        } catch (Exception e) {
            Gdx.app.error("GameScreen", "Failed to load player texture", e);
            Pixmap pixmap = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
            pixmap.setColor(Color.RED); // 改成红色，便于识别
            pixmap.fillCircle(32, 32, 30); // 画个圆，不是纯色块
            playerTexture = new Texture(pixmap);
            playerTextureRegion = new TextureRegion(playerTexture);
            pixmap.dispose();
        }

        // 初始化本地位置（等待服务器同步或设为中心）
        predictedPosition.set(640, 300); // 临时值，会被首次同步覆盖
    }

    private void collectAndPredict(float delta) {
        // 1. 采集当前帧输入
        Vector2 dir = getMovementInput(); // 你已有的方法
        boolean attacking = Gdx.input.isKeyJustPressed(Input.Keys.SPACE);

        // 2. 创建输入命令
        PlayerInputCommand input = new PlayerInputCommand(inputSequence++, dir, attacking);

        // 3. 缓存为“未确认输入”
        unconfirmedInputs.put(input.seq, input);

        // 4. 立即预测（应用到 predictedPosition）
        applyInputLocally(predictedPosition, predictedRotation, input, delta);
    }

    private void clampPositionToMap(Vector2 position) {
        if (playerTextureRegion == null) {
            return; // 纹理未加载，无法计算
        }

        float halfWidth = playerTextureRegion.getRegionWidth() / 2.0f;
        float halfHeight = playerTextureRegion.getRegionHeight() / 2.0f;

        // X轴限制: [halfWidth, WORLD_WIDTH - halfWidth]
        position.x = MathUtils.clamp(position.x, halfWidth, WORLD_WIDTH - halfWidth);
        // Y轴限制: [halfHeight, WORLD_HEIGHT - halfHeight]
        position.y = MathUtils.clamp(position.y, halfHeight, WORLD_HEIGHT - halfHeight);
    }


    private Vector2 getMovementInput() {
        Vector2 input = new Vector2();
        if (Gdx.input.isKeyPressed(Input.Keys.W)) input.y += 1;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) input.y -= 1;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) input.x -= 1;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) input.x += 1;
        return input.len2() > 0 ? input.nor() : input;
    }

    private void applyInputLocally(Vector2 pos, float rot, PlayerInputCommand input, float delta) {
        if (input.moveDir.len2() > 0.1f) {
            float speed = PLAYER_SPEED;
            // 可从 serverPlayerStates 获取动态速度（略）
            pos.add(input.moveDir.x * speed * delta, input.moveDir.y * speed * delta);
            clampPositionToMap(pos);
        }
        // TODO攻击逻辑可扩展
    }

    @Override
    public void render(float delta) {
        if (!hasReceivedInitialState || playerTextureRegion == null) {
            Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            return;
        }

        // === 1. 采集当前输入 ===
        Vector2 dir = getMovementInput();
        boolean attacking = Gdx.input.isKeyJustPressed(Input.Keys.SPACE);

        // === 2. 创建并缓存新输入 ===
        PlayerInputCommand input = new PlayerInputCommand(inputSequence++, dir, attacking);
        unconfirmedInputs.put(input.seq, input);

        // === 3. 立即预测（应用到 predictedPosition）===
        applyInputLocally(predictedPosition, predictedRotation, input, delta);
        clampPositionToMap(predictedPosition);

        // === 4. 发送新输入到服务器 ===
        sendPlayerInputToServer(input); // 修改此方法接收 input

        // === 5. 渲染 ===
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        clampPositionToMap(predictedPosition);
        camera.position.set(predictedPosition.x, predictedPosition.y, 0);
        camera.update();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        batch.draw(backgroundTexture, 0, 0, WORLD_WIDTH, WORLD_HEIGHT);

        float width = playerTextureRegion.getRegionWidth();
        float height = playerTextureRegion.getRegionHeight();

        // 自己：用预测位置
        batch.draw(playerTextureRegion,
                predictedPosition.x - width / 2,
                predictedPosition.y - height / 2,
                width / 2, height / 2,
                width, height,
                1, 1,
                predictedRotation);

        // 其他人：用服务器状态
        int myId = game.getPlayerId();
        for (Message.PlayerState state : serverPlayerStates.values()) {
            if (state.getPlayerId() == myId || !state.getIsAlive()) continue;
            Vector2 otherPos = new Vector2(state.getPosition().getX(), state.getPosition().getY());
            clampPositionToMap(otherPos);
            batch.draw(playerTextureRegion,
                    state.getPosition().getX() - width / 2,
                    state.getPosition().getY() - height / 2,
                    width / 2, height / 2,
                    width, height,
                    1, 1,
                    state.getRotation());
        }

        Gdx.app.log("位置",lastServerPosition.x + "," + lastServerPosition.y);
        Gdx.app.log("位置",predictedPosition.x + "," + predictedPosition.y);
        batch.end();
    }

    private void sendPlayerInputToServer(PlayerInputCommand cmd) {
        if (game.getTcpClient() == null || game.getPlayerId() <= 0) return;

        Message.Vector2 pbVec = Message.Vector2.newBuilder()
                .setX(cmd.moveDir.x)
                .setY(cmd.moveDir.y)
                .build();

        Message.C2S_PlayerInput inputMsg = Message.C2S_PlayerInput.newBuilder()
                .setPlayerId(game.getPlayerId())
                .setMoveDirection(pbVec)
                .setIsAttacking(cmd.isAttacking)
                .setInputSeq(cmd.seq)
                .setDeltaMs(0) // 可省略或设为固定值
                .build();

        try {
            game.getTcpClient().sendPlayerInput(inputMsg);
        } catch (Exception e) {
            Gdx.app.error("GameScreen", "Failed to send input", e);
        }
    }
    // —————— 供 Main.java 调用的网络事件入口 ——————

    public void onGameStateReceived(Message.S2C_GameStateSync sync) {
        serverPlayerStates.clear();
        int myId = game.getPlayerId();
        Message.PlayerState selfStateFromServer = null;

        for (Message.PlayerState player : sync.getPlayersList()) {
            serverPlayerStates.put((int) player.getPlayerId(), player);
            if (player.getPlayerId() == myId && player.getIsAlive()) {
                selfStateFromServer = player;
            }
        }

        if (selfStateFromServer == null) return;

        // === 关键：获取服务器已处理的最后一个输入序号 ===
        int lastProcessedSeq = selfStateFromServer.getLastProcessedInputSeq(); // ← 新字段！

        // 创建快照
        Vector2 serverPos = new Vector2(
                selfStateFromServer.getPosition().getX(),
                selfStateFromServer.getPosition().getY()
        );
        clampPositionToMap(serverPos);
        PlayerStateSnapshot snapshot = new PlayerStateSnapshot(
                serverPos,
                selfStateFromServer.getRotation(),
                lastProcessedSeq
        );

        snapshotHistory.offer(snapshot);
        while (snapshotHistory.size() > 10) {
            snapshotHistory.poll(); // 防止内存爆炸
        }

        // === 执行回滚 + 重放 ===
        reconcileWithServer(snapshot);
    }

    private void reconcileWithServer(PlayerStateSnapshot serverSnapshot) {
        // 1. 回滚到服务器状态
        predictedPosition.set(serverSnapshot.position);
        predictedRotation = serverSnapshot.rotation;

        clampPositionToMap(predictedPosition);

        // 2. 重放未确认输入（seq > server 已处理的）
        for (PlayerInputCommand input : unconfirmedInputs.values()) {
            if (input.seq > serverSnapshot.lastProcessedInputSeq) {
                applyInputLocally(predictedPosition, predictedRotation, input, 1f / 60f);
            }
        }

        // 3. 清除已确认输入
        unconfirmedInputs.entrySet().removeIf(entry ->
                entry.getKey() <= serverSnapshot.lastProcessedInputSeq
        );

        hasReceivedInitialState = true;
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
    public void resize(int width, int height) {
        viewport.update(width, height, true); // true 表示居中
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
        if (batch != null) batch.dispose();
        if (playerTexture != null) playerTexture.dispose();
        if (backgroundTexture != null) backgroundTexture.dispose();
    }
}