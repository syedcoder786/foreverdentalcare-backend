-- Replace the seeded placeholder address with the clinic's real address (only if it was never edited).
UPDATE clinic_settings
SET address = 'Forever Dental Care (Dr. S. M. Akif), Mohalla Rajdwara Road, Miston Ganj, Rampur, Uttar Pradesh 244901'
WHERE address LIKE '[PLACEHOLDER]%';
