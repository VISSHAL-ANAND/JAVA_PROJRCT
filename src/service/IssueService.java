package service;
import dao.IssueDAO; import dao.TicketDAO; import database.DBConnection; import model.Issue;
import java.sql.*;
public class IssueService {
 private final IssueDAO issueDAO=new IssueDAO(); private final TicketDAO ticketDAO=new TicketDAO(); private final PriorityService priorityService=new PriorityService();
 public int submit(Issue issue,int reporterId)throws SQLException{
  if(issue==null||reporterId<=0)throw new IllegalArgumentException("Invalid issue data");
  issue.setPriority(priorityService.calculatePriority(issue));
  try(Connection c=DBConnection.getConnection()){c.setAutoCommit(false);try{
   int issueId=issueDAO.createIssue(c,issue); int ticketId=ticketDAO.createTicket(c,issueId,reporterId);
   Integer tech=findTechnician(c,issue.getCategory()); if(tech!=null){ticketDAO.assignTechnician(c,ticketId,tech);notifyUser(c,tech,"New ticket #"+ticketId+" assigned to you.");notifyUser(c,reporterId,"Ticket #"+ticketId+" has been assigned to a technician.");}
   c.commit(); return ticketId;
  }catch(Exception e){c.rollback();if(e instanceof SQLException s)throw s;throw new SQLException(e);}finally{c.setAutoCommit(true);}}
 }
 private Integer findTechnician(Connection c,String category)throws SQLException{
  String q="SELECT id FROM users WHERE role IN ('MAINTENANCE','TECHNICIAN') AND available=TRUE ORDER BY CASE WHEN LOWER(COALESCE(specialization,''))=LOWER(?) THEN 0 ELSE 1 END,id LIMIT 1";
  try(PreparedStatement s=c.prepareStatement(q)){s.setString(1,category==null?"":category);try(ResultSet r=s.executeQuery()){return r.next()?r.getInt(1):null;}}
 }
 private void notifyUser(Connection c,int id,String msg)throws SQLException{try(PreparedStatement s=c.prepareStatement("INSERT INTO notifications(user_id,message) VALUES(?,?)")){s.setInt(1,id);s.setString(2,msg);s.executeUpdate();}}
}