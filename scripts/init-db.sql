-- ============================================
-- Database Initialization for Movie Ticket Booking System
-- MICROSERVICE PATTERN: Each service has its OWN database
-- ============================================

-- ============================================
-- BOOKING SERVICE DATABASE (booking_db)
-- ============================================
CREATE DATABASE IF NOT EXISTS booking_db;
USE booking_db;

CREATE TABLE IF NOT EXISTS bookings (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    movie_id VARCHAR(36) NOT NULL,
    showtime_id VARCHAR(36) NOT NULL,
    seat_ids JSON NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    status ENUM('PENDING', 'SEATS_RESERVED', 'PAYMENT_PROCESSED', 'CONFIRMED', 'CANCELLED') DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS outbox (
    id VARCHAR(36) PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload JSON NOT NULL,
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
    processed BOOLEAN DEFAULT FALSE,
    INDEX idx_aggregate (aggregate_type, aggregate_id),
    INDEX idx_event_type (event_type),
    INDEX idx_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS processed_events (
    id VARCHAR(36) PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    event_type VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_event_id (event_id)
);

-- ============================================
-- SEAT SERVICE DATABASE (seat_db)
-- ============================================
CREATE DATABASE IF NOT EXISTS seat_db;
USE seat_db;

CREATE TABLE IF NOT EXISTS seats (
    id VARCHAR(36) PRIMARY KEY,
    showtime_id VARCHAR(36) NOT NULL,
    seat_number VARCHAR(10) NOT NULL,
    seat_row VARCHAR(5) NOT NULL,
    status ENUM('AVAILABLE', 'HELD', 'BOOKED') DEFAULT 'AVAILABLE',
    booking_id VARCHAR(36) NULL,
    expire_at DATETIME NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_showtime_seat (showtime_id, seat_number),
    INDEX idx_showtime (showtime_id),
    INDEX idx_status (status),
    INDEX idx_expire_at (expire_at)
);

CREATE TABLE IF NOT EXISTS outbox (
    id VARCHAR(36) PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload JSON NOT NULL,
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
    processed BOOLEAN DEFAULT FALSE,
    INDEX idx_aggregate (aggregate_type, aggregate_id),
    INDEX idx_event_type (event_type),
    INDEX idx_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS processed_events (
    id VARCHAR(36) PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    event_type VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_event_id (event_id)
);

-- ============================================
-- MOVIE SERVICE DATABASE (movie_db)
-- ============================================
CREATE DATABASE IF NOT EXISTS movie_db;
USE movie_db;

CREATE TABLE IF NOT EXISTS movies (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    genre VARCHAR(100),
    duration INT NOT NULL,
    poster_url VARCHAR(500),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS showtimes (
    id VARCHAR(36) PRIMARY KEY,
    movie_id VARCHAR(36) NOT NULL,
    hall VARCHAR(50) NOT NULL,
    start_time DATETIME NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (movie_id) REFERENCES movies(id),
    INDEX idx_movie_id (movie_id),
    INDEX idx_start_time (start_time)
);

CREATE TABLE IF NOT EXISTS outbox (
    id VARCHAR(36) PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload JSON NOT NULL,
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
    processed BOOLEAN DEFAULT FALSE,
    INDEX idx_aggregate (aggregate_type, aggregate_id),
    INDEX idx_event_type (event_type),
    INDEX idx_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS processed_events (
    id VARCHAR(36) PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    event_type VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_event_id (event_id)
);

-- ============================================
-- AUTH SERVICE DATABASE (auth_db)
-- ============================================
CREATE DATABASE IF NOT EXISTS auth_db;
USE auth_db;

CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NULL,
    role ENUM('USER', 'ADMIN') DEFAULT 'USER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_email (email)
);

-- ============================================
-- PAYMENT SERVICE DATABASE (payment_db)
-- ============================================
CREATE DATABASE IF NOT EXISTS payment_db;
USE payment_db;

CREATE TABLE IF NOT EXISTS wallets (
    user_id VARCHAR(36) PRIMARY KEY,
    balance DECIMAL(12,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS payments (
    id VARCHAR(36) PRIMARY KEY,
    booking_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    status ENUM('PENDING', 'PROCESSED', 'FAILED', 'REFUNDED') DEFAULT 'PENDING',
    failure_reason VARCHAR(255) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_booking_id (booking_id),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status)
);

CREATE TABLE IF NOT EXISTS topups (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    order_code BIGINT NOT NULL UNIQUE,
    amount DECIMAL(12,2) NOT NULL,
    status ENUM('PENDING', 'PAID', 'CANCELLED', 'EXPIRED', 'FAILED') DEFAULT 'PENDING',
    checkout_url VARCHAR(500) NULL,
    payment_link_id VARCHAR(255) NULL,
    transaction_reference VARCHAR(255) NULL,
    counter_account_bank_name VARCHAR(255) NULL,
    counter_account_name VARCHAR(255) NULL,
    counter_account_number VARCHAR(255) NULL,
    paid_at TIMESTAMP NULL,
    description VARCHAR(255) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_order_code (order_code)
);

CREATE TABLE IF NOT EXISTS outbox (
    id VARCHAR(36) PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload JSON NOT NULL,
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
    processed BOOLEAN DEFAULT FALSE,
    INDEX idx_aggregate (aggregate_type, aggregate_id),
    INDEX idx_event_type (event_type),
    INDEX idx_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS processed_events (
    id VARCHAR(36) PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    event_type VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_event_id (event_id)
);

-- ============================================
-- AI RECOMMENDER SERVICE DATABASE (ai_db)
-- ============================================
CREATE DATABASE IF NOT EXISTS ai_db;
USE ai_db;

CREATE TABLE IF NOT EXISTS movie_embeddings (
    movie_id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    genres JSON NOT NULL,
    embedding JSON NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_updated_at (updated_at)
);

CREATE TABLE IF NOT EXISTS user_behavior (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    movie_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_movie_id (movie_id),
    INDEX idx_user_movie (user_id, movie_id)
);

CREATE TABLE IF NOT EXISTS processed_events (
    id VARCHAR(36) PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    event_type VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_event_id (event_id)
);

-- ============================================
-- Grant permissions for Debezium CDC
-- ============================================
GRANT SELECT, RELOAD, SHOW DATABASES, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'root'@'%';
FLUSH PRIVILEGES;
