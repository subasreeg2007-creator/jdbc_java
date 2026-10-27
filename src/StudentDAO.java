import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class StudentDAO {
    public void addStudent(String rollNo, String name, String department, int semester) throws SQLException {
        String sql = "INSERT INTO students(roll_no, student_name, department, current_semester) VALUES(?,?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, rollNo);
            ps.setString(2, name);
            ps.setString(3, department.toUpperCase());
            ps.setInt(4, semester);
            ps.executeUpdate();
        }
    }
}
