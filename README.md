# Forever Dental Care: Website & Appointment Management

A dental clinic website with online appointment requests and an admin dashboard. It is a modular monolith with two parts:

| Part | Stack |
|---|---|
| `frontend/` | React 19, TypeScript, Vite 6, Tailwind CSS 4, React Router 7, React Query 5, React Hook Form + Zod, Axios, Lucide icons |
| `backend/` | Java 21, Spring Boot 3.5 (Web, Data JPA, Security, OAuth2 Resource Server for JWT, Validation), Flyway, Lombok, Maven |
| Database | PostgreSQL 16 (Docker Compose). Tests run on H2 in PostgreSQL mode, using the same Flyway migrations. |

> **Placeholders:** all clinic-specific content is a clearly marked placeholder. Replace it before going live (see "Replacing placeholder content" below).

---

## 1. Prerequisites

- **Java 21+** and **Maven 3.9+**
- **Node.js 20+** and npm
- **Docker Desktop**, for PostgreSQL. You can also use a locally installed PostgreSQL 14+.

You don't need any paid external services.

## 2. Local setup

This repository is the **backend**. The frontend lives in its own repository: https://github.com/syedcoder786/foreverdentalcare-frontend

```bash
# 1. Environment file (never commit the real one)
cp .env.example .env               # used by both docker-compose and Spring Boot
```

Edit `.env` and set at least:

- `DB_PASSWORD`: any strong password.
- `JWT_SECRET`: 32 or more random characters (`openssl rand -base64 48`).
- `ADMIN_EMAIL` / `ADMIN_PASSWORD`: the first admin account (password of at least 10 characters).
- `COOKIE_SECURE=false`: only for local http development.
- `CORS_ALLOWED_ORIGINS`: the frontend origin(s), e.g. `http://localhost:5173` locally or your deployed frontend URL.

```bash
# 2. Start PostgreSQL
docker compose up -d

# 3. Start the backend (http://localhost:8080). Flyway creates tables and seed data.
mvn spring-boot:run
```

Then start the frontend from its repository (`npm install && npm run dev`) and open **http://localhost:5173** for the website and **http://localhost:5173/admin** for the admin dashboard. Log in with `ADMIN_EMAIL` / `ADMIN_PASSWORD`.

On first start, the admin account is created **only if no admin exists yet**. After that you can remove `ADMIN_PASSWORD` from `.env`.

During development the Vite dev server proxies `/api` to the backend. The browser therefore sees a single origin, and the refresh-token cookie works with `SameSite=Strict`.

## 3. Running tests

```bash
mvn test        # 73 tests: scheduling engine, per-doctor booking and concurrency, CMS, uploads, auth, rate limiting
```

The backend tests need no database server. They use in-memory H2 and run the real Flyway migrations.

What the tests cover:

- **Booking:** a new booking is PENDING. Past dates, closed days, off-grid times, inactive services and invalid input are rejected.
- **Duplicate-slot prevention:** a unique-constraint test, a test that 8 simultaneous requests for one slot produce exactly 1 booking, and a test that cancelling frees the slot.
- **Admin authorization:** all admin APIs return 401 without a token. Invalid tokens and wrong passwords are rejected. Refresh tokens rotate, and reusing an old one is detected. Logout revokes the session.
- **Status rules, rescheduling conflicts, service CRUD and soft delete, settings validation, pagination and filtering.**
- **Rate limiting** on login and on booking.
- **Frontend:** form validation, 409 conflict handling, server field errors, network failures, and refreshing the token after a 401.

## 4. Project structure

