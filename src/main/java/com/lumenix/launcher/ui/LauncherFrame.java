package com.lumenix.launcher.ui;

import com.lumenix.launcher.Paths;
import com.lumenix.launcher.auth.AuthSession;
import com.lumenix.launcher.auth.MicrosoftAuth;
import com.lumenix.launcher.download.DownloadManager;
import com.lumenix.launcher.install.InstallMeta;
import com.lumenix.launcher.install.InstallService;
import com.lumenix.launcher.install.LauncherConfig;
import com.lumenix.launcher.launch.LaunchService;
import com.lumenix.launcher.util.Log;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public final class LauncherFrame extends JFrame {
    private final DownloadManager downloads = new DownloadManager();
    private final InstallService install = new InstallService(downloads);
    private final JLabel status = new JLabel("Ready");
    private final JTextArea logArea = new JTextArea();
    private final JComboBox<String> edition = new JComboBox<>(new String[]{"FREE", "PAID"});
    private final JButton loginBtn = new JButton("Microsoft Login");
    private final JButton installBtn = new JButton("Install / Update");
    private final JButton repairBtn = new JButton("Repair Installation");
    private final JButton playBtn = new JButton("PLAY");
    private final JLabel accountLbl = new JLabel("Not signed in");
    private AuthSession session = MicrosoftAuth.load();
    private LauncherConfig config;

    public LauncherFrame() {
        super("Lumenix Launcher");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(720, 520);
        setLocationRelativeTo(null);
        try {
            Paths.ensureDirs();
            config = install.loadConfig();
            edition.setSelectedItem(config.edition != null ? config.edition : "FREE");
        } catch (Exception e) { config = new LauncherConfig(); }
        if (session != null && session.isValid()) accountLbl.setText("Signed in: " + session.username);
        buildUi();
        Log.setUiSink(line -> SwingUtilities.invokeLater(() -> {
            logArea.append(line + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
            status.setText(line.length() > 80 ? line.substring(0, 80) + "…" : line);
        }));
        Log.info("Lumenix Launcher — persistent root: " + Paths.root());
        Log.info("Minecraft target: " + Paths.MC_VERSION + " + Fabric only");
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(16, 16, 16, 16));
        root.setBackground(new Color(0x12, 0x14, 0x1a));
        JLabel title = new JLabel("LUMENIX");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 28f));
        title.setForeground(new Color(0x8a, 0xc7, 0xff));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(title, BorderLayout.WEST);
        accountLbl.setForeground(Color.LIGHT_GRAY);
        top.add(accountLbl, BorderLayout.EAST);
        root.add(top, BorderLayout.NORTH);
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.setOpaque(false);
        JLabel ed = new JLabel("Edition:");
        ed.setForeground(Color.WHITE);
        row.add(ed);
        edition.setPreferredSize(new Dimension(120, 28));
        row.add(edition);
        center.add(row);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        buttons.setOpaque(false);
        for (JButton b : new JButton[]{loginBtn, installBtn, repairBtn}) {
            b.setBackground(new Color(0x1e, 0x24, 0x32));
            b.setForeground(Color.WHITE);
            b.setFocusPainted(false);
            buttons.add(b);
        }
        playBtn.setBackground(new Color(0x2d, 0x6a, 0x4f));
        playBtn.setForeground(Color.WHITE);
        playBtn.setFocusPainted(false);
        playBtn.setFont(playBtn.getFont().deriveFont(Font.BOLD, 14f));
        buttons.add(playBtn);
        center.add(buttons);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBackground(new Color(0x0c, 0x0e, 0x14));
        logArea.setForeground(new Color(0xc8, 0xd0, 0xdc));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setPreferredSize(new Dimension(680, 280));
        center.add(scroll);
        root.add(center, BorderLayout.CENTER);
        status.setForeground(new Color(0x9a, 0xa3, 0xb2));
        root.add(status, BorderLayout.SOUTH);
        setContentPane(root);
        loginBtn.addActionListener(e -> runBg("Login", this::doLogin));
        installBtn.addActionListener(e -> runBg("Install", this::doInstall));
        repairBtn.addActionListener(e -> runBg("Repair", this::doRepair));
        playBtn.addActionListener(e -> runBg("Play", this::doPlay));
    }

    private void runBg(String name, Task t) {
        setBusy(true);
        new Thread(() -> {
            try { t.run(); }
            catch (Throwable ex) {
                Log.error(name + " failed", ex);
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this, ex.getMessage(), name + " failed", JOptionPane.ERROR_MESSAGE));
            } finally { SwingUtilities.invokeLater(() -> setBusy(false)); }
        }, "lumenix-" + name.toLowerCase()).start();
    }

    private void setBusy(boolean busy) {
        loginBtn.setEnabled(!busy);
        installBtn.setEnabled(!busy);
        repairBtn.setEnabled(!busy);
        playBtn.setEnabled(!busy);
        edition.setEnabled(!busy);
    }

    private void doLogin() throws Exception {
        session = MicrosoftAuth.loginDeviceCode(msg -> Log.info(msg));
        SwingUtilities.invokeLater(() -> accountLbl.setText("Signed in: " + session.username));
    }

    private void doInstall() throws Exception {
        config.edition = String.valueOf(edition.getSelectedItem());
        install.saveConfig(config);
        InstallMeta meta = install.loadMeta();
        install.ensureMinecraft(meta);
        install.ensureFabric(meta);
        install.ensureLumenixClient(config, meta);
        Log.info("Install/update finished — existing valid files were reused where possible.");
    }

    private void doRepair() throws Exception {
        config.edition = String.valueOf(edition.getSelectedItem());
        install.saveConfig(config);
        install.repair(config);
    }

    private void doPlay() throws Exception {
        config.edition = String.valueOf(edition.getSelectedItem());
        install.saveConfig(config);
        if (session == null || !session.isValid()) {
            Log.warn("No valid session — starting Microsoft login");
            session = MicrosoftAuth.loginDeviceCode(msg -> Log.info(msg));
            SwingUtilities.invokeLater(() -> accountLbl.setText("Signed in: " + session.username));
        }
        InstallMeta meta = install.loadMeta();
        install.ensureMinecraft(meta);
        install.ensureFabric(meta);
        install.ensureLumenixClient(config, meta);
        Process p = LaunchService.launch(config, meta, session);
        Log.info("Game process started (pid=" + p.pid() + "). Logs: " + Paths.logs().resolve("game.log"));
    }

    @FunctionalInterface private interface Task { void run() throws Exception; }
}
