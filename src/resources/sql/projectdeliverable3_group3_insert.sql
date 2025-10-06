INSERT INTO GUEST VALUES
('Y012776', 'Chester Bennington', '333 Olive Drive, Maricopa, CA', 'cbennington@yahoo.com', '661-770-6672', '4000 1093 3910 3478'),
('4820467392', 'Keanu Reeves', '1073 Center Street, Las Vegas, NV', 'keanureeves1@hotmail.com', '803-763-7821', '3820 5830 4720 5738'),
('D-393-584-12-480-4', 'Jennifer Lopez', '482 Old SChool Road, Miami, FL', 'jlo@outlook.com', '805-392-2840', '1930 4804 3820 4820'),
('2917892384014', 'Steve Harvey', '38102 Marisol Avenue, Bozeman, MT', 'surveysays@gmail.com', '390-383-1929', '3180 5830 0485 2084'),
('Y028304', 'Chris Pratt', '211 Stockdale Drive, Stockdale, CA', 'pratt.chris79@icloud.com', '661-805-2880', '4848 2030 6939 2803');

INSERT INTO HOTEL VALUES
('H1', 'Marriot', '40229 Center Street, Los Angeles, CA'),
('H2', 'Best Western', '3830 Main Street, Tempe, AZ'),
('H3', 'Motel 6', '58045 Willow Street, Birmingham, AL'),
('H4', 'Best Western', '3729 Willow Boulevard, Tempe, AZ'),
('H5', 'Marriot', '4802 Willow Street, Los Angeles, AZ'),
('H6', 'Marriot', '32939 Jefferson Street, Las Vegas, NV');

INSERT INTO ROOM VALUES
('R22', 'H1', 3, 'Deluxe'),
('R36', 'H2', 4, 'Suite'),
('R1', 'H3', 1, 'Standard'),
('R2', 'H3', 1, 'Standard'),
('R12', 'H2', 2, 'Deluxe'),
('R15', 'H1', 2, 'Suite'),
('R1', 'H4', 1, 'Standard'),
('R2', 'H4', 1, 'Double'),
('R1', 'H5', 1, 'Deluxe'),
('R2', 'H5', 1, 'Suite'),
('R10', 'H6', 2, 'Deluxe'),
('R12', 'H6', 2, 'Presidential Suite');

INSERT INTO RESERVATION VALUES
('B1', '2025-02-04', '2025-02-08', 125, 'R1', 'H3', '4820467392'),
('B2', '2024-06-13', '2025-06-16', 350, 'R2', 'H3', '4820467392'),
('B3', '2019-10-18', '2019-10-19', 395, 'R22', 'H1', 'D-393-584-12-480-4'),
('B4', '2013-02-12', '2013-02-16', 295, 'R15', 'H1', 'D-393-584-12-480-4'),
('B5', '2021-07-30', '2021-08-05', 270, 'R36', 'H2', 'Y012776'),
('B6', '2022-01-01', '2022-01-03', 150, 'R2', 'H3', '2917892384014'),
('B7', '2021-09-11', '2021-09-15', 125, 'R12', 'H2', 'Y028304');

-- make sure COUNTER table exists
CREATE TABLE IF NOT EXISTS HOTEL_MANAGEMENT.COUNTER (
    name     VARCHAR(50) PRIMARY KEY,
    next_val INT NOT NULL
    );

-- adds reservation row to counter if missing
INSERT INTO COUNTER (name, next_val)
VALUES ('RESERVATION', 1)
ON DUPLICATE KEY UPDATE next_val = next_val;

--needed to keep booking numbers from overlapping
UPDATE HOTEL_MANAGEMENT.COUNTER c
SET c.next_val = (
    SELECT COALESCE(MAX(n), 0) + 1
    FROM (
        SELECT CAST(TRIM(SUBSTRING(BookingID, 2)) AS UNSIGNED) AS n
        FROM HOTEL_MANAGEMENT.RESERVATION
    ) AS t
)
WHERE c.name = 'RESERVATION';