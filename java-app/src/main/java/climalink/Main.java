package climalink;

import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClimateFrame(new SerialService()).setVisible(true));
    }
}
