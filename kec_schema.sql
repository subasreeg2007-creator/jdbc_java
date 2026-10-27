CREATE DATABASE IF NOT EXISTS kec_academic_db;
USE kec_academic_db;

CREATE TABLE IF NOT EXISTS students (
    roll_no VARCHAR(20) PRIMARY KEY,
    student_name VARCHAR(100) NOT NULL,
    department VARCHAR(20) NOT NULL,
    current_semester INT NOT NULL CHECK (current_semester BETWEEN 1 AND 8)
);

CREATE TABLE IF NOT EXISTS courses (
    course_code VARCHAR(20) PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    credits DECIMAL(4,1) NOT NULL CHECK (credits > 0),
    course_type ENUM('THEORY','PRACTICAL','EMBEDDED') NOT NULL
);

CREATE TABLE IF NOT EXISTS enrollments (
    enrollment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    roll_no VARCHAR(20) NOT NULL,
    course_code VARCHAR(20) NOT NULL,
    semester INT NOT NULL CHECK (semester BETWEEN 1 AND 8),
    classes_held INT NOT NULL DEFAULT 0 CHECK (classes_held >= 0),
    classes_attended INT NOT NULL DEFAULT 0 CHECK (classes_attended >= 0),
    condonation_approved BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE KEY uq_student_course_sem (roll_no, course_code, semester),
    CONSTRAINT fk_enrollment_student FOREIGN KEY (roll_no)
        REFERENCES students(roll_no) ON DELETE CASCADE,
    CONSTRAINT fk_enrollment_course FOREIGN KEY (course_code)
        REFERENCES courses(course_code) ON DELETE CASCADE,
    CONSTRAINT chk_attendance_count CHECK (classes_attended <= classes_held)
);

CREATE TABLE IF NOT EXISTS assessment_marks (
    enrollment_id BIGINT PRIMARY KEY,
    cia1 DECIMAL(5,2) NULL,
    cia2 DECIMAL(5,2) NULL,
    assignment_quiz_lab DECIMAL(5,2) NULL,
    ese_marks DECIMAL(5,2) NULL,
    ese_absent BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_marks_enrollment FOREIGN KEY (enrollment_id)
        REFERENCES enrollments(enrollment_id) ON DELETE CASCADE,
    CONSTRAINT chk_cia1 CHECK (cia1 IS NULL OR cia1 BETWEEN 0 AND 100),
    CONSTRAINT chk_cia2 CHECK (cia2 IS NULL OR cia2 BETWEEN 0 AND 100),
    CONSTRAINT chk_aql CHECK (assignment_quiz_lab IS NULL OR assignment_quiz_lab BETWEEN 0 AND 100),
    CONSTRAINT chk_ese CHECK (ese_marks IS NULL OR ese_marks BETWEEN 0 AND 100)
);
