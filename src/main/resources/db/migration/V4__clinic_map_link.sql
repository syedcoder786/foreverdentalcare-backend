-- Google Maps share link for the clinic, used for the "Get directions" buttons.
ALTER TABLE clinic_settings ADD COLUMN map_link_url VARCHAR(500);

UPDATE clinic_settings SET map_link_url = 'https://maps.app.goo.gl/yZkxW1acwqR6b8X97' WHERE map_link_url IS NULL;
