This program was run using Java 24 and MySQL Workbench.


Setup:

Unzip the program folder and open in your favorite Java IDE. IntelliJ works well.

To run the program first start MySQL Workbench and get a running instance of a server on your computer.
Note the user, password, and port that you select.

SQL scripts are now under src/resources/sql due to class requirements. The dedicated resources root cannot be
of the src folder.

Go to the file /src/db/Database.

At the top of the file you must change the 3 strings password, user, port to match your configuration.

Once you have done that compile and run the program.

The first time you run the program it will come to the main menu.

Select 1. To seed the database.

From there you can select whatever item you want from the menu to conduct insert, update, delete,
and querying functionalities.

Program Explanation:

DemoApplication is what the user will see while interacting with the program. It displays the menus and gives
the requested information about what the queries did. The ReseravationDao file is the Data Access Object. It acts
to a controller in that it allows the seperation of the application and the database layer. The database layer
consists of the Database file that translates SQL to Java visa-versa and the actual sql files that we use to
access the SQL server.

Video URL

TO DO

Contributors:

Julie Bush
Anthony Tharpe
William Maroon

