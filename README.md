# CareFlow Healthcare Management System

CareFlow is an integrated, secure, and modern healthcare management portal featuring role-specific dashboards for **Patients**, **Doctor Specialists**, and **Clinic Administrators**. Powered by a Spring Boot REST API and a React Vite frontend, it integrates with **Google Calendar** for appointment syncing, features an asynchronous **Email Engine** with automatic retries, and leverages **Google Gemini AI** to generate smart pre-visit symptoms summaries and patient-friendly clinical outlines.

---

## Table of Contents
1. [Database Schema](#1-database-schema)
2. [Environment Variables & Setup Guide](#2-environment-variables--setup-guide)
3. [API Documentation](#3-api-documentation)
4. [LLM Prompt Engineering](#4-llm-prompt-engineering)
5. [Google Calendar OAuth 2.0 Integration Setup](#5-google-calendar-oauth-20-integration-setup)
6. [Local & Cloud Deployment Guide](#6-local--cloud-deployment-guide)

---

## 1. Database Schema

CareFlow uses a relational MySQL database. The primary tables are:

### Users Table (`users`)
Stores core credentials and authentication roles.
- `id` (BIGINT, Primary Key, Auto-Increment)
- `email` (VARCHAR, Unique, Indexed)
- `password` (VARCHAR)
- `full_name` (VARCHAR)
- `role` (VARCHAR: `PATIENT`, `DOCTOR`, `ADMIN`)
- `created_at` (TIMESTAMP)

### Doctor Profiles Table (`doctor_profiles`)
Extends user accounts with clinical specialty information.
- `id` (BIGINT, Primary Key, Auto-Increment)
- `user_id` (BIGINT, Foreign Key referencing `users(id)`)
- `specialization` (VARCHAR)
- `working_hours_start` (VARCHAR, e.g., `09:00`)
- `working_hours_end` (VARCHAR, e.g., `17:00`)
- `slot_duration_minutes` (INT)

### Appointments Table (`appointments`)
Logs slot transactions, diagnostic descriptions, and AI summaries.
- `id` (BIGINT, Primary Key, Auto-Increment)
- `patient_id` (BIGINT, Foreign Key referencing `users(id)`)
- `doctor_id` (BIGINT, Foreign Key referencing `doctor_profiles(id)`)
- `appointment_date` (DATE)
- `start_time` (TIME)
- `end_time` (TIME)
- `status` (VARCHAR: `HELD`, `CONFIRMED`, `CANCELLED`, `CANCELLED_BY_DOCTOR_LEAVE`)
- `urgency_level` (VARCHAR: `LOW`, `MEDIUM`, `HIGH`)
- `patient_symptoms` (TEXT)
- `pre_visit_summary` (TEXT)
- `post_visit_notes` (TEXT)
- `post_visit_summary` (TEXT)
- `active_booking_marker` (VARCHAR, Unique lock to prevent double-booking)
- `google_event_id_patient` (VARCHAR)
- `google_event_id_doctor` (VARCHAR)

### Slot Holds Table (`slot_holds`)
Temporary holds placed on slots to secure them during form-filling.
- `id` (BIGINT, Primary Key, Auto-Increment)
- `doctor_id` (BIGINT, Foreign Key referencing `doctor_profiles(id)`)
- `held_by_patient_id` (BIGINT, Foreign Key referencing `users(id)`)
- `slot_date` (DATE)
- `start_time` (TIME)
- `expires_at` (TIMESTAMP, defaults to 5 minutes duration)

### Doctor Leaves Table (`doctor_leaves`)
Registry of vacation dates. Adding a leave automatically cancels conflicting appointments.
- `id` (BIGINT, Primary Key, Auto-Increment)
- `doctor_id` (BIGINT, Foreign Key referencing `doctor_profiles(id)`)
- `leave_date` (DATE)
- `reason` (VARCHAR)

### Prescriptions Table (`prescriptions`)
Patient medical script prescriptions.
- `id` (BIGINT, Primary Key, Auto-Increment)
- `appointment_id` (BIGINT, Foreign Key referencing `appointments(id)`)
- `medication_name` (VARCHAR)
- `dosage` (VARCHAR)
- `frequency` (VARCHAR: `ONCE_DAILY`, `TWICE_DAILY`, `THRICE_DAILY`)
- `duration_days` (INT)
- `start_date` (DATE)
- `active` (BOOLEAN)

### Notification Queue Table (`notification_queue`)
Asynchronous transaction queue for reliable email dispatch.
- `id` (BIGINT, Primary Key, Auto-Increment)
- `recipient_email` (VARCHAR)
- `subject` (VARCHAR)
- `body` (TEXT)
- `status` (VARCHAR: `PENDING`, `SENT`, `FAILED`)
- `retry_count` (INT, Max retries capped at 3)
- `error_message` (TEXT)
- `scheduled_at` (TIMESTAMP)
- `sent_at` (TIMESTAMP)

---

## 2. Environment Variables & Setup Guide

### Environment Variables Template (`.env.example`)
Create a `.env` file locally or define these in your cloud provider environment dashboard:

```env
# Database Configuration
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/healthcare_db?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=your_mysql_password

# JWT Security
JWT_SECRET_KEY=your_base64_encoded_jwt_secret_key_minimum_256_bits

# Google Gemini API
LLM_API_KEY=your_google_gemini_api_key

# Google OAuth 2.0 Credentials
GOOGLE_CLIENT_ID=your_google_oauth_client_id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your_google_oauth_client_secret
GOOGLE_REDIRECT_URI=http://localhost:8080/api/oauth/google/callback

# SMTP Email Configuration (Gmail SMTP example)
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=your_gmail_address@gmail.com
SPRING_MAIL_PASSWORD=your_16_character_gmail_app_password
```

---

## 3. API Documentation

All API endpoints require a JWT token in the `Authorization` header (`Bearer <token>`) except those explicitly noted as `[PUBLIC]`.

### Authentication Endpoints
- `POST /api/auth/register` `[PUBLIC]`: Register a new user (`PATIENT`, `DOCTOR`, or `ADMIN`).
- `POST /api/auth/login` `[PUBLIC]`: Authenticate and receive a JWT token.

### Doctor & Booking Endpoints
- `GET /api/doctors` `[PUBLIC]`: Search doctor profiles. Query parameters: `specialization` (optional).
- `GET /api/appointments/slots` `[PUBLIC]`: Fetch computed slots for a doctor on a specific date. Query parameters: `doctorId`, `date`.
- `POST /api/appointments/hold`: Place a 5-minute hold on a slot.
- `POST /api/appointments/confirm`: Confirm booking of a held slot, provide symptoms, and sync with Google Calendar.

### Patient Portal
- `GET /api/appointments/patient`: List all appointments for the logged-in patient.

### Doctor Portal
- `GET /api/appointments/doctor` `[DOCTOR]`: List all daily appointments scheduled for the logged-in doctor.
- `POST /api/appointments/{id}/complete` `[DOCTOR]`: Log clinical diagnostic notes, add prescriptions, and generate an AI summary.

### Administrator Portal
- `GET /api/admin/doctors` `[ADMIN]`: List all doctor profiles.
- `POST /api/admin/doctors` `[ADMIN]`: Register a new doctor.
- `PUT /api/admin/doctors/{id}` `[ADMIN]`: Update doctor profile details.
- `POST /api/admin/doctors/{id}/leaves` `[ADMIN]`: Register doctor leave (triggers automatic conflicts cancellation).
- `GET /api/admin/doctors/{id}/leaves` `[ADMIN]`: Fetch registered leaves for a doctor.
- `DELETE /api/admin/doctors/{id}/leaves/{leaveId}` `[ADMIN]`: Delete/cancel a leave.
- `GET /api/admin/doctors/conflicts` `[ADMIN]`: View a log of all automatically cancelled appointments due to leaves.

---

## 4. LLM Prompt Engineering

CareFlow leverages Google Gemini AI using structured system prompts to guarantee clean formats and patient-friendly output:

### System Prompt 1: Pre-Visit Symptom Analysis
Generates clinical triage notes and recommended questions for the physician.
- **Trigger**: Fired automatically upon booking confirmation.
- **System Prompt**:
  > "Analyse these symptoms and return: urgency level (Low / Medium / High), chief complaint, and three suggested questions for the doctor. Symptoms: <symptoms>"

### System Prompt 2: Post-Visit Summary Conversion
Translates raw medical terminology into a clear, patient-friendly overview.
- **Trigger**: Fired automatically upon doctor completing a visit.
- **System Prompt**:
  > "Convert these clinical notes into a patient-friendly summary with medication schedule and follow-up steps: <notes>"

---

## 5. Google Calendar OAuth 2.0 Integration Setup

CareFlow automatically adds events to both the Patient's and Doctor's Google Calendar upon booking. Follow these steps to generate credentials:

1. Go to the [Google Cloud Console](https://console.cloud.google.com/).
2. Create a new project named `CareFlow-Healthcare`.
3. In the left sidebar, navigate to **APIs & Services** > **Library**. Search for **Google Calendar API** and click **Enable**.
4. Configure the **OAuth Consent Screen**:
   - Select **External**.
   - Fill in developer email and application details.
   - Under **Scopes**, add `/auth/calendar` and `/auth/calendar.events`.
   - Add your test Google accounts under **Test Users**.
5. Generate **Credentials**:
   - Go to **APIs & Services** > **Credentials**.
   - Click **Create Credentials** > **OAuth Client ID**.
   - Select **Web Application**.
   - Under **Authorized JavaScript origins**, add:
     - `http://localhost:5173` (local Vite URL)
     - `https://your-app.vercel.app` (production Vercel URL)
   - Under **Authorized redirect URIs**, add:
     - `http://localhost:8080/api/oauth/google/callback` (local backend redirect)
     - `https://your-service.onrender.com/api/oauth/google/callback` (production Render redirect)
6. Copy the generated **Client ID** and **Client Secret** and configure them in your environment variables.

---

## 6. Local & Cloud Deployment Guide

### Local Setup (Development)

#### Backend:
1. Ensure JDK 17 is installed.
2. Edit [`src/main/resources/application.yml`](src/main/resources/application.yml) or create a `.env` file containing your credentials.
3. Run the Spring Boot application:
   ```bash
   ./mvnw spring-boot:run
   ```

#### Frontend:
1. Navigate to the `frontend` folder:
   ```bash
   cd frontend
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Run the development server:
   ```bash
   npm run dev
   ```

---

### Production Cloud Deployment (Vercel & Render)

#### Backend (Render):
1. Push your code to GitHub (ensure [`Dockerfile`](Dockerfile) and [`src/main/resources/application.yml`](src/main/resources/application.yml) are committed).
2. Create a **New Web Service** on Render, connecting your GitHub repository.
3. Render will auto-detect the `Dockerfile` and set the runtime to **`Docker`**.
4. Under **Environment Variables**, add the variables defined in the `.env.example` template.

#### Frontend (Vercel):
1. Open the [`frontend/vercel.json`](frontend/vercel.json) file and ensure the `"destination"` URL points to your Render backend URL:
   ```json
   {
     "rewrites": [
       {
         "source": "/api/:path*",
         "destination": "https://healthcaremanagement-xtzc.onrender.com/api/:path*"
       }
     ]
   }
   ```
2. Create a **New Project** on Vercel, connecting the same GitHub repository.
3. In configuration, set **Root Directory** to `frontend`.
4. Click **Deploy**. Vercel will compile the React code and route api requests seamlessly to your Render backend.
