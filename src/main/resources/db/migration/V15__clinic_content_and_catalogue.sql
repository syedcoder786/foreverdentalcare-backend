-- =====================================================================================================
-- V15: real clinic content. Replaces every seeded placeholder with the clinic's own data:
--   * Dr. S. M. Akif (dentist, BDS) - main practitioner; the website is dental-first.
--   * Dr. Faiza Khan (BUMS) - Unani consultations for WOMEN ONLY, evening shift only.
--   * Full dental treatment catalogue with Rampur-level prices, plus a short Unani list for women.
--   * Running percentage offers on selected treatments.
--   * Website copy (home, about, doctors, treatments, contact, booking) and SEO text.
-- Written to work both on the live database (which has the seeded rows plus a few admin edits) and on a fresh one.
-- Portable SQL (PostgreSQL primary, H2 PostgreSQL-mode for tests).
-- =====================================================================================================

-- ---------- Specialties ----------
UPDATE specialties
SET name = 'Dentistry',
    description = 'Complete dental care for the whole family: check-ups, cleaning, fillings, root canals, extractions, crowns, implants, dentures, braces and teeth whitening, with Dr. S. M. Akif.',
    icon = 'tooth', display_order = 1, active = TRUE, updated_at = CURRENT_TIMESTAMP
WHERE slug = 'dentistry';

UPDATE specialties
SET name = 'Unani Medicine',
    description = 'Unani consultations for women only, with Dr. Faiza Khan (BUMS). Available in the evening shift, Monday to Saturday.',
    icon = 'female', display_order = 2, active = TRUE, updated_at = CURRENT_TIMESTAMP
WHERE slug = 'unani-medicine';

-- ---------- Dr. S. M. Akif (the seeded dentist, slug kept so existing links keep working) ----------
UPDATE doctors
SET full_name = 'Dr. S. M. Akif',
    professional_title = 'Dentist (BDS)',
    gender = 'MALE',
    short_bio = 'Dr. S. M. Akif leads Forever Dental Care and looks after the whole family''s teeth, from routine check-ups and fillings to root canals, extractions, crowns and smile makeovers.',
    biography = 'Dr. S. M. Akif is the dentist behind Forever Dental Care in Miston Ganj, Rampur. He believes good dentistry starts with a proper conversation: every visit begins with a careful check-up, and the options, number of sittings and the cost are explained before any treatment starts.

He treats patients of every age, from a child''s first dental visit to elderly patients who need dentures. His day-to-day work covers fillings, painless-as-possible root canal treatment under local anaesthesia, extractions including wisdom teeth, crowns and bridges, implants, braces and teeth whitening.

Instruments are cleaned and sterilised for every patient, and treatment is carried out at a pace that keeps you comfortable.',
    qualifications = '[{"degree":"BDS - Bachelor of Dental Surgery","institution":"","year":""}]',
    credentials_verified = TRUE,
    consultation_fee = 200.00,
    display_order = 1, active = TRUE, featured = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE slug = 'dentist';

-- ---------- Dr. Faiza Khan (BUMS): rename the admin-created doctor if present, otherwise create her ----------
UPDATE doctors
SET full_name = 'Dr. Faiza Khan', slug = 'dr-faiza-khan', updated_at = CURRENT_TIMESTAMP
WHERE slug = 'dr-doctor-2';

INSERT INTO doctors (full_name, slug, professional_title, gender, qualifications, credentials_verified,
                     display_order, active, featured, version, created_at, updated_at)
SELECT 'Dr. Faiza Khan', 'dr-faiza-khan', 'Unani Physician (BUMS)', 'FEMALE', '[]', TRUE,
       2, TRUE, TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM clinic_settings
WHERE id = 1 AND NOT EXISTS (SELECT 1 FROM doctors WHERE slug = 'dr-faiza-khan');

