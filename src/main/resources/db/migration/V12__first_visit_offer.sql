-- Consultation fee and a "first visit" percentage discount on that fee, managed in Admin -> Clinic settings.
-- The offer is shown on the website until a patient's first appointment is confirmed (and again if it is cancelled).
ALTER TABLE clinic_settings
    ADD COLUMN consultation_fee              INTEGER,
    ADD COLUMN first_visit_discount_percent  INTEGER,
    ADD COLUMN first_visit_offer_enabled     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_settings_consultation_fee CHECK (consultation_fee IS NULL OR consultation_fee BETWEEN 0 AND 1000000),
    ADD CONSTRAINT ck_settings_first_visit_discount CHECK (first_visit_discount_percent IS NULL OR first_visit_discount_percent BETWEEN 1 AND 100);
