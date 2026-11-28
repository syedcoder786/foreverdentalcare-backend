-- Time-limited percentage offers on a treatment's fee, managed in Admin -> Services.
-- The offer covers appointments whose date falls within [offer_start_date, offer_end_date] (clinic-local dates).
ALTER TABLE services
    ADD COLUMN offer_discount_percent INTEGER,
    ADD COLUMN offer_start_date       DATE,
    ADD COLUMN offer_end_date         DATE,
    ADD CONSTRAINT ck_services_offer CHECK (
        (offer_discount_percent IS NULL AND offer_start_date IS NULL AND offer_end_date IS NULL)
        OR (offer_discount_percent BETWEEN 1 AND 100
            AND offer_start_date IS NOT NULL AND offer_end_date IS NOT NULL
            AND offer_end_date >= offer_start_date));

-- Appointments keep the consultation fee (doctor) and the treatment fee separately, each with its own discount:
--   consultation_fee + first_visit_discount_percent  (online first visit)
--   treatment_fee    + treatment_discount_percent    (treatment offer covering the appointment date)
ALTER TABLE appointments
    ADD COLUMN treatment_fee              NUMERIC(10, 2),
    ADD COLUMN treatment_discount_percent INTEGER,
    ADD CONSTRAINT ck_appointments_treatment_discount CHECK (
        treatment_discount_percent IS NULL OR treatment_discount_percent BETWEEN 1 AND 100);
