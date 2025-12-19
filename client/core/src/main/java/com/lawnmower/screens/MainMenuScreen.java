package com.lawnmower.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.StretchViewport;
import com.lawnmower.Main;

import java.util.UUID;

public class MainMenuScreen implements Screen {
    private Main game;
    private Stage stage;
    private Skin skin;
    private Texture backgroundTexture;
    private Image backgroundImage;
    private Texture background;
    private Label playerNameLabel;

    // 虚拟设计分辨率（根据你的 background_main.png 实际尺寸调整）
    private static final float DESIGN_WIDTH = 2560f;
    private static final float DESIGN_HEIGHT = 1440f;

    // 成员变量
    private Table table;
    private TextButton singlePlayer, multiplayer, settings, exit;

    public MainMenuScreen(Main game, Skin skin) {
        this.game = game;
        this.skin = skin;
    }

    @Override
    public void show() {
        stage = new Stage(new StretchViewport(DESIGN_WIDTH, DESIGN_HEIGHT));
        Gdx.input.setInputProcessor(stage);

        // 加载背景纹理
        backgroundTexture = new Texture(Gdx.files.internal("background/background_little.png"));
        backgroundImage = new Image(backgroundTexture);

        // 设置背景图大小和缩放方式
        backgroundImage.setSize(DESIGN_WIDTH, DESIGN_HEIGHT);
        backgroundImage.setScaling(Scaling.stretch); // 强制拉伸填满

        // 添加到舞台底层
        stage.addActor(backgroundImage);

        playerNameLabel = new Label("测试", skin);
        stage.addActor(playerNameLabel); // ← 直接加入舞台

        table = new Table();
        stage.addActor(table);

        TextButton.TextButtonStyle buttonStyle = skin.get("MainPage", TextButton.TextButtonStyle.class);

        // 正确赋值给成员变量
        singlePlayer = new TextButton("单人游戏", buttonStyle);
        multiplayer = new TextButton("多人游戏", buttonStyle);
        settings = new TextButton("设置", buttonStyle);
        exit = new TextButton("退出游戏", buttonStyle);

        layoutButtons(); // 基于 DESIGN_WIDTH 布局

        // 添加点击事件
        singlePlayer.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {

                String name ="玩家"+ UUID.randomUUID();

                // 发送登录请求
                try {
                    lawnmower.Message.C2S_Login loginReq = lawnmower.Message.C2S_Login.newBuilder()
                            .setPlayerName(name)
                            .build();
                    game.getTcpClient().sendPacket(lawnmower.Message.MessageType.MSG_C2S_LOGIN, loginReq);
                } catch (Exception e) {
                    showError("无法连接服务器");
                }
            }
        });

        multiplayer.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new RoomListScreen(game, skin));
            }
        });

        settings.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Gdx.app.log("Menu", "设置");
            }
        });

        exit.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Gdx.app.exit();
            }
        });
    }
    // 供 Main.java 调用：登录结果回调
    public void onSinglePlayerLoginResult(boolean success, String message, int playerId) {
        Gdx.app.postRunnable(() -> {
            if (success) {
                // 登录成功 → 进入单人游戏
                game.setPlayerId(playerId);
                game.setScreen(new RoomListScreen(game, skin)); // ← 你要实现的单人游戏屏幕
            } else {
                showError("登录失败: " + message);
            }
        });
    }

    private void layoutButtons() {
        table.clear();

        // 固定按钮尺寸（匹配你的 MainPage_up.png）
        float btnWidth = 500;   // 根据实际图片调整
        float btnHeight = 80;
        float pad = 15;

        // 添加 UI 元素（基于虚拟坐标系，无需百分比）
        table.add(singlePlayer).width(btnWidth).height(btnHeight).pad(pad);
        table.row();
        table.add(multiplayer).width(btnWidth).height(btnHeight).pad(pad);
        table.row();
        table.add(settings).width(btnWidth).height(btnHeight).pad(pad);
        table.row();
        table.add(exit).width(btnWidth).height(btnHeight).pad(pad);

        // 居中在虚拟屏幕中央（可改为固定位置，如墓碑坐标）
        table.setPosition(
                (DESIGN_WIDTH - table.getPrefWidth()) / 3f,
                (DESIGN_HEIGHT - table.getPrefHeight()) / 1.8f
        );
        // 单独设置 Label 的位置（例如放在墓碑上方、左上角等）
        playerNameLabel.setPosition(
                200,   // x 坐标（从左往右）
                1200   // y 坐标（从下往上，接近顶部）
        );
    }

    public void showError(String message) {
        Gdx.app.postRunnable(() -> {
            new Dialog("错误", skin)
                    .text(message)
                    .button("确定")
                    .show(stage);
        });
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0.5f, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // 所有内容通过 stage 绘制，统一受 Viewport 控制
        stage.act(delta);
        stage.draw(); // 背景 + UI 一起缩放！
    }

    @Override
    public void resize(int width, int height) {
        // FitViewport 自动处理缩放，只需更新视口
        stage.getViewport().update(width, height, true);
        // 注意：layoutButtons() 不需要在这里调用！
        // 因为 UI 坐标是固定的，FitViewport 已自动缩放渲染
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        if (background != null) background.dispose();
        stage.dispose();
    }
}