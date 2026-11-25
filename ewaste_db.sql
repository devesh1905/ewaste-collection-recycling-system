-- ============================================================
-- E-Waste Collection & Recycling Management System
-- MySQL 8 script: schema + sample data + sample queries
-- Run:  mysql -uewaste -pewaste123 < ewaste_db.sql
-- (Passwords are stored as plain text - known limitation, future work = hashing)
-- ============================================================
CREATE DATABASE IF NOT EXISTS ewaste_db;
USE ewaste_db;

-- drop children first so the script can be re-run
DROP TABLE IF EXISTS recycling_records;
DROP TABLE IF EXISTS pickup_requests;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    user_id   INT PRIMARY KEY AUTO_INCREMENT,
    full_name VARCHAR(100) NOT NULL,
    username  VARCHAR(50)  NOT NULL UNIQUE,
    password  VARCHAR(100) NOT NULL,
    phone     VARCHAR(15),
    role      ENUM('ADMIN','USER') NOT NULL DEFAULT 'USER'
);

CREATE TABLE pickup_requests (
    request_id  INT PRIMARY KEY AUTO_INCREMENT,
    user_id     INT NOT NULL,
    item_name   VARCHAR(100) NOT NULL,
    category    VARCHAR(30)  NOT NULL,
    quantity    INT NOT NULL CHECK (quantity > 0),
    address     VARCHAR(255) NOT NULL,
    pickup_date DATE NOT NULL,
    status      ENUM('PENDING','APPROVED','COLLECTED','RECYCLED','REJECTED') NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE recycling_records (
    recycle_id        INT PRIMARY KEY AUTO_INCREMENT,
    request_id        INT NOT NULL UNIQUE,
    recycled_quantity INT NOT NULL,
    recycling_method  VARCHAR(100) NOT NULL,
    recycling_date    DATE NOT NULL,
    remarks           VARCHAR(255),
    FOREIGN KEY (request_id) REFERENCES pickup_requests(request_id)
);

-- ---------------- sample data ----------------
INSERT INTO users (full_name, username, password, phone, role) VALUES
('System Admin',      'admin',     'admin123',  '9000000001', 'ADMIN'),
('K S Sree Vishal',   'vishal',    'vishal123', '9000000002', 'USER'),
('Deveshwar',         'deveshwar', 'dev123',    '9000000003', 'USER');

INSERT INTO pickup_requests (user_id, item_name, category, quantity, address, pickup_date, status) VALUES
(2, 'Old Samsung phones',   'Mobile',     2, '12 Anna Nagar, Chennai',   CURDATE() + INTERVAL 3 DAY,  'PENDING'),
(2, 'Dell Inspiron laptop', 'Laptop',     1, '12 Anna Nagar, Chennai',   CURDATE() + INTERVAL 5 DAY,  'APPROVED'),
(2, 'Laptop battery packs', 'Battery',    3, '12 Anna Nagar, Chennai',   CURDATE() + INTERVAL 1 DAY,  'COLLECTED'),
(2, 'Mobile chargers',      'Charger',    4, '12 Anna Nagar, Chennai',   CURDATE() - INTERVAL 10 DAY, 'RECYCLED'),
(3, 'LG CRT television',    'Television', 1, '8 Thandalam, Chennai',     CURDATE() + INTERVAL 4 DAY,  'PENDING'),
(3, 'Redmi phones',         'Mobile',     3, '8 Thandalam, Chennai',     CURDATE() - INTERVAL 6 DAY,  'RECYCLED'),
(3, 'Wi-Fi router and cables', 'Other',   2, '8 Thandalam, Chennai',     CURDATE() + INTERVAL 2 DAY,  'REJECTED'),
(3, 'HP laptop',            'Laptop',     1, '8 Thandalam, Chennai',     CURDATE() + INTERVAL 6 DAY,  'APPROVED');

INSERT INTO recycling_records (request_id, recycled_quantity, recycling_method, recycling_date, remarks) VALUES
(4, 4, 'Copper extraction and plastic shredding',      CURDATE() - INTERVAL 8 DAY, 'All chargers recycled'),
(6, 3, 'Component refurbishing and metal recovery',    CURDATE() - INTERVAL 4 DAY, 'Two phones refurbished, one shredded');

-- ---------------- sample queries (for viva) ----------------
-- 1. JOIN: every request with the user's name
SELECT r.request_id, u.full_name, r.item_name, r.category, r.quantity, r.status
FROM pickup_requests r JOIN users u ON r.user_id = u.user_id
ORDER BY r.request_id;

-- 2. GROUP BY: number of requests per status (used by the dashboard summary cards)
SELECT status, COUNT(*) AS total FROM pickup_requests GROUP BY status;

-- 3. GROUP BY + JOIN: requests per user
SELECT u.username, COUNT(*) AS total_requests
FROM users u JOIN pickup_requests r ON r.user_id = u.user_id
GROUP BY u.username;

-- 4. JOIN of three tables: recycling details
SELECT rr.recycle_id, u.username, r.item_name, rr.recycled_quantity, rr.recycling_method, rr.recycling_date
FROM recycling_records rr
JOIN pickup_requests r ON rr.request_id = r.request_id
JOIN users u ON r.user_id = u.user_id;

-- 5. Total items recycled
SELECT SUM(recycled_quantity) AS items_recycled FROM recycling_records;