UPDATE doctors
SET professional_title = 'Unani Physician (BUMS)',
    gender = 'FEMALE',
    short_bio = 'Dr. Faiza Khan is a Unani physician (BUMS) who consults women only, in the evenings. She listens carefully and suggests Unani care, diet and lifestyle advice suited to each patient.',
    biography = 'Dr. Faiza Khan holds a BUMS (Bachelor of Unani Medicine and Surgery) degree and offers Unani consultations at Forever Dental Care for women only, so female patients can speak openly and comfortably with a female doctor.

Her consultations focus on understanding your health history, daily routine and diet, and then suggesting Unani treatment and regimen-based therapy (Ilaj bil Tadbeer) such as Hijama where it is suitable. She works within her registered scope of practice and will advise you to see a specialist whenever that is the right step.

She is available in the evening shift, Monday to Saturday.',
    qualifications = '[{"degree":"BUMS - Bachelor of Unani Medicine and Surgery","institution":"","year":""}]',
    credentials_verified = TRUE,
    consultation_fee = 150.00,
    display_order = 2, active = TRUE, featured = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE slug = 'dr-faiza-khan';

INSERT INTO doctor_specialties (doctor_id, specialty_id)
SELECT d.id, s.id FROM doctors d, specialties s
WHERE d.slug = 'dr-faiza-khan' AND s.slug = 'unani-medicine'
  AND NOT EXISTS (SELECT 1 FROM doctor_specialties x WHERE x.doctor_id = d.id AND x.specialty_id = s.id);

-- Evening shift only: Monday-Saturday 17:00-21:00, Sunday off.
INSERT INTO doctor_availability (doctor_id, day_of_week, start_time, end_time, break_start_time, break_end_time,
                                 slot_duration_minutes, active)
SELECT d.id, x.dow, '17:00:00', '21:00:00', NULL, NULL, 30, TRUE
FROM doctors d
     CROSS JOIN (VALUES ('MONDAY'), ('TUESDAY'), ('WEDNESDAY'), ('THURSDAY'), ('FRIDAY'), ('SATURDAY'), ('SUNDAY')) AS x(dow)
WHERE d.slug = 'dr-faiza-khan'
  AND NOT EXISTS (SELECT 1 FROM doctor_availability a WHERE a.doctor_id = d.id AND a.day_of_week = x.dow);

UPDATE doctor_availability
SET start_time = '17:00:00', end_time = '21:00:00', break_start_time = NULL, break_end_time = NULL,
    slot_duration_minutes = 30,
    active = CASE WHEN day_of_week = 'SUNDAY' THEN FALSE ELSE TRUE END
WHERE doctor_id = (SELECT id FROM doctors WHERE slug = 'dr-faiza-khan');

-- ---------- Dental treatments (existing rows keep their slug; prices in INR, Rampur level) ----------
-- consultation_fee on a service is the TREATMENT fee, charged on top of the doctor's consultation fee.
-- 0 = nothing extra beyond the consultation. Prices are "starting from" and are confirmed after examination.
UPDATE services SET
    description = 'A complete check-up of your teeth and gums with Dr. Akif. We find the cause of pain or sensitivity and give you a clear treatment plan with costs before anything is done.',
    details = 'Examination of teeth, gums and bite|Diagnosis of pain, sensitivity, cavities and bleeding gums|X-ray advised only when needed|Written treatment plan with cost before you decide',
    icon = 'stethoscope', consultation_fee = 0, estimated_duration_minutes = 30, display_order = 1, active = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE slug = 'general-dental-consultation';

UPDATE services SET
    description = 'Ultrasonic scaling and polishing that removes tartar, stains and plaque, helping stop bleeding gums and bad breath.',
    details = 'Ultrasonic scaling of both jaws|Stain removal and polishing|Advice on brushing and flossing|Recommended every 6 months',
    icon = 'sparkles', consultation_fee = 800, estimated_duration_minutes = 45, display_order = 2, active = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE slug = 'teeth-cleaning';

