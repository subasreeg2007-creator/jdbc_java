# KEC R2024 Student Academic & Continuous Evaluation System (Java JDBC)

A menu-driven Java + MySQL starter project for student/course management, enrollment, incremental assessment marks, attendance eligibility, result calculation, and academic reports.

## Requirements
- JDK 8 or later
- MySQL Server 8.x
- MySQL Connector/J JAR

## 1. Create database and tables
Run `kec_schema.sql` in MySQL Workbench or command line:

```sql
SOURCE D:/path/to/KEC_Student_Academic_System/kec_schema.sql;
```

Or copy/paste the SQL contents into MySQL Workbench.

## 2. Add JDBC driver
Download MySQL Connector/J from the official MySQL website and place the JAR inside `lib/`.
Expected filename in the commands below: `mysql-connector-j-9.x.x.jar`.
The ZIP intentionally does not include a third-party JAR.

## 3. Configure database credentials
Open `src/DBConnection.java` and update `USER` and `PASSWORD` for your local MySQL account.

## 4. Compile (Windows CMD)
From the project folder:

```bat
mkdir bin
javac -cp "lib\mysql-connector-j-9.x.x.jar" -d bin src\*.java
```

Replace the JAR filename with the exact filename you downloaded.

## 5. Run
```bat
java -cp "bin;lib\mysql-connector-j-9.x.x.jar" MainApp
```

## Implemented menu
1. Add student
2. Add course
3. Enroll student in course
4. Record/update assessment marks (CIA1, CIA2, assignment/quiz/lab, ESE; ESE supports AB)
5. Update attendance
6. Student semester mark sheet
7. Attendance defaulter report (<65%)
8. Department result summary
0. Exit

## Evaluation assumptions
The supplied brief does not specify how multiple CIA components are combined into a single CIA score. This starter calculates the CIA score as the average of CIA1, CIA2, and assignment/quiz/lab score, then applies the course-type CIA/ESE weightages. Adjust `AcademicDAO.java` if your faculty provides a different rule.

For grade bands, the total is out of 100: O 91–100, A+ 81–90, A 71–80, B+ 61–70, B 50–60; RA otherwise. ESE threshold checks use weighted ESE contribution: THEORY 27/60, PRACTICAL 20/40, EMBEDDED 22.5/50. Attendance below 65% produces SA and bars the student from ESE. Attendance 65%–74.99% is marked conditional; this project records a `condonationApproved` flag on enrollment.

## Notes
- This is a student-project starter; verify the exact R2024 evaluation policy with your department before formal use.
- Screenshots in the `screenshots/` folder should be captured from your own running application; no fabricated screenshots are included.
