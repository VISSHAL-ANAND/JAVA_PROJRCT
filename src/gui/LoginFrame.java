package gui;

import dao.UserDAO;
import exception.AuthenticationException;
import model.User;
import service.AuthService;

import javax.swing.*;
import java.awt.*;

public class LoginFrame extends JFrame {
    private final AuthService authService = new AuthService(new UserDAO());

    public LoginFrame() {
        setTitle("CAMPUSOS - Login");
        setSize(1050,650);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        build();
    }

    private void build() {
        JPanel root=new JPanel(new GridLayout(1,2));
        JPanel brand=new JPanel(){
            protected void paintComponent(Graphics g){super.paintComponent(g);Graphics2D x=(Graphics2D)g;x.setPaint(new GradientPaint(0,0,new Color(8,57,103),getWidth(),getHeight(),new Color(18,117,205)));x.fillRect(0,0,getWidth(),getHeight());}
        };
        brand.setLayout(new BoxLayout(brand,BoxLayout.Y_AXIS));
        JLabel logo=new JLabel("CAMPUSOS");logo.setForeground(Color.WHITE);logo.setFont(new Font("SansSerif",Font.BOLD,40));logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel sub=new JLabel("<html><center>Campus Operations & Emergency<br>Management System</center></html>");sub.setForeground(Color.WHITE);sub.setFont(new Font("SansSerif",Font.PLAIN,17));sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        brand.add(Box.createVerticalGlue());brand.add(logo);brand.add(Box.createVerticalStrut(18));brand.add(sub);brand.add(Box.createVerticalGlue());

        JPanel form=new JPanel(new GridBagLayout());form.setBackground(Color.WHITE);GridBagConstraints g=new GridBagConstraints();g.insets=new Insets(10,15,10,15);g.fill=GridBagConstraints.HORIZONTAL;
        JLabel welcome=new JLabel("<html><h1>Welcome Back</h1>Sign in to your CAMPUSOS account</html>");welcome.setForeground(new Color(12,55,95));
        JTextField email=new JTextField(24);JPasswordField pass=new JPasswordField(24);JComboBox<String> role=new JComboBox<>(new String[]{"AUTO","STUDENT","FACULTY","MAINTENANCE","ADMIN"});
        JCheckBox remember=new JCheckBox("Remember me");JButton login=new JButton("Login");login.setBackground(new Color(35,115,230));login.setForeground(Color.WHITE);
        int y=0;g.gridx=0;g.gridy=y++;g.gridwidth=2;form.add(welcome,g);g.gridwidth=1;
        add(form,g,y++,"User ID / Email",email);add(form,g,y++,"Password",pass);add(form,g,y++,"Role",role);g.gridx=1;g.gridy=y++;form.add(remember,g);g.gridx=1;g.gridy=y;form.add(login,g);
        login.addActionListener(e->{
            try{
                User user=authService.login(email.getText().trim(),new String(pass.getPassword()));
                String selected=(String)role.getSelectedItem();
                if(!"AUTO".equals(selected)&&!selected.equalsIgnoreCase(user.getRole())&&!(selected.equals("MAINTENANCE")&&user.getRole().equals("TECHNICIAN"))){
                    JOptionPane.showMessageDialog(this,"Selected role does not match your account.");return;
                }
                dispose();new DashboardFrame().setVisible(true);
            }catch(AuthenticationException ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Login Failed",JOptionPane.ERROR_MESSAGE);}
        });
        root.add(brand);root.add(form);add(root);
    }
    private void add(JPanel p,GridBagConstraints g,int y,String label,Component c){g.gridx=0;g.gridy=y;g.weightx=0;p.add(new JLabel(label),g);g.gridx=1;g.weightx=1;p.add(c,g);}
    public static void main(String[] args){SwingUtilities.invokeLater(()->new LoginFrame().setVisible(true));}
}
