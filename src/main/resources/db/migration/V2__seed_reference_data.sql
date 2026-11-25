-- PLACEHOLDER clinic information. Replace via the admin dashboard (Settings) before going live.
INSERT INTO clinic_settings (id, clinic_name, address, phone, whatsapp_number, email, map_embed_url,
                             opening_time, closing_time, break_start_time, break_end_time,
                             working_days, slot_duration_minutes, booking_window_days, updated_at)
VALUES (1, 'Forever Dental Care', '[PLACEHOLDER] Clinic street address, City, State, PIN',
        '+910000000000', '+910000000000', 'contact@example.com', NULL,
        '10:00:00', '19:00:00', '13:30:00', '14:30:00',
        'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY', 30, 60, CURRENT_TIMESTAMP);

-- Treatment catalogue. Fees are intentionally NULL ("discussed at consultation") until the clinic sets them.
-- `details` holds pipe-separated key points shown on the website.
INSERT INTO services (name, description, details, icon, consultation_fee, estimated_duration_minutes, display_order, active, created_at, updated_at) VALUES
('General Dental Consultation',
 'A thorough check-up of your teeth and gums, a discussion of any concerns, and a personalised treatment plan if care is needed.',
 'Review of dental and medical history|Examination of teeth, gums and bite|X-rays only if clinically required|Clear explanation of options and costs before any treatment',
 'stethoscope', NULL, 30, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Teeth Cleaning',
 'Professional scaling and polishing to remove plaque and tartar that regular brushing cannot reach.',
 'Removal of plaque and tartar build-up|Polishing of tooth surfaces|Home-care advice for brushing and flossing|Interval between cleanings advised by your dentist',
 'sparkles', NULL, 45, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Root Canal Treatment',
 'Treatment to clean and seal the inside of a tooth when its pulp is infected or inflamed, aiming to save the natural tooth.',
 'Performed under local anaesthesia|May require more than one visit|A crown may be advised afterwards to protect the tooth|Suitability is assessed after examination and X-rays',
 'activity', NULL, 60, 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Dental Implants',
 'A titanium post placed in the jaw to support a replacement tooth, as one option for replacing missing teeth.',
 'Detailed assessment of bone and gum health first|Treatment is carried out in stages over several months|Not every patient is a suitable candidate|Alternatives such as bridges or dentures will be discussed',
 'anchor', NULL, 60, 4, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Teeth Whitening',
 'Professionally supervised whitening to lighten natural tooth shade. Results vary from person to person.',
 'Assessment to check whether whitening is suitable for you|Does not change the colour of crowns or fillings|Temporary sensitivity can occur|Results vary and are not permanent',
 'sun', NULL, 60, 5, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Braces and Aligners',
 'Orthodontic options, including fixed braces and clear aligners, to help straighten teeth and improve bite alignment.',
 'Initial orthodontic assessment and records|Choice between braces and aligners where suitable|Treatment length depends on individual needs|Regular review visits are required',
 'smile', NULL, 45, 6, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Tooth Extraction',
 'Removal of a tooth that cannot be saved or that is causing problems, including wisdom teeth where appropriate.',
 'Performed under local anaesthesia|Post-extraction care instructions provided|Replacement options can be discussed|Complex cases may require referral',
 'scissors', NULL, 45, 7, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Dental Crowns',
 'A custom-made cap that covers a damaged or weakened tooth to restore its shape and function.',
 'Tooth preparation and impressions|Temporary crown while the final crown is made|Material options discussed with you|Usually completed over two visits',
 'crown', NULL, 60, 8, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