UPDATE services SET
    description = 'Single or multi-sitting root canal treatment under local anaesthesia to remove infection, stop the pain and save your natural tooth.',
    details = 'Done under local anaesthesia|Price is per tooth (front teeth to molars)|Usually completed in 1 to 3 sittings|A crown is advised afterwards to protect the tooth',
    icon = 'toothbroken', consultation_fee = 3000, estimated_duration_minutes = 60, display_order = 4, active = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE slug = 'root-canal-treatment';

UPDATE services SET
    description = 'Custom-made caps that restore the shape, strength and look of a broken, worn or root-canal-treated tooth.',
    details = 'Metal-ceramic (PFM) crown from the listed price|Zirconia and full-ceramic crowns available at extra cost|Bridges to replace a missing tooth|Usually completed in 2 visits',
    icon = 'crown', consultation_fee = 3500, estimated_duration_minutes = 60, display_order = 5, active = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE slug = 'dental-crowns';

UPDATE services SET
    description = 'Gentle removal of a tooth that is badly decayed, loose or cannot be saved, under local anaesthesia.',
    details = 'Simple extraction under local anaesthesia|Price is per tooth|Written aftercare instructions|Options to replace the tooth are explained',
    icon = 'scissors', consultation_fee = 500, estimated_duration_minutes = 45, display_order = 6, active = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE slug = 'tooth-extraction';

UPDATE services SET
    description = 'Fixed replacement for missing teeth: a titanium implant topped with a natural-looking crown that feels and works like your own tooth.',
    details = 'Price is per implant, crown included|Bone and gum check before planning|Completed in stages over 3 to 6 months|Bridges or dentures discussed as alternatives',
    icon = 'anchor', consultation_fee = 25000, estimated_duration_minutes = 60, display_order = 8, active = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE slug = 'dental-implants';

UPDATE services SET
    description = 'Straighten crooked, crowded or gapped teeth with fixed metal or ceramic braces, or nearly invisible clear aligners.',
    details = 'Metal braces from the listed price (full treatment)|Ceramic braces and clear aligners at extra cost|Monthly adjustment visits included|Treatment usually takes 12 to 24 months',
    icon = 'smile', consultation_fee = 28000, estimated_duration_minutes = 45, display_order = 10, active = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE slug = 'braces-and-aligners';

UPDATE services SET
    description = 'In-clinic professional whitening that lightens your natural tooth shade by several shades in a single sitting.',
    details = 'Single in-clinic sitting|Cleaning first for the best result|Does not change the colour of crowns or fillings|Popular before weddings and functions',
    icon = 'sun', consultation_fee = 4500, estimated_duration_minutes = 60, display_order = 11, active = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE slug = 'teeth-whitening';

INSERT INTO services (name, slug, description, details, icon, consultation_fee, estimated_duration_minutes,
                      display_order, active, specialty_id, created_at, updated_at)
SELECT v.name, v.slug, v.description, v.details, v.icon, v.fee, v.duration, v.ord, TRUE, sp.id,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('Tooth-Coloured Fillings', 'tooth-coloured-fillings',
     'Natural-looking composite fillings that repair cavities and chipped teeth and blend in with the colour of your teeth.',
     'Price is per tooth|Matched to your tooth shade|Completed in a single visit|Old silver fillings can be replaced',
     'tooth', 700, 30, 3),
    ('Wisdom Tooth Removal', 'wisdom-tooth-removal',
     'Surgical removal of a painful, impacted or partly erupted wisdom tooth under local anaesthesia.',
     'X-ray to plan the removal|Done under local anaesthesia|Stitches and aftercare included|Follow-up check after a week',
     'syringe', 3000, 60, 7),
    ('Dentures', 'dentures',
     'Comfortable removable partial and complete dentures that bring back your smile and your ability to chew properly.',
     'Partial dentures from the listed price|Complete upper and lower sets available|Measured and fitted over 3 to 4 visits|Adjustments after fitting included',
     'dentures', 8000, 45, 9),
    ('Kids'' Dental Care', 'kids-dental-care',
     'Friendly dental care for children: check-ups, fluoride application, pit and fissure sealants and treatment of milk teeth.',
     'Gentle, child-friendly approach|Fluoride and sealants to prevent cavities|Treatment of decayed milk teeth|Advice for parents on brushing and diet',
     'baby', 300, 30, 12),
    ('Gum Treatment', 'gum-treatment',
     'Deep cleaning and care for bleeding, swollen or receding gums, to keep your teeth firm and healthy.',
     'Deep cleaning below the gum line|Treatment of bleeding and swollen gums|Price is per jaw|Review visit to check healing',
     'activity', 1500, 45, 13)
) AS v(name, slug, description, details, icon, fee, duration, ord)
JOIN specialties sp ON sp.slug = 'dentistry'
WHERE NOT EXISTS (SELECT 1 FROM services s WHERE s.slug = v.slug);

