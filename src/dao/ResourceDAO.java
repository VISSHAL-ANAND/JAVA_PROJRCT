package dao;
import database.DBConnection; import java.sql.*; import java.util.*;
public class ResourceDAO{
 public List<Integer> findAvailableResourceIds()throws SQLException{List<Integer> ids=new ArrayList<>();String q="SELECT id FROM resources WHERE available=TRUE AND quantity>0 ORDER BY id";try(Connection c=DBConnection.getConnection();PreparedStatement s=c.prepareStatement(q);ResultSet r=s.executeQuery()){while(r.next())ids.add(r.getInt(1));}return ids;}
 public OptionalInt findIdByName(String name)throws SQLException{String q="SELECT id FROM resources WHERE name=? AND available=TRUE AND quantity>0 LIMIT 1";try(Connection c=DBConnection.getConnection();PreparedStatement s=c.prepareStatement(q)){s.setString(1,name);try(ResultSet r=s.executeQuery()){return r.next()?OptionalInt.of(r.getInt(1)):OptionalInt.empty();}}}

 public List<Map<String,Object>> findAvailableResources() throws SQLException {
  List<Map<String,Object>> items=new ArrayList<>();
  String q="SELECT id,name,type,quantity FROM resources WHERE available=TRUE AND quantity>0 ORDER BY name LIMIT 100";
  try(Connection c=DBConnection.getConnection();PreparedStatement s=c.prepareStatement(q);ResultSet r=s.executeQuery()){
   while(r.next()){Map<String,Object> item=new LinkedHashMap<>();item.put("id",r.getInt("id"));item.put("name",r.getString("name"));item.put("type",r.getString("type"));item.put("quantity",r.getInt("quantity"));items.add(item);}
  }
  return items;
 }

}