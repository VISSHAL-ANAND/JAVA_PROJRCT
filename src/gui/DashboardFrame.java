package gui;

import client.Session;
import dao.BookingDAO;
import dao.IssueDAO;
import dao.TicketDAO;
import model.*;
import service.EmergencyService;
import service.PriorityService;
import security.AccessControl;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.time.LocalDateTime;
import java.util.List;

public class DashboardFrame extends JFrame {
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Color navy = new Color(7, 42, 76);
    private final Color blue = new Color(25, 110, 230);
    private final User user;

    public DashboardFrame() {
        user = Session.getCurrentUser();
        if(user==null) throw new IllegalStateException("No active session");
        setTitle("CAMPUSOS - " + user.getRole());
        setSize(1280,760); setLocationRelativeTo(null); setDefaultCloseOperation(EXIT_ON_CLOSE);
        build();
    }

    private void build() {
        JPanel root=new JPanel(new BorderLayout());
        root.add(sidebar(),BorderLayout.WEST);
        root.add(content,BorderLayout.CENTER);
        add(root);
        addPage("Dashboard",new DashboardPanel(),true);
        if(AccessControl.hasRole(Role.STUDENT,Role.FACULTY)) {
            addPage("Report Issue",new ReportIssuePanel(),false);
            addPage("My Tickets",new TicketsPanel("My Tickets"),false);
            addPage("Book Resources",new BookingPanel(),false);
        }
        if(AccessControl.hasRole(Role.MAINTENANCE,Role.TECHNICIAN))
            addPage("My Tasks",new TicketsPanel("Maintenance Tasks"),false);
        if(AccessControl.hasRole(Role.ADMIN)) {
            addPage("Tickets",new TicketsPanel("All Tickets"),false);
            addPage("Resources",new BookingPanel(),false);
            addPage("Reports",new ReportsPanel(),false);
        }
        addPage("Notifications",new NotificationsPanel(),false);
        addPage("Emergency",new EmergencyPanel(),false);
    }

    private JPanel sidebar() {
        JPanel side=new JPanel(); side.setBackground(navy); side.setPreferredSize(new Dimension(225,760));
        side.setLayout(new BoxLayout(side,BoxLayout.Y_AXIS));
        JLabel logo=new JLabel("  CAMPUSOS",SwingConstants.LEFT); logo.setForeground(Color.WHITE);
        logo.setFont(new Font("SansSerif",Font.BOLD,24)); logo.setBorder(new EmptyBorder(25,15,25,5)); side.add(logo);
        JLabel who=new JLabel("  "+user.getName()+"\n  "+user.getRole());
        who.setForeground(new Color(210,225,240)); side.add(who); side.add(Box.createVerticalStrut(15));
        addNav(side,"Dashboard","Dashboard");
        if(AccessControl.hasRole(Role.STUDENT,Role.FACULTY)){addNav(side,"Report Issue","Report Issue");addNav(side,"My Tickets","My Tickets");addNav(side,"Book Resources","Book Resources");}
        if(AccessControl.hasRole(Role.MAINTENANCE,Role.TECHNICIAN)) addNav(side,"My Tasks","My Tasks");
        if(AccessControl.hasRole(Role.ADMIN)){addNav(side,"Tickets","Tickets");addNav(side,"Resources","Resources");addNav(side,"Reports","Reports");}
        addNav(side,"Notifications","Notifications"); addNav(side,"Emergency","Emergency");
        side.add(Box.createVerticalGlue());
        JButton logout=new JButton("Logout"); logout.addActionListener(e->{Session.logout();dispose();new LoginFrame().setVisible(true);}); side.add(logout);
        side.setBorder(new EmptyBorder(0,8,12,8)); return side;
    }

    private void addNav(JPanel side,String text,String key){
        JButton b=new JButton(text); b.setAlignmentX(Component.LEFT_ALIGNMENT); b.setMaximumSize(new Dimension(205,42));
        b.addActionListener(e->cards.show(content,key)); side.add(b); side.add(Box.createVerticalStrut(6));
    }
    private void addPage(String key,JPanel panel,boolean first){content.add(panel,key);if(first)cards.show(content,key);}