-- ---------- Unani treatments (women only, Dr. Faiza Khan) ----------
-- The admin-created "test" service already has appointments, so it becomes the Unani consultation.
UPDATE services SET name = 'Unani Consultation for Women', slug = 'unani-consultation-women', updated_at = CURRENT_TIMESTAMP
WHERE slug = 'test';

INSERT INTO services (name, slug, description, details, icon, consultation_fee, estimated_duration_minutes,
                      display_order, active, specialty_id, created_at, updated_at)
SELECT v.name, v.slug, v.description, v.details, v.icon, v.fee, v.duration, v.ord, TRUE, sp.id,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('Unani Consultation for Women', 'unani-consultation-women', '', '', 'female', 0, 30, 21),
    ('Hijama (Cupping) Therapy', 'hijama-cupping-therapy', '', '', 'droplets', 600, 45, 22),
    ('Unani Skin & Hair Care', 'unani-skin-hair-care', '', '', 'leaf', 300, 30, 23)
) AS v(name, slug, description, details, icon, fee, duration, ord)
JOIN specialties sp ON sp.slug = 'unani-medicine'
WHERE NOT EXISTS (SELECT 1 FROM services s WHERE s.slug = v.slug);

UPDATE services SET
    description = 'A private consultation with Dr. Faiza Khan for women''s health concerns, understood and treated the Unani way.',
    details = 'For women only, with a female doctor|Detailed history, routine and diet review|Unani medicines and regimen advised as needed|Medicines are charged separately',
    icon = 'female', consultation_fee = 0, estimated_duration_minutes = 30, display_order = 21, active = TRUE,
    specialty_id = (SELECT id FROM specialties WHERE slug = 'unani-medicine'), updated_at = CURRENT_TIMESTAMP
WHERE slug = 'unani-consultation-women';

UPDATE services SET
    description = 'Traditional Unani cupping therapy (Hijama) performed by a female doctor in privacy, using sterile, single-use disposables.',
    details = 'For women only, by a female doctor|Sterile, single-use disposables|Suitability checked before the session|Aftercare advice provided',
    icon = 'droplets', consultation_fee = 600, estimated_duration_minutes = 45, display_order = 22, active = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE slug = 'hijama-cupping-therapy';

UPDATE services SET
    description = 'Unani care for common skin and hair concerns such as acne, pigmentation, dandruff and hair fall, with diet and routine advice.',
    details = 'For women only|Herbal and regimen-based care|Diet and lifestyle guidance|Medicines are charged separately',
    icon = 'leaf', consultation_fee = 300, estimated_duration_minutes = 30, display_order = 23, active = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE slug = 'unani-skin-hair-care';

-- ---------- Who provides what ----------
INSERT INTO doctor_services (doctor_id, service_id)
SELECT d.id, s.id
FROM doctors d
     JOIN services s ON s.specialty_id = (SELECT id FROM specialties WHERE slug = 'dentistry')
