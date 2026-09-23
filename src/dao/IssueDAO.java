package dao;

import database.DBConnection;
import model.Issue;
import java.sql.*;

public class IssueDAO {
    public int createIssue(Issue issue) throws SQLException {
        String sql="INSERT INTO issues(title,description,category,location,attachment_path,priority) VALUES(?,?,?,?,?,?)";
        try(Connection c=DBConnection.getConnection(); PreparedStatement s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
            s.setString(1,issue.getTitle());s.setString(2,issue.getDescription());s.setString(3,issue.getCategory());
            s.setString(4,issue.getLocation());s.setString(5,issue.getAttachmentPath());s.setString(6,issue.getPriority().name());s.executeUpdate();
            try(ResultSet k=s.getGeneratedKeys()){if(k.next())return k.getInt(1);}
        }
        throw new SQLException("Unable to create issue");
    }
}
