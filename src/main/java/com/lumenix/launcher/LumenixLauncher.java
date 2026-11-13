package com.lumenix.launcher;

import com.lumenix.launcher.ui.LauncherFrame;
import com.lumenix.launcher.util.Log;

import javax.swing.*;

public final class LumenixLauncher {
    public static void main(String[] args) {
        try {
            Paths.ensureDirs();
        } catch (Exception e) {
            System.err.println("Cannot create data dirs: " + e.getMessage());
        }
        // Force a predictable cross-platform look so custom buttons stay readable
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {}
        System.setProperty("apple.awt.application.name", "Lumenix Launcher");
        SwingUtilities.invokeLater(() -> {
            LauncherFrame frame = new LauncherFrame();
            frame.setVisible(true);
            Log.info("UI ready");
        });
    }
}
