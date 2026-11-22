package climalink;

import com.fazecast.jSerialComm.SerialPort;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Serial layer: opens the COM port, reads lines on a background thread, writes commands. */
public class SerialService {
    private SerialPort port;
    private Thread reader;
    private volatile boolean running;
    private OutputStream out;

    public static String[] listPorts() {
        SerialPort[] ports = SerialPort.getCommPorts();
        String[] names = new String[ports.length];
        for (int i = 0; i < ports.length; i++) names[i] = ports[i].getSystemPortName();
        return names;
    }

    public void connect(String name, Consumer<String> onLine, Consumer<String> onError) throws IOException {
        port = SerialPort.getCommPort(name);
        port.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 500, 0);
        if (!port.openPort()) throw new IOException("Cannot open " + name + " (busy or wrong port)");
        out = port.getOutputStream();
        running = true;
        reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(port.getInputStream(), StandardCharsets.US_ASCII))) {
                while (running) {
                    try {
                        String line = in.readLine();
                        if (line != null && !line.isBlank()) onLine.accept(line.trim());
                    } catch (SerialPortTimeoutExceptionWrapper | IOException e) {
                        if (!port.isOpen()) throw e;
                    }
                }
            } catch (Exception e) {
                if (running) onError.accept("Device disconnected: " + e.getMessage());
            }
        }, "serial-reader");
        reader.setDaemon(true);
        reader.start();
    }

    /** jSerialComm throws SerialPortTimeoutException (an IOException) on read timeout; we just keep reading. */
    private static class SerialPortTimeoutExceptionWrapper extends RuntimeException {}

    public synchronized void send(String cmd) throws IOException {
        if (!isConnected()) throw new IOException("Not connected");
        out.write((cmd + "\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    public boolean isConnected() { return port != null && port.isOpen(); }

    public void disconnect() {
        running = false;
        if (port != null) port.closePort();
        port = null;
    }
}
