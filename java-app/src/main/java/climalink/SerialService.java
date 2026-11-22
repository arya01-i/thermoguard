package climalink;

import com.fazecast.jSerialComm.SerialPort;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Serial layer: opens the COM port, reads lines on a background thread and writes commands.
 * One message = one line terminated by '\n'.
 */
public class SerialService {
    public static final int BAUD = 9600;

    private SerialPort port;
    private Thread reader;
    private volatile boolean running;
    private volatile long lastRxMillis;

    public static String[] listPorts() {
        SerialPort[] ports = SerialPort.getCommPorts();
        String[] names = new String[ports.length];
        for (int i = 0; i < ports.length; i++) names[i] = ports[i].getSystemPortName();
        return names;
    }

    public void connect(String name, Consumer<String> onLine, Consumer<String> onError) throws IOException {
        disconnect();
        port = SerialPort.getCommPort(name);
        port.setComPortParameters(BAUD, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 200, 200);
        if (!port.openPort()) {
            port = null;
            throw new IOException("Cannot open " + name + " (wrong port or already used by another program)");
        }
        running = true;
        lastRxMillis = System.currentTimeMillis();
        final SerialPort p = port;
        reader = new Thread(() -> readLoop(p, onLine, onError), "serial-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void readLoop(SerialPort p, Consumer<String> onLine, Consumer<String> onError) {
        StringBuilder line = new StringBuilder();
        byte[] buf = new byte[64];
        while (running) {
            int n = p.readBytes(buf, buf.length);   // returns 0 on timeout, -1 on error
            if (n < 0 || !p.isOpen()) {
                if (running) onError.accept("Device disconnected");
                running = false;
                return;
            }
            for (int i = 0; i < n; i++) {
                char c = (char) buf[i];
                if (c == '\n') {
                    String s = line.toString().trim();
                    line.setLength(0);
                    if (!s.isEmpty()) { lastRxMillis = System.currentTimeMillis(); onLine.accept(s); }
                } else if (c != '\r' && line.length() < 120) {
                    line.append(c);
                }
            }
        }
    }

    public synchronized void send(String cmd) throws IOException {
        if (!isConnected()) throw new IOException("Not connected");
        byte[] data = (cmd + "\n").getBytes(StandardCharsets.US_ASCII);
        if (port.writeBytes(data, data.length) != data.length) throw new IOException("Write failed — device disconnected?");
    }

    /** Milliseconds since the last complete line was received. */
    public long millisSinceLastRx() { return System.currentTimeMillis() - lastRxMillis; }

    public boolean isConnected() { return port != null && port.isOpen() && running; }

    public String portName() { return port == null ? null : port.getSystemPortName(); }

    public void disconnect() {
        running = false;
        if (port != null) port.closePort();
        port = null;
    }
}
