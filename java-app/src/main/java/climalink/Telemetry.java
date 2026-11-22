package climalink;

import java.util.HashMap;
import java.util.Map;

/** Application logic: parses "DATA;T=23.4;H=45.0;WIN=0;MODE=AUTO;ALARM=0". */
public record Telemetry(double temp, double hum, int window, String mode, boolean alarm) {
    public static Telemetry parse(String line) {
        if (!line.startsWith("DATA;")) return null;
        Map<String, String> kv = new HashMap<>();
        for (String part : line.substring(5).split(";")) {
            String[] p = part.split("=", 2);
            if (p.length == 2) kv.put(p[0], p[1]);
        }
        try {
            return new Telemetry(Double.parseDouble(kv.get("T")), Double.parseDouble(kv.get("H")),
                    Integer.parseInt(kv.get("WIN")), kv.getOrDefault("MODE", "?"), "1".equals(kv.get("ALARM")));
        } catch (Exception e) {
            return null; // malformed line
        }
    }
}