WHERE d.slug = 'dentist'
  AND NOT EXISTS (SELECT 1 FROM doctor_services x WHERE x.doctor_id = d.id AND x.service_id = s.id);

INSERT INTO doctor_services (doctor_id, service_id)
SELECT d.id, s.id
FROM doctors d
     JOIN services s ON s.specialty_id = (SELECT id FROM specialties WHERE slug = 'unani-medicine')
WHERE d.slug = 'dr-faiza-khan'
  AND s.slug IN ('unani-consultation-women', 'hijama-cupping-therapy', 'unani-skin-hair-care')
  AND NOT EXISTS (SELECT 1 FROM doctor_services x WHERE x.doctor_id = d.id AND x.service_id = s.id);

-- ---------- Offers (percentage off the treatment fee, for appointments dated inside the window) ----------
UPDATE services SET offer_discount_percent = NULL, offer_start_date = NULL, offer_end_date = NULL
WHERE slug IN ('general-dental-consultation', 'tooth-coloured-fillings', 'dental-crowns', 'tooth-extraction',
               'wisdom-tooth-removal', 'dentures', 'gum-treatment',
               'unani-consultation-women', 'unani-skin-hair-care');

UPDATE services SET offer_discount_percent = 25, offer_start_date = '2026-11-28', offer_end_date = '2027-01-15' WHERE slug = 'teeth-cleaning';
UPDATE services SET offer_discount_percent = 20, offer_start_date = '2026-11-28', offer_end_date = '2026-12-31' WHERE slug = 'root-canal-treatment';
UPDATE services SET offer_discount_percent = 30, offer_start_date = '2026-11-28', offer_end_date = '2027-02-28' WHERE slug = 'teeth-whitening';
UPDATE services SET offer_discount_percent = 15, offer_start_date = '2026-11-28', offer_end_date = '2027-01-31' WHERE slug = 'braces-and-aligners';
UPDATE services SET offer_discount_percent = 20, offer_start_date = '2026-11-28', offer_end_date = '2027-01-31' WHERE slug = 'kids-dental-care';
UPDATE services SET offer_discount_percent = 10, offer_start_date = '2026-12-01', offer_end_date = '2027-01-31' WHERE slug = 'dental-implants';
UPDATE services SET offer_discount_percent = 15, offer_start_date = '2026-11-28', offer_end_date = '2026-12-31' WHERE slug = 'hijama-cupping-therapy';

-- ---------- Clinic branding ----------
UPDATE clinic_settings
SET phone = '+919457014679',
    whatsapp_number = '+919457014679',
    email = 'syedakif700@gmail.com',
    tagline = 'Gentle dental care in Rampur',
    header_cta_text = 'Book appointment',
    footer_description = 'Forever Dental Care is a family dental clinic in Miston Ganj, Rampur, led by Dr. S. M. Akif. Unani consultations for women are available in the evenings with Dr. Faiza Khan.',
    copyright_text = 'All rights reserved.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 1;

-- ---------- Navigation: dental treatments first ----------
UPDATE navigation_items SET display_order = 1 WHERE url = '/';
UPDATE navigation_items SET display_order = 2, label = 'Treatments' WHERE url = '/services';
UPDATE navigation_items SET display_order = 3, label = 'Our Doctors' WHERE url = '/doctors';
UPDATE navigation_items SET display_order = 4, label = 'About' WHERE url = '/about';
UPDATE navigation_items SET display_order = 5, label = 'Specialties' WHERE url = '/specialties';
UPDATE navigation_items SET display_order = 6, label = 'Contact' WHERE url = '/contact';