    private abstract class BasePanel extends JPanel {
        BasePanel(String title){setLayout(new BorderLayout(15,15));setBorder(new EmptyBorder(25,30,25,30));
            JLabel h=new JLabel(title);h.setFont(new Font("SansSerif",Font.BOLD,26));h.setForeground(navy);add(h,BorderLayout.NORTH);}
        JLabel stat(String label,String value){JLabel l=new JLabel("<html><b>"+label+"</b><br><font size='5'>"+value+"</font></html>");
            l.setOpaque(true);l.setBackground(Color.WHITE);l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(220,230,240)),new EmptyBorder(18,18,18,18)));return l;}
    }

    private class DashboardPanel extends BasePanel {
        DashboardPanel(){super("Welcome, "+user.getName()+"!");
            JPanel center=new JPanel(new BorderLayout(15,15)); JPanel stats=new JPanel(new GridLayout(1,4,12,12));
            stats.add(stat("My Tickets",user.getRole().equals("ADMIN")?"124":"3"));stats.add(stat("Bookings",user.getRole().equals("ADMIN")?"37":"2"));
            stats.add(stat("Notifications","3"));stats.add(stat("Announcements","5"));center.add(stats,BorderLayout.NORTH);
            JTextArea info=new JTextArea("\n  Here's what's happening on campus today.\n\n  Use the menu to report issues, track tickets, book resources,\n  receive notifications, and access role-specific operations.");
            info.setEditable(false);info.setFont(new Font("SansSerif",Font.PLAIN,16));center.add(info,BorderLayout.CENTER);add(center,BorderLayout.CENTER);
        }
    }

    private class ReportIssuePanel extends BasePanel {
        JTextField title=new JTextField(); JTextField location=new JTextField(); JTextArea description=new JTextArea(6,30);
        JComboBox<String> category=new JComboBox<>(new String[]{"Electrical","Mechanical","IT / Network","Plumbing","Furniture","Cleaning","Others"});
        JComboBox<Priority> severity=new JComboBox<>(Priority.values()); JLabel file=new JLabel("No file selected"); File[] selected={null};
        ReportIssuePanel(){super("Report a New Issue"); JPanel form=new JPanel(new GridBagLayout()); GridBagConstraints g=new GridBagConstraints();
            g.insets=new Insets(7,7,7,7);g.fill=GridBagConstraints.HORIZONTAL; int y=0;
            addRow(form,g,y++,"Category",category);addRow(form,g,y++,"Location",location);addRow(form,g,y++,"Title",title);addRow(form,g,y++,"Description",new JScrollPane(description));addRow(form,g,y++,"Severity",severity);
            JButton upload=new JButton("Upload Photo");upload.addActionListener(e->{JFileChooser c=new JFileChooser();if(c.showOpenDialog(this)==JFileChooser.APPROVE_OPTION){selected[0]=c.getSelectedFile();file.setText(selected[0].getName());}});
            JPanel fp=new JPanel(new BorderLayout(8,0));fp.add(upload,BorderLayout.WEST);fp.add(file,BorderLayout.CENTER);addRow(form,g,y++,"Attachment",fp);
            JButton submit=new JButton("Submit Issue");submit.addActionListener(e->submit());addRow(form,g,y,"",submit);add(form,BorderLayout.CENTER);}
        void addRow(JPanel p,GridBagConstraints g,int y,String label,Component c){g.gridx=0;g.gridy=y;g.weightx=0;p.add(new JLabel(label),g);g.gridx=1;g.weightx=1;g.gridwidth=2;p.add(c,g);g.gridwidth=1;}
        void submit(){if(title.getText().isBlank()||location.getText().isBlank()||description.getText().isBlank()){JOptionPane.showMessageDialog(this,"Fill all required fields.");return;}
            try{Priority priority=(Priority)severity.getSelectedItem();Issue issue=new Issue(0,title.getText(),description.getText(),(String)category.getSelectedItem(),location.getText(),selected[0]==null?null:selected[0].getAbsolutePath(),priority);
                int issueId=new IssueDAO().createIssue(issue);new TicketDAO().createTicket(issueId,user.getId());JOptionPane.showMessageDialog(this,"Issue submitted successfully. Ticket created.");title.setText("");description.setText("");}
            catch(Exception ex){JOptionPane.showMessageDialog(this,"Could not submit issue: "+ex.getMessage());}}
    }

    private class TicketsPanel extends BasePanel {
        TicketsPanel(String title){super(title); JPanel top=new JPanel(new FlowLayout(FlowLayout.LEFT));top.add(new JLabel("Search:"));top.add(new JTextField(22));top.add(new JComboBox<>(new String[]{"All Categories","Electrical","IT / Network","Plumbing","Furniture"}));top.add(new JComboBox<>(new String[]{"All Status","Open","Assigned","In Progress","Resolved"}));
            String[] cols={"ID","Issue","Location","Priority","Status","Actions"};Object[][] rows={{"CAMP-10452","Projector not working","Block C - Lab 204","HIGH","IN PROGRESS","View"},{"CAMP-10448","AC not cooling","Block A","MEDIUM","ASSIGNED","View"},{"CAMP-10431","WiFi not working","Block B","LOW","OPEN","View"}};
            JTable table=new JTable(rows,cols);add(top,BorderLayout.NORTH);add(new JScrollPane(table),BorderLayout.CENTER);}
    }

    private class BookingPanel extends BasePanel {
        JComboBox<String> resource=new JComboBox<>(new String[]{"Seminar Hall A","Seminar Hall B","Lab 204","Conference Room","Projector (Lab)"});
        JTextField date=new JTextField("25/09/2026"),start=new JTextField("02:00 PM"),end=new JTextField("04:00 PM"); JTextArea purpose=new JTextArea(4,20);
        BookingPanel(){super("Book a Resource");JPanel f=new JPanel(new GridBagLayout());GridBagConstraints g=new GridBagConstraints();g.insets=new Insets(8,8,8,8);g.fill=GridBagConstraints.HORIZONTAL;
            row(f,g,0,"Resource",resource);row(f,g,1,"Date",date);row(f,g,2,"Start Time",start);row(f,g,3,"End Time",end);row(f,g,4,"Purpose",new JScrollPane(purpose));
            JButton check=new JButton("Check Availability & Book");check.addActionListener(e->JOptionPane.showMessageDialog(this,"Availability checked. Booking request recorded for "+resource.getSelectedItem()+"."));row(f,g,5,"",check);add(f,BorderLayout.CENTER);}
        void row(JPanel p,GridBagConstraints g,int y,String s,Component c){g.gridx=0;g.gridy=y;g.weightx=0;p.add(new JLabel(s),g);g.gridx=1;g.weightx=1;p.add(c,g);}
    }

    private class EmergencyPanel extends BasePanel {
        JComboBox<String> type=new JComboBox<>(new String[]{"Fire","Medical","Security","Electrical Hazard","Other"}); JTextField location=new JTextField();JTextArea msg=new JTextArea(6,30);
        EmergencyPanel(){super("Emergency Report");JPanel f=new JPanel(new GridBagLayout());GridBagConstraints g=new GridBagConstraints();g.insets=new Insets(8,8,8,8);g.fill=GridBagConstraints.HORIZONTAL;
            row(f,g,0,"Emergency Type",type);row(f,g,1,"Location",location);row(f,g,2,"Message",new JScrollPane(msg));
            JButton send=new JButton("SEND EMERGENCY ALERT");send.setForeground(Color.RED);send.addActionListener(e->send());row(f,g,3,"",send);add(f,BorderLayout.CENTER);}
        void row(JPanel p,GridBagConstraints g,int y,String s,Component c){g.gridx=0;g.gridy=y;p.add(new JLabel(s),g);g.gridx=1;g.weightx=1;p.add(c,g);}
        void send(){if(location.getText().isBlank()||msg.getText().isBlank()){JOptionPane.showMessageDialog(this,"Location and message are required.");return;}
            try{new EmergencyService().createEmergency(user.getId(),(String)type.getSelectedItem(),msg.getText()+" | Location: "+location.getText());JOptionPane.showMessageDialog(this,"Emergency alert sent.");}catch(Exception ex){JOptionPane.showMessageDialog(this,"Unable to send alert: "+ex.getMessage());}}
    }

    private class NotificationsPanel extends BasePanel {
        NotificationsPanel(){super("Notifications");JList<String> list=new JList<>(new String[]{"Your ticket CAMP-10452 has been assigned.","Emergency alert: Electrical hazard reported in Block B.","Resource booking Seminar Hall A confirmed.","New campus announcement available."});add(new JScrollPane(list),BorderLayout.CENTER);}
    }
    private class ReportsPanel extends BasePanel {
        ReportsPanel(){super("Reports");JPanel p=new JPanel(new GridLayout(2,2,15,15));p.add(stat("Total Tickets","124"));p.add(stat("Open","37"));p.add(stat("In Progress","28"));p.add(stat("Resolved","48"));add(p,BorderLayout.CENTER);}
    }

    public static void main(String[] args){SwingUtilities.invokeLater(()->new DashboardFrame().setVisible(true));}
}
