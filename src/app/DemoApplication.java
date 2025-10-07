package app;

import db.Database;
import dao.ReservationDao;

import java.nio.file.Path;
import java.sql.*;
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
                System.out.println("1) Bootstrap DB (create + sample data)");
                System.out.println("2) Create reservation (if available)");
                System.out.println("3) Read reservation by BookingID");
                System.out.println("4) Update reservation dates");
                System.out.println("5) Delete reservation");
                System.out.println("6) List available rooms (by hotel + dates)");
                System.out.println("0) Exit");
                System.out.print("Choose: ");
                String choice = in.nextLine().trim();

                try {
                    switch (choice) {

                        // this seeds data if this is the first time running the application
                        // you can run it if it's already been run and it will just stop and
                        // say duplicates
                        case "1" -> {
                            // scripts

                            // old file path didn't work
                            // Database.runSqlFile(con, Path.of("sql/projectdeliverable3_group3_create.sql"));  // creates DB & tables
                            // Database.runSqlFile(con, Path.of("sql/projectdeliverable3_group3_insert.sql"));  // inserts sample data

                            // testing if create is the issue, moved resources due to class requirements for src only
                            // System.out.println("Running CREATE script...");
                            // Database.runSqlResource(con, "/sql/projectdeliverable3_group3_create.sql");
                            // System.out.println("CREATE complete.");

                            //testing if insert is the issue, moved resources due to class requirements for src only
                            // System.out.println("Running INSERT script...");
                            // Database.runSqlResource(con, "/sql/projectdeliverable3_group3_insert.sql");
                            // System.out.println("INSERT complete. Sample data loaded.");

                            // not needed, but doesn't hurt anything, moved resources due to class requirements for src only
                            // Database.runSqlResourceSelects(con, "/sql/projectdeliverable3_group3_select.sql");
                            // System.out.println("Database created and sample data loaded.");

                            var createPath = java.nio.file.Paths.get("src", "resources",
                                    "sql", "projectdeliverable3_group3_create.sql").toAbsolutePath();
                            var insertPath = java.nio.file.Paths.get("src", "resources",
                                    "sql", "projectdeliverable3_group3_insert.sql").toAbsolutePath();

                            System.out.println("CREATE @ " + createPath);
                            db.Database.runSqlFile(con, createPath);

                            System.out.println("INSERT @ " + insertPath);
                            db.Database.runSqlFile(con, insertPath);
                        }
                        case "2" -> {

                             /*  This didn't flow very well, redid. Left in to show iterations.
                             *setup auto generating of BookingId to keep from having duplicates
                             *System.out.print("BookingID: ");
                             *String b = in.nextLine();
                             *System.out.print("HotelID: ");
                             *String h = in.nextLine();
                             *System.out.print("RoomNumber: ");
                             *String r = in.nextLine();
                             *System.out.print("CheckIn (YYYY-MM-DD): ");
                             *LocalDate ci = LocalDate.parse(in.nextLine());
                             *System.out.print("CheckOut (YYYY-MM-DD): ");
                             *LocalDate co = LocalDate.parse(in.nextLine());
                             *System.out.print("CostPerNight: ");
                             *double cpn = Double.parseDouble(in.nextLine());
                             *System.out.print("GovernmentID: ");
                             *String gid = in.nextLine();
                             *
                             *boolean ok = dao.createReservationIfAvailable(con, h, r, ci, co, cpn, gid);
                             *System.out.println(ok ? "Created." : "Room not available or not found.");
                             */

                            // set hotel and date wanted
                            String h;
                            String roomNum;
                            LocalDate ci;
                            LocalDate co;

                            try {
                                System.out.print("HotelID: ");
                                h = in.nextLine();
                                PreparedStatement stmt = con.prepareStatement("SELECT hotelid FROM hotel WHERE hotelid = ?");
                                stmt.setString(1, h);
                                ResultSet rs = stmt.executeQuery();
                                if (!rs.next()) {
                                    System.out.println("Hotel ID does not exist. Please try again.");
                                    break;
                                }
                            } catch (Exception e) {
                                System.out.println("Invalid SQL: " + e.getMessage());
                                break;
                            }

                            // CHECK DATE AVAILABILITY
                            try {
                                System.out.print("CheckIn (YYYY-MM-DD): ");
                                ci = LocalDate.parse(in.nextLine());
                                System.out.print("CheckOut (YYYY-MM-DD): ");
                                co = LocalDate.parse(in.nextLine());
                                PreparedStatement stmt = con.prepareStatement("select * from reservation where (CheckInDate BETWEEN ? AND ?) OR CheckOutDate BETWEEN ? AND ?;");
                                stmt.setString(1, ci.toString());
                                stmt.setString(2, co.toString());
                                stmt.setString(3, ci.toString());
                                stmt.setString(4, co.toString());
                                ResultSet rs = stmt.executeQuery();
                                if (rs.next()) {
                                    System.out.println("Date's are not available. Please try again.");
                                    break;
                                }
                            } catch (Exception e) {
                                System.out.println("Invalid date format, please use YYYY-MM-DD format: " + e.getMessage());
                                break;
                            }

                            // get rooms available
                            var rooms = dao.listAvailableRooms(con, h, ci, co);
                            if (rooms.isEmpty()) {
                                System.out.println("No rooms available");
                                break;
                            }

                            // show rooms and rates
                            System.out.println("\nRooms Available");
                            List<String> roomNumbers = new ArrayList<>();
                            for (int i = 0; i < rooms.size(); i++) {
                                var r = rooms.get(i);
                                roomNum = r.get("RoomNumber");
                                roomNumbers.add(roomNum);

                                var oldRate = dao.suggestRatePerNight(con, h, roomNum);
                                String rateTxt = oldRate.map(x -> String.format("$%.2f", x)).orElse("-");

                                System.out.printf("%2d) Room %s | Type %s | Floor: %s | Suggested: %s%n",
                                        (i + 1), roomNum, r.getOrDefault("RoomType", "?"),
                                        r.getOrDefault("Floor", "?"), rateTxt);
                            }

                            // select room
                            System.out.print("\nChoose Room Number (e.g., R22): ");
                            roomNum = in.nextLine().trim();

                            boolean inList = false;
                            for (String rn : roomNumbers) {
                                if (rn.equalsIgnoreCase(roomNum)) {
                                    roomNum = rn;   // normalize to exact case from list
                                    inList = true;
                                    break;
                                }
                            }
                            if (!inList) {
                                System.out.println("That room isn't in the available list.");
                                break;
                            }
                            // figure out price, will suggest last price for the room
                            // if never used it will ask for price automatically
                            var suggestedRate = dao.suggestRatePerNight(con, h, roomNum);
                            Double rate;
                            if (suggestedRate.isPresent()) {
                                System.out.printf("Suggested rate is $%.2f. Enter to accept, or new rate:  ",
                                        suggestedRate.get());
                                        String over= in.nextLine().trim();
                                        rate = over.isEmpty() ? suggestedRate.get() : Double.parseDouble(over);
                            } else {
                                System.out.print("No suggested rate found, please enter new one: ");
                                rate = Double.parseDouble(in.nextLine().trim());
                            }

                            // get GovernmentId from guest
                            System.out.print("GovernmentId of guest: ");
                            String govId = in.nextLine().trim();

                            //gues must exist first due to bad database design, workaround by
                            //quick creating guest here if not already in db

                            if (!dao.guestExists(con, govId)) {
                                System.out.print("No guest with that GovernmentID. Create minimal guest now? (Y/N): ");
                                String ans = in.nextLine().trim().toLowerCase();
                                if (ans.equals("y") || ans.equals("yes")) {
                                    System.out.print("Guest name: ");
                                    String name = in.nextLine().trim();
                                    int inserted = dao.insertGuestMinimal(con, govId, name.isEmpty() ? "New Guest" : name);
                                    if (inserted != 1) {
                                        System.out.println("Could not create guest. Aborting.");
                                        break;
                                    }
                                } else {
                                    System.out.println("Please create the guest first, then try again.");
                                    break;
                                }
                            }

                            //booking is created here
                            boolean ok = dao.createReservationIfAvailable(con, h, roomNum, ci, co, rate, govId);
                            System.out.println(ok ? "Reservation created" : "Reservation not created");

                            // show faux email that we would send to guest. We would later hook this into
                            // some sort of notification system. Email is only sent to the guest if the
                            // booking is successful. Starts with if(ok).

                            if (ok) {
                                var bid = dao.findBookingIdFor(con, h, roomNum, ci, co, govId);
                                if (bid.isPresent()) {
                                    var rv = dao.getById(con, bid.get());
                                    if (rv.isPresent()) {
                                        var m = rv.get();
                                        String email = String.valueOf(m.getOrDefault("GuestEmail", "(unknown)"));
                                        String bookingId = String.valueOf(m.get("BookingID"));
                                        String guest = String.valueOf(m.get("GuestName"));
                                        String hotel = String.valueOf(m.get("HotelName"));
                                        String rn    = String.valueOf(m.get("RoomNumber"));
                                        var checkIn  = (java.time.LocalDate) m.get("CheckInDate");
                                        var checkOut = (java.time.LocalDate) m.get("CheckOutDate");
                                        long nights = java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut);
                                        double cpn = (double) m.get("CostPerNight");
                                        double total = nights * cpn;

                                        System.out.println();
                                        System.out.println("((%s)) to Guest -- booking details".formatted(email));
                                        System.out.println("Booking: %s".formatted(bookingId));
                                        System.out.println("Guest  : %s".formatted(guest));
                                        System.out.println("Hotel  : %s".formatted(hotel));
                                        System.out.println("Room   : %s".formatted(rn));
                                        System.out.println("Dates  : %s → %s  (%d nights)".formatted(checkIn, checkOut, nights));
                                        System.out.println("Rate   : $%.2f/night   Total: $%.2f".formatted(cpn, total));
                                        System.out.println();
                                    }
                                }
                            }

                        }
                        case "3" -> {
                            System.out.print("BookingID: ");
                            String b = in.nextLine();
                            var res = dao.getById(con, b);
                            System.out.println(res.map(Object::toString).orElse("Not found"));
                        }
                        case "4" -> {
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
                        case "5" -> {
                            System.out.print("BookingID: ");
                            String b = in.nextLine();
                            int n = dao.delete(con, b);
                            System.out.println(n == 1 ? "Deleted." : "Booking ID does not exist. Please try again.");
                        }
                        case "6" -> {
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
                System.err.println("\n*** DB CONNECTION CHECK FAILED ***");
                System.err.println("Cause: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                System.exit(1);
            }
    }
}

