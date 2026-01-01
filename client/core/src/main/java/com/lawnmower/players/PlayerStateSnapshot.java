package com.lawnmower.players;

import com.badlogic.gdx.math.Vector2;

public class PlayerStateSnapshot {
    public final Vector2 position;
    public final float rotation;
    public final int lastProcessedInputSeq;

    public PlayerStateSnapshot(Vector2 pos, float rot, int lastSeq) {
        this.position = new Vector2(pos);
        this.rotation = rot;
        this.lastProcessedInputSeq = lastSeq;
    }
}