-- ---------- Website copy ----------
UPDATE website_sections SET
    title = 'Healthy smiles start with gentle dental care.',
    subtitle = 'Family dental clinic in Rampur',
    body = 'From check-ups and fillings to root canals, implants and braces, Dr. S. M. Akif explains every option and its cost upfront, so you always know what to expect.',
    primary_button_label = 'Book an appointment', primary_button_url = '/book',
    secondary_button_label = 'View treatments', secondary_button_url = '/services',
    items = '[{"title":"Upfront treatment prices"},{"title":"Sterilised instruments"},{"title":"Kids & elders welcome"}]',
    updated_at = CURRENT_TIMESTAMP
WHERE page_key = 'HOME' AND section_key = 'hero';

UPDATE website_sections SET
    title = 'Your neighbourhood dental clinic in Miston Ganj.',
    subtitle = 'Welcome',
    body = 'Forever Dental Care is a calm, clean clinic on Rajdwara Road where families from across Rampur come for honest dental advice. Dr. S. M. Akif treats every patient as he would his own family: careful examination, a clear explanation and fair prices.

For women who prefer Unani medicine, Dr. Faiza Khan (BUMS) consults in the evenings, for women only.',
    primary_button_label = 'About the clinic', primary_button_url = '/about',
    updated_at = CURRENT_TIMESTAMP
WHERE page_key = 'HOME' AND section_key = 'intro';

UPDATE website_sections SET
    title = 'Meet our doctors', subtitle = 'Our team',
    body = 'A dentist for the whole family and a female Unani physician for women, each caring for you within their own qualification.',
    updated_at = CURRENT_TIMESTAMP
WHERE page_key = 'HOME' AND section_key = 'doctors';

UPDATE website_sections SET
    title = 'Dental care first, with Unani care for women', subtitle = 'What we offer',
    body = 'Dentistry is our main focus, all day. In the evening shift, Dr. Faiza Khan offers Unani consultations for women only.',
    updated_at = CURRENT_TIMESTAMP
WHERE page_key = 'HOME' AND section_key = 'specialties';

UPDATE website_sections SET
    title = 'Popular dental treatments', subtitle = 'Treatments',
    body = 'Clear prices, explained before we begin. Final cost depends on your examination.',
    primary_button_label = 'All treatments', primary_button_url = '/services',
    updated_at = CURRENT_TIMESTAMP
WHERE page_key = 'HOME' AND section_key = 'services';

UPDATE website_sections SET
    title = 'Why families in Rampur trust us', subtitle = 'Why choose us',
    body = 'Good dentistry is about trust: careful hands, clean instruments and honest advice.',
    items = '[{"icon":"shield","title":"Sterilised for every patient","text":"Instruments are cleaned and sterilised, and disposables are used only once, for every single patient."},{"icon":"clipboard","title":"Prices explained upfront","text":"You get your treatment plan, the number of sittings and the cost before anything begins. No surprises."},{"icon":"heart","title":"Gentle, unhurried care","text":"Local anaesthesia, a calm pace and a doctor who checks in with you at every step."},{"icon":"baby","title":"Kids to grandparents","text":"From a child''s first check-up to dentures for elders, the whole family is looked after under one roof."},{"icon":"female","title":"Female doctor for women","text":"Women can consult Dr. Faiza Khan (BUMS) for Unani care in the evenings, in complete privacy."},{"icon":"calendar","title":"Easy online booking","text":"Pick a doctor, treatment and time online. The clinic confirms your appointment on call or WhatsApp."}]',
    updated_at = CURRENT_TIMESTAMP
WHERE page_key = 'HOME' AND section_key = 'why_choose_us';

UPDATE website_sections SET title = 'What our patients say', subtitle = 'Patient stories', updated_at = CURRENT_TIMESTAMP
WHERE page_key = 'HOME' AND section_key = 'testimonials';

UPDATE website_sections SET title = 'Inside the clinic', subtitle = 'Gallery', updated_at = CURRENT_TIMESTAMP
WHERE page_key = 'HOME' AND section_key = 'gallery';

