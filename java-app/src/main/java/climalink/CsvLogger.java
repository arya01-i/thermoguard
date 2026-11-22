package climalink;

import java.io.*;
import java.time.LocalDateTime;

/** Application logic: appends telemetry rows to a CSV file. */
public class CsvLogger implements Closeable {
    private final PrintWriter out;

    public CsvLogger(File file) throws IOException {
        boolean fresh = !file.exists();
        out = new PrintWriter(new FileWriter(file, true));
        if (fresh) out.println("timestamp,temperature_c,humidity_pct,window_deg,mode,alarm");
    }

    public void log(Telemetry t) {
        out.printf(java.util.Locale.US, "%s,%.1f,%.1f,%d,%s,%d%n",
                LocalDateTime.now(), t.temp(), t.hum(), t.window(), t.mode(), t.alarm() ? 1 : 0);
        out.flush();
    }

    @Override public void close() { out.close(); }
}