```
foreverdentalcare-backend/
  docker-compose.yml       PostgreSQL
  .env.example             all backend/database variables
  src/main/java/com/clinic/
    config/       AppProperties (typed env config), clinic-timezone Clock, Jackson, admin bootstrap
    controller/   Public, Auth, AdminDashboard, AdminAppointment, AdminService, AdminSettings
    service/      AppointmentService, SlotService, DentalServiceCatalogService,
                  ClinicSettingsService, DashboardService, AuthService
    repository/   Spring Data repositories + AppointmentSpecifications (search/filter)
    entity/       Admin, Patient, DentalService, Appointment, ClinicSettings, RefreshToken
    dto/          request/ and response/ records (entities are never exposed)
    mapper/       entity <-> DTO mappers
    exception/    GlobalExceptionHandler (@RestControllerAdvice) + domain exceptions
    security/     SecurityConfig (Spring Security login + JWT resource server), UserDetailsService, JWT issuing, refresh cookie, rate limiter
    enums/        AppointmentStatus (with transition rules), Role
    util/         phone validation/normalisation
  src/main/resources/db/migration/   V1 schema, V2 placeholder seed data
```

## 5. Doctors, specialties and independent schedules

- **Specialties** (e.g. Dentistry, Unani Medicine) group treatments. A doctor can have several specialties.
- **Services** belong to one specialty and are assigned to one or more doctors (many-to-many). A doctor can only be given services from their own specialties, and only receives bookings for the services assigned to them. Both rules are enforced by the backend.
- **Each doctor has an independent calendar:**
  - **Weekly schedule:** one row per weekday with on/off, start/end time, an optional break and slot length (15/30/45/60 or custom).
  - **Special hours** for particular dates replace that weekday's schedule.
  - **Blocked time:** a full day or a time range, per doctor. A block with no doctor is a **clinic-wide holiday** that applies to everyone.
- **Appointments** store `doctor_id`, a start time and an `end_time`. A service occupies its duration rounded up to whole slots and can't cross the doctor's break. Example: a 60-minute treatment in 30-minute slots blocks two slots.
- **Conflict prevention, always per doctor (never clinic-wide):**
  1. Booking and rescheduling lock the doctor's database row (`SELECT … FOR UPDATE`), so concurrent requests for the **same** doctor are serialised while other doctors are unaffected.
  2. Under that lock the backend re-validates working hours, the break, blocked time, past times, the booking window and **time-range overlap** with that doctor's pending or confirmed appointments. Cancelled appointments release their time.
  3. Database backstops:
     - `UNIQUE (doctor_id, date, start, slot_active)`, on every database.
     - On **PostgreSQL**, an exclusion constraint (`btree_gist`, migration `db/vendor/postgresql/V3_2`) that rejects any overlapping active range for the same doctor.

  A lost race returns **HTTP 409**.
- **Credentials:** qualifications and registration details are shown publicly **only after the admin ticks "credentials verified"** on the doctor's profile.

## 6. Website content management

Nothing on the public site is hard-coded. What the admin edits, and where:

| What | Where in admin |
|---|---|
| Hero, intro, featured doctors and treatments, why-choose-us, testimonials, gallery, timings and call-to-action (order and visibility of every section) | **Homepage editor** |
| About (intro, mission, vision, facilities, equipment), Doctors, Specialties, Treatments, Contact and Booking page text, plus custom sections | **Website content → page tabs** |
| Header menu (also used for the footer links) | **Website content → Navigation menu** |
| Logo, clinic name, tagline, header button, WhatsApp, footer text, copyright and social links | **Website content → Header & footer** |
| Address, phone, email, map, clinic hours, booking window and clinic holidays | **Clinic settings** |
| Testimonials (publishing requires recorded patient consent, enforced by the database) | **Testimonials** |
| Gallery photos with captions, categories and order | **Gallery** |
| Page title, meta description, Open Graph image and indexing for each page | **SEO settings** |
| Doctor, specialty and treatment URL slugs | On their own admin pages |

Every admin change is written to an **audit log** (shown on the dashboard, `GET /api/admin/audit-logs`).

