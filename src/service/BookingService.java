package service;

import dao.BookingDAO;
import exception.BookingException;

import java.sql.SQLException;
import java.time.LocalDateTime;

public class BookingService {
    private final BookingDAO bookingDAO;

    public BookingService(BookingDAO bookingDAO) {
        this.bookingDAO = bookingDAO;
    }

    public void validateBooking(int resourceId, LocalDateTime start, LocalDateTime end)
            throws BookingException {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new BookingException("Invalid booking time range");
        }

        try {
            if (bookingDAO.hasConflict(resourceId, start, end)) {
                throw new BookingException("Resource is already booked for this time");
            }
        } catch (SQLException e) {
            throw new BookingException("Unable to check resource availability");
        }
    }
}
