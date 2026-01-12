package com.lawnmower.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.lawnmower.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public class DesktopLauncher {
    private static final Logger log = LoggerFactory.getLogger(DesktopLauncher.class);

    public static void main(String[] args) {
        forceUtf8Console();
        Main game = new Main();
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("植物大战僵尸-ROGUELIKE");
        config.setWindowedMode(1000, 563);
        config.setForegroundFPS(60);
        config.setWindowListener(new Lwjgl3WindowAdapter() {
            @Override
            public boolean closeRequested() {
                game.requestExit();
                return true;
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(game::requestExit, "lawnmower-shutdown"));

        new Lwjgl3Application(game, config);
    }

    private static void forceUtf8Console() {
        try {
            System.setProperty("sun.stdout.encoding", "UTF-8");
            System.setProperty("sun.stderr.encoding", "UTF-8");
            System.setProperty("stdout.encoding", "UTF-8");
            System.setProperty("stderr.encoding", "UTF-8");
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Failed to switch console streams to UTF-8", e);
        }
    }
}