**Image uploads.** `POST /api/admin/uploads` accepts JPEG, PNG, WebP or GIF up to 5 MB. Files are validated by their **content** (magic bytes), not their name, and SVG is rejected. They're stored outside the database behind the `FileStorage` interface:
- `LocalFileStorage` (`STORAGE_PROVIDER=local`, the default) writes to `UPLOAD_DIR` (default `./uploads`) and serves files from `/api/public/files/{name}`. Back up the upload folder together with the database.
- `CloudinaryFileStorage` (`STORAGE_PROVIDER=cloudinary`) uploads to Cloudinary and saves the absolute `https://res.cloudinary.com/...` URL, so images are served by Cloudinary's CDN. Set `CLOUDINARY_URL=cloudinary://<api_key>:<api_secret>@<cloud_name>` in `backend/.env` (never commit it) and optionally `CLOUDINARY_FOLDER` (default `forever-dental-care`). The backend refuses to start if the provider is `cloudinary` and the URL is missing.
- **Admin → Image storage** lets an admin switch between local disk and Cloudinary at runtime: paste a `CLOUDINARY_URL` (or type the cloud name, API key and API secret), press **Test connection** (it calls Cloudinary's ping and usage APIs), then save with their admin password. The settings are saved to `storage-connection.json` (`STORAGE_CONFIG_FILE`, git-ignored, holds the API secret) and override `STORAGE_PROVIDER` / `CLOUDINARY_URL`; **Go back to .env settings** deletes the file. The secret is never sent back to the browser.
- Switching providers doesn't move existing images: images uploaded locally keep their `/api/public/files/...` URLs and are still served from the upload folder after a switch to Cloudinary, so keep that folder.
- To add S3 or another store, implement `FileStorage` and route to it from `SwitchableFileStorage`.

### Patient sign-in with WhatsApp

Patients sign in with their mobile number only. There is no password and no separate sign-up form:

1. On **Book appointment** (or the sign-in prompt that appears about 20 seconds after the site opens), the patient enters their mobile number and gets a 6-digit code on WhatsApp.
2. A returning patient is signed in right away. A new number is asked for a name, which creates the profile.
3. **My profile** (`/profile`) shows their details and appointments, with a button to book again. Bookings are linked to the profile, so name and phone are filled in automatically.

**Setup:** Admin → Settings → **WhatsApp sign-in**. Enter the Phone number ID, a permanent access token, and the name and language of an approved **Authentication** template with a **Copy code** button, then use **Send test**. The token is stored on the server and never returned to the browser (only its last 4 characters are shown).

**Security:**
- Codes expire after 5 minutes. Each code allows 5 wrong attempts, a new one can be requested after 30 seconds, and each number gets at most 5 codes per hour, plus per-IP limits. Only an HMAC of each code is stored.
- Sessions use Spring Security with JWT, like the admin login: a short-lived access token with role `PATIENT` kept in memory, plus a rotating refresh token in an HttpOnly cookie scoped to `/api/patient-auth`.
- **Local development:** with `OTP_LOG_CODES=true` and WhatsApp not set up, codes are written to the backend log instead. Never enable this in production.

### WhatsApp appointment messages and marketing

**Admin → WhatsApp** has three tabs:
- **Setup:** the WhatsApp connection and the sign-in code template.
- **Template names:** nothing is added automatically. Create templates in WhatsApp Manager, then save each name and language here (or with *+ Add a new template name…* in any template picker). Saved names are reused across appointment and marketing messages; a name in use can't be removed.
- **Appointment messages:** add a message for any of *Appointment received*, *Confirmed*, *Cancelled* and *Rescheduled* (one each), choose a saved template name, and pick what fills each placeholder. The options are patient name, treatment, doctor, date, time, booking reference, clinic details or your own text. Header text, media headers (image/video/document link) and URL or coupon-code buttons are supported. The chosen template is read live from your WhatsApp Business Account, so the editor shows a preview with exactly the placeholders each one has. Messages are sent in the background after the change is saved, and a WhatsApp error never blocks a booking.
- **Marketing:** add any number of promotional templates and send one as a campaign to every patient (all patients are included; there is no opt-in checkbox). Progress and sent/failed counts are shown per campaign.

Every template has **Send test**, which sends it with example data.

The older public booking endpoint (`POST /api/public/appointments`) still works for API clients. The website uses the signed-in `POST /api/patient/appointments`.

## 7. API overview

Every response uses the same envelope: `{ success, message?, data?, errors?, timestamp }`.

**Public** (active and published content only, no patient data):

| Method | Endpoint | Notes |
|---|---|---|
| GET | `/api/public/site` | Clinic settings + navigation |
| GET | `/api/public/pages/{HOME\|ABOUT\|DOCTORS\|SPECIALTIES\|SERVICES\|CONTACT\|BOOK}` | Active sections + SEO |
| GET | `/api/public/specialties`, `/specialties/{slug}` | Specialty with its doctors and services |
| GET | `/api/public/services?specialtyId=&doctorId=`, `/services/{slug}` | |
| GET | `/api/public/doctors?specialtyId=`, `/doctors/{id}`, `/doctors/by-slug/{slug}` | Credentials only if verified |
| GET | `/api/public/doctors/{id}/services` | Only services assigned to that doctor |
| GET | `/api/public/doctors/{id}/availability?date=&serviceId=` | That doctor's hours, break and every slot with an available flag |
| GET | `/api/public/doctors/{id}/available-slots?date=&serviceId=` | Bookable slots only |
| GET | `/api/public/testimonials`, `/gallery`, `/files/{name}` | |
| POST | `/api/public/appointments` | `{doctorId, serviceId, date, time, patient…}`. Rate limited. Always **PENDING**. |

**Auth:** `POST /api/auth/login | refresh | logout`, unchanged.

**Admin** (JWT, role ADMIN):

| Method | Endpoint |
|---|---|
| GET | `/api/admin/dashboard`: totals, per-doctor breakdown, today's schedule, recent activity |
| GET/POST | `/api/admin/appointments`: filters `search, status, doctorId, specialtyId, date/from/to`, pagination. POST = manual booking. |
| GET, PATCH | `/api/admin/appointments/{id}`, `/{id}/status`, `/{id}/reschedule` |
| GET | `/api/admin/calendar?from=&to=&doctorId=`: all doctors or one (week/month views) |
| CRUD | `/api/admin/doctors`, plus `/{id}/schedule` (GET/PUT), `/{id}/blocked-dates` (POST, DELETE `/{blockId}`), `/{id}/appointments?from=&to=`, `/{id}/day?date=` |
| GET/POST/DELETE | `/api/admin/holidays`: clinic-wide blocked dates |
| CRUD | `/api/admin/specialties`, `/api/admin/services` |
| GET | `/api/admin/patients`, `/patients/{id}`, `/audit-logs` |
| GET/PUT | `/api/admin/settings`, `/navigation`, `/seo`, `/seo/{pageKey}` |
| GET/POST, PUT/DELETE | `/api/admin/pages/{pageKey}/sections`, `/api/admin/sections/{id}` |
| CRUD | `/api/admin/testimonials`, `/api/admin/gallery` |
| POST | `/api/admin/uploads` (multipart `file`) |

HTTP status codes: 400 invalid input (with field `errors`), 401/403 auth, 404 not found, 409 slot taken or other conflict, 422 business rule violated (e.g. outside the doctor's hours), 429 rate limited.

**Status transitions:**

- `PENDING → CONFIRMED | CANCELLED`
- `CONFIRMED → COMPLETED | CANCELLED`
- `COMPLETED` and `CANCELLED` are final.

Rescheduling keeps the same doctor and releases the old time.

## 8. Other design decisions

- **Timezone.** Dates and times are clinic-local wall-clock values (`DATE`/`TIME`). Every "today" and "now" check uses a `Clock` in `APP_TIMEZONE`. Audit timestamps are UTC.
- **Authentication.**
  - Login goes through Spring Security's `AuthenticationManager` (`DaoAuthenticationProvider` + `AdminUserDetailsService`).
  - The access token is a short-lived HS256 JWT kept in memory only. It's issued with Spring's `JwtEncoder` and validated by the OAuth2 resource server (signature, issuer, expiry) on `/api/admin/**`.
  - The refresh token lives in an HttpOnly, Secure, SameSite=Strict cookie. It's hashed in the database and rotated on every use, and reusing an old one revokes all of that admin's sessions.
  - Passwords are hashed with BCrypt.
- **Rate limiting.** Booking, login and refresh are limited per client IP (in memory, single instance).
- **Seed data.** Only what existing data needs:
  - The original dentist as a placeholder profile, with the clinic's old hours and the 8 dental treatments.
  - The "Dentistry" and "Unani Medicine" specialties, and neutral placeholder page text.

  No Unani treatments, testimonials or verified credentials are seeded.

### Adding the BUMS practitioner

1. **Doctors → Add doctor.** Enter her name, title (e.g. "Unani Physician (BUMS)"), gender and photo. Tick the **Unani Medicine** specialty.
   - Add her qualifications and registration number and authority.
   - Tick *credentials verified* only after checking the certificates and the official register.
2. **Schedule.** Set her own days, hours (e.g. 16:00–20:00), break and slot length. Add leave under *Blocked time*.
3. **Services → Add service** under Unani Medicine, assigned to her. Only add treatments within her registered scope of practice, with descriptions that make no medical claims.
4. **Dentist profile.** Replace the `[PLACEHOLDER]` text and set his hours (e.g. 10:00–14:00). The two calendars never conflict.

## 9. Production deployment

A simple, low-cost setup is one VM (or a PaaS) with a reverse proxy serving everything from **one domain**:

```
https://clinic.example.com/        -> frontend/dist (static files, SPA fallback to index.html)
https://clinic.example.com/api/*   -> Spring Boot on :8080
```

Steps:

1. Build the frontend (separate repository) with `npm ci && npm run build`, then serve its `dist/`. Configure the SPA fallback so unknown paths return `index.html`.
2. Build the backend with `mvn -DskipTests package`, then run `java -jar target/dental-clinic-backend-1.0.0.jar` as a service.
3. Set the production environment variables:
   - `DB_*`
   - a strong `JWT_SECRET`
   - `COOKIE_SECURE=true`
   - `CORS_ALLOWED_ORIGINS=https://clinic.example.com`
   - `FORWARD_HEADERS_STRATEGY=native`, so rate limiting sees the real client IP behind the proxy
   - `ADMIN_EMAIL` / `ADMIN_PASSWORD` for the first start only
   - `UPLOAD_DIR` on persistent storage (and include it in backups)
4. Use HTTPS (for example with Let's Encrypt) and managed PostgreSQL backups.

Example Nginx config:

```nginx
location /api/ { proxy_pass http://127.0.0.1:8080; proxy_set_header Host $host;
                 proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                 proxy_set_header X-Forwarded-Proto $scheme; }
location /     { root /var/www/dental-clinic; try_files $uri /index.html; }
```

If the API must live on a different subdomain (for example `api.clinic.example.com`), set `VITE_API_BASE_URL` at build time. Keep both on the same registrable domain so the `SameSite=Strict` cookie still works.

Health check: `GET /actuator/health`.

## 10. Known limitations / next steps

- There are no automated emails or SMS. The clinic contacts patients by phone or WhatsApp; the dashboard has Call and WhatsApp buttons.
- The admin password can't be changed from the UI yet.
- The public site is a single-page app. Google renders it, but for other crawlers or link previews consider prerendering or SSR later.
- Uploaded images aren't deleted automatically when they're removed from content, because they may be referenced elsewhere.
- The rate limiter is in memory, which suits a single instance.
- A multi-instance deployment would need Redis for rate limiting and object storage for uploads.
