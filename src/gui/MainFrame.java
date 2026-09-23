package gui;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {
    public MainFrame(String title) {
        super(title);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
    }

    protected JPanel createHeader(String title) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        JLabel label = new JLabel(title);
        label.setFont(new Font("SansSerif", Font.BOLD, 22));
        panel.add(label, BorderLayout.WEST);
        return panel;
    }
}
