package com.lawnmower.ui.Drop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

public class DropPopup extends Group {
    private final Skin skin;
    private final Texture popupBgTex; // 弹窗背景图片
    private final float targetX;      // 弹窗最终停留的X坐标
    private final float targetY;      // 弹窗最终停留的Y坐标

    // 构造器：传入皮肤、背景图片路径、目标位置
    public DropPopup(Skin skin, String bgImagePath, float targetX, float targetY) {
        this.skin = skin;
        this.targetX = targetX;
        this.targetY = targetY;

        // 1. 加载弹窗背景图片
        popupBgTex = new Texture(Gdx.files.internal(bgImagePath));
        TextureRegionDrawable bgDrawable = new TextureRegionDrawable(new TextureRegion(popupBgTex));

        // 2. 创建弹窗背景（作为根容器）
        Button bgButton = new Button(bgDrawable);
        bgButton.setSize(popupBgTex.getWidth(), popupBgTex.getHeight());
        addActor(bgButton);

        // 初始化弹窗位置：屏幕上方外（不可见）
        setPosition(targetX, Gdx.graphics.getHeight() + popupBgTex.getHeight());
        setSize(popupBgTex.getWidth(), popupBgTex.getHeight());
        setVisible(false); // 默认隐藏
    }

    // 显示弹窗：执行下落动画
    public void show() {
        setVisible(true);
        clearActions(); // 清除原有动画
        // 动画序列：重置位置 → 缓动下落至目标位置（1秒完成）
        addAction(Actions.sequence(
                Actions.moveTo(targetX, Gdx.graphics.getHeight() + getHeight()), // 初始位置（上方外）
                Actions.moveTo(targetX, targetY, 1f) // 下落至目标位置，耗时1秒
        ));
    }

    public void hide() {
        clearActions();
        addAction(Actions.sequence(
                Actions.moveTo(targetX, Gdx.graphics.getHeight() + getHeight(), 0.5f),
                Actions.run(() -> {
                    setVisible(false);
                    remove(); // 自动从舞台移除
                })
        ));
    }

    // 资源释放（避免内存泄漏）
    public void dispose() {
        popupBgTex.dispose();
    }
}
