-- "Get directions" should open Google Maps route planning, not just the place page.
-- Destination = the clinic's plus code (R259+5H Rampur), which pins the exact location.
UPDATE clinic_settings
SET map_link_url = 'https://www.google.com/maps/dir/?api=1&destination=R259%2B5H%20Rampur%2C%20Uttar%20Pradesh'
WHERE map_link_url IS NULL
   OR map_link_url = 'https://www.google.com/maps?cid=10761615374267279563'
   OR map_link_url LIKE '%goo.gl/%';
