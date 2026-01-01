package com.lawnmower.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.StretchViewport;

import com.lawnmower.Main;
import com.lawnmower.ui.Drop.DropPopup;
import com.lawnmower.ui.slider.StepSlider;
import lawnmower.Message;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RoomListScreen implements Screen {
    private Main game;
    private Skin skin;
    private Stage stage;
    private Table roomTable;
    private Texture backgroundTexture;
    private Image backgroundImage;
    private Label titleLabel;
    private float animStateTime = 0f;
    private Image animImage; // 用于显示动画的 Actor

    private TextureAtlas defInAtlas;
    private TextureAtlas defStayAtlas;
    private TextureAtlas defOutAtlas;

    private Animation<TextureRegion> animIn;
    private Animation<TextureRegion> animStay;    // def ~ def4 (12 frames)
    private Animation<TextureRegion> animOut;     // def_out

    private Label errorLabel;                     // 错误提示文本
    private int currentAnimPhase = -1;            // -1=空闲, 0=in, 1=stay, 2=out

    private String lastErrorMsg = "";


    // 虚拟设计分辨率（与 MainMenuScreen 一致）
    private static final float DESIGN_WIDTH = 2560f;
    private static final float DESIGN_HEIGHT = 1440f;

    // ===== 分页核心参数 =====
    private List<Message.RoomInfo> allRooms; // 存储所有房间数据
    private int currentPage = 0; // 当前页码（从0开始）
    private static final int ROOMS_PER_PAGE = 8; // 每页显示8个房间
    private TextButton prevPageBtn; // 上一页按钮
    private TextButton nextPageBtn; // 下一页按钮
    private Label pageInfoLabel; // 页码信息标签（如“第1页/共3页”）
    private boolean justClicked = false;
    private Window errorWindow = null; // 新增字段：用于管理弹窗
    private EventListener globalClickListener = null;

    public RoomListScreen(Main game, Skin skin) {
        this.game = game;
        this.skin = skin;
        this.allRooms = new ArrayList<>(); // 初始化房间列表
    }

    @Override
    public void show() {
        stage = new Stage(new StretchViewport(DESIGN_WIDTH, DESIGN_HEIGHT));
        Gdx.input.setInputProcessor(stage);

        loadAnimations();

        animImage = new Image();
        animImage.setSize(256, 256); // 初始大小（可被 showError 覆盖）

        // 加载背景
        backgroundTexture = new Texture(Gdx.files.internal("background/roomListBackground.png"));
        backgroundImage = new Image(backgroundTexture);
        backgroundImage.setSize(DESIGN_WIDTH, DESIGN_HEIGHT);
        backgroundImage.setScaling(Scaling.stretch);
        stage.addActor(backgroundImage);

        // 创建主容器 Table（用于手动定位）
        Table mainTable = new Table();
        stage.addActor(mainTable);

        // 按钮样式
        TextButton.TextButtonStyle backButtonStyle = skin.get("RoomList", TextButton.TextButtonStyle.class);
        TextButton.TextButtonStyle defaultButtonStyle = skin.get("RoomList_def", TextButton.TextButtonStyle.class);

        // 返回按钮
        TextButton backButton = new TextButton("返回", backButtonStyle);
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MainMenuScreen(game, skin));
            }
        });

        // 创建房间按钮
        TextButton createBtn = new TextButton("创建房间", backButtonStyle);
        createBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                openCreateRoomDialog();
            }
        });

        // 房间列表容器：设置左上角对齐
        roomTable = new Table();
        roomTable.top().center(); // 强制顶部对齐 + 水平居中
        roomTable.padTop(50); // 顶部留小间距（和设计图中“房间列表”标题的距离匹配）
        roomTable.defaults().padBottom(20); // 按钮之间的垂直间距

        // 透明背景的ScrollPane
        ScrollPane.ScrollPaneStyle scrollPaneStyle = new ScrollPane.ScrollPaneStyle(skin.get("default", ScrollPane.ScrollPaneStyle.class));
        scrollPaneStyle.background = null; // 透明背景
        ScrollPane scrollPane = new ScrollPane(roomTable, scrollPaneStyle);
        scrollPane.setSize(1650, 700);
        scrollPane.setPosition(470, 150);
        stage.addActor(scrollPane);

        // 上一页按钮
        prevPageBtn = new TextButton("<<", defaultButtonStyle);
        prevPageBtn.setSize(150, 70);
        prevPageBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (currentPage > 0) {
                    currentPage--;
                    refreshRoomList(); // 刷新当前页房间列表
                }
            }
        });

        // 下一页按钮
        nextPageBtn = new TextButton(">>", defaultButtonStyle);
        nextPageBtn.setSize(150, 70);
        nextPageBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                int totalPages = getTotalPages();
                if (currentPage < totalPages - 1) {
                    currentPage++;
                    refreshRoomList(); // 刷新当前页房间列表
                }
            }
        });

        // 页码信息标签（透明背景）
        pageInfoLabel = new Label("第1页/共1页", skin,"default_32");
        pageInfoLabel.getStyle().font.getData().setScale(1.5f);
        pageInfoLabel.getStyle().background = null; // 透明背景

        // 标题标签
        titleLabel = new Label("房间列表", skin);
        titleLabel.getStyle().font.getData().setScale(2.5f);
        titleLabel.setPosition(1050, 840);
        stage.addActor(titleLabel);

        // 布局：主按钮区域
        mainTable.clear();
        float btnWidth = 300;
        float btnHeight = 120;
        float pad = 15;

        // 添加创建房间、返回按钮
        mainTable.add(createBtn).width(btnWidth).height(btnHeight).pad(pad);
        mainTable.row();
        mainTable.add(backButton).width(btnWidth).height(btnHeight).pad(pad);
        mainTable.row();

        prevPageBtn.setPosition(
                900,80
        );
        pageInfoLabel.setPosition(
                1150,95
        );
        nextPageBtn.setPosition(
                1500,80
        );

        // 手动定位主Table
        mainTable.setPosition(2100, 1150);

        // 初始化分页按钮状态（默认禁用）
        updatePageButtonStatus();

        // 添加ScrollPane到舞台
        stage.addActor(scrollPane);
        stage.addActor(prevPageBtn);
        stage.addActor(pageInfoLabel);
        stage.addActor(nextPageBtn);

