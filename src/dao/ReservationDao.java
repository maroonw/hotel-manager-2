package dao;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public class ReservationDao {


    // Case 6 & 2 helpers for Availabiltiy and Pricing

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

    //reservation rate helper. It asks for a rate if there hasn't been one before
    // instead of asking for a rate for a room every single time.

    public Optional<Double> suggestRatePerNight(Connection con, String hotelId, String roomNumber) throws SQLException {
        String sql = """
        SELECT CostPerNight
        FROM RESERVATION
        WHERE HotelID = ? AND RoomNumber = ?
        ORDER BY CheckInDate DESC
        LIMIT 1
    """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, hotelId);
            ps.setString(2, roomNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(rs.getDouble(1));
                return Optional.empty();
            }
        }
    }

    // Case 2 helpers in order -- easier to walk through and explain
    // Case 2 - create reservation
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

    // case 2 - helpers to handle guest lookup / creation. Needed due to db design.

    //couldn't create a reservation without a guest becuse it was a foreign key. Error in
    // database design

    public boolean guestExists(Connection con, String governmentId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT 1 FROM GUEST WHERE GovernmentID = ?")) {
            ps.setString(1, governmentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public int insertGuestMinimal(Connection con, String governmentId, String name) throws SQLException {
        String sql = """
        INSERT INTO GUEST (GovernmentID, Name, Address, Email, PhoneNumber, CardNumber)
        VALUES (?, ?, 'N/A', 'na@example.com', '000-000-0000', '4000 0000 0000 0000')
    """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, governmentId);
            ps.setString(2, name);
            return ps.executeUpdate(); // 1 if inserted
        }
    }

    // case 2 - send email to guest - not needed, but we wanted to show we were thinking about next steps

    // find bookingId
    public Optional<String> findBookingIdFor(Connection con,
                                             String hotelId, String roomNumber,
                                             LocalDate checkIn, LocalDate checkOut,
                                             String governmentId) throws SQLException {

        String sql = """
        SELECT BookingID
        FROM RESERVATION
        WHERE HotelID = ? AND RoomNumber = ?
            AND CheckInDate = ? AND CheckOutDate = ?
                AND GovernmentID = ?
        ORDER BY CAST(SUBSTRING(BookingID,2) AS UNSIGNED) DESC
        LIMIT 1
    """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, hotelId);
            ps.setString(2, roomNumber);
            ps.setDate(3, java.sql.Date.valueOf(checkIn));
            ps.setDate(4, java.sql.Date.valueOf(checkOut));
            ps.setString(5, governmentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        }
    }

    //CASE 3 - Read Reservations


    // READ one reservation with joins
    public Optional<Map<String, Object>> getById(Connection con, String bookingId) throws SQLException {
        String sql = """
            SELECT r.BookingID, r.CheckInDate, r.CheckOutDate, r.CostPerNight,
                   r.RoomNumber, r.HotelID, r.GovernmentID,
                   g.Name AS GuestName, g.Email AS GuestEmail,
                   h.HotelName, h.Address AS HotelAddress
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
                m.put("GuestEmail", rs.getString("GuestEmail"));
                m.put("HotelName", rs.getString("HotelName"));
                m.put("HotelAddress", rs.getString("HotelAddress"));
                return Optional.of(m);
            }
        }
    }

    //CASE 4 - Update Reservation Dates

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

    //CASE 5 - Delete reservation

    // DELETE
    public int delete(Connection con, String bookingId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM RESERVATION WHERE BookingID = ?")) {
            ps.setString(1, bookingId);
            return ps.executeUpdate();
        }
    }

    //Shared - used mainly for creating a booking, ensures no duplicate booking id is created

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

