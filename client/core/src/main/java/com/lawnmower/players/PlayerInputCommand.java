package com.lawnmower.players;

import com.badlogic.gdx.math.Vector2;

public class PlayerInputCommand {
    public final int seq;
    public final Vector2 moveDir;
    public final boolean isAttacking;
    public final long timestampMs;

    public PlayerInputCommand(int seq, Vector2 moveDir, boolean isAttacking) {
        this.seq = seq;
        this.moveDir = new Vector2(moveDir);
        this.isAttacking = isAttacking;
        this.timestampMs = System.currentTimeMillis();
    }
}
