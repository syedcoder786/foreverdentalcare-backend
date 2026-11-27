-- When a patient refresh token was replaced by a newer one. A replaced token presented again within a short grace
-- period is a concurrent refresh (two tabs, a double request), not theft, and must not sign the patient out.
ALTER TABLE patient_refresh_tokens ADD COLUMN rotated_at TIMESTAMP WITH TIME ZONE;
