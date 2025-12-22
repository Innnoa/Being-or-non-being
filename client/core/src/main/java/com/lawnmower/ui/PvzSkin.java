package com.lawnmower.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.SpriteDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

import java.nio.charset.StandardCharsets;

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
        //注册 white 纹理
        skin.add("white", createColoredTexture(Color.WHITE));
        FileHandle charFile = Gdx.files.internal("fonts/chars.txt");
        String characters = charFile.readString(); // 自动按 UTF-8 读取
        BitmapFont defaultFont = new BitmapFont();
        defaultFont.getData().setScale(3f);
        skin.add("system-default-font", defaultFont);

        /**
         * 字体
         */
        BitmapFont font = getFont(Gdx.files.internal("fonts/pvz.ttf"),characters,40);

        BitmapFont fontCute_40 = getFont(Gdx.files.internal("fonts/pvz_cute.ttf"), characters,40);

        BitmapFont fontCute_44 = getFont(Gdx.files.internal("fonts/pvz_cute.ttf"), characters,44);
        
        BitmapFont fontCute_32 = getFont(Gdx.files.internal("fonts/pvz_cute.ttf"), characters,32);

        BitmapFont huipu_font = getFont(Gdx.files.internal("fonts/huipu_pvz.ttf"),50);
        /**
         * 按钮
         */
        skin.add("MainPage", getTextButtonStyle(font,"ui/button/MainPage/MainPage_up.png","ui/button/MainPage/MainPage_down.png"));

        skin.add("RoomList",getTextButtonStyle(fontCute_40,"ui/button/RoomList/roomListButton_on.png","ui/button/RoomList/roomListButton_down.png"));

        skin.add("RoomList_def", getTextButtonStyle(defaultFont,"ui/button/RoomList/roomListButton_on.png","ui/button/RoomList/roomListButton_down.png"));

        skin.add("PapperButton", getTextButtonStyle(font,"ui/button/PapperButton/paperButton_on.png","ui/button/PapperButton/paperButton_down.png"));

        skin.add("CreateButton", getTextButtonStyle(fontCute_40,"ui/button/createRoomButton/createButton_on.png","ui/button/createRoomButton/createButton_down.png"));

        skin.add("default", new TextButton.TextButtonStyle(getTextButtonStyle(font,"ui/button/MainPage/MainPage_up.png","ui/button/MainPage/MainPage_down.png")));
        /**
         * 输入框
         */
        skin.add("TextField", getTextFieldStyle(font, skin,"ui/input/editbox.png"));

        skin.add("default", getTextFieldStyle(font, skin));
        /**
         * 滑动选择器
         */
        skin.add("step-slider-style", getSliderStyle("ui/slider/slider_strip.png","ui/slider/slider_piece.png"));
        /**
         * 标签
         */
        skin.add("default_32", getLabelDefault(fontCute_32));

        skin.add("default_36", getLabelDefault(fontCute_40));

        skin.add("default", getLabelDefault(fontCute_44));

        skin.add("default_huipu", getLabelDefault(huipu_font));
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
    /**
     *
     * @param font 传入字体
     * @return
     */
    private static Label.LabelStyle getLabelDefault(BitmapFont font){
        Label.LabelStyle labelStyle = new Label.LabelStyle();
        labelStyle.font = font;
        labelStyle.fontColor = Color.BLACK;
        labelStyle.background = null;
        return labelStyle;
    };
    /**
     *
     * @param strip 条
     * @param piece 块
     * @return
     */
    private static Slider.SliderStyle getSliderStyle(String strip,String piece) {
        Texture sliderBgTex = new Texture(Gdx.files.internal(strip)); // 进度条背景（4段长度）
        Texture sliderKnobTex = new Texture(Gdx.files.internal(piece)); // 滑块（圆形/方形）

        Drawable sliderBg = new TextureRegionDrawable(new TextureRegion(sliderBgTex));
        Drawable sliderKnob = new TextureRegionDrawable(new TextureRegion(sliderKnobTex));

        // 2. 创建Slider样式
        Slider.SliderStyle sliderStyle = new Slider.SliderStyle();
        sliderStyle.background = sliderBg;          // 进度条背景
        sliderStyle.knob = sliderKnob;              // 滑块
        sliderStyle.knobBefore = sliderBg;          // 已选中部分的填充（可换不同纹理）
        return sliderStyle;
    }
    /**
     *
     * @param font 字体
     * @param skin 皮肤
     * @param background 背景
     * @return
     */
    private static TextField.TextFieldStyle getTextFieldStyle(BitmapFont font, Skin skin,String background) {
        Texture backgroundTexture = new Texture(Gdx.files.internal(background)); // 替换为你的背景图片路径
        Drawable backgroundDrawable = new TextureRegionDrawable(new TextureRegion(backgroundTexture));

        // 创建TextField样式，并设置背景
        TextField.TextFieldStyle textFieldStyleWithBackground = new TextField.TextFieldStyle();
        textFieldStyleWithBackground.font = font;        // 使用之前加载的字体
        textFieldStyleWithBackground.fontColor = Color.WHITE;
        textFieldStyleWithBackground.background = backgroundDrawable; // 设置背景
        textFieldStyleWithBackground.cursor = skin.newDrawable("white", Color.WHITE); // 光标
        return textFieldStyleWithBackground;
    }
    /**
     *
     * @param font 字体
     * @param skin 皮肤
     * @return
     */
    private static TextField.TextFieldStyle getTextFieldStyle(BitmapFont font, Skin skin) {
        var textFieldStyle = new TextField.TextFieldStyle();
        textFieldStyle.font = font;        // ← 关键
        textFieldStyle.fontColor = Color.WHITE;
        textFieldStyle.background = createColoredDrawable(Color.valueOf("555555"));
        textFieldStyle.cursor = skin.newDrawable("white", Color.WHITE); // 光标
        return textFieldStyle;
    }
    /**
     *
     * @param font 字体
     * @param up 没按
     * @param down 按下去
     * @return
     */
    private static TextButton.TextButtonStyle getTextButtonStyle(BitmapFont font ,String up,String down) {
        Texture upTex = new Texture(Gdx.files.internal(up));
        Texture downTex = new Texture(Gdx.files.internal(down));

        Drawable upDrawable = new TextureRegionDrawable(new TextureRegion(upTex));
        Drawable downDrawable = new TextureRegionDrawable(new TextureRegion(downTex));

        TextButton.TextButtonStyle buttonStyle_first = new TextButton.TextButtonStyle();
        buttonStyle_first.up = upDrawable;
        buttonStyle_first.down = downDrawable;
        buttonStyle_first.font = font;
        buttonStyle_first.fontColor = Color.BLACK;
        return buttonStyle_first;
    }
    /**
     * @param fontCuteFile 字体文件
     * @param characters 字符集
     * @param size 字号
     * @return 加载全部字符的字体
     */
    private static BitmapFont getFont(FileHandle fontCuteFile, String characters , Integer size) {
        FreeTypeFontGenerator generator_Cute = new FreeTypeFontGenerator(fontCuteFile);
        FreeTypeFontGenerator.FreeTypeFontParameter param_Cute = new FreeTypeFontGenerator.FreeTypeFontParameter();

        // 1. 核心修复：低版本兼容 - 用 PixmapPacker 配置大纹理尺寸
        int textureSize = 4096; // 4096x4096 足够容纳所有字符
        PixmapPacker packer = new PixmapPacker(textureSize, textureSize, Pixmap.Format.RGBA8888, 2, false);
        param_Cute.packer = packer; // 绑定自定义打包器，指定大纹理尺寸

        // 2. 保留关键配置（编码、渲染、数字集）
        param_Cute.size = size;
        param_Cute.color = Color.BLACK; // 高对比度
        param_Cute.borderWidth = 1;
        param_Cute.borderColor = Color.BLACK; // 黑色边框增强辨识度
        param_Cute.magFilter = Texture.TextureFilter.Nearest;
        param_Cute.minFilter = Texture.TextureFilter.Nearest;
        param_Cute.hinting = FreeTypeFontGenerator.Hinting.Full;

        // 3. 半角+全角数字双保险（避免编码问题）
        String halfWidthNum = "0123456789";
        String fullWidthNum = "０１２３４５６７８９";
        String finalChars = new String(
                (characters + halfWidthNum + fullWidthNum).getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        param_Cute.characters = finalChars;

        // 4. 生成字体并处理打包器
        BitmapFont fontCute = generator_Cute.generateFont(param_Cute);
        generator_Cute.dispose();
        packer.dispose(); // 释放打包器资源（关键，避免内存泄漏）

        return fontCute;
    }
    /**
     * @param fontCuteFile 字体文件
     * @param size 字号
     * @return 指定字符的字体
     */
    private static BitmapFont getFont(FileHandle fontCuteFile,Integer size) {
        FreeTypeFontGenerator generator_Cute = new FreeTypeFontGenerator(fontCuteFile);
        FreeTypeFontGenerator.FreeTypeFontParameter param_Cute = new FreeTypeFontGenerator.FreeTypeFontParameter();

        param_Cute.size = size;
        param_Cute.color = Color.WHITE;
        param_Cute.characters = "!\\\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\\\]^_`abcdefghijklmnopqrstuvwxyz{|}~人简单普通困难炼狱单多设置退出游戏创建房间返回第页";
        BitmapFont fontCute = generator_Cute.generateFont(param_Cute);
        generator_Cute.dispose();
        return fontCute;
    }
}