package climalink;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.io.File;

/** GUI layer: connection bar, live readings, controls, history chart, serial log. */
public class ClimateFrame extends JFrame {
    private final SerialService serial;
    private final JComboBox<String> portBox = new JComboBox<>(SerialService.listPorts());
    private final JButton connectBtn = new JButton("Connect");
    private final JLabel status = new JLabel("Disconnected");
    private final JLabel tempLbl = big("-- °C"), humLbl = big("-- %"), winLbl = big("Closed");
    private final JLabel alarmLbl = new JLabel("OK", SwingConstants.CENTER);
    private final JToggleButton autoBtn = new JToggleButton("AUTO mode", true);
    private final JSlider winSlider = new JSlider(0, 180, 0);
    private final JSpinner thrT = new JSpinner(new SpinnerNumberModel(28.0, 10.0, 50.0, 0.5));
    private final JTextArea log = new JTextArea(8, 40);
    private final Chart chart = new Chart();
    private final JButton demoBtn = new JButton("▶ Demo");
    private final JToggleButton csvBtn = new JToggleButton("Log CSV");
    private CsvLogger csv;
    private String lastPort;
    private boolean userDisconnected = true;
    private Timer demoTimer;
    /** Watchdog: PING when quiet, mark timeout, auto-reconnect after a cable drop. */
    private final Timer watchdog = new Timer(1000, e -> watchdogTick());
    private final DateTimeFormatter tf = DateTimeFormatter.ofPattern("HH:mm:ss");

    public ClimateFrame(SerialService serial) {
        super("ClimaLink — Smart Climate Station");
        this.serial = serial;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        add(topBar(), BorderLayout.NORTH);
        add(center(), BorderLayout.CENTER);
        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        add(new JScrollPane(log), BorderLayout.SOUTH);
        setControlsEnabled(false);
        watchdog.start();
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel topBar() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refresh = new JButton("↻");
        refresh.addActionListener(e -> portBox.setModel(new DefaultComboBoxModel<>(SerialService.listPorts())));
        connectBtn.addActionListener(e -> toggleConnection());
        p.add(new JLabel("Port:")); p.add(portBox); p.add(refresh); p.add(connectBtn); p.add(status);
        return p;
    }

    private JPanel center() {
        JPanel readings = new JPanel(new GridLayout(1, 4, 8, 8));
        readings.add(card("Temperature", tempLbl));
        readings.add(card("Humidity", humLbl));
        readings.add(card("Window", winLbl));
        alarmLbl.setOpaque(true);
        alarmLbl.setFont(alarmLbl.getFont().deriveFont(Font.BOLD, 22f));
        readings.add(card("Alarm", alarmLbl));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        autoBtn.addActionListener(e -> {
            send(autoBtn.isSelected() ? "MODE AUTO" : "MODE MANUAL");
            winSlider.setEnabled(!autoBtn.isSelected());
        });
        winSlider.setMajorTickSpacing(45); winSlider.setPaintTicks(true); winSlider.setPaintLabels(true);
        winSlider.addChangeListener(e -> { if (!winSlider.getValueIsAdjusting()) send("WIN " + winSlider.getValue()); });
        JButton thrBtn = new JButton("Set limit");
        thrBtn.addActionListener(e -> send("THR T " + thrT.getValue()));
        JButton[] leds = { colorBtn("Red", "LED 255 0 0"), colorBtn("Green", "LED 0 255 0"), colorBtn("Blue", "LED 0 0 255"), colorBtn("Off", "LED 0 0 0") };
        JToggleButton buzz = new JToggleButton("Buzzer");
        buzz.addActionListener(e -> send(buzz.isSelected() ? "BUZZ ON" : "BUZZ OFF"));
        controls.add(autoBtn); controls.add(new JLabel("Window°")); controls.add(winSlider);
        controls.add(new JLabel("Temp limit")); controls.add(thrT); controls.add(thrBtn);
        for (JButton b : leds) controls.add(b);
        controls.add(buzz);
        demoBtn.addActionListener(e -> runDemo());
        csvBtn.addActionListener(e -> toggleCsv());
        controls.add(demoBtn); controls.add(csvBtn);

        JPanel c = new JPanel(new BorderLayout(8, 8));
        c.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        c.add(readings, BorderLayout.NORTH);
        c.add(chart, BorderLayout.CENTER);
        c.add(controls, BorderLayout.SOUTH);
        return c;
    }

    private JButton colorBtn(String name, String cmd) {
        JButton b = new JButton(name);
        b.addActionListener(e -> send(cmd));
        return b;
    }

