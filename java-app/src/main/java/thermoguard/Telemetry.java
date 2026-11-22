package thermoguard;

import java.util.HashMap;
import java.util.Map;

/**
 * Application logic: parses one telemetry line, e.g.
 * "DATA;T=24.5;H=52.0;WIN=0;FAN=1;MODE=AUTO;ALARM=0".
 */
public record Telemetry(double temp, double hum, int window, boolean fan, String mode, boolean alarm) {
    public static Telemetry parse(String line) {
        if (!line.startsWith("DATA;")) return null;
        Map<String, String> kv = new HashMap<>();
        for (String part : line.substring(5).split(";")) {
            String[] p = part.split("=", 2);
            if (p.length == 2) kv.put(p[0], p[1]);
        }
        try {
            return new Telemetry(
                    Double.parseDouble(kv.get("T")),
                    Double.parseDouble(kv.get("H")),
                    Integer.parseInt(kv.get("WIN")),
                    "1".equals(kv.get("FAN")),
                    kv.getOrDefault("MODE", "?"),
                    "1".equals(kv.get("ALARM")));
        } catch (Exception e) {
            return null; // malformed or incomplete line -> ignored
        }
    }
}
