# Spendwise 2026 — Modern Calm Fintech Tracker

> A cross-platform personal finance and travel tracker built for 2026 with a calm, premium fintech design inspired by Apple Wallet, Revolut, and Copilot Money.

[![Next.js](https://img.shields.io/badge/Next.js-15.0-black?logo=next.js)](https://nextjs.org/)
[![Jetpack Compose](https://img.shields.io/badge/Android-Jetpack%20Compose-3DDC84?logo=android)](https://developer.android.com/jetpack/compose)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin)](https://kotlinlang.org/)
[![Supabase](https://img.shields.io/badge/Backend-Supabase-3ECF8E?logo=supabase)](https://supabase.com/)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind-3.4-38B2AC?logo=tailwind-css)](https://tailwindcss.com/)

---

## ✨ Features

- 🌙 **2026 Calm Fintech Design System**:
  - Rich near-black dark mode (`#090A0F`) & crisp neutral light mode (`#F8F9FC`).
  - Dynamic status bar and navigation bar icon color switching.
  - Large rounded corners (20–28px), generous spacing, tonal elevation surfaces, and subtle glassmorphism on key cards.
  - Violet/indigo brand accent (`#6366F1`), semantic emerald for inflow (`#10B981`), and rose for outflow (`#F43F5E`).
- ⚡ **Instant Theme Switching**: Quick-toggle button in the header (Sun/Moon) plus a dedicated **Theme & Appearance** selector (Dark, Light, System Default) in Settings.
- 📊 **Realtime Dashboard & Analytics**:
  - Hero card: Total net balance in large bold type with trend indicators (`+₹1,05,500 this month`).
  - Monthly Inflow & Outflow metric cards with smooth mini sparklines.
  - Interactive Donut Chart showing spending by category with percentages and clean legend alignment.
  - Recent activity grouped by friendly date (Today, Yesterday, Date).
- 🎯 **Category Budgets**:
  - Monthly category spending targets.
  - Color-coded progress states (Emerald < 80%, Amber warning at 80%, Rose alert at 100%+).
  - Clean `+ Add Budget` header button with zero floating action button overlap.
- ✈️ **Travel & Itinerary Tracking**:
  - Trip budget management, date ranges, and daily average spend calculations.
- 🔄 **Realtime Cloud Sync & Offline-First Architecture**:
  - Android: Offline-first Room database cache with background Supabase Realtime synchronization.
  - Web: Optimistic UI updates with instant Supabase CDC integration.

---

## 📱 Platforms

### 1. Android App (`/android`)
- **Framework**: Jetpack Compose & Material 3
- **Language**: Kotlin 2.0
- **Architecture**: MVVM with Clean Architecture
- **Dependency Injection**: Hilt
- **Local Database**: Android Jetpack Room with Kotlin Coroutines Flow
- **Remote Backend**: Supabase Kotlin SDK (`postgrest-kt`, `realtime-kt`, `auth-kt`)

### 2. Web Application (`/web`)
- **Framework**: Next.js 15 (App Router)
- **Styling**: Tailwind CSS & Lucide Icons
- **State & Theme**: React Context & `next-themes`
- **Backend SDK**: `@supabase/ssr` & `@supabase/supabase-js`

### 3. Backend & Database (`/supabase`)
- **Database**: PostgreSQL with Row Level Security (RLS) policies
- **Realtime**: PostgreSQL Change Data Capture (CDC) broadcast channels
- **Auth**: Email / Password & Google OAuth 2.0

---

## 📁 Repository Structure

```text
├── android/                   # Native Android application
│   ├── app/src/main/
│   │   ├── java/com/spendwise/app/
│   │   │   ├── data/          # Room DB, DAOs, Entities & Supabase Repository
│   │   │   ├── di/            # Hilt Dependency Injection modules
│   │   │   ├── ui/            # Compose screens (Dashboard, Budgets, Travel, Settings, etc.)
│   │   │   ├── ui/theme/      # 2026 Calm Fintech color tokens & typography
│   │   │   └── util/          # Currency formatters & helpers
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── web/                       # Next.js 15 web application
│   ├── src/app/               # App Router pages (Overview, Budgets, Trips, Activity, Settings)
│   ├── src/components/        # Reusable UI components & modals
│   ├── src/context/           # Finance state context with optimistic updates
│   ├── tailwind.config.ts     # 2026 design token configuration
│   └── vercel.json
├── supabase/                  # PostgreSQL schema, triggers & RLS policies
│   └── schema.sql
├── SETUP_GUIDE.md             # Detailed setup & configuration guide
└── .gitignore
```

---

## 🚀 Quick Start Guide

### Web Application (Next.js)

1. Navigate to the `web` directory:
   ```bash
   cd web
   npm install
   ```
2. Configure your `.env.local`:
   ```env
   NEXT_PUBLIC_SUPABASE_URL=https://<your-project>.supabase.co
   NEXT_PUBLIC_SUPABASE_ANON_KEY=<your-anon-key>
   NEXT_PUBLIC_DEFAULT_CURRENCY=INR
   ```
3. Start development server:
   ```bash
   npm run dev
   ```
4. Open [http://localhost:3000](http://localhost:3000) in your browser.

---

### Android Application

1. Open the `android/` directory in Android Studio.
2. Ensure Android SDK 34+ and JDK 17 are installed.
3. Build the debug APK via terminal:
   ```bash
   cd android
   gradlew assembleDebug
   ```
4. Output APK location:
   ```text
   android/app/build/outputs/apk/debug/app-debug.apk
   ```
5. Install on a connected device:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 🌐 Hosting & Deployment

### Deploy Web App to Vercel (Free & Instant)
1. Import this repository into [Vercel](https://vercel.com).
2. Set **Root Directory** to `web`.
3. Add environment variables:
   - `NEXT_PUBLIC_SUPABASE_URL`
   - `NEXT_PUBLIC_SUPABASE_ANON_KEY`
   - `NEXT_PUBLIC_DEFAULT_CURRENCY` = `INR`
4. Click **Deploy**.

### Distribute Android App
- **Direct Download**: Create a [GitHub Release](https://github.com/Mathidevdevil/Expancetraker/releases) and attach `app-debug.apk`.
- **Google Play Store**: Generate a signed Android App Bundle using `gradlew bundleRelease` and upload to Google Play Console.

---

## 📄 License

This project is licensed under the MIT License