    private void toggleConnection() {
        if (serial.isConnected()) {
            userDisconnected = true;
            serial.disconnect();
            onDisconnected("Disconnected");
            return;
        }
        String name = (String) portBox.getSelectedItem();
        if (name == null) { JOptionPane.showMessageDialog(this, "No serial port found. Plug in the Arduino and press ↻."); return; }
        try {
            open(name);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Connection error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void open(String name) throws Exception {
        {
            serial.connect(name,
                    line -> SwingUtilities.invokeLater(() -> onLine(line)),
                    err -> SwingUtilities.invokeLater(() -> onDisconnected(err)));
            status.setText("Connected to " + name);
            connectBtn.setText("Disconnect");
            setControlsEnabled(true);
            append("-- connected " + name);
            lastPort = name;
            userDisconnected = false;
        }
    }

    private void watchdogTick() {
        if (serial.isConnected()) {
            long quiet = serial.millisSinceLastRx();
            if (quiet > 6000) status.setText("Timeout: no data from Arduino for " + quiet / 1000 + " s");
            else if (quiet > 3000) send("PING");
        } else if (!userDisconnected && lastPort != null) {
            // cable was unplugged: try to reopen the same port every second
            try { open(lastPort); append("-- reconnected"); } catch (Exception ignored) { status.setText("Reconnecting to " + lastPort + "…"); }
        }
    }

    /** Demo mode: a predefined 2-minute-friendly scenario that exercises every command. */
    private void runDemo() {
        if (demoTimer != null && demoTimer.isRunning()) { demoTimer.stop(); demoBtn.setText("▶ Demo"); return; }
        String[] steps = { "MODE MANUAL", "LED 0 0 255", "WIN 45", "LED 0 255 0", "WIN 90", "LED 255 0 0",
                "BUZZ ON", "BUZZ OFF", "WIN 0", "THR T 20", "MODE AUTO", "THR T 28" };
        int[] i = {0};
        demoBtn.setText("■ Stop");
        demoTimer = new Timer(1500, e -> {
            if (i[0] >= steps.length) { demoTimer.stop(); demoBtn.setText("▶ Demo"); autoBtn.setSelected(true); return; }
            send(steps[i[0]++]);
        });
        demoTimer.start();
    }

    private void toggleCsv() {
        try {
            if (csvBtn.isSelected()) { File f = new File("climalink-log.csv"); csv = new CsvLogger(f); append("-- logging to " + f.getAbsolutePath()); }
            else if (csv != null) { csv.close(); csv = null; append("-- CSV logging stopped"); }
        } catch (Exception ex) { csvBtn.setSelected(false); status.setText("CSV error: " + ex.getMessage()); }
    }

    private void onDisconnected(String msg) {
        if (demoTimer != null) demoTimer.stop();
        serial.disconnect();
        status.setText(msg);
        connectBtn.setText("Connect");
        setControlsEnabled(false);
        append("-- " + msg);
    }

    private void onLine(String line) {
        append("< " + line);
        Telemetry t = Telemetry.parse(line);
        if (t != null) {
            tempLbl.setText(String.format("%.1f °C", t.temp()));
            humLbl.setText(String.format("%.1f %%", t.hum()));
            winLbl.setText(t.window() == 0 ? "Closed" : "Open " + t.window() + "°");
            alarmLbl.setText(t.alarm() ? "ALARM" : "OK");
            alarmLbl.setBackground(t.alarm() ? new Color(0xE5484D) : new Color(0x30A46C));
            chart.add(t.temp(), t.hum());
            if (csv != null) csv.log(t);
            if (!status.getText().startsWith("Connected")) status.setText("Connected to " + serial.portName());
        } else if (line.startsWith("ERR;")) {
            status.setText("Arduino error: " + line.substring(4));
        }
    }

    private void send(String cmd) {
        try { serial.send(cmd); append("> " + cmd); }
        catch (Exception e) { status.setText(e.getMessage()); }
    }

    private void setControlsEnabled(boolean on) {
        autoBtn.setEnabled(on); thrT.setEnabled(on); demoBtn.setEnabled(on);
        winSlider.setEnabled(on && !autoBtn.isSelected());
    }

    private void append(String s) {
        log.append(LocalTime.now().format(tf) + "  " + s + "\n");
        log.setCaretPosition(log.getDocument().getLength());
    }

    private static JLabel big(String s) {
        JLabel l = new JLabel(s, SwingConstants.CENTER);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 26f));
        return l;
    }

    private static JPanel card(String title, JComponent body) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(body);
        p.setPreferredSize(new Dimension(170, 90));
        return p;
    }

    /** Simple line chart of the last 60 readings. */
    static class Chart extends JPanel {
        private final Deque<double[]> pts = new ArrayDeque<>();
        Chart() { setPreferredSize(new Dimension(700, 200)); setBackground(Color.WHITE); }
        void add(double t, double h) { if (pts.size() == 60) pts.removeFirst(); pts.addLast(new double[]{t, h}); repaint(); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.GRAY); g2.drawString("Temp (red, 0–50 °C)  Humidity (blue, 0–100 %)", 8, 14);
            draw(g2, 0, 50, new Color(0xE5484D));
            draw(g2, 1, 100, new Color(0x0090FF));
        }
        private void draw(Graphics2D g, int idx, double max, Color c) {
            g.setColor(c); g.setStroke(new BasicStroke(2f));
            int w = getWidth(), h = getHeight() - 20, i = 0, px = -1, py = -1;
            for (double[] p : pts) {
                int x = (int) (i++ * (w / 59.0));
                int y = 20 + (int) (h - Math.min(p[idx], max) / max * h);
                if (px >= 0) g.drawLine(px, py, x, y);
                px = x; py = y;
            }
        }
    }
}