UPDATE website_sections SET
    title = 'Visit us at a time that suits you', subtitle = 'Clinic timings',
    body = 'Dental appointments run through the day, Monday to Saturday. Unani consultations for women are in the evening shift only. For severe tooth pain or swelling, please call the clinic.',
    updated_at = CURRENT_TIMESTAMP
WHERE page_key = 'HOME' AND section_key = 'timings';

UPDATE website_sections SET
    title = 'Tooth pain, or just due for a check-up?',
    body = 'Book online in under two minutes. The clinic will call or WhatsApp you to confirm.',
    primary_button_label = 'Book an appointment', primary_button_url = '/book',
    updated_at = CURRENT_TIMESTAMP
WHERE page_key = 'HOME' AND section_key = 'cta';

UPDATE website_sections SET
    title = 'About Forever Dental Care', subtitle = 'About us',
    body = 'Forever Dental Care is a family dental clinic on Mohalla Rajdwara Road, Miston Ganj, Rampur, led by Dr. S. M. Akif (BDS). We started with a simple promise: every patient deserves a careful examination, an honest explanation and treatment at a fair, clearly stated price.

Most of what we do is everyday dentistry done well: check-ups, cleaning, fillings, root canals, extractions, crowns, dentures, implants, braces and whitening, for children, adults and elders alike.

In the evenings the clinic also offers Unani consultations for women only, with Dr. Faiza Khan (BUMS), so that female patients can consult a female doctor in privacy.',
    updated_at = CURRENT_TIMESTAMP
WHERE page_key = 'ABOUT' AND section_key = 'intro';

UPDATE website_sections SET
    title = 'Our mission',
    body = 'To give every family in Rampur dental care they can trust: safe, gentle and clearly explained, at prices that are fair and stated upfront.',
    updated_at = CURRENT_TIMESTAMP
WHERE page_key = 'ABOUT' AND section_key = 'mission';

UPDATE website_sections SET
    title = 'Our vision',
    body = 'A community where regular check-ups are normal, children grow up without fear of the dentist, and nobody loses a tooth that could have been saved.',
    updated_at = CURRENT_TIMESTAMP
WHERE page_key = 'ABOUT' AND section_key = 'vision';

UPDATE website_sections SET
    title = 'What to expect at your visit', subtitle = 'Our clinic',
    body = 'A clean, calm space designed around your comfort and safety.',
    items = '[{"icon":"shield","title":"Sterilisation for every patient","text":"Instruments are cleaned and sterilised after every use, and gloves, masks and suction tips are single-use."},{"icon":"tooth","title":"Dedicated dental treatment room","text":"A comfortable, adjustable dental chair with good lighting, so treatment is precise and you stay relaxed."},{"icon":"female","title":"Private consultation for women","text":"Unani consultations and Hijama for women take place in privacy with a female doctor."},{"icon":"users","title":"Family-friendly waiting area","text":"A clean, seated waiting area for patients and the family members who come with them."},{"icon":"clipboard","title":"Clear records and plans","text":"Your treatment plan and appointments are recorded, so every visit picks up where the last one left off."},{"icon":"calendar","title":"Appointments that respect your time","text":"Booked slots keep waiting short. You can book online or by phone and WhatsApp."}]',
    updated_at = CURRENT_TIMESTAMP
WHERE page_key = 'ABOUT' AND section_key = 'facilities';

-- The equipment list should only ever show equipment the clinic really has; hidden until the admin fills it in.
UPDATE website_sections SET active = FALSE, items = '[]', body = NULL, updated_at = CURRENT_TIMESTAMP
WHERE page_key = 'ABOUT' AND section_key = 'equipment';

UPDATE website_sections SET
    title = 'Our doctors', subtitle = 'Our team',
    body = 'Dr. S. M. Akif provides dental care for the whole family. Dr. Faiza Khan (BUMS) offers Unani consultations for women only, in the evening shift.',
    updated_at = CURRENT_TIMESTAMP
WHERE page_key = 'DOCTORS' AND section_key = 'intro';

