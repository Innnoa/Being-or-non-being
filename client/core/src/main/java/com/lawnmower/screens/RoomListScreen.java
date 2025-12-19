package com.lawnmower.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
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

    public RoomListScreen(Main game, Skin skin) {
        this.game = game;
        this.skin = skin;
        this.allRooms = new ArrayList<>(); // 初始化房间列表
    }

    @Override
    public void show() {
        stage = new Stage(new StretchViewport(DESIGN_WIDTH, DESIGN_HEIGHT));
        Gdx.input.setInputProcessor(stage);

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

        // ===== 分页控件初始化 =====
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
        pageInfoLabel = new Label("第1页/共0页", skin,"default_32");
        pageInfoLabel.getStyle().font.getData().setScale(1.5f);
        pageInfoLabel.getStyle().background = null; // 透明背景

        // 标题标签
        titleLabel = new Label("房间列表", skin);
        titleLabel.getStyle().font.getData().setScale(2.5f);
        titleLabel.setPosition(1050, 860);
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

         //请求房间列表（取消注释启用）
//         try {
//             game.getTcpClient().sendGetRoomList();
//         } catch (IOException e) {
//             Gdx.app.error("NET", "请求房间列表失败", e);
//         }
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
        // 目标位置：和你原来 window.setPosition(750, 900) 一致
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
                // TODO: 发送网络请求

//                try {
//                            game.getTcpClient().sendCreateRoom(name, max);
//                        } catch (IOException e) {
//                            showError("网络错误");
//                        }
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
        Gdx.app.postRunnable(() -> {
            new Dialog("错误", skin)
                    .text(msg)
                    .button("确定")
                    .show(stage);
        });
    }

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
        stage.dispose();
    }
}