package app;

import com.mysql.cj.x.protobuf.MysqlxPrepare;
import db.Database;
import dao.ReservationDao;

import java.nio.file.Path;
import java.sql.*;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.*;

public class DemoApplication {
    public static void main(String[] args) throws Exception {
        connectionCheck();

        Scanner in = new Scanner(System.in);
        ReservationDao dao = new ReservationDao();

        try (Connection con = Database.connect()) {
            while (true) {
                System.out.println("\n=== Hotel Management ===");
                //System.out.println("1) Bootstrap DB (create + sample data)");
                System.out.println("1) Create reservation (if available)");
                System.out.println("2) Read reservation by BookingID");
                System.out.println("3) Update reservation dates");
                System.out.println("4) Delete reservation");
                System.out.println("5) List available rooms (by hotel + dates)");
                System.out.println("0) Exit");
                System.out.print("Choose: ");
                String choice = in.nextLine().trim();

                try {
                    switch (choice) {
//                        case "1" -> {
//                            // scripts
//
//                            // old file path didn't work
//                            // Database.runSqlFile(con, Path.of("sql/projectdeliverable3_group3_create.sql"));  // creates DB & tables
//                            // Database.runSqlFile(con, Path.of("sql/projectdeliverable3_group3_insert.sql"));  // inserts sample data
//
//                            // testing if create is the issue
//                            System.out.println("Running CREATE script...");
//                            Database.runSqlResource(con, "/sql/projectdeliverable3_group3_create.sql");
//                            System.out.println("CREATE complete.");
//
//                            //testing if insert is the issue
//                            System.out.println("Running INSERT script...");
//                            Database.runSqlResource(con, "/sql/projectdeliverable3_group3_insert.sql");
//                            System.out.println("INSERT complete. Sample data loaded.");
//
//                            Database.runSqlResourceSelects(con, "/sql/projectdeliverable3_group3_select.sql");
//                            System.out.println("Database created and sample data loaded.");
//                        }
                        case "1" -> {
                            // GET HOTEL ID
                            String hotel = null;
                            String room = null;
                            LocalDate checkin = null;
                            LocalDate checkout = null;
                            double cpn = 0;

                            try {
                                System.out.print("HotelID: ");
                                hotel = in.nextLine();
                                PreparedStatement stmt = con.prepareStatement("SELECT hotelid FROM hotel WHERE hotelid = ?");
                                stmt.setString(1, hotel);
                                ResultSet rs = stmt.executeQuery();
                                if (!rs.next()) {
                                    System.out.println("Hotel ID does not exist. Please try again.");
                                    break;
                                }
                            } catch (Exception e) {
                                System.out.println("Invalid SQL: " + e.getMessage());
                                break;
                            }
                            // GET ROOM NUMBER
                            try {
                                System.out.print("RoomNumber: ");
                                room = in.nextLine();
                                PreparedStatement stmt = con.prepareStatement("SELECT RoomNumber FROM room WHERE RoomNumber = ?");
                                stmt.setString(1, room);
                                ResultSet rs = stmt.executeQuery();
                                if (!rs.next()) {
                                    System.out.println("Room number does not exist. Please try again.");
                                    break;
                                }
                            } catch (Exception e) {
                                System.out.println("RoomNumber does not exist: " + e.getMessage());
                                break;
                            }
                            // CHECK DATE AVAILABILITY
                            try {
                                System.out.print("CheckIn (YYYY-MM-DD): ");
                                checkin = LocalDate.parse(in.nextLine());
                                System.out.print("CheckOut (YYYY-MM-DD): ");
                                checkout = LocalDate.parse(in.nextLine());
                                PreparedStatement stmt = con.prepareStatement("select * from reservation where (CheckInDate BETWEEN ? AND ?) OR CheckOutDate BETWEEN ? AND ?;");
                                stmt.setString(1, checkin.toString());
                                stmt.setString(2, checkout.toString());
                                stmt.setString(3, checkin.toString());
                                stmt.setString(4, checkout.toString());
                                ResultSet rs = stmt.executeQuery();
                                if (rs.next()) {
                                    System.out.println("Date's are not available. Please try again.");
                                    break;
                                }
                            } catch (Exception e) {
                                System.out.println("Invalid date format, please use YYYY-MM-DD format: " + e.getMessage());
                                break;
                            }
                            // COST PER NIGHT
                            try {
                                System.out.print("CostPerNight: ");
                                cpn = Double.parseDouble(in.nextLine());
                            } catch (Exception e) {
                                System.out.println("Invalid cost per night:" + e.getMessage());
                                break;
                            }
                            // GOVERNMENT ID
                            System.out.print("GovernmentID: ");
                            String gid = in.nextLine();

                            boolean ok = dao.createReservationIfAvailable(con, hotel, room, checkin, checkout, cpn, gid);
                            System.out.println(ok ? "Created." : "Room not available or not found.");
                        }
                        case "2" -> {
                            System.out.print("BookingID: ");
                            String b = in.nextLine();
                            var res = dao.getById(con, b);
                            System.out.println(res.map(Object::toString).orElse("BookingID does not exist."));
                        }
                        case "3" -> {
                            String b = null;
                            try {
                                System.out.print("BookingID: ");
                                b = in.nextLine();
                                PreparedStatement stmt = con.prepareStatement("SELECT BookingID FROM reservation WHERE BookingID = ?");
                                stmt.setString(1, b);
                                ResultSet rs = stmt.executeQuery();
                                if (!rs.next()) {
                                    System.out.println("Booking ID does not exist. Please try again.");
                                    break;
                                }
                            } catch (SQLException e){
                                System.out.println("Invalid SQL Statement: " + e.getMessage());
                                break;
                            }
                            LocalDate ni = null;
                            LocalDate no = null;
                            try {
                                System.out.print("New CheckIn (YYYY-MM-DD): ");
                                ni = LocalDate.parse(in.nextLine());
                                System.out.print("New CheckOut (YYYY-MM-DD): ");
                                no = LocalDate.parse(in.nextLine());
                            } catch (Exception e) {
                                System.out.println("Invalid date format, please use YYYY-MM-DD format when you try again: " + e.getMessage());
                                break;
                            }
                            if (ni.isBefore(no)) {
                                boolean ok = dao.updateDates(con, b, ni, no);
                                System.out.println(ok ? "Updated." : "Conflict or booking not found.");
                            } else {
                                System.out.println("The checkout date must be before the checkin date.\nThis action could not be completed. Please try again.");
                                break;
                            }
                        }
                        case "4" -> {
                            System.out.print("BookingID: ");
                            String b = in.nextLine();
                            int n = dao.delete(con, b);
                            System.out.println(n == 1 ? "Deleted." : "Booking ID does not exist. Please try again.");
                        }
                        case "5" -> {
                            System.out.print("HotelID: ");
                            String h = in.nextLine();
                            try {
                                PreparedStatement stmt = con.prepareStatement("SELECT hotelid FROM hotel WHERE hotelid = ?");
                                stmt.setString(1, h);
                                ResultSet rs = stmt.executeQuery();
                                if (!rs.next()) {
                                    System.out.println("Hotel ID does not exist. Please try again.");
                                    break;
                                }
                            } catch (Exception e) {
                                System.out.println("Invalid SQL: " + e.getMessage());
                            }
                            LocalDate ci = null;
                            LocalDate co = null;
                            try {
                                System.out.print("CheckIn Date (YYYY-MM-DD): ");
                                ci = LocalDate.parse(in.nextLine());
                                System.out.print("CheckOut Date (YYYY-MM-DD): ");
                                co = LocalDate.parse(in.nextLine());
                            } catch (Exception e) {
                                System.out.println("Invalid date format, please use YYYY-MM-DD format when you try again: " + e.getMessage());
                                break;
                            }
                            if (ci.isBefore(co)) {
                                var rooms = dao.listAvailableRooms(con, h, ci, co);
                                rooms.forEach(System.out::println);
                            } else {
                                System.out.println("The checkout date must be before the checkin date.\nThis action could not be completed. Please try again.");
                                break;
                            }
                        }
                        case "0" -> {
                            System.out.println("Bye!");
                            return;
                        }
                        default -> System.out.println("Invalid choice.");
                    }
                } catch (Exception ex) {
                    System.out.println("Error: " + ex.getMessage());
                }
            }
        }
    }

        // trying to debug teh connection
        private static void connectionCheck() {
            try (var con = Database.connectServer();
                 var st  = con.createStatement();
                 var rs  = st.executeQuery("SELECT 1")) {
                rs.next();
                System.out.println("DB OK -> " + rs.getInt(1));
            } catch (Exception e) {
                System.err.println("\n*** DB SANITY CHECK FAILED ***");
                System.err.println("Cause: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                System.exit(1);
            }
    }
}

