package com.lumenix.launcher.ui;

import com.lumenix.launcher.Paths;
import com.lumenix.launcher.auth.AuthSession;
import com.lumenix.launcher.auth.MicrosoftAuth;
import com.lumenix.launcher.download.DownloadManager;
import com.lumenix.launcher.install.InstallMeta;
import com.lumenix.launcher.install.InstallService;
import com.lumenix.launcher.install.LauncherConfig;
import com.lumenix.launcher.launch.LaunchService;
import com.lumenix.launcher.util.DiscordWebhook;
import com.lumenix.launcher.util.Log;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

/**
 * High-contrast custom UI — does NOT rely on system L&amp;F button colors
 * (Windows often paints white-on-white if you only setForeground).
 */
public final class LauncherFrame extends JFrame {
    private static final Color BG = new Color(0x0E1016);
    private static final Color PANEL = new Color(0x161A24);
    private static final Color CARD = new Color(0x1C2230);
    private static final Color TEXT = new Color(0xF2F4F8);
    private static final Color MUTED = new Color(0xA8B0C0);
    private static final Color ACCENT = new Color(0x5B9DFF);
    private static final Color BTN = new Color(0x2A3348);
    private static final Color BTN_HOVER = new Color(0x3A4660);
    private static final Color PLAY = new Color(0x1F8B4C);
    private static final Color PLAY_HOVER = new Color(0x27A85C);
    private static final Color BORDER = new Color(0x2E3648);

    private final DownloadManager downloads = new DownloadManager();
    private final InstallService install = new InstallService(downloads);

    private final JLabel status = new JLabel("Ready");
    private final JTextArea logArea = new JTextArea();
    private final JComboBox<String> edition = new JComboBox<>(new String[]{"FREE", "PAID"});
    private final JButton loginBtn = solidButton("Microsoft Login", BTN, BTN_HOVER);
    private final JButton installBtn = solidButton("Install / Update", BTN, BTN_HOVER);
    private final JButton repairBtn = solidButton("Repair", BTN, BTN_HOVER);
    private final JButton webhooksBtn = solidButton("Discord Webhooks…", BTN, BTN_HOVER);
    private final JButton playBtn = solidButton("PLAY", PLAY, PLAY_HOVER);
    private final JLabel accountLbl = new JLabel("Not signed in");

    private AuthSession session = MicrosoftAuth.load();
    private LauncherConfig config;

