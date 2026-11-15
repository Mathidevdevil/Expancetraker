# Spendwise — Supabase Setup & Authentication Guide

Spendwise uses a single unified Supabase backend for both the Next.js web application and the native Android app.

---

## 1. Quick Database Setup

1. Open your project on [Supabase Dashboard](https://supabase.com/dashboard).
2. Go to **SQL Editor** in the left sidebar.
3. Click **New query**, paste the entire contents of [`supabase/schema.sql`](./schema.sql), and click **Run**.
4. This will:
   - Create tables: `categories`, `trips`, `transactions`, `budgets`.
   - Configure Row Level Security (RLS) so users can only access their own data.
   - Attach automatic `updated_at` triggers.
   - Configure a user signup trigger (`on_auth_user_created_seed_categories`) that auto-populates 15 essential default categories (Food, Travel, Salary, etc.) for every new user.
   - Register all 4 tables with the `supabase_realtime` publication so updates broadcast instantly across web and mobile clients.

---

## 2. Supabase API Credentials

Go to **Project Settings** $\rightarrow$ **API** on your Supabase dashboard and copy:
1. **Project URL**: `https://<your-project-ref>.supabase.co`
2. **Anon Public Key**: `eyJhbGciOi...`

Add these into:
- Web: `web/.env.local`
- Android: `android/local.properties` (or `app/src/main/res/values/secrets.xml`)

---

## 3. Google Sign-In & OAuth Setup

Spendwise supports both standard Email/Password authentication and one-tap Google Sign-In across Web and Android.

### A. Google Cloud Console
1. Go to [Google Cloud Console](https://console.cloud.google.com/) $\rightarrow$ **APIs & Services** $\rightarrow$ **Credentials**.
2. Configure your **OAuth Consent Screen** (User type: External, add App Name: *Spendwise* and your email).
3. Create **OAuth 2.0 Client ID (Web Application)**:
   - Name: `Spendwise Web Client`
   - Authorized Javascript origins:
     - `http://localhost:3000`
     - Your Vercel production domain (e.g. `https://spendwise.vercel.app`)
     - `https://<your-project-ref>.supabase.co`
   - Authorized redirect URIs:
     - `https://<your-project-ref>.supabase.co/auth/v1/callback`
     - `http://localhost:3000/auth/callback`
4. Copy the generated **Client ID** and **Client Secret**.

### B. Configure Google in Supabase
1. In your Supabase Dashboard, go to **Authentication** $\rightarrow$ **Providers** $\rightarrow$ **Google**.
2. Toggle **Enable Google provider**.
3. Paste the **Client ID** and **Client Secret** obtained above.
4. Click **Save**.

### C. Android Google Sign-In (Credential Manager API)
1. In Google Cloud Console $\rightarrow$ **Credentials**, click **Create Credentials** $\rightarrow$ **OAuth 2.0 Client ID**.
2. Application type: **Android**.
3. Package name: `com.spendwise.app`.
4. SHA-1 certificate fingerprint:
   - For your debug keystore, run:
     ```bash
     keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
     ```
   - Copy the `SHA1:` fingerprint and paste it into the field.
5. In the Android app, pass the **Web Client ID** to the `GetGoogleIdOption` in Google Credential Manager (Supabase requires the ID token issued for the Web client to authenticate the user securely).

---

## 4. Realtime Configuration Check
1. Go to **Database** $\rightarrow$ **Publications** on Supabase.
2. Confirm that `supabase_realtime` includes `categories`, `trips`, `transactions`, and `budgets`.
3. In **Database** $\rightarrow$ **Replication**, ensure `REPLICA IDENTITY FULL` is enabled for each table (the SQL schema already executed this).
