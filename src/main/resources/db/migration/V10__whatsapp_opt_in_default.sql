-- Every patient now receives WhatsApp offers by default (no opt-in checkbox).
ALTER TABLE patients ALTER COLUMN whatsapp_opt_in SET DEFAULT TRUE;
UPDATE patients SET whatsapp_opt_in = TRUE, whatsapp_opt_in_at = COALESCE(whatsapp_opt_in_at, created_at)
WHERE whatsapp_opt_in = FALSE;
