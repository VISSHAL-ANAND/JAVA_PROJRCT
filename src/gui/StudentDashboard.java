package gui;

import javax.swing.*;
import java.awt.*;

public class StudentDashboard extends MainFrame {
    public StudentDashboard() {
        super("CAMPUSOS - Student Dashboard");
        add(createHeader("Student Dashboard"), BorderLayout.NORTH);

        JPanel content = new JPanel(new GridLayout(2, 2, 12, 12));
        content.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JButton reportIssue = new JButton("Report Issue");
        JButton myTickets = new JButton("My Tickets");
        JButton resources = new JButton("Book Resource");
        JButton notifications = new JButton("Notifications");

        reportIssue.addActionListener(e -> showMessage("Issue reporting module"));
        myTickets.addActionListener(e -> showMessage("Ticket tracking module"));
        resources.addActionListener(e -> showMessage("Resource booking module"));
        notifications.addActionListener(e -> showMessage("Notification module"));

        content.add(reportIssue);
        content.add(myTickets);
        content.add(resources);
        content.add(notifications);
        add(content, BorderLayout.CENTER);
    }

    private void showMessage(String message) {
        JOptionPane.showMessageDialog(this, message);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new StudentDashboard().setVisible(true));
    }
}
