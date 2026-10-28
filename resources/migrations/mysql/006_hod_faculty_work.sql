-- CAMPUSOS HOD / faculty work management.
-- Apply once to an existing MySQL 8 database after reviewing.
-- HOD is represented by the existing ADMIN role; professors use FACULTY.
USE campusos;

CREATE TABLE IF NOT EXISTS faculty_work_invitations (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL,
    department VARCHAR(100),
    token_hash CHAR(64) NOT NULL UNIQUE,
    invited_by INT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    accepted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_faculty_work_invites_email_expiry (email, expires_at),
    CONSTRAINT fk_faculty_work_invited_by FOREIGN KEY (invited_by) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS faculty_work_assignments (
    id INT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    assigned_to INT NOT NULL,
    assigned_by INT NOT NULL,
    due_at TIMESTAMP NULL,
    status ENUM('PENDING','IN_PROGRESS','COMPLETED') NOT NULL DEFAULT 'PENDING',
    progress_percent INT NOT NULL DEFAULT 0,
    progress_note VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_faculty_work_assignee_status_due (assigned_to, status, due_at),
    INDEX idx_faculty_work_status_due (status, due_at),
    CONSTRAINT chk_faculty_work_progress CHECK (progress_percent BETWEEN 0 AND 100),
    CONSTRAINT fk_faculty_work_assignee FOREIGN KEY (assigned_to) REFERENCES users(id),
    CONSTRAINT fk_faculty_work_assigner FOREIGN KEY (assigned_by) REFERENCES users(id)
);
