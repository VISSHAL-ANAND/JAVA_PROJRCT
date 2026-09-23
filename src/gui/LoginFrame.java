package gui;

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
        JButton login = new JButton("Login");

        gbc.gridx = 0; gbc.gridy = 0;
        form.add(new JLabel("Email:"), gbc);
        gbc.gridx = 1;
        form.add(email, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        form.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1;
        form.add(password, gbc);

        gbc.gridx = 1; gbc.gridy = 2;
        form.add(login, gbc);

        login.addActionListener(e -> JOptionPane.showMessageDialog(
                this, "Authentication will connect to AuthService."));

        add(form, BorderLayout.CENTER);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
