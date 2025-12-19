package com.lawnmower.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.SpriteDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

public class PvzSkin {
    private static Texture createColoredTexture(Color color) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    private static Drawable createColoredDrawable(Color color) {
        return new SpriteDrawable(new Sprite(createColoredTexture(color)));
    }

    public static Skin create() {
        Skin skin = new Skin();

        // 在代码中动态生成常用汉字范围（避免文件太大）
        // 读取字符集文件（UTF-8）
        FileHandle charFile = Gdx.files.internal("fonts/chars.txt");
        String characters = charFile.readString(); // 自动按 UTF-8 读取


        // 无参构造 → 加载内置默认字体（仅支持ASCII，中英混合需方案2）
        BitmapFont defaultFont = new BitmapFont();
        defaultFont.getData().setScale(3f);
        skin.add("system-default-font", defaultFont);
        // === 1. 先加载中文字体（唯一一次！）===
        FileHandle fontFile = Gdx.files.internal("fonts/pvz.ttf"); // 支持中文
        BitmapFont font = getFont(fontFile,characters,40);
        // === 2. 注册字体 ===
        skin.add("default-font", font);
        /**
         * 第二个字体
         */
        FileHandle fontCuteFile = Gdx.files.internal("fonts/pvz_cute.ttf"); // 支持中文
        BitmapFont fontCute_36 = getFont(fontCuteFile, characters,40);

        skin.add("default-font-cute-36", fontCute_36);

        FileHandle fontCuteFile_50 = Gdx.files.internal("fonts/pvz_cute.ttf"); // 支持中文
        BitmapFont fontCute_50 = getFont(fontCuteFile_50, characters,44);
        skin.add("default-font-cute-50", fontCute_50);

        FileHandle fontCuteFile_32 = Gdx.files.internal("fonts/pvz_cute.ttf"); // 支持中文
        BitmapFont fontCute_32 = getFont(fontCuteFile_32, characters,32);
        skin.add("default-font-cute-32", fontCute_32);

        /**
         * 阿里巴巴惠普体
         */
        FileHandle huipu = Gdx.files.internal("fonts/huipu_pvz.ttf"); // 支持中文
        BitmapFont huipu_font = getFont(huipu,50);
        skin.add("huipu", huipu_font);

        // === 3. 注册 white 纹理（必须）===
        Texture whiteTex = createColoredTexture(Color.WHITE);
        skin.add("white", whiteTex);

        Texture upTex = new Texture(Gdx.files.internal("ui/button/MainPage/MainPage_up.png"));
        Texture downTex = new Texture(Gdx.files.internal("ui/button/MainPage/MainPage_down.png"));

        Drawable upDrawable = new TextureRegionDrawable(new TextureRegion(upTex));
        Drawable downDrawable = new TextureRegionDrawable(new TextureRegion(downTex));


        // === 3. 创建按钮样式 ===
        TextButton.TextButtonStyle buttonStyle_first = new TextButton.TextButtonStyle();
        buttonStyle_first.up = upDrawable;
        buttonStyle_first.down = downDrawable;
        buttonStyle_first.font = font;
        buttonStyle_first.fontColor = Color.BLACK;

        // 注册为 "concrete" 样式（名字可自定义）
        skin.add("MainPage", buttonStyle_first);

        /**
         * roomList
         */
        Texture upRoomList = new Texture(Gdx.files.internal("ui/button/RoomList/roomListButton_on.png"));
        Texture downRoomList = new Texture(Gdx.files.internal("ui/button/RoomList/roomListButton_down.png"));

        Drawable upRoomList_Drawable= new TextureRegionDrawable(new TextureRegion(upRoomList));
        Drawable downRoomList_Drawable = new TextureRegionDrawable(new TextureRegion(downRoomList));


        // === 3. 创建按钮样式 ===
        TextButton.TextButtonStyle buttonStyle_RoomList = new TextButton.TextButtonStyle();
        buttonStyle_RoomList.up = upRoomList_Drawable;
        buttonStyle_RoomList.down = downRoomList_Drawable;
        buttonStyle_RoomList.font = fontCute_36;
        buttonStyle_RoomList.fontColor = Color.BLACK;

        // 注册为 "concrete" 样式（名字可自定义）
        skin.add("RoomList", buttonStyle_RoomList);

        // === 3. 创建按钮样式 ===
        TextButton.TextButtonStyle buttonStyle_RoomList_def = new TextButton.TextButtonStyle();
        buttonStyle_RoomList_def.up = upRoomList_Drawable;
        buttonStyle_RoomList_def.down = downRoomList_Drawable;
        buttonStyle_RoomList_def.fontColor = Color.BLACK;
        buttonStyle_RoomList_def.font = defaultFont;
        skin.add("RoomList_def", buttonStyle_RoomList_def);

        /**
         * paperButton
         */
        Texture upPapperButton = new Texture(Gdx.files.internal("ui/button/PapperButton/paperButton_on.png"));
        Texture downPaperButton = new Texture(Gdx.files.internal("ui/button/PapperButton/paperButton_down.png"));

        Drawable upPapperButton_Drawable= new TextureRegionDrawable(new TextureRegion(upPapperButton));
        Drawable downPapperButton_Drawable = new TextureRegionDrawable(new TextureRegion(downPaperButton));


        // === 3. 创建按钮样式 ===
        TextButton.TextButtonStyle buttonStyle_PapperButton = new TextButton.TextButtonStyle();
        buttonStyle_PapperButton.up = upPapperButton_Drawable;
        buttonStyle_PapperButton.down = downPapperButton_Drawable;
        buttonStyle_PapperButton.font = font;
        buttonStyle_PapperButton.fontColor = Color.BLACK;

        // 注册为 "concrete" 样式（名字可自定义）
        skin.add("PapperButton", buttonStyle_PapperButton);

        Texture upCreateButton = new Texture(Gdx.files.internal("ui/button/createRoomButton/createButton_on.png"));
        Texture downCreateButton = new Texture(Gdx.files.internal("ui/button/createRoomButton/createButton_down.png"));

        Drawable upCreateButton_Drawable= new TextureRegionDrawable(new TextureRegion(upCreateButton));
        Drawable downCreateButton_Drawable = new TextureRegionDrawable(new TextureRegion(downCreateButton));


        // === 3. 创建按钮样式 ===
        TextButton.TextButtonStyle buttonStyle_CreateButton = new TextButton.TextButtonStyle();
        buttonStyle_CreateButton.up = upCreateButton_Drawable;
        buttonStyle_CreateButton.down = downCreateButton_Drawable;
        buttonStyle_CreateButton.font = fontCute_36;
        buttonStyle_CreateButton.fontColor = Color.BLACK;
        skin.add("CreateButton", buttonStyle_CreateButton);



        TextButton.TextButtonStyle defaultButtonStyle = new TextButton.TextButtonStyle(buttonStyle_first);
        skin.add("default", defaultButtonStyle);


        // 为输入框准备背景纹理
        Texture backgroundTexture = new Texture(Gdx.files.internal("ui/input/editbox.png")); // 替换为你的背景图片路径
        Drawable backgroundDrawable = new TextureRegionDrawable(new TextureRegion(backgroundTexture));

        // 创建TextField样式，并设置背景
        TextField.TextFieldStyle textFieldStyleWithBackground = new TextField.TextFieldStyle();
        textFieldStyleWithBackground.font = font;        // 使用之前加载的字体
        textFieldStyleWithBackground.fontColor = Color.WHITE;
        textFieldStyleWithBackground.background = backgroundDrawable; // 设置背景
        textFieldStyleWithBackground.cursor = skin.newDrawable("white", Color.WHITE); // 光标
        skin.add("TextField", textFieldStyleWithBackground); // 注册新样式，"backgrounded" 是这个样式的名称
        /**
         * 创建滑动选择器
         */
        // 1. 加载进度条背景/滑块纹理（替换为你的PVZ风格资源）
        Texture sliderBgTex = new Texture(Gdx.files.internal("ui/slider/slider_strip.png")); // 进度条背景（4段长度）
        Texture sliderKnobTex = new Texture(Gdx.files.internal("ui/slider/slider_piece.png")); // 滑块（圆形/方形）

        Drawable sliderBg = new TextureRegionDrawable(new TextureRegion(sliderBgTex));
        Drawable sliderKnob = new TextureRegionDrawable(new TextureRegion(sliderKnobTex));

        // 2. 创建Slider样式
        Slider.SliderStyle sliderStyle = new Slider.SliderStyle();
        sliderStyle.background = sliderBg;          // 进度条背景
        sliderStyle.knob = sliderKnob;              // 滑块
        sliderStyle.knobBefore = sliderBg;          // 已选中部分的填充（可换不同纹理）
        skin.add("step-slider-style", sliderStyle);

        // TextField
        var textFieldStyle = new TextField.TextFieldStyle();
        textFieldStyle.font = font;        // ← 关键
        textFieldStyle.fontColor = Color.WHITE;
        textFieldStyle.background = createColoredDrawable(Color.valueOf("555555"));
        textFieldStyle.cursor = skin.newDrawable("white", Color.WHITE); // 光标
        skin.add("default", textFieldStyle);

        Label.LabelStyle labelStyle = new Label.LabelStyle();
        labelStyle.font = fontCute_50;
        labelStyle.fontColor = Color.BLACK;
        labelStyle.background = null;
        skin.add("default", labelStyle);

        Label.LabelStyle labelStyle_32 = new Label.LabelStyle();
        labelStyle_32.font = fontCute_32;
        labelStyle_32.fontColor = Color.BLACK;
        labelStyle_32.background = null;
        skin.add("default_32", labelStyle_32);

        Label.LabelStyle labelStyle_36 = new Label.LabelStyle();
        labelStyle_36.font = fontCute_36;
        labelStyle_36.fontColor = Color.BLACK;
        labelStyle_36.background = null;
        skin.add("default_36", labelStyle_36);

        Label.LabelStyle labelStyle_huipu = new Label.LabelStyle();
        labelStyle_huipu.font = huipu_font;
        labelStyle_huipu.fontColor = Color.BLACK;
        labelStyle_huipu.background = null;
        skin.add("default_huipu", labelStyle_huipu);
        // Window
        var windowStyle = new Window.WindowStyle();
        windowStyle.titleFont = font;
        windowStyle.titleFontColor = Color.WHITE;
        windowStyle.background = createColoredDrawable(Color.valueOf("222222"));
        skin.add("default", windowStyle);

        // 创建一个简单的 ScrollPane 默认样式
        ScrollPane.ScrollPaneStyle scrollPaneStyle = new ScrollPane.ScrollPaneStyle();
        scrollPaneStyle.background = skin.newDrawable("white", Color.DARK_GRAY); // 背景（可选）
        scrollPaneStyle.vScroll = skin.newDrawable("white", Color.LIGHT_GRAY);   // 垂直滚动条
        scrollPaneStyle.vScrollKnob = skin.newDrawable("white", Color.WHITE);    // 滚动块
        scrollPaneStyle.hScroll = skin.newDrawable("white", Color.LIGHT_GRAY);   // 水平滚动条（如需要）
        scrollPaneStyle.hScrollKnob = skin.newDrawable("white", Color.WHITE);

        // 注册为 "default"
        skin.add("default", scrollPaneStyle, ScrollPane.ScrollPaneStyle.class);


        // 然后在 SelectBoxStyle 中引用它
        var selectBoxStyle = new SelectBox.SelectBoxStyle();
        selectBoxStyle.font = font;
        selectBoxStyle.fontColor = Color.WHITE;
        selectBoxStyle.background = createColoredDrawable(Color.valueOf("444444"));
        selectBoxStyle.listStyle = new List.ListStyle();
        selectBoxStyle.listStyle.font = font;
        selectBoxStyle.listStyle.fontColorUnselected = Color.LIGHT_GRAY;
        selectBoxStyle.listStyle.fontColorSelected = Color.WHITE;
        selectBoxStyle.listStyle.selection = createColoredDrawable(Color.valueOf("555555"));

        //关键修复：设置 scrollStyle
        selectBoxStyle.scrollStyle = scrollPaneStyle; // ← 直接使用刚创建的对象

        skin.add("default", selectBoxStyle);
        return skin;
    }

    private static BitmapFont getFont(FileHandle fontCuteFile, String characters , Integer size) {
        FreeTypeFontGenerator generator_Cute = new FreeTypeFontGenerator(fontCuteFile);
        FreeTypeFontGenerator.FreeTypeFontParameter param_Cute = new FreeTypeFontGenerator.FreeTypeFontParameter();

        param_Cute.size = size;
        param_Cute.color = Color.WHITE;
        param_Cute.characters = characters;
        BitmapFont fontCute = generator_Cute.generateFont(param_Cute);
        generator_Cute.dispose();
        return fontCute;
    }

    private static BitmapFont getFont(FileHandle fontCuteFile,Integer size) {
        FreeTypeFontGenerator generator_Cute = new FreeTypeFontGenerator(fontCuteFile);
        FreeTypeFontGenerator.FreeTypeFontParameter param_Cute = new FreeTypeFontGenerator.FreeTypeFontParameter();

        param_Cute.size = size;
        param_Cute.color = Color.WHITE;
        param_Cute.characters = "1234567890人简单普通困难炼狱";
        BitmapFont fontCute = generator_Cute.generateFont(param_Cute);
        generator_Cute.dispose();
        return fontCute;
    }
}