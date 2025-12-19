package com.lawnmower.ui.slider;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;

public class StepSlider extends Group {
    private Slider slider;
    private Array<String> stepLabels;
    private int currentStep = 0;
    private ChangeListener changeListener;

    public StepSlider(Skin skin, String labelStyle, String... labels) {
        if (labels == null || labels.length == 0) {
            throw new IllegalArgumentException("至少需要一个档位标签");
        }
        this.stepLabels = new Array<>(labels);
        init(skin, labelStyle);
    }

    private void init(Skin skin, String labelStyle) {
        int stepCount = stepLabels.size;

        // 创建底层 Slider
        slider = new Slider(0, stepCount - 1, 1f, false, skin, "step-slider-style");
        slider.setSize(getPrefWidth(), getPrefHeight());
        addActor(slider);

        // 监听滑动
        slider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                snapToNearestStep();
            }
        });
    }

    // 强制对齐到最近档位
    private void snapToNearestStep() {
        float value = slider.getValue();
        int step = MathUtils.clamp(Math.round(value), 0, stepLabels.size - 1);
        if (step != currentStep) {
            currentStep = step;
            slider.setValue(step);
            notifyChangeListener();
        }
    }

    // 对外提供当前档位索引（0-based）
    public int getCurrentStep() {
        return currentStep;
    }

    // 对外提供当前档位文本
    public String getCurrentLabel() {
        return stepLabels.get(currentStep);
    }

    // 设置档位（可选）
    public void setCurrentStep(int step) {
        if (step >= 0 && step < stepLabels.size) {
            currentStep = step;
            slider.setValue(step);
            notifyChangeListener();
        }
    }

    // 设置改变监听器
    public void setChangeListener(ChangeListener listener) {
        this.changeListener = listener;
    }

    private void notifyChangeListener() {
        if (changeListener != null) {
            changeListener.changed(null, null); // 可以传递具体的事件和Actor对象
        }
    }

    public float getPrefWidth() {
        return 400; // 默认宽度，可在构造后 setSize 覆盖
    }

    public float getPrefHeight() {
        return 60; // 默认高度
    }
}