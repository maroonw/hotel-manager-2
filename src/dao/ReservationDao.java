package dao;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public class ReservationDao {

    // Auto-ID version: generate B# and delegate
    public boolean createReservationIfAvailable(
            Connection con,
            String hotelId, String roomNumber,
            LocalDate checkIn, LocalDate checkOut,
            double costPerNight, String governmentId) throws SQLException {

        String bookingId = nextBookingId(con);
        return createReservationIfAvailable(
                con, bookingId, hotelId, roomNumber, checkIn, checkOut, costPerNight, governmentId);
    }

    // Full version: performs availability check + insert using given bookingId
    // this gets used if BookingId is specified
    // not used currently, but kept in case we need it in the future
    public boolean createReservationIfAvailable(
            Connection con,
            String bookingId, String hotelId, String roomNumber,
            LocalDate checkIn, LocalDate checkOut,
            double costPerNight, String governmentId) throws SQLException {

        String verifyRoom = """
        SELECT 1 FROM ROOM
        WHERE HotelID = ? AND RoomNumber = ?
        FOR UPDATE
    """;
        String conflict = """
        SELECT 1 FROM RESERVATION
        WHERE HotelID = ? AND RoomNumber = ?
          AND ? < CheckOutDate
          AND CheckInDate < ?
        FOR UPDATE
    """;
        String insert = """
        INSERT INTO RESERVATION
          (BookingID, CheckInDate, CheckOutDate, CostPerNight, RoomNumber, HotelID, GovernmentID)
        VALUES (?, ?, ?, ?, ?, ?, ?)
    """;

        boolean oldAuto = con.getAutoCommit();
        con.setAutoCommit(false);
        try (PreparedStatement vr = con.prepareStatement(verifyRoom);
             PreparedStatement cf = con.prepareStatement(conflict);
             PreparedStatement ins = con.prepareStatement(insert)) {

            // 1) Verify room exists
            vr.setString(1, hotelId);
            vr.setString(2, roomNumber);
            try (ResultSet rs = vr.executeQuery()) {
                if (!rs.next()) { con.rollback(); return false; }
            }

            // 2) Check for overlap conflicts
            cf.setString(1, hotelId);
            cf.setString(2, roomNumber);
            cf.setDate(3, java.sql.Date.valueOf(checkIn));
            cf.setDate(4, java.sql.Date.valueOf(checkOut));
            try (ResultSet rs = cf.executeQuery()) {
                if (rs.next()) { con.rollback(); return false; }
            }

            // 3) Insert
            ins.setString(1, bookingId);
            ins.setDate(2, java.sql.Date.valueOf(checkIn));
            ins.setDate(3, java.sql.Date.valueOf(checkOut));
            ins.setDouble(4, costPerNight);
            ins.setString(5, roomNumber);
            ins.setString(6, hotelId);
            ins.setString(7, governmentId);
            ins.executeUpdate();

            con.commit();
            return true;
        } catch (SQLException e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(oldAuto);
        }
    }


    // READ one reservation with joins
    public Optional<Map<String, Object>> getById(Connection con, String bookingId) throws SQLException {
        String sql = """
            SELECT r.BookingID, r.CheckInDate, r.CheckOutDate, r.CostPerNight,
                   r.RoomNumber, r.HotelID, r.GovernmentID,
                   g.Name AS GuestName, h.HotelName, h.Address AS HotelAddress
            FROM RESERVATION r
            JOIN GUEST g ON g.GovernmentID = r.GovernmentID
            JOIN HOTEL h ON h.HotelID = r.HotelID
            WHERE r.BookingID = ?
        """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("BookingID", rs.getString("BookingID"));
                m.put("CheckInDate", rs.getDate("CheckInDate").toLocalDate());
                m.put("CheckOutDate", rs.getDate("CheckOutDate").toLocalDate());
                m.put("CostPerNight", rs.getDouble("CostPerNight"));
                m.put("RoomNumber", rs.getString("RoomNumber"));
                m.put("HotelID", rs.getString("HotelID"));
                m.put("GovernmentID", rs.getString("GovernmentID"));
                m.put("GuestName", rs.getString("GuestName"));
                m.put("HotelName", rs.getString("HotelName"));
                m.put("HotelAddress", rs.getString("HotelAddress"));
                return Optional.of(m);
            }
        }
    }

    // UPDATE dates (recheck availability against other bookings)
    public boolean updateDates(Connection con, String bookingId, LocalDate newIn, LocalDate newOut) throws SQLException {
        String getRoom = "SELECT RoomNumber, HotelID FROM RESERVATION WHERE BookingID = ? FOR UPDATE";
        String conflict = """
            SELECT 1 FROM RESERVATION
            WHERE HotelID = ? AND RoomNumber = ? AND BookingID <> ?
              AND ? < CheckOutDate
              AND CheckInDate < ?
            FOR UPDATE
        """;
        String update = "UPDATE RESERVATION SET CheckInDate = ?, CheckOutDate = ? WHERE BookingID = ?";

        boolean old = con.getAutoCommit();
        con.setAutoCommit(false);
        try (PreparedStatement gr = con.prepareStatement(getRoom)) {
            gr.setString(1, bookingId);
            String room=null, hotel=null;
            try (ResultSet rs = gr.executeQuery()) {
                if (rs.next()) { room = rs.getString(1); hotel = rs.getString(2); }
                else { con.rollback(); return false; }
            }

            try (PreparedStatement cf = con.prepareStatement(conflict)) {
                cf.setString(1, hotel);
                cf.setString(2, room);
                cf.setString(3, bookingId);
                cf.setDate(4, java.sql.Date.valueOf(newIn));
                cf.setDate(5, java.sql.Date.valueOf(newOut));
                try (ResultSet rs = cf.executeQuery()) {
                    if (rs.next()) { con.rollback(); return false; }
                }
            }

            try (PreparedStatement up = con.prepareStatement(update)) {
                up.setDate(1, java.sql.Date.valueOf(newIn));
                up.setDate(2, java.sql.Date.valueOf(newOut));
                up.setString(3, bookingId);
                up.executeUpdate();
            }

            con.commit();
            return true;
        } catch (SQLException e) {
            con.rollback(); throw e;
        } finally {
            con.setAutoCommit(old);
        }
    }

    // DELETE
    public int delete(Connection con, String bookingId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM RESERVATION WHERE BookingID = ?")) {
            ps.setString(1, bookingId);
            return ps.executeUpdate();
        }
    }

    // AVAILABLE ROOMS for date range
    public List<Map<String,String>> listAvailableRooms(Connection con, String hotelId, LocalDate in, LocalDate out) throws SQLException {
        String sql = """
            SELECT r.RoomNumber, r.RoomType, r.Floor
            FROM ROOM r
            WHERE r.HotelID = ?
              AND NOT EXISTS (
                SELECT 1
                FROM RESERVATION x
                WHERE x.HotelID   = r.HotelID
                  AND x.RoomNumber= r.RoomNumber
                  AND ? < x.CheckOutDate
                  AND x.CheckInDate < ?
              )
            ORDER BY r.RoomType, r.Floor, r.RoomNumber
        """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, hotelId);
            ps.setDate(2, java.sql.Date.valueOf(in));
            ps.setDate(3, java.sql.Date.valueOf(out));
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String,String>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String,String> m = new LinkedHashMap<>();
                    m.put("RoomNumber", rs.getString("RoomNumber"));
                    m.put("RoomType", rs.getString("RoomType"));
                    m.put("Floor", String.valueOf(rs.getInt("Floor")));
                    list.add(m);
                }
                return list;
            }
        }
    }

    // creating the next booking ID from the counter table
    // this makes sure that no booking IDs are the same
    private String nextBookingId(Connection con) throws SQLException {
        // Atomically increment and fetch the counter row
        try (PreparedStatement up = con.prepareStatement(
                "UPDATE COUNTER SET next_val = next_val + 1 WHERE name = 'RESERVATION'")) {
            if (up.executeUpdate() != 1) throw new SQLException("Need counter row");
        }
        try (PreparedStatement sel = con.prepareStatement(
                "SELECT next_val FROM COUNTER WHERE name = 'RESERVATION'")) {
            try (var rs = sel.executeQuery()) {
                if (!rs.next()) throw new SQLException("Counter row missing (select)");
                int n = rs.getInt(1) - 1;
                return "B" + n;
            }
        }
    }
}