//TODO
         //请求房间列表（取消注释启用）
         try {
             game.getTcpClient().sendGetRoomList();
         } catch (IOException e) {
             Gdx.app.error("NET", "请求房间列表失败", e);
         }
    }

    private void loadAnimations() {
        defInAtlas = new TextureAtlas("def_in/def_in.atlas");
        Array<TextureRegion> inFrames = new Array<>();
        for (int i = 0; i < 6; i++) {
            inFrames.add(defInAtlas.findRegion("in_" + i));
        }
        animIn = new Animation<>(0.1f, inFrames);

        // 加载停留动画
        defStayAtlas = new TextureAtlas("def/def.atlas");
        Array<TextureRegion> stayFrames = new Array<>();
        for (int i = 0; i < 12; i++) {
            String name = String.format("frame_%02d_delay-0.13s", i);
            stayFrames.add(defStayAtlas.findRegion(name));
        }
        animStay = new Animation<>(0.13f, stayFrames,Animation.PlayMode.LOOP);

        // 加载退出动画
        defOutAtlas = new TextureAtlas("def_out/def_out.atlas");
        Array<TextureRegion> outFrames = new Array<>();
        for (int i = 0; i < 6; i++) {
            outFrames.add(defOutAtlas.findRegion("out_" + i));
        }
        animOut = new Animation<>(0.1f, outFrames);
    }

    // ===== 分页核心方法 =====
    /**
     * 获取总页数
     */
    private int getTotalPages() {
        if (allRooms.isEmpty()) return 0;
        return (int) Math.ceil((double) allRooms.size() / ROOMS_PER_PAGE);
    }

    /**
     * 刷新当前页的房间列表
     */
    private void refreshRoomList() {
        roomTable.clearChildren();

        if (allRooms.isEmpty()) {
            roomTable.add(new Label("未找到房间", skin ,"default_32"))
                    .center().top(); // 无房间提示也贴顶部居中
            updatePageButtonStatus();
            return;
        }

        int startIndex = currentPage * ROOMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ROOMS_PER_PAGE, allRooms.size());

        roomTable.clear();
        roomTable.top();
        roomTable.padTop(10).defaults().padBottom(20);

        for (int i = startIndex; i < endIndex; i++) {
            Message.RoomInfo room = allRooms.get(i);
            String status = room.getIsPlaying() ? " [游戏中]" : "";
            String text = String.format("%s (%d/%d)%s",
                    room.getRoomName(),
                    room.getCurrentPlayers(),
                    room.getMaxPlayers(),
                    status);

            TextButton btn = new TextButton(text, skin,"PapperButton");
            float btnWidth = 800; // 和设计图中“我的房间”按钮宽度一致
            float btnHeight = 70;
            btn.setWidth(btnWidth);
            btn.setHeight(btnHeight);

            btn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    joinRoom(room.getRoomId());
                }
            });

            // 仅水平居中，不拉伸、不占满
            roomTable.add(btn)
                    .width(btnWidth)
                    .height(btnHeight)
                    .center();
            roomTable.row();
        }

        updatePageButtonStatus();
    }

    /**
     * 更新分页按钮禁用状态和页码信息
     */
    private void updatePageButtonStatus() {
        int totalPages = getTotalPages();

        // 更新页码信息
        String pageText = String.format("第%d页/共%d页", currentPage + 1, totalPages);
        pageInfoLabel.setText(pageText);

        // 禁用/启用上一页按钮
        prevPageBtn.setDisabled(currentPage == 0);
        // 禁用/启用下一页按钮
        nextPageBtn.setDisabled(totalPages <= 1 || currentPage >= totalPages - 1);
    }

    // 接收房间列表数据后触发
    public void onRoomListReceived(List<Message.RoomInfo> rooms) {
        Gdx.app.postRunnable(() -> {
            this.allRooms = new ArrayList<>(rooms); // 存储所有房间数据
            this.currentPage = 0; // 重置为第1页
            refreshRoomList(); // 刷新列表
        });
    }

    private void openCreateRoomDialog() {
        // === 1. 创建 DropPopup 容器 ===
        float targetX = 750;
        float targetY = 400;
        DropPopup dropPopup = new DropPopup(skin, "background/createRoomLong.png", targetX, targetY);

        // 手动设置大小（因为背景图尺寸固定）
        Texture bgTex = new Texture(Gdx.files.internal("background/createRoomLong.png"));
        dropPopup.setSize(bgTex.getWidth(), bgTex.getHeight());
        bgTex.dispose(); // 立即释放，因为我们只取尺寸

        // === 2. 创建内容（和你原来的一样）===
        Group contentGroup = new Group();
        contentGroup.setSize(dropPopup.getWidth(), dropPopup.getHeight());

        // 标题
        Label titleLabel = new Label("创建房间", skin, "default");
        titleLabel.setPosition(300, 500);
        contentGroup.addActor(titleLabel);

        // 房间名输入
        Label nameLabel = new Label("房 间 名:", skin, "default_36");
        nameLabel.setPosition(230, 400);
        contentGroup.addActor(nameLabel);

        TextField nameField = new TextField("我的房间", skin, "TextField");
        nameField.setAlignment(Align.center);
        nameField.setPosition(400, 400);
        nameField.setSize(400, 60);
        contentGroup.addActor(nameField);

        // === 房间人数行 ===
        Table peopleRow = new Table();
        peopleRow.setPosition(260, 300);
        peopleRow.setSize(600, 60);
        Label peopleLabel = new Label("房间人数:", skin, "default_36");
        peopleRow.add(peopleLabel).left().padRight(10);
        StepSlider stepSlider_people = new StepSlider(skin, "default_huipu", "1人", "2人", "3人", "4人");
        stepSlider_people.setSize(400, 50);
        peopleRow.add(stepSlider_people).width(400).height(50).padRight(10);
        Label peopleValueLabel = new Label("1人", skin, "default_huipu");
        peopleValueLabel.setWidth(80);
        peopleValueLabel.setAlignment(Align.center);
        peopleRow.add(peopleValueLabel).width(80).left().padLeft(10);
        contentGroup.addActor(peopleRow);

        stepSlider_people.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                peopleValueLabel.setText(stepSlider_people.getCurrentLabel());
            }
        });

        // === 游戏难度行 ===
        Table difficultyRow = new Table();
        difficultyRow.setPosition(265, 200);
        difficultyRow.setSize(600, 60);
        Label diffLabel = new Label("游戏难度:", skin, "default_36");
        difficultyRow.add(diffLabel).left().padRight(10);
        StepSlider stepSlider = new StepSlider(skin, "default_huipu", "简单", "普通", "困难", "炼狱");
        stepSlider.setSize(400, 50);
        difficultyRow.add(stepSlider).width(400).height(50).padRight(10);
        Label diffValueLabel = new Label("简单", skin, "default_huipu");
        difficultyRow.add(diffValueLabel).left();
        contentGroup.addActor(difficultyRow);

        stepSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                diffValueLabel.setText(stepSlider.getCurrentLabel());
            }
        });

        // === 按钮：创建 / 取消 ===
        TextButton createBtn = new TextButton("创建", skin,"CreateButton");
        TextButton cancelBtn = new TextButton("取消", skin,"CreateButton");
        createBtn.setPosition(200, 50);
        createBtn.setSize(300,100);
        cancelBtn.setPosition(580, 50);
        cancelBtn.setSize(300,100);

        // 创建逻辑
        createBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String roomName = nameField.getText().trim();
                int maxPlayers = stepSlider_people.getCurrentStep() + 1; // 注意：StepSlider 返回 0~3，对应 1~4人
                if (roomName.isEmpty()) {
                    showError("房间名不能为空");
                    return;
                }
                Gdx.app.log("CreateRoom", "名称: " + roomName + ", 人数: " + maxPlayers + ", 难度: " + stepSlider.getCurrentLabel());
