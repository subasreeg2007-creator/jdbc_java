import java.sql.*;
import java.util.Scanner;

public class MainApp {
    private static final Scanner SC = new Scanner(System.in);
    private static final StudentDAO STUDENTS = new StudentDAO();
    private static final AcademicDAO ACADEMICS = new AcademicDAO();

    public static void main(String[] args) {
        try (Connection ignored = DBConnection.getConnection()) {
            System.out.println("MySQL connection successful.");
        } catch (SQLException ex) {
            System.out.println("Database connection failed: " + ex.getMessage());
            System.out.println("Check MySQL service, schema, Connector/J JAR, and DBConnection credentials.");
            return;
        }

        while (true) {
            System.out.println("\n========== KEC STUDENT ACADEMIC SYSTEM ==========");
            System.out.println("1. Add Student");
            System.out.println("2. Add Course");
            System.out.println("3. Enroll Student in Course");
            System.out.println("4. Add/Update Assessment Marks");
            System.out.println("5. Update Attendance");
            System.out.println("6. View Student Semester Mark Sheet");
            System.out.println("7. Attendance Defaulter Report");
            System.out.println("8. Department Result Summary");
            System.out.println("0. Exit");
            int choice = readInt("Enter choice: ");
            try {
                switch (choice) {
                    case 1: addStudent(); break;
                    case 2: addCourse(); break;
                    case 3: enroll(); break;
                    case 4: marks(); break;
                    case 5: attendance(); break;
                    case 6: ACADEMICS.printMarkSheet(readLine("Enter Roll Number: ").toUpperCase()); break;
                    case 7: ACADEMICS.printAttendanceDefaulters(); break;
                    case 8: ACADEMICS.printDepartmentSummary(); break;
                    case 0: System.out.println("Thank you for using KEC Student Academic System."); return;
                    default: System.out.println("Invalid choice. Please select from the menu.");
                }
            } catch (Exception ex) {
                System.out.println("Operation failed: " + ex.getMessage());
            }
        }
    }

    private static void addStudent() throws SQLException {
        String roll=readLine("Roll Number (e.g. 24ITR001): ").toUpperCase();
        String name=readLine("Student Name: ");
        String dept=readLine("Department (CSE/ECE/MECH/IT): ").toUpperCase();
        int sem=readInt("Current Semester (1-8): ");
        STUDENTS.addStudent(roll,name,dept,sem);
        System.out.println("Student added successfully.");
    }
    private static void addCourse() throws SQLException {
        String code=readLine("Course Code (e.g. 24ITR31): ").toUpperCase();
        String title=readLine("Course Title: ");
        double credits=readDouble("Credits: ");
        String type=readLine("Course Type (THEORY/PRACTICAL/EMBEDDED): ").toUpperCase();
        ACADEMICS.addCourse(code,title,credits,type);
        System.out.println("Course added successfully.");
    }
    private static void enroll() throws SQLException {
        String roll=readLine("Roll Number: ").toUpperCase();
        String code=readLine("Course Code: ").toUpperCase();
        int sem=readInt("Semester: ");
        long id=ACADEMICS.enroll(roll,code,sem);
        System.out.println("Enrollment successful. Enrollment ID = " + id + " (use this ID for marks/attendance).");
    }
    private static void marks() throws SQLException {
        long id=readLong("Enrollment ID: ");
        Double c1=readOptionalMark("CIA Test 1 (0-100, blank to keep/zero): ");
        Double c2=readOptionalMark("CIA Test 2 (0-100, blank to keep/zero): ");
        Double aql=readOptionalMark("Assignment/Quiz/Lab (0-100, blank to keep/zero): ");
        String absentInput=readLine("Absent in ESE? (Y/N): ").trim();
        boolean absent=absentInput.equalsIgnoreCase("Y");
        Double ese=absent ? null : readOptionalMark("ESE Marks (0-100, blank to keep/zero): ");
        ACADEMICS.saveMarks(id,c1,c2,aql,ese,absent);
        System.out.println("Assessment marks saved successfully.");
    }
    private static void attendance() throws SQLException {
        long id=readLong("Enrollment ID: ");
        int held=readInt("Total classes held: ");
        int attended=readInt("Classes attended: ");
        double pct=held==0 ? 0 : attended*100.0/held;
        boolean condonation=false;
        if(pct>=65 && pct<75) condonation=readLine("Medical condonation approved? (Y/N): ").equalsIgnoreCase("Y");
        ACADEMICS.updateAttendance(id,held,attended,condonation);
        System.out.printf("Attendance updated: %.2f%%%n",pct);
    }
    private static String readLine(String prompt) {
        System.out.print(prompt);
        return SC.nextLine().trim();
    }
    private static int readInt(String prompt) {
        while(true) { try { return Integer.parseInt(readLine(prompt)); } catch(NumberFormatException ex) { System.out.println("Enter a valid whole number."); } }
    }
    private static long readLong(String prompt) {
        while(true) { try { return Long.parseLong(readLine(prompt)); } catch(NumberFormatException ex) { System.out.println("Enter a valid ID number."); } }
    }
    private static double readDouble(String prompt) {
        while(true) { try { return Double.parseDouble(readLine(prompt)); } catch(NumberFormatException ex) { System.out.println("Enter a valid number."); } }
    }
    private static Double readOptionalMark(String prompt) {
        while(true) {
            String value=readLine(prompt);
            if(value.isEmpty()) return 0.0;
            try {
                double mark=Double.parseDouble(value);
                if(mark>=0 && mark<=100) return mark;
                System.out.println("Marks must be between 0 and 100.");
            } catch(NumberFormatException ex) { System.out.println("Enter a valid mark from 0 to 100."); }
        }
    }
}
