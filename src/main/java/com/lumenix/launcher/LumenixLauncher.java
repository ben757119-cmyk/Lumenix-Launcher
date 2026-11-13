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
        System.setProperty("apple.awt.application.name", "Lumenix Launcher");
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            LauncherFrame frame = new LauncherFrame();
            frame.setVisible(true);
            Log.info("UI ready");
        });
    }
}
