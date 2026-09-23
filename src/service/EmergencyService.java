package service;

import database.DBConnection;
import java.sql.*;

public class EmergencyService {
    public int createEmergency(int createdBy,String title,String message)throws SQLException{
        if(createdBy<=0||title==null||title.isBlank()||message==null||message.isBlank())
            throw new IllegalArgumentException("Emergency details are required");
        try(Connection c=DBConnection.getConnection()){
            c.setAutoCommit(false);
            try{
                int id;
                try(PreparedStatement s=c.prepareStatement("INSERT INTO emergencies(title,message,created_by) VALUES(?,?,?)",Statement.RETURN_GENERATED_KEYS)){
                    s.setString(1,title);s.setString(2,message);s.setInt(3,createdBy);s.executeUpdate();
                    try(ResultSet r=s.getGeneratedKeys()){if(!r.next())throw new SQLException("Unable to create emergency");id=r.getInt(1);}
                }
                try(PreparedStatement s=c.prepareStatement("INSERT INTO notifications(user_id,message) SELECT id,? FROM users WHERE id<>?")){
                    s.setString(1,"EMERGENCY: "+title+" - "+message);s.setInt(2,createdBy);s.executeUpdate();
                }
                c.commit(); return id;
            }catch(Exception e){c.rollback();if(e instanceof SQLException s)throw s;throw new SQLException(e);}
            finally{c.setAutoCommit(true);}
        }
    }
}