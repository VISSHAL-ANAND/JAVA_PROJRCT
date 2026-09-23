package dao;
import database.DBConnection; import java.sql.*; import java.util.*;
public class TicketDAO{
 public List<Integer> findOpenTicketIds()throws SQLException{List<Integer> ids=new ArrayList<>();String q="SELECT id FROM tickets WHERE status IN ('OPEN','ASSIGNED','IN_PROGRESS') ORDER BY created_at";try(Connection c=DBConnection.getConnection();PreparedStatement s=c.prepareStatement(q);ResultSet r=s.executeQuery()){while(r.next())ids.add(r.getInt(1));}return ids;}
 public int createTicket(int issueId,int reporterId)throws SQLException{try(Connection c=DBConnection.getConnection()){return createTicket(c,issueId,reporterId);}}
 public int createTicket(Connection c,int issueId,int reporterId)throws SQLException{try(PreparedStatement s=c.prepareStatement("INSERT INTO tickets(issue_id,reported_by,status) VALUES(?,?,'OPEN')",Statement.RETURN_GENERATED_KEYS)){s.setInt(1,issueId);s.setInt(2,reporterId);s.executeUpdate();try(ResultSet r=s.getGeneratedKeys()){if(r.next())return r.getInt(1);}}throw new SQLException("Unable to create ticket");}
 public void assignTechnician(int ticketId,int technicianId)throws SQLException{try(Connection c=DBConnection.getConnection()){assignTechnician(c,ticketId,technicianId);}}
 public void assignTechnician(Connection c,int ticketId,int technicianId)throws SQLException{try(PreparedStatement s=c.prepareStatement("UPDATE tickets SET assigned_technician_id=?,status='ASSIGNED' WHERE id=? AND status='OPEN'")){s.setInt(1,technicianId);s.setInt(2,ticketId);if(s.executeUpdate()!=1)throw new SQLException("Ticket could not be assigned");}}
}