CREATE DATABASE IF NOT EXISTS campusos;
USE campusos;

CREATE TABLE IF NOT EXISTS users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role ENUM('STUDENT','FACULTY','MAINTENANCE','TECHNICIAN','ADMIN') NOT NULL,
    register_number VARCHAR(50),
    department VARCHAR(100),
    specialization VARCHAR(100),
    available BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS issues (
    id INT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(100) NOT NULL,
    location VARCHAR(200),
    attachment_path VARCHAR(500),
    priority ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL DEFAULT 'LOW',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tickets (
    id INT PRIMARY KEY AUTO_INCREMENT,
    issue_id INT NOT NULL,
    reported_by INT NOT NULL,
    assigned_technician_id INT,
    status ENUM('OPEN','ASSIGNED','IN_PROGRESS','RESOLVED','CLOSED') NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ticket_issue FOREIGN KEY (issue_id) REFERENCES issues(id),
    CONSTRAINT fk_ticket_reporter FOREIGN KEY (reported_by) REFERENCES users(id),
    CONSTRAINT fk_ticket_technician FOREIGN KEY (assigned_technician_id) REFERENCES users(id)
);


CREATE TABLE IF NOT EXISTS ticket_history (
    id INT PRIMARY KEY AUTO_INCREMENT,
    ticket_id INT NOT NULL,
    changed_by INT,
    previous_status VARCHAR(20) NOT NULL,
    new_status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_history_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id),
    CONSTRAINT fk_history_user FOREIGN KEY (changed_by) REFERENCES users(id)
);

CREATE INDEX idx_ticket_history_ticket_created ON ticket_history(ticket_id, created_at);
ALTER TABLE ticket_history MODIFY changed_by INT NULL;

CREATE TABLE IF NOT EXISTS resources (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    type VARCHAR(100) NOT NULL,
    quantity INT NOT NULL DEFAULT 0,
    available BOOLEAN DEFAULT TRUE,
    CONSTRAINT chk_resources_quantity_nonnegative CHECK (quantity >= 0)
);

CREATE TABLE IF NOT EXISTS bookings (
    id INT PRIMARY KEY AUTO_INCREMENT,
    resource_id INT NOT NULL,
    booked_by INT NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    CONSTRAINT chk_bookings_time_order CHECK (start_time < end_time),
    CONSTRAINT fk_booking_resource FOREIGN KEY (resource_id) REFERENCES resources(id),
    CONSTRAINT fk_booking_user FOREIGN KEY (booked_by) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS notifications (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS emergencies (
    id INT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    created_by INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_emergency_creator FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS token_revocations (
    token_hash CHAR(64) PRIMARY KEY,
    expires_at TIMESTAMP NOT NULL,
    INDEX idx_token_revocations_expiry (expires_at)
);

CREATE TABLE IF NOT EXISTS login_attempt_limits (
    key_hash CHAR(64) PRIMARY KEY,
    attempts INT NOT NULL DEFAULT 0,
    window_started_at TIMESTAMP NOT NULL,
    blocked_until TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL,
    INDEX idx_login_attempt_limits_updated (updated_at)
);

CREATE INDEX idx_users_role_name_id ON users(role, name, id);
CREATE INDEX idx_users_role_available_name_id ON users(role, available, name, id);
CREATE INDEX idx_tickets_status ON tickets(status);
CREATE INDEX idx_tickets_priority ON issues(priority);
CREATE INDEX idx_notifications_user_read ON notifications(user_id, is_read);
CREATE INDEX idx_notifications_user_created_id ON notifications(user_id, created_at DESC, id DESC);
CREATE INDEX idx_tickets_reported_by_created ON tickets(reported_by, created_at);
CREATE INDEX idx_tickets_created_id ON tickets(created_at DESC, id DESC);
CREATE INDEX idx_tickets_assignee_status ON tickets(assigned_technician_id, status);
CREATE INDEX idx_bookings_resource_start ON bookings(resource_id, start_time);
CREATE INDEX idx_bookings_user_start ON bookings(booked_by, start_time);
CREATE INDEX idx_bookings_user_start_id ON bookings(booked_by, start_time DESC, id DESC);
CREATE INDEX idx_emergencies_created ON emergencies(created_at DESC, id DESC);
