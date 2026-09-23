package gui;

import javax.swing.*;
import java.awt.*;

public class TechnicianDashboard extends MainFrame {
    public TechnicianDashboard() {
        super("CAMPUSOS - Technician Dashboard");
        add(createHeader("Technician Dashboard"), BorderLayout.NORTH);

        JPanel content = new JPanel(new GridLayout(2, 2, 12, 12));
        content.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JButton assigned = new JButton("Assigned Tickets");
        JButton accept = new JButton("Accept Ticket");
        JButton resolve = new JButton("Resolve Ticket");
        JButton availability = new JButton("Update Availability");

        assigned.addActionListener(e -> showMessage("Assigned tickets module"));
        accept.addActionListener(e -> showMessage("Ticket acceptance module"));
        resolve.addActionListener(e -> showMessage("Ticket resolution module"));
        availability.addActionListener(e -> showMessage("Technician availability module"));

        content.add(assigned);
        content.add(accept);
        content.add(resolve);
        content.add(availability);
        add(content, BorderLayout.CENTER);
    }

    private void showMessage(String message) {
        JOptionPane.showMessageDialog(this, message);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TechnicianDashboard().setVisible(true));
    }
}
