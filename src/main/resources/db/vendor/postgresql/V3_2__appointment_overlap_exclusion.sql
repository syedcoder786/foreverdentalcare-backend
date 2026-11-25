-- PostgreSQL-only defence in depth: the database itself rejects two overlapping active appointments
-- for the SAME doctor (different doctors may overlap freely). Cancelled appointments are ignored.
-- AppointmentService already prevents overlaps under a doctor row lock; this guarantees it even for writes
-- that bypass the application.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE appointments
    ADD CONSTRAINT ex_appointments_doctor_no_overlap
    EXCLUDE USING gist (
        doctor_id WITH =,
        tsrange(appointment_date + appointment_time, appointment_date + end_time, '[)') WITH &&
    ) WHERE (status <> 'CANCELLED');
