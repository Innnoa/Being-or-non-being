package com.lawnmower.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.lawnmower.Config;
import com.lawnmower.Main;
import lawnmower.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.lawnmower.network.TcpClient;

import java.io.IOException;

public class DesktopLauncher {
    private static final Logger log = LoggerFactory.getLogger(DesktopLauncher.class);

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("LawnMower Game");
        config.setWindowedMode(1000, 563);
        config.setForegroundFPS(60);

        new Lwjgl3Application(new Main(), config);
    }
}
