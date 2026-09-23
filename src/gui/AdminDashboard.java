package gui;

import javax.swing.*;
import java.awt.*;

public class AdminDashboard extends MainFrame {
    public AdminDashboard() {
        super("CAMPUSOS - Admin Dashboard");
        add(createHeader("Admin Dashboard"), BorderLayout.NORTH);

        JPanel content = new JPanel(new GridLayout(2, 2, 12, 12));
        content.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JButton tickets = new JButton("Manage Tickets");
        JButton users = new JButton("Manage Users");
        JButton emergency = new JButton("Emergency Broadcast");
        JButton reports = new JButton("Reports");

        tickets.addActionListener(e -> showMessage("Ticket management module"));
        users.addActionListener(e -> showMessage("User management module"));
        emergency.addActionListener(e -> showMessage("Emergency broadcast module"));
        reports.addActionListener(e -> showMessage("Reports module"));

        content.add(tickets);
        content.add(users);
        content.add(emergency);
        content.add(reports);
        add(content, BorderLayout.CENTER);
    }

    private void showMessage(String message) {
        JOptionPane.showMessageDialog(this, message);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AdminDashboard().setVisible(true));
    }
}
