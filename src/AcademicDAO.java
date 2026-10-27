import java.sql.*;
import java.util.Locale;

public class AcademicDAO {
    public void addCourse(String code, String title, double credits, String type) throws SQLException {
        String sql = "INSERT INTO courses(course_code,course_title,credits,course_type) VALUES(?,?,?,?)";
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, title);
            ps.setDouble(3, credits);
            ps.setString(4, type.toUpperCase(Locale.ROOT));
            ps.executeUpdate();
        }
    }

    public long enroll(String roll, String code, int semester) throws SQLException {
        String sql = "INSERT INTO enrollments(roll_no,course_code,semester) VALUES(?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, roll);
            ps.setString(2, code);
            ps.setInt(3, semester);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    try (PreparedStatement marks = con.prepareStatement(
                            "INSERT INTO assessment_marks(enrollment_id) VALUES(?)")) {
                        marks.setLong(1, id);
                        marks.executeUpdate();
                    }
                    return id;
                }
            }
        }
        throw new SQLException("Enrollment created, but enrollment ID was not returned.");
    }

    public void saveMarks(long enrollmentId, Double cia1, Double cia2, Double aql,
                          Double ese, boolean absent) throws SQLException {
        String sql = "UPDATE assessment_marks SET cia1=?,cia2=?,assignments=?,ese_marks=?,ese_absent=? WHERE enrollment_id=?";
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            setNullableDouble(ps, 1, cia1);
            setNullableDouble(ps, 2, cia2);
            setNullableDouble(ps, 3, aql);
            setNullableDouble(ps, 4, absent ? null : ese);
            ps.setBoolean(5, absent);
            ps.setLong(6, enrollmentId);
            if (ps.executeUpdate() == 0) throw new SQLException("Enrollment ID not found.");
        }
    }

    public void updateAttendance(long enrollmentId, int held, int attended, boolean condonation) throws SQLException {
        if (held < 0 || attended < 0 || attended > held) throw new IllegalArgumentException("Attendance counts must satisfy 0 <= attended <= held.");
        String sql = "UPDATE enrollments SET classes_held=?,classes_attended=?,medical_condonation=? WHERE enrollment_id=?";
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, held); ps.setInt(2, attended); ps.setBoolean(3, condonation); ps.setLong(4, enrollmentId);
            if (ps.executeUpdate() == 0) throw new SQLException("Enrollment ID not found.");
        }
    }

    public void printMarkSheet(String roll) throws SQLException {
        String sql = "SELECT s.roll_no,s.student_name,s.department,e.semester,c.course_code,c.course_title,c.credits,c.course_type," +
                "e.classes_held,e.classes_attended,e.medical_condonation,m.cia1,m.cia2,m.assignments,m.ese_marks,m.ese_absent " +
                "FROM students s JOIN enrollments e ON s.roll_no=e.roll_no JOIN courses c ON c.course_code=e.course_code " +
                "JOIN assessment_marks m ON m.enrollment_id=e.enrollment_id WHERE s.roll_no=? ORDER BY e.semester,c.course_code";
        double weightedPoints = 0, creditsTotal = 0;
        boolean found = false;
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, roll);
            try (ResultSet rs = ps.executeQuery()) {
                System.out.println("\n========== KEC STUDENT SEMESTER MARK SHEET ==========");
                while (rs.next()) {
                    found = true;
                    String type = rs.getString("course_type");
                    int held = rs.getInt("classes_held"), attended = rs.getInt("classes_attended");
                    double attendance = held == 0 ? 0 : attended * 100.0 / held;
                    boolean condonation = rs.getBoolean("medical_condonation");
                    double cia1 = nullable(rs, "cia1"), cia2 = nullable(rs, "cia2"), aql = nullable(rs, "assignments");
                    double ese = nullable(rs, "ese_marks");
                    boolean absent = rs.getBoolean("ese_absent");
                    double cia = (cia1 + cia2 + aql) / 3.0;
                    double ciaWeight, eseWeight, eseMin;
                    if ("THEORY".equals(type)) { ciaWeight=0.40; eseWeight=0.60; eseMin=27; }
                    else if ("PRACTICAL".equals(type)) { ciaWeight=0.60; eseWeight=0.40; eseMin=20; }
                    else { ciaWeight=0.50; eseWeight=0.50; eseMin=22.5; }
                    double internalWeighted = cia * ciaWeight;
                    double externalWeighted = ese * eseWeight;
                    double total = internalWeighted + externalWeighted;
                    String grade, status;
                    if (attendance < 65) { grade="SA"; status="BARRED - SHORTAGE OF ATTENDANCE"; }
                    else if (attendance < 75 && !condonation) { grade="SA"; status="CONDITIONAL - MEDICAL CONDONATION REQUIRED"; }
                    else if (absent) { grade="AB"; status="FAIL - ABSENT IN ESE"; }
                    else if (externalWeighted < eseMin || total < 50) { grade="RA"; status="FAIL"; }
                    else { grade = gradeFor(total); status="PASS"; }
                    int gp = gradePoint(grade);
                    double credits = rs.getDouble("credits");
                    weightedPoints += credits * gp;
                    creditsTotal += credits;
                    System.out.printf(Locale.ROOT,
                        "%nStudent: %s | %s | Dept: %s | Sem: %d%nCourse: %s - %s (%s), Credits: %.1f%n" +
                        "CIA1: %.2f, CIA2: %.2f, Assignment/Quiz/Lab: %.2f, CIA Avg: %.2f%n" +
                        "Internal weighted: %.2f, ESE raw: %.2f, ESE weighted: %.2f, Total: %.2f%n" +
                        "Attendance: %.2f%% | Grade: %s | Status: %s%n",
                        rs.getString("student_name"), rs.getString("roll_no"), rs.getString("department"), rs.getInt("semester"),
                        rs.getString("course_code"), rs.getString("course_title"), type, credits, cia1, cia2, aql, cia,
                        internalWeighted, absent ? 0.0 : ese, absent ? 0.0 : externalWeighted, total, attendance, grade, status);
                }
            }
        }
        if (!found) { System.out.println("No enrollment/marks found for roll number: " + roll); return; }
        double sgpa = creditsTotal == 0 ? 0 : weightedPoints / creditsTotal;
        System.out.printf(Locale.ROOT, "%nSGPA: %.2f%nEquivalent Percentage (SGPA x 10): %.2f%%%n", sgpa, sgpa * 10);
        System.out.println("====================================================\n");
    }

    public void printAttendanceDefaulters() throws SQLException {
        String sql = "SELECT s.roll_no,s.student_name,s.department,c.course_code,e.classes_held,e.classes_attended " +
                "FROM students s JOIN enrollments e ON s.roll_no=e.roll_no JOIN courses c ON c.course_code=e.course_code " +
                "WHERE e.classes_held>0 AND (e.classes_attended*100.0/e.classes_held)<65 ORDER BY s.department,s.roll_no";
        try (Connection con=DBConnection.getConnection(); PreparedStatement ps=con.prepareStatement(sql); ResultSet rs=ps.executeQuery()) {
            System.out.println("\n--- ATTENDANCE DEFAULTERS (<65%) : SA ---");
            boolean any=false;
            while(rs.next()) { any=true; double pct=rs.getInt("classes_attended")*100.0/rs.getInt("classes_held");
                System.out.printf(Locale.ROOT,"%s | %s | %s | Course %s | %.2f%% | SA%n",
                    rs.getString("roll_no"),rs.getString("student_name"),rs.getString("department"),rs.getString("course_code"),pct); }
            if(!any) System.out.println("No attendance defaulters found.");
        }
    }

    public void printDepartmentSummary() throws SQLException {
        String sql = "SELECT s.department,COUNT(*) AS course_results," +
                "SUM(CASE WHEN e.classes_held>0 AND e.classes_attended*100.0/e.classes_held<65 THEN 0 " +
                "WHEN m.ese_absent=1 THEN 0 WHEN " +
                "(COALESCE(m.cia1,0)+COALESCE(m.cia2,0)+COALESCE(m.assignments,0))/3.0*" +
                "CASE c.course_type WHEN 'THEORY' THEN 0.40 WHEN 'PRACTICAL' THEN 0.60 ELSE 0.50 END<0 THEN 0 " +
                "ELSE CASE WHEN ((COALESCE(m.cia1,0)+COALESCE(m.cia2,0)+COALESCE(m.assignments,0))/3.0*" +
                "CASE c.course_type WHEN 'THEORY' THEN 0.40 WHEN 'PRACTICAL' THEN 0.60 ELSE 0.50 END +" +
                "COALESCE(m.ese_marks,0)*CASE c.course_type WHEN 'THEORY' THEN 0.60 WHEN 'PRACTICAL' THEN 0.40 ELSE 0.50 END)>=50 THEN 1 ELSE 0 END END) AS pass_count " +
                "FROM students s JOIN enrollments e ON e.roll_no=s.roll_no JOIN courses c ON c.course_code=e.course_code " +
                "JOIN assessment_marks m ON m.enrollment_id=e.enrollment_id GROUP BY s.department ORDER BY s.department";
        try (Connection con=DBConnection.getConnection(); PreparedStatement ps=con.prepareStatement(sql); ResultSet rs=ps.executeQuery()) {
            System.out.println("\n--- DEPARTMENT RESULT SUMMARY ---");
            while(rs.next()) System.out.printf("%s | Enrolled course results: %d | Pass count (approx): %d | Fail count (approx): %d%n",
                    rs.getString("department"),rs.getInt("course_results"),rs.getInt("pass_count"),rs.getInt("course_results")-rs.getInt("pass_count"));
        }
        System.out.println("Note: Class average SGPA is not included in this compact summary; calculate per-student SGPA from the mark-sheet output.");
    }

    private static String gradeFor(double total) {
        if (total >= 91) return "O";
        if (total >= 81) return "A+";
        if (total >= 71) return "A";
        if (total >= 61) return "B+";
        if (total >= 50) return "B";
        return "RA";
    }
    private static int gradePoint(String grade) {
        switch (grade) { case "O": return 10; case "A+": return 9; case "A": return 8; case "B+": return 7; case "B": return 6; default: return 0; }
    }
    private static double nullable(ResultSet rs, String column) throws SQLException {
        double value=rs.getDouble(column); return rs.wasNull() ? 0.0 : value;
    }
    private static void setNullableDouble(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) ps.setNull(index, Types.DECIMAL); else {
            if (value < 0 || value > 100) throw new IllegalArgumentException("Marks must be between 0 and 100.");
            ps.setDouble(index, value);
        }
    }
}