    public LauncherFrame() {
        super("Lumenix Launcher");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(820, 560));
        setSize(900, 600);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);

        try {
            Paths.ensureDirs();
            config = install.loadConfig();
            edition.setSelectedItem(config.edition != null ? config.edition : "FREE");
        } catch (Exception e) {
            config = new LauncherConfig();
        }
        if (session != null && session.isValid()) {
            accountLbl.setText("Signed in: " + session.username);
        }

        buildUi();

        Log.setUiSink(line -> SwingUtilities.invokeLater(() -> {
            logArea.append(line + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
            String s = line.length() > 100 ? line.substring(0, 100) + "…" : line;
            status.setText(s);
        }));
        Log.info("Lumenix Launcher — root: " + Paths.root());
        Log.info("Target: Minecraft " + Paths.MC_VERSION + " + Fabric only");
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(20, 24, 16, 24));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("LUMENIX");
        title.setFont(new Font("Segoe UI", Font.BOLD, 32));
        title.setForeground(ACCENT);
        JLabel sub = new JLabel("Launcher  ·  Minecraft 26.2 + Fabric");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        sub.setForeground(MUTED);
        JPanel titleCol = new JPanel();
        titleCol.setLayout(new BoxLayout(titleCol, BoxLayout.Y_AXIS));
        titleCol.setOpaque(false);
        titleCol.add(title);
        titleCol.add(Box.createVerticalStrut(4));
        titleCol.add(sub);
        header.add(titleCol, BorderLayout.WEST);
        accountLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        accountLbl.setForeground(TEXT);
        header.add(accountLbl, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // Center card
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER, 1, true),
                new EmptyBorder(16, 16, 16, 16)));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        controls.setOpaque(false);

        JLabel edLbl = new JLabel("Edition");
        edLbl.setForeground(MUTED);
        edLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        controls.add(edLbl);

        styleCombo(edition);
        controls.add(edition);
        controls.add(loginBtn);
        controls.add(installBtn);
        controls.add(repairBtn);
        controls.add(webhooksBtn);
        playBtn.setPreferredSize(new Dimension(120, 36));
        controls.add(playBtn);
        card.add(controls);
        card.add(Box.createVerticalStrut(10));

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBackground(CARD);
        logArea.setForeground(TEXT);
        logArea.setCaretColor(TEXT);
        logArea.setBorder(new EmptyBorder(10, 10, 10, 10));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(new LineBorder(BORDER, 1));
        scroll.getViewport().setBackground(CARD);
        scroll.setPreferredSize(new Dimension(840, 340));
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(scroll);

        JPanel centerWrap = new JPanel(new BorderLayout());
        centerWrap.setOpaque(false);
        centerWrap.setBorder(new EmptyBorder(18, 0, 12, 0));
        centerWrap.add(card, BorderLayout.CENTER);
        root.add(centerWrap, BorderLayout.CENTER);

        status.setForeground(MUTED);
        status.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        root.add(status, BorderLayout.SOUTH);

        setContentPane(root);

        loginBtn.addActionListener(e -> runBg("Login", this::doLogin));
        installBtn.addActionListener(e -> runBg("Install", this::doInstall));
        repairBtn.addActionListener(e -> runBg("Repair", this::doRepair));
        playBtn.addActionListener(e -> runBg("Play", this::doPlay));
        webhooksBtn.addActionListener(e -> openWebhookDialog());
    }

    private void openWebhookDialog() {
        JTextField launches = field(config.webhookLaunches);
        JTextField logins = field(config.webhookLogins);
        JTextField installs = field(config.webhookInstalls);
        JTextField errors = field(config.webhookErrors);
        JTextField general = field(config.webhookGeneral);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(PANEL);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        int row = 0;
        row = addRow(form, c, row, "Launches (PLAY)", launches);
        row = addRow(form, c, row, "Logins", logins);
        row = addRow(form, c, row, "Installs / updates", installs);
        row = addRow(form, c, row, "Errors", errors);
        addRow(form, c, row, "General", general);

        JLabel hint = new JLabel("<html><body style='width:360px;color:#A8B0C0'>Paste full Discord webhook URLs. Leave blank to disable. Saved to Config/launcher.json.</body></html>");

        JPanel wrap = new JPanel(new BorderLayout(0, 10));
        wrap.setBackground(PANEL);
        wrap.setBorder(new EmptyBorder(8, 8, 8, 8));
        wrap.add(hint, BorderLayout.NORTH);
        wrap.add(form, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(
                this, wrap, "Discord webhooks",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        config.webhookLaunches = launches.getText().trim();
        config.webhookLogins = logins.getText().trim();
        config.webhookInstalls = installs.getText().trim();
        config.webhookErrors = errors.getText().trim();
        config.webhookGeneral = general.getText().trim();
        try {
            install.saveConfig(config);
            Log.info("Discord webhooks saved.");
            DiscordWebhook.send(config.webhookGeneral, "Lumenix Launcher: webhooks updated.");
        } catch (Exception ex) {
            Log.error("Failed to save webhooks", ex);
        }
    }

    private static int addRow(JPanel form, GridBagConstraints c, int row, String label, JTextField field) {
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        JLabel l = new JLabel(label);
        l.setForeground(TEXT);
        form.add(l, c);
        c.gridx = 1; c.weightx = 1;
        form.add(field, c);
        return row + 1;
    }

    private static JTextField field(String value) {
        JTextField f = new JTextField(value != null ? value : "", 36);
        f.setBackground(CARD);
        f.setForeground(TEXT);
        f.setCaretColor(TEXT);
        f.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER, 1), new EmptyBorder(6, 8, 6, 8)));
        return f;
    }

    private void runBg(String name, Task t) {
        setBusy(true);
        new Thread(() -> {
            try {
                t.run();
            } catch (Throwable ex) {
                Log.error(name + " failed", ex);
                DiscordWebhook.send(config.webhookErrors,
                        "**Lumenix Launcher error** (`" + name + "`): " + safe(ex.getMessage()));
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this,
                                ex.getMessage() != null ? ex.getMessage() : String.valueOf(ex),
                                name + " failed", JOptionPane.ERROR_MESSAGE));
            } finally {
                SwingUtilities.invokeLater(() -> setBusy(false));
            }
        }, "lumenix-" + name.toLowerCase()).start();
    }

    private void setBusy(boolean busy) {
        loginBtn.setEnabled(!busy);
        installBtn.setEnabled(!busy);
        repairBtn.setEnabled(!busy);
        playBtn.setEnabled(!busy);
        webhooksBtn.setEnabled(!busy);
        edition.setEnabled(!busy);
    }

    private void doLogin() throws Exception {
        session = MicrosoftAuth.loginDeviceCode(msg -> Log.info(msg));
        SwingUtilities.invokeLater(() -> accountLbl.setText("Signed in: " + session.username));
        DiscordWebhook.send(config.webhookLogins,
                "**Login** — `" + session.username + "` signed in on Lumenix Launcher.");
    }

    private void doInstall() throws Exception {
        config.edition = String.valueOf(edition.getSelectedItem());
        install.saveConfig(config);
        InstallMeta meta = install.loadMeta();
        install.ensureMinecraft(meta);
        install.ensureFabric(meta);
        install.ensureLumenixClient(config, meta);
        Log.info("Install/update finished — valid files reused where possible.");
        DiscordWebhook.send(config.webhookInstalls,
                "**Install/Update** — edition `" + config.edition + "` ready (MC " + Paths.MC_VERSION + " + Fabric).");
    }

    private void doRepair() throws Exception {
        config.edition = String.valueOf(edition.getSelectedItem());
        install.saveConfig(config);
        install.repair(config);
        DiscordWebhook.send(config.webhookInstalls,
                "**Repair** finished for edition `" + config.edition + "`.");
    }

    private void doPlay() throws Exception {
        config.edition = String.valueOf(edition.getSelectedItem());
        install.saveConfig(config);
        if (session == null || !session.isValid()) {
            Log.warn("No valid session — starting Microsoft login");
            session = MicrosoftAuth.loginDeviceCode(msg -> Log.info(msg));
            SwingUtilities.invokeLater(() -> accountLbl.setText("Signed in: " + session.username));
            DiscordWebhook.send(config.webhookLogins,
                    "**Login** — `" + session.username + "` signed in on Lumenix Launcher.");
        }
        InstallMeta meta = install.loadMeta();
        install.ensureMinecraft(meta);
        install.ensureFabric(meta);
        install.ensureLumenixClient(config, meta);
        Process p = LaunchService.launch(config, meta, session);
        Log.info("Game started (pid=" + p.pid() + "). Log: " + Paths.logs().resolve("game.log"));
        DiscordWebhook.send(config.webhookLaunches,
                "**Launch** — `" + session.username + "` started **" + config.edition
                        + "** (MC " + Paths.MC_VERSION + ").");
    }

    private static String safe(String s) {
        if (s == null) return "unknown";
        return s.length() > 180 ? s.substring(0, 180) + "…" : s;
    }

    /** Custom painted button so label is always high-contrast. */
    private static JButton solidButton(String text, Color bg, Color hover) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fill = !isEnabled() ? new Color(0x2A2A2A)
                        : getModel().isPressed() ? hover.darker()
                        : getModel().isRollover() ? hover : bg;
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(isEnabled() ? TEXT : MUTED);
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };
        b.setFont(new Font("Segoe UI", Font.BOLD, 13));
        b.setForeground(TEXT);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(Math.max(130, text.length() * 9 + 24), 36));
        return b;
    }

    private static void styleCombo(JComboBox<String> box) {
        box.setBackground(CARD);
        box.setForeground(TEXT);
        box.setFont(new Font("Segoe UI", Font.BOLD, 13));
        box.setPreferredSize(new Dimension(100, 34));
        box.setBorder(new LineBorder(BORDER, 1));
    }

    @FunctionalInterface
    private interface Task { void run() throws Exception; }
}
