package com.lawnmower.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.StretchViewport;
import com.lawnmower.Main;
import lawnmower.Message;
import java.io.IOException;
import java.util.List;

public class GameRoomScreen implements Screen {
    private Main game;
    private Skin skin;
    private Stage stage;
    private Table playerSlotTable; // 替代原来的 playerTable

    private TextButton readyButton;
    private TextButton startButton;
    private boolean isHost = false;
    private boolean amIReady = false;
    private int currentRoomId = -1;

    private Texture backgroundTexture;
    private Image backgroundImage;
    private Texture lockedSlotTex;      // 图2：空槽（锁链）
    private Texture unlockedSlotTex;    // 图3：有人（白框）

    // ===== 配置 =====
    private static final float DESIGN_WIDTH = 2560f;
    private static final float DESIGN_HEIGHT = 1440f;
    private static final int MAX_PLAYERS = 4; // 与 RoomListScreen 的 StepSlider 一致
    private static final int SLOT_SIZE = 120; // 像素

    public GameRoomScreen(Main game, Skin skin) {
        this.game = game;
        this.skin = skin;
    }

    @Override
    public void show() {
        stage = new Stage(new StretchViewport(DESIGN_WIDTH, DESIGN_HEIGHT));
        Gdx.input.setInputProcessor(stage);

        // 背景
        backgroundTexture = new Texture(Gdx.files.internal("background/gameReadyRoom.png"));
        backgroundImage = new Image(backgroundTexture);
        backgroundImage.setSize(DESIGN_WIDTH, DESIGN_HEIGHT);
        backgroundImage.setScaling(Scaling.stretch);
        stage.addActor(backgroundImage);

        // 加载槽位图片
        lockedSlotTex = new Texture(Gdx.files.internal("background/LockedRoom.png"));
        unlockedSlotTex = new Texture(Gdx.files.internal("background/unLockedRoom.png"));

        // 槽位容器
        playerSlotTable = new Table();
        playerSlotTable.pad(10).defaults().padLeft(10).padRight(10);

        // 返回按钮
        TextButton backButton = new TextButton("离开房间", skin,"CreateButton");
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                leaveRoom();
            }
        });

        // 准备按钮
        readyButton = new TextButton("准备", skin,"CreateButton");
        readyButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                toggleReady();
            }
        });

        // 开始游戏按钮（仅房主）
        startButton = new TextButton("开始游戏", skin,"CreateButton");
        startButton.setVisible(false);
        startButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                startGame();
            }
        });

        // 布局
        Table root = new Table();
        root.setFillParent(true);
        root.top().padTop(20);
        root.add(backButton).left().row();
        root.add(new Label("等待中", skin)).center().padTop(10).row();
        root.add(playerSlotTable).expandX().fillX().padTop(80).padBottom(80).row(); // 居中区域
        root.add(readyButton).center().padTop(20).row();
        root.add(startButton).center().padTop(10).row();

        stage.addActor(root);
    }

    // —————— 网络回调 ——————
    public void onRoomUpdate(int roomId, List<Message.PlayerInfo> players) {
        this.currentRoomId = roomId;

        // 创建一个 effectively final 的引用（不修改原始参数）
        final List<Message.PlayerInfo> safePlayers = (players == null) ? List.of() : players;

        Gdx.app.postRunnable(() -> {
            playerSlotTable.clearChildren();

            // 使用 safePlayers，它是 final，可以在 lambda 中安全使用
            boolean[] occupied = new boolean[MAX_PLAYERS];
            String[] names = new String[MAX_PLAYERS];
            for (int i = 0; i < MAX_PLAYERS; i++) {
                occupied[i] = false;
                names[i] = "";
            }

            for (int i = 0; i < Math.min(safePlayers.size(), MAX_PLAYERS); i++) {
                occupied[i] = true;
                names[i] = safePlayers.get(i).getPlayerName();
            }

            // 构建 UI 槽位
            for (int i = 0; i < MAX_PLAYERS; i++) {
                Texture tex = occupied[i] ? unlockedSlotTex : lockedSlotTex;
                Image slot = new Image(tex);
                slot.setSize(SLOT_SIZE, SLOT_SIZE);

                if (occupied[i]) {
                    Label nameLabel = new Label(names[i], skin, "default");
                    nameLabel.setFontScale(0.9f);
                    Table slotWithLabel = new Table();
                    slotWithLabel.add(slot).row();
                    slotWithLabel.add(nameLabel).center().padTop(5);
                    playerSlotTable.add(slotWithLabel).size(SLOT_SIZE, SLOT_SIZE + 30);
                } else {
                    playerSlotTable.add(slot).size(SLOT_SIZE, SLOT_SIZE);
                }
            }

            // 更新按钮状态
            isHost = false;
            amIReady = false;
            for (Message.PlayerInfo p : safePlayers) {
                if (p.getPlayerId() == game.getPlayerId()) {
                    amIReady = p.getIsReady();
                    readyButton.setText(amIReady ? "取消准备" : "准备");
                }
                if (p.getIsHost() && p.getPlayerId() == game.getPlayerId()) {
                    isHost = true;
                }
            }
            startButton.setVisible(isHost);
        });
    }

    // —————— 操作方法 ——————
    private void toggleReady() {
        try {
            game.getTcpClient().sendSetReady(!amIReady);
        } catch (IOException e) {
            showError("网络错误");
        }
    }

    private void startGame() {
        if (!isHost) return;
        try {
            game.getTcpClient().sendStartGame();
        } catch (IOException e) {
            showError("无法开始游戏");
        }
    }

    private void leaveRoom() {
        try {
            game.getTcpClient().sendLeaveRoom();
        } catch (IOException e) {
            // 忽略
        }
        game.setScreen(new RoomListScreen(game, skin));
    }

    public void showError(String msg) {
        Gdx.app.postRunnable(() -> {
            new Dialog("提示", skin)
                    .text(msg)
                    .button("确定")
                    .show(stage);
        });
    }

    // —————— 生命周期 ——————
    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.1f, 0.15f, 0.1f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void hide() {}

    @Override
    public void dispose() {
        if (backgroundTexture != null) backgroundTexture.dispose();
        if (lockedSlotTex != null) lockedSlotTex.dispose();
        if (unlockedSlotTex != null) unlockedSlotTex.dispose();
        stage.dispose();
    }
}