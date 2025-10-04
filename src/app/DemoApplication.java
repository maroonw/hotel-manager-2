package app;

import db.Database;
import dao.ReservationDao;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
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
                        case "1" -> {
                            // scripts

                            // old file path didn't work
                            // Database.runSqlFile(con, Path.of("sql/projectdeliverable3_group3_create.sql"));  // creates DB & tables
                            // Database.runSqlFile(con, Path.of("sql/projectdeliverable3_group3_insert.sql"));  // inserts sample data

                            // testing if create is the issue
                            System.out.println("Running CREATE script...");
                            Database.runSqlResource(con, "/sql/projectdeliverable3_group3_create.sql");
                            System.out.println("CREATE complete.");

                            //testing if insert is the issue
                            System.out.println("Running INSERT script...");
                            Database.runSqlResource(con, "/sql/projectdeliverable3_group3_insert.sql");
                            System.out.println("INSERT complete. Sample data loaded.");

                            Database.runSqlResourceSelects(con, "/sql/projectdeliverable3_group3_select.sql");
                            System.out.println("Database created and sample data loaded.");
                        }
                        case "2" -> {
                            //System.out.print("BookingID: ");
                            //String b = in.nextLine();
                            System.out.print("HotelID: ");
                            String h = in.nextLine();
                            System.out.print("RoomNumber: ");
                            String r = in.nextLine();
                            System.out.print("CheckIn (YYYY-MM-DD): ");
                            LocalDate ci = LocalDate.parse(in.nextLine());
                            System.out.print("CheckOut (YYYY-MM-DD): ");
                            LocalDate co = LocalDate.parse(in.nextLine());
                            System.out.print("CostPerNight: ");
                            double cpn = Double.parseDouble(in.nextLine());
                            System.out.print("GovernmentID: ");
                            String gid = in.nextLine();

                            boolean ok = dao.createReservationIfAvailable(con, h, r, ci, co, cpn, gid);
                            System.out.println(ok ? "Created." : "Room not available or not found.");
                        }
                        case "3" -> {
                            System.out.print("BookingID: ");
                            String b = in.nextLine();
                            var res = dao.getById(con, b);
                            System.out.println(res.map(Object::toString).orElse("Not found"));
                        }
                        case "4" -> {
                            System.out.print("BookingID: ");
                            String b = in.nextLine();
                            System.out.print("New CheckIn: ");
                            LocalDate ni = LocalDate.parse(in.nextLine());
                            System.out.print("New CheckOut: ");
                            LocalDate no = LocalDate.parse(in.nextLine());
                            boolean ok = dao.updateDates(con, b, ni, no);
                            System.out.println(ok ? "Updated." : "Conflict or booking not found.");
                        }
                        case "5" -> {
                            System.out.print("BookingID: ");
                            String b = in.nextLine();
                            int n = dao.delete(con, b);
                            System.out.println(n == 1 ? "Deleted." : "Not found.");
                        }
                        case "6" -> {
                            System.out.print("HotelID: ");
                            String h = in.nextLine();
                            System.out.print("CheckIn: ");
                            LocalDate ci = LocalDate.parse(in.nextLine());
                            System.out.print("CheckOut: ");
                            LocalDate co = LocalDate.parse(in.nextLine());
                            var rooms = dao.listAvailableRooms(con, h, ci, co);
                            rooms.forEach(System.out::println);
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

