# Spendwise — Complete Setup & Deployment Guide

Spendwise is a cross-platform personal finance and travel tracker featuring:
- **Shared Supabase Backend** (PostgreSQL with Row Level Security, Triggers & Realtime)
- **Next.js Web Application** (`/web` — App Router, Tailwind, dark mode, optimistic updates)
- **Native Android App** (`/android` — Kotlin, Jetpack Compose, Material 3, Room offline cache, < 5s quick-add)

---

## 1. Supabase Setup (5 Minutes)

1. Sign up or log into [Supabase Dashboard](https://supabase.com/dashboard).
2. Create a new project named **Spendwise**.
3. Go to **SQL Editor** in the left sidebar $\rightarrow$ click **New query**.
4. Open [`supabase/schema.sql`](./supabase/schema.sql), copy everything, paste it into the editor, and click **Run**.
   - This provisions all 4 tables: `categories`, `trips`, `transactions`, `budgets`.
   - Enables Row Level Security (RLS) policies.
   - Sets up automatic triggers to auto-provision 15 default categories for every new user.
   - Enables Supabase Realtime broadcast channels.
5. In Supabase Dashboard $\rightarrow$ **Project Settings** $\rightarrow$ **API**, copy:
   - **Project URL**
   - **anon / public key**

### Environment Configuration
- In `web/.env.local`:
  ```env
  NEXT_PUBLIC_SUPABASE_URL=https://<your-project-ref>.supabase.co
  NEXT_PUBLIC_SUPABASE_ANON_KEY=<your-anon-key>
  NEXT_PUBLIC_DEFAULT_CURRENCY=INR
  ```
- In `android/local.properties`:
  ```properties
  sdk.dir=C\:\\Users\\mathi\\AppData\\Local\\Android\\Sdk
  supabase.url=https://<your-project-ref>.supabase.co
  supabase.key=<your-anon-key>
  ```

---

## 2. Google OAuth 2.0 & Android SHA-1 Setup

Spendwise supports one-tap Google Sign-In on both Web and Android.

### Step 1: Google Cloud Console
1. Visit [Google Cloud Console](https://console.cloud.google.com/) $\rightarrow$ **APIs & Services** $\rightarrow$ **Credentials**.
2. Configure OAuth Consent Screen (App Name: *Spendwise*).
3. Create **OAuth 2.0 Client ID (Web Application)**:
   - Authorized JavaScript origins:
     - `http://localhost:3000`
     - Your production Vercel URL
     - `https://<your-project-ref>.supabase.co`
   - Authorized redirect URIs:
     - `https://<your-project-ref>.supabase.co/auth/v1/callback`
     - `http://localhost:3000/auth/callback`
4. Copy Web Client ID and Client Secret into Supabase $\rightarrow$ **Authentication** $\rightarrow$ **Providers** $\rightarrow$ **Google** and toggle **Enable**.

### Step 2: Android SHA-1 Keystore Fingerprint
To enable Google Sign-In via Android Credential Manager:
1. Run this command in your terminal:
   ```cmd
   keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
   ```
2. Copy the `SHA1:` fingerprint.
3. In Google Cloud Console $\rightarrow$ **Create Credentials** $\rightarrow$ **OAuth 2.0 Client ID**:
   - Application type: **Android**
   - Package name: `com.spendwise.app`
   - SHA-1 certificate fingerprint: Paste your copied SHA-1 string.

---

## 3. Running & Deploying the Web Client (Next.js)

### Local Development
```bash
cd web
npm run dev
```
Open [http://localhost:3000](http://localhost:3000) in your browser.

### Deploy to Vercel
1. Install Vercel CLI or connect your Git repository:
   ```bash
   npm i -g vercel
   cd web
   vercel
   ```
2. In the Vercel project settings, add the environment variables:
   - `NEXT_PUBLIC_SUPABASE_URL`
   - `NEXT_PUBLIC_SUPABASE_ANON_KEY`
3. Promote to production:
   ```bash
   vercel --prod
   ```

---

## 4. Building the Android App (Debug APK)

### Build Command
Run from the `android/` directory:
```bash
gradlew.bat assembleDebug
```

The compiled APK will be generated at:
```
android/app/build/outputs/apk/debug/app-debug.apk
```

### Install onto your Phone
Connect your Android phone via USB (with USB Debugging enabled in Developer Options) or start an emulator, then run:
```bash
adb install android/app/build/outputs/apk/debug/app-debug.apk
```
Or transfer the `.apk` file directly to your phone and tap to install!