UPDATE website_sections SET
    title = 'Our specialties', subtitle = 'Specialties',
    body = 'Dentistry for everyone, all day. Unani medicine for women, in the evenings.',
    updated_at = CURRENT_TIMESTAMP
WHERE page_key = 'SPECIALTIES' AND section_key = 'intro';

UPDATE website_sections SET
    title = 'Treatments & prices', subtitle = 'Treatments',
    body = 'Prices are starting prices in Indian Rupees and are charged in addition to the doctor''s consultation fee. Your exact cost is confirmed after examination, before any treatment begins.',
    updated_at = CURRENT_TIMESTAMP
WHERE page_key = 'SERVICES' AND section_key = 'intro';

UPDATE website_sections SET
    title = 'Get in touch', subtitle = 'Contact',
    body = 'Call us, message us on WhatsApp or book online. You will find us on Mohalla Rajdwara Road, Miston Ganj, Rampur.',
    updated_at = CURRENT_TIMESTAMP
WHERE page_key = 'CONTACT' AND section_key = 'intro';

UPDATE website_sections SET
    title = 'Book an appointment', subtitle = 'Appointments',
    body = 'Choose your doctor, treatment and a time that suits you. The clinic will call or WhatsApp you to confirm. Unani consultations are for women only, in the evening shift.',
    updated_at = CURRENT_TIMESTAMP
WHERE page_key = 'BOOK' AND section_key = 'intro';

UPDATE website_sections SET
    title = 'Severe pain or swelling?',
    body = 'Please call the clinic directly so we can see you as soon as possible.',
    updated_at = CURRENT_TIMESTAMP
WHERE page_key = 'BOOK' AND section_key = 'emergency';

-- ---------- SEO ----------
UPDATE page_seo SET meta_title = 'Dentist in Rampur',
    meta_description = 'Family dental clinic in Miston Ganj, Rampur. Check-ups, root canals, implants, braces and whitening with Dr. S. M. Akif. Book online.',
    updated_at = CURRENT_TIMESTAMP WHERE page_key = 'HOME';
UPDATE page_seo SET meta_title = 'About us',
    meta_description = 'Forever Dental Care is a family dental clinic in Rampur led by Dr. S. M. Akif, with Unani care for women by Dr. Faiza Khan.',
    updated_at = CURRENT_TIMESTAMP WHERE page_key = 'ABOUT';
UPDATE page_seo SET meta_title = 'Our doctors',
    meta_description = 'Meet Dr. S. M. Akif (BDS), dentist, and Dr. Faiza Khan (BUMS), Unani physician for women, at Forever Dental Care, Rampur.',
    updated_at = CURRENT_TIMESTAMP WHERE page_key = 'DOCTORS';
UPDATE page_seo SET meta_title = 'Specialties',
    meta_description = 'Dentistry for the whole family and Unani medicine for women at Forever Dental Care, Rampur.',
    updated_at = CURRENT_TIMESTAMP WHERE page_key = 'SPECIALTIES';
UPDATE page_seo SET meta_title = 'Dental treatments & prices',
    meta_description = 'Dental treatment prices in Rampur: cleaning, fillings, root canal, crowns, implants, dentures, braces and whitening. Current offers included.',
    updated_at = CURRENT_TIMESTAMP WHERE page_key = 'SERVICES';
UPDATE page_seo SET meta_title = 'Contact',
    meta_description = 'Address, phone, WhatsApp, clinic timings and directions to Forever Dental Care, Miston Ganj, Rampur.',
    updated_at = CURRENT_TIMESTAMP WHERE page_key = 'CONTACT';
UPDATE page_seo SET meta_title = 'Book an appointment',
    meta_description = 'Book a dental appointment with Dr. S. M. Akif or a Unani consultation for women with Dr. Faiza Khan, online.',
    updated_at = CURRENT_TIMESTAMP WHERE page_key = 'BOOK';
