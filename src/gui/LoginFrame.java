package gui;

import client.Session;
import model.Admin;
import model.Student;
import model.Technician;

import javax.swing.*;
import java.awt.*;

public class LoginFrame extends MainFrame {
    public LoginFrame() {
        super("CAMPUSOS - Login");
        add(createHeader("CAMPUSOS Login"), BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField email = new JTextField(24);
        JPasswordField password = new JPasswordField(24);
        JComboBox<String> role = new JComboBox<>(new String[]{"STUDENT", "TECHNICIAN", "ADMIN"});
        JButton login = new JButton("Login");

        gbc.gridx = 0; gbc.gridy = 0;
        form.add(new JLabel("Email:"), gbc);
        gbc.gridx = 1;
        form.add(email, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        form.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1;
        form.add(password, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        form.add(new JLabel("Role:"), gbc);
        gbc.gridx = 1;
        form.add(role, gbc);

        gbc.gridx = 1; gbc.gridy = 3;
        form.add(login, gbc);

        login.addActionListener(e -> {
            String selectedRole = (String) role.getSelectedItem();
            String enteredEmail = email.getText().trim();

            if (enteredEmail.isBlank() || password.getPassword().length == 0) {
                JOptionPane.showMessageDialog(this, "Email and password are required");
                return;
            }

            int demoId = 1;
            switch (selectedRole) {
                case "STUDENT" -> Session.login(
                        new Student(demoId, "Student User", enteredEmail, "",
                                "DEMO-001", "CSE"));
                case "TECHNICIAN" -> Session.login(
                        new Technician(demoId, "Technician User", enteredEmail, "",
                                "GENERAL", true));
                case "ADMIN" -> Session.login(
                        new Admin(demoId, "Admin User", enteredEmail, ""));
            }

            openDashboard(selectedRole);
        });

        add(form, BorderLayout.CENTER);
    }

    private void openDashboard(String role) {
        dispose();

        switch (role) {
            case "STUDENT" -> new StudentDashboard().setVisible(true);
            case "TECHNICIAN" -> new TechnicianDashboard().setVisible(true);
            case "ADMIN" -> new AdminDashboard().setVisible(true);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
