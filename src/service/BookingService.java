package service;
import dao.BookingDAO; import java.sql.SQLException; import java.time.LocalDateTime;
public class BookingService{
 private final BookingDAO dao=new BookingDAO();
 public int book(int resourceId,int userId,LocalDateTime start,LocalDateTime end)throws SQLException{
  if(resourceId<=0||userId<=0||start==null||end==null||!start.isBefore(end))throw new IllegalArgumentException("Invalid booking data");
  if(start.isBefore(LocalDateTime.now()))throw new IllegalArgumentException("Booking must start in the future");
  return dao.createIfAvailable(resourceId,userId,start,end);
 }
}