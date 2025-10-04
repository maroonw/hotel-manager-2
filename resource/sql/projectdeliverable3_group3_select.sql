-- Check-ins
SELECT r.BookingID, g.Name AS Guest, r.RoomNumber, r.HotelID
FROM RESERVATION r
JOIN GUEST g ON g.GovernmentID = r.GovernmentID
WHERE r.CheckInDate = '2025-03-10'
ORDER BY r.HotelID, r.RoomNumber;

-- Check-outs
SELECT r.BookingID, g.Name AS Guest, r.RoomNumber, r.HotelID
FROM RESERVATION r
JOIN GUEST g ON g.GovernmentID = r.GovernmentID
WHERE r.CheckOutDate = '2025-03-14'
ORDER BY r.HotelID, r.RoomNumber;

-- cost nights reservation
SELECT r.BookingID,
       g.Name AS Guest,
       r.HotelID, r.RoomNumber,
       r.CheckInDate, r.CheckOutDate,
       DATEDIFF(r.CheckOutDate, r.CheckInDate) AS Nights,
       r.CostPerNight,
       DATEDIFF(r.CheckOutDate, r.CheckInDate) * r.CostPerNight AS TotalCost
FROM RESERVATION r
JOIN GUEST g ON g.GovernmentID = r.GovernmentID
ORDER BY r.CheckInDate DESC;

-- delete reservation
DELETE FROM RESERVATION
WHERE BookingID = @p_BookingID;

-- guest history
SELECT r.BookingID, h.HotelName, h.Address AS HotelAddress,
       r.RoomNumber, r.CheckInDate, r.CheckOutDate,
       DATEDIFF(r.CheckOutDate, r.CheckInDate) AS Nights,
       r.CostPerNight
FROM RESERVATION r
JOIN HOTEL h ON h.HotelID = r.HotelID
WHERE r.GovernmentID = '4820467392'   -- Keanu Reeves (sample data)
ORDER BY r.CheckInDate DESC;

-- insert reservation
START TRANSACTION;

SELECT 1
FROM ROOM r
WHERE r.HotelID = @p_HotelID
  AND r.RoomNumber = @p_RoomNumber
FOR UPDATE;

SELECT 1
FROM RESERVATION x
WHERE x.HotelID = @p_HotelID
  AND x.RoomNumber = @p_RoomNumber
  AND @p_CheckIn  < x.CheckOutDate
  AND x.CheckInDate < @p_CheckOut
FOR UPDATE;

INSERT INTO RESERVATION
  (BookingID, CheckInDate, CheckOutDate, CostPerNight, RoomNumber, HotelID, GovernmentID)
VALUES
  (@p_BookingID, @p_CheckIn, @p_CheckOut, @p_CostPerNight, @p_RoomNumber, @p_HotelID, @p_GovID);

COMMIT;

-- list rooms date
SELECT r.RoomNumber, r.RoomType, r.Floor
FROM ROOM r
WHERE r.HotelID = @p_HotelID
  AND NOT EXISTS (
    SELECT 1
    FROM RESERVATION x
    WHERE x.HotelID   = r.HotelID
      AND x.RoomNumber= r.RoomNumber
      AND @p_CheckIn  < x.CheckOutDate
      AND x.CheckInDate < @p_CheckOut
  )
ORDER BY r.RoomType, r.Floor, r.RoomNumber;

-- read reservation
SELECT r.*, g.Name AS GuestName, h.HotelName, h.Address AS HotelAddress
FROM RESERVATION r
JOIN GUEST g ON g.GovernmentID = r.GovernmentID
JOIN HOTEL h ON h.HotelID = r.HotelID
WHERE r.BookingID = @p_BookingID;

-- rev hotel month
SET @p_Start='2025-02-01';
SET @p_End  ='2025-03-01';

SELECT h.HotelID, h.HotelName,
       SUM(
         GREATEST(0,
           DATEDIFF(
             LEAST(r.CheckOutDate, @p_End),
             GREATEST(r.CheckInDate, @p_Start)
           )
         ) * r.CostPerNight
       ) AS Revenue
FROM HOTEL h
LEFT JOIN RESERVATION r
  ON r.HotelID = h.HotelID
 AND r.CheckInDate < @p_End
 AND r.CheckOutDate > @p_Start
GROUP BY h.HotelID, h.HotelName
ORDER BY Revenue DESC;

-- room utilization hotel month
-- Example: utilization H3, Feb 2025
-- Count reserved nights per room
SET @p_Hotel='H3';
SET @p_Start='2025-02-01';
SET @p_End  ='2025-03-01'; -- exclusive end

SELECT r.RoomNumber,
       SUM(
         GREATEST(
           0,
           DATEDIFF(
             LEAST(x.CheckOutDate, @p_End),
             GREATEST(x.CheckInDate, @p_Start)
           )
         )
       ) AS NightsBooked
FROM ROOM r
LEFT JOIN RESERVATION x
  ON x.HotelID = r.HotelID
 AND x.RoomNumber = r.RoomNumber
 AND x.CheckInDate < @p_End
 AND x.CheckOutDate > @p_Start
WHERE r.HotelID = @p_Hotel
GROUP BY r.RoomNumber
ORDER BY r.RoomNumber;

-- update reservation
START TRANSACTION;

SELECT @room := RoomNumber, @hotel := HotelID
FROM RESERVATION
WHERE BookingID = @p_BookingID
FOR UPDATE;

SELECT 1
FROM RESERVATION x
WHERE x.HotelID = @hotel
  AND x.RoomNumber = @room
  AND x.BookingID <> @p_BookingID
  AND @p_NewIn  < x.CheckOutDate
  AND x.CheckInDate < @p_NewOut
FOR UPDATE;

UPDATE RESERVATION
SET CheckInDate = @p_NewIn,
    CheckOutDate = @p_NewOut
WHERE BookingID = @p_BookingID;

COMMIT;