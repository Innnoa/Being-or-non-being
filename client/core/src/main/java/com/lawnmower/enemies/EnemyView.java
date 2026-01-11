package com.lawnmower.enemies;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

/**
 * 负责渲染/插值一只僵尸。
 */
public class EnemyView {

    private static final float DISPLAY_LERP_RATE = 10f;
    private static final float DISPLAY_SNAP_DISTANCE = 6f;

    private final int enemyId;
    private final Vector2 targetPosition = new Vector2();
    private final Vector2 displayPosition = new Vector2();
    private final Vector2 lastServerPosition = new Vector2();
    private final Vector2 velocity = new Vector2();

    private Animation<TextureRegion> walkAnimation;
    private TextureRegion fallbackFrame;
    private float animationTime = 0f;
    private long lastServerUpdateMs = 0L;
    private boolean alive = true;
    private boolean facingRight = true;

    public EnemyView(int enemyId,
                     Vector2 initialPosition,
                     Animation<TextureRegion> animation,
                     TextureRegion fallbackFrame) {
        this.enemyId = enemyId;
        if (initialPosition != null) {
            targetPosition.set(initialPosition);
            displayPosition.set(initialPosition);
            lastServerPosition.set(initialPosition);
        }
        setAnimation(animation, fallbackFrame);
    }

    public void setAnimation(Animation<TextureRegion> animation, TextureRegion fallback) {
        this.walkAnimation = animation;
        if (fallback != null) {
            this.fallbackFrame = fallback;
        }
    }

    public void applyServerState(Vector2 serverPosition, boolean isAlive, long serverTimeMs) {
        if (serverPosition == null) {
            return;
        }

        if (lastServerUpdateMs > 0L) {
            long deltaMs = Math.max(1L, serverTimeMs - lastServerUpdateMs);
            velocity.set(serverPosition).sub(lastServerPosition).scl(1000f / deltaMs);
        } else {
            velocity.setZero();
        }

        if (Math.abs(velocity.x) > 0.0001f) {
            facingRight = velocity.x >= 0f;
        }

        targetPosition.set(serverPosition);
        lastServerPosition.set(serverPosition);
        lastServerUpdateMs = serverTimeMs;
        alive = isAlive;
    }

    public void teleport(Vector2 position, long timestampMs) {
        if (position == null) {
            return;
        }
        targetPosition.set(position);
        displayPosition.set(position);
        lastServerPosition.set(position);
        lastServerUpdateMs = timestampMs;
    }

    public boolean isAlive() {
        return alive;
    }

    public void render(SpriteBatch batch, float delta) {
        if (batch == null || (!alive && walkAnimation == null && fallbackFrame == null)) {
            return;
        }
        if (delta > 0f) {
            float distSq = displayPosition.dst2(targetPosition);
            if (distSq > DISPLAY_SNAP_DISTANCE * DISPLAY_SNAP_DISTANCE) {
                displayPosition.set(targetPosition);
            } else {
                float alpha = MathUtils.clamp(delta * DISPLAY_LERP_RATE, 0f, 1f);
                displayPosition.lerp(targetPosition, alpha);
            }
            animationTime += delta;
        }

        TextureRegion frame = null;
        if (walkAnimation != null && walkAnimation.getKeyFrames().length > 0) {
            frame = walkAnimation.getKeyFrame(animationTime, true);
        }
        if (frame == null) {
            frame = fallbackFrame;
        }
        if (frame == null) {
            return;
        }

        float width = frame.getRegionWidth();
        float height = frame.getRegionHeight();
        float drawX = displayPosition.x - width / 2f;
        float drawY = displayPosition.y - height / 2f;
        float originX = width / 2f;
        float originY = height / 2f;
        float scaleX = facingRight ? 1f : -1f;

        batch.draw(frame, drawX, drawY, originX, originY, width, height, scaleX, 1f, 0f);
    }
}
