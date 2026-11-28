-- The consultation fee comes from each treatment / doctor (already stored there), so the clinic-wide fee added in V12
-- is dropped. The first-visit discount stays clinic-wide (clinic_settings.first_visit_discount_percent).
ALTER TABLE clinic_settings DROP CONSTRAINT IF EXISTS ck_settings_consultation_fee;
ALTER TABLE clinic_settings DROP COLUMN IF EXISTS consultation_fee;

-- Snapshot of the fee and the first-visit discount on each appointment, so the clinic sees what the patient was shown.
--   consultation_fee              treatment fee, else the doctor's fee, at booking time (NULL = discussed at consultation)
--   first_visit_discount_percent  set when this appointment carries the online first-visit discount
--   online_booking                booked by the patient on the website (only these can carry the discount)
ALTER TABLE appointments
    ADD COLUMN consultation_fee             NUMERIC(10, 2),
    ADD COLUMN first_visit_discount_percent INTEGER,
    ADD COLUMN online_booking               BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_appointments_discount CHECK (first_visit_discount_percent IS NULL OR first_visit_discount_percent BETWEEN 1 AND 100);