//TODO
                try {
                    game.getTcpClient().sendCreateRoom(roomName, maxPlayers);
                } catch (IOException e) {
                    showError("网络错误");
                }
                dropPopup.hide();
            }
        });

        cancelBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                dropPopup.hide();
            }
        });

        contentGroup.addActor(createBtn);
        contentGroup.addActor(cancelBtn);

        // === 3. 将内容添加到 DropPopup ===
        dropPopup.addActor(contentGroup);

        // === 4. 添加到舞台并显示 ===
        stage.addActor(dropPopup);
        dropPopup.show(); // 触发下落动画

    }

    private void joinRoom(int roomId) {
        try {
            game.getTcpClient().sendJoinRoom(roomId);
        } catch (IOException e) {
            showError("加入房间失败");
        }
    }

    public void showError(String msg) {
        if (currentAnimPhase != -1) return; // 防重复

        lastErrorMsg = msg;
        currentAnimPhase = 0; // 👈 改为 0：先播放 in 动画
        animStateTime = 0f;

        // 显示图像
        animImage.setSize(400, 800);
        animImage.setPosition(0, 0);
        animImage.setVisible(true);
        if (animImage.getParent() == null) {
            stage.addActor(animImage);
        }

        // 显示错误文本
        if (errorWindow == null) {
            errorWindow = new Window("", skin);

            // 设置背景
            try {
                Texture bgTexture = new Texture(Gdx.files.internal("background/speakBackground2.png"));
                errorWindow.setBackground(new TextureRegionDrawable(new TextureRegion(bgTexture)));
            } catch (Exception e) {
                Gdx.app.error("UI", "Failed to load dialog background", e);
                // 可选：设置默认背景色
                errorWindow.setBackground(skin.newDrawable("default-select", 0.1f, 0.1f, 0.1f, 0.8f));
            }

            errorWindow.setModal(true);
            errorWindow.setMovable(false);
            errorWindow.setResizable(false);
            errorWindow.pad(30);

            // 点击任意空白处关闭（包括背景）
            errorWindow.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    // 如果还在 stay 或 in 阶段，触发 out 动画
                    if (currentAnimPhase == 0 || currentAnimPhase == 1) {
                        playOutAnimation();
                    }
                    // 注意：不立即 remove，等 out 动画结束统一 cleanup
                }

                // 关键：允许点击穿透到背景（但子控件如按钮会拦截）
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                    return true; // 消费事件，防止传递给 stage 下层
                }
            });

            // 内容布局
            Table contentTable = new Table();
            contentTable.pad(40);

            Label messageLabel = new Label(msg, skin, "default_32");
            messageLabel.setWrap(true);
            messageLabel.setAlignment(Align.center);
            messageLabel.setWidth(500);

            Label hintLabel = new Label("      点击任意位置关闭...", skin, "default_32");
            hintLabel.setWrap(true);
            hintLabel.setAlignment(Align.center);
            hintLabel.setWidth(500);

            contentTable.add(messageLabel).width(500).padBottom(20).row();
            contentTable.add(hintLabel).width(500);

            errorWindow.add(contentTable).expand().fill();

            // 居中显示
            errorWindow.pack();
            errorWindow.setPosition(
                    150,750
            );

            stage.addActor(errorWindow);
        }
        globalClickListener = new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                // 只要弹窗存在，任意点击都触发退出
                if (currentAnimPhase == 0 || currentAnimPhase == 1) {
                    playOutAnimation();
                }
                return true; // 消费事件，防止穿透（可选）
            }
        };
        stage.addListener(globalClickListener);
    }

    private void playStayAssistant() {
        if (currentAnimPhase != 0) return;
        currentAnimPhase = 1; // 切换到 stay
        animStateTime = 0f;
    }

    private void playOutAnimation() {
        if (currentAnimPhase == 0 || currentAnimPhase == 1) {
            currentAnimPhase = 2;
            animStateTime = 0f;
        }
    }

    private void cleanupAnimation() {
        if (animImage.getParent() != null) animImage.remove();

        if (errorWindow != null && errorWindow.getStage() != null) {
            errorWindow.remove();
            errorWindow = null;
        }

        // === 移除全局点击监听器 ===
        if (globalClickListener != null) {
            stage.removeListener(globalClickListener);
            globalClickListener = null;
        }

        currentAnimPhase = -1;
        animStateTime = 0f;
        justClicked = false;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // 处理动画逻辑
        if (currentAnimPhase != -1 && animImage.getParent() != null) {
            animStateTime += delta;
            TextureRegion currentFrame = null;
            Gdx.app.log("DEBUG", "currentAnimPhase = " + currentAnimPhase);

            switch (currentAnimPhase) {
                case 0: // in 动画（一次性）
                    currentFrame = animIn.getKeyFrame(animStateTime, false);
                    if (animIn.isAnimationFinished(animStateTime)) {
                        playStayAssistant(); // 自动切换到 stay
                    }
                    break;

                case 1: // stay 阶段（循环）
                    currentFrame = animStay.getKeyFrame(animStateTime, true);
                    Gdx.app.log("Anim", "Stay phase, time=" + animStateTime);
                    break;

                case 2: // out 阶段
                    currentFrame = animOut.getKeyFrame(animStateTime, false);
                    Gdx.app.log("Anim", "Out phase, time=" + animStateTime + ", finished=" + animOut.isAnimationFinished(animStateTime));
                    if (animOut.isAnimationFinished(animStateTime)) {
                        cleanupAnimation();
                    }
                    break;
            }

            if (currentFrame != null) {
                animImage.setDrawable(new TextureRegionDrawable(currentFrame));
            }
        }

        if (currentAnimPhase == 1 && (Gdx.input.isTouched() || Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.SPACE))) {
            // 防止连续触发：只在“刚按下”时响应
            // 使用一个简单的标志避免一帧内多次触发
            if (!justClicked) {
                justClicked = true;
                playOutAnimation();
                currentAnimPhase = 2;
            }
        } else {
            justClicked = false; // 松开后重置
        }
        // 舞台渲染
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
    public void hide() {
        // 强制清理，防止状态残留
        if (currentAnimPhase != -1) {
            cleanupAnimation();
        }
    }

    @Override
    public void dispose() {
        hide();
        stage.dispose();
        backgroundTexture.dispose();
        defInAtlas.dispose();
        defStayAtlas.dispose();
        defOutAtlas.dispose();
    }
}