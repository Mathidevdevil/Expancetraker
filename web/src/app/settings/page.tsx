'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { useFinance } from '@/context/finance-context';
import { CURRENCIES, CurrencyCode } from '@/types/database';
import { useTheme } from 'next-themes';
import { createClient } from '@/lib/supabase/client';
import { EditProfileModal } from '@/components/edit-profile-modal';
import {
  Coins,
  Moon,
  Sun,
  Laptop,
  LogOut,
  LogIn,
  Database,
  User,
  Check,
  ShieldCheck,
  Edit3,
} from 'lucide-react';

export default function SettingsPage() {
  const { currency, setCurrencyCode, userProfile, user, isSupabaseLive, isRealtimeConnected } = useFinance();
  const { theme, setTheme } = useTheme();
  const supabase = createClient();

  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const [isEditProfileOpen, setIsEditProfileOpen] = useState(false);

  const handleLogout = async () => {
    setIsLoggingOut(true);
    try {
      if (isSupabaseLive) {
        await supabase.auth.signOut();
      }
      window.location.href = '/login';
    } finally {
      setIsLoggingOut(false);
    }
  };

  return (
    <div className="space-y-6 max-w-3xl animate-in fade-in duration-300 pb-16">
      <div>
        <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-foreground">
          Settings
        </h1>
        <p className="text-sm text-muted-foreground mt-1">
          Profile, preferences, default currency, and cloud synchronization
        </p>
      </div>

      {/* User Profile Card */}
      <div className="p-7 rounded-3xl fintech-card space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="flex items-center gap-4">
            <div className="w-14 h-14 rounded-2xl bg-primary/15 border border-primary/30 text-primary flex items-center justify-center font-black text-xl shadow-inner shrink-0">
              {userProfile.initials}
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg font-bold text-foreground">{userProfile.name}</h2>
                <span className="px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-500 text-[10px] font-bold border border-emerald-500/20">
                  Active
                </span>
              </div>
              <p className="text-xs text-muted-foreground mt-0.5">{userProfile.email}</p>
              {userProfile.bio && (
                <p className="text-xs text-muted-foreground/80 mt-1 italic">
                  &ldquo;{userProfile.bio}&rdquo;
                </p>
              )}
            </div>
          </div>

          <button
            onClick={() => setIsEditProfileOpen(true)}
            className="px-4 py-2.5 rounded-2xl bg-primary hover:bg-primary/90 text-primary-foreground text-xs font-bold flex items-center justify-center gap-2 shadow-sm transition-all active:scale-[0.98] shrink-0"
          >
            <Edit3 className="w-3.5 h-3.5" />
            <span>Edit Profile</span>
          </button>
        </div>
      </div>

      {/* Currency Selection */}
      <div className="p-7 rounded-3xl fintech-card space-y-4">
        <div className="flex items-center gap-3.5">
          <div className="p-3 rounded-2xl bg-primary/10 text-primary">
            <Coins className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-bold text-foreground">Default Currency</h2>
            <p className="text-xs text-muted-foreground">Used across dashboard metrics, budget limits, and CSV reports</p>
          </div>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-2">
          {(Object.keys(CURRENCIES) as CurrencyCode[]).map((code) => {
            const config = CURRENCIES[code];
            const isSelected = currency.code === code;

            return (
              <button
                key={code}
                onClick={() => setCurrencyCode(code)}
                className={`p-4 rounded-2xl border text-left transition-all ${
                  isSelected
                    ? 'border-primary bg-primary/10 text-primary shadow-sm'
                    : 'border-border bg-secondary/30 hover:bg-secondary/60 text-foreground'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="text-2xl font-black">{config.symbol}</span>
                  {isSelected && <Check className="w-4 h-4 text-primary" />}
                </div>
                <p className="text-xs font-bold mt-1.5">{config.code}</p>
                <p className="text-[11px] text-muted-foreground truncate">{config.name}</p>
              </button>
            );
          })}
        </div>
      </div>

      {/* Appearance & Theme */}
      <div className="p-7 rounded-3xl fintech-card space-y-4">
        <div className="flex items-center gap-3.5">
          <div className="p-3 rounded-2xl bg-primary/10 text-primary">
            <Sun className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-bold text-foreground">Theme & Appearance</h2>
            <p className="text-xs text-muted-foreground">Toggle between 2026 Calm Dark (default) and Clean Light mode</p>
          </div>
        </div>

        <div className="grid grid-cols-3 gap-3 pt-2">
          <button
            onClick={() => setTheme('dark')}
            className={`p-4 rounded-2xl border flex flex-col items-center gap-2 transition-all ${
              theme === 'dark'
                ? 'border-primary bg-primary/10 text-primary font-bold shadow-sm'
                : 'border-border bg-secondary/30 hover:bg-secondary/60 text-muted-foreground'
            }`}
          >
            <Moon className="w-5 h-5" />
            <span className="text-xs">Dark Mode</span>
          </button>
          <button
            onClick={() => setTheme('light')}
            className={`p-4 rounded-2xl border flex flex-col items-center gap-2 transition-all ${
              theme === 'light'
                ? 'border-primary bg-primary/10 text-primary font-bold shadow-sm'
                : 'border-border bg-secondary/30 hover:bg-secondary/60 text-muted-foreground'
            }`}
          >
            <Sun className="w-5 h-5" />
            <span className="text-xs">Light Mode</span>
          </button>
          <button
            onClick={() => setTheme('system')}
            className={`p-4 rounded-2xl border flex flex-col items-center gap-2 transition-all ${
              theme === 'system'
                ? 'border-primary bg-primary/10 text-primary font-bold shadow-sm'
                : 'border-border bg-secondary/30 hover:bg-secondary/60 text-muted-foreground'
            }`}
          >
            <Laptop className="w-5 h-5" />
            <span className="text-xs">System</span>
          </button>
        </div>
      </div>

      {/* Cloud & Realtime Status */}
      <div className="p-7 rounded-3xl fintech-card space-y-4">
        <div className="flex items-center gap-3.5">
          <div className="p-3 rounded-2xl bg-emerald-500/10 text-emerald-500">
            <Database className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-bold text-foreground">Backend & Realtime Status</h2>
            <p className="text-xs text-muted-foreground">Multi-device sync state via Supabase PostgreSQL</p>
          </div>
        </div>

        <div className="p-4 rounded-2xl bg-secondary/40 border border-border/50 flex items-center justify-between text-xs">
          <div className="flex items-center gap-2.5">
            <div
              className={`w-2.5 h-2.5 rounded-full ${
                isRealtimeConnected ? 'bg-emerald-500 animate-pulse' : 'bg-muted-foreground'
              }`}
            />
            <span className="font-semibold text-foreground">
              {isRealtimeConnected ? 'Supabase Realtime Channel Connected' : isSupabaseLive ? 'Supabase Connected' : 'Local Offline Mode'}
            </span>
          </div>
          <span className="text-muted-foreground font-mono">postgres-cdc</span>
        </div>
      </div>

      {/* User Session & Logout */}
      <div className="p-7 rounded-3xl fintech-card space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3.5">
            <div className="p-3 rounded-2xl bg-secondary text-foreground">
              <User className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-foreground">Active Session</h2>
              <p className="text-xs text-muted-foreground">
                {user?.email || 'Demo Mode (Offline Mock User)'}
              </p>
            </div>
          </div>

          {user ? (
            <button
              onClick={handleLogout}
              disabled={isLoggingOut}
              className="px-4 py-2 rounded-2xl bg-rose-500/10 text-rose-500 hover:bg-rose-500/20 text-xs font-semibold flex items-center gap-2 transition-colors border border-rose-500/20"
            >
              <LogOut className="w-3.5 h-3.5" />
              <span>{isLoggingOut ? 'Signing out...' : 'Sign Out'}</span>
            </button>
          ) : (
            <Link
              href="/login"
              className="px-4 py-2 rounded-2xl bg-primary text-primary-foreground hover:bg-primary/90 text-xs font-semibold flex items-center gap-2 transition-colors shadow-sm"
            >
              <LogIn className="w-3.5 h-3.5" />
              <span>Sign In to Cloud Account</span>
            </Link>
          )}
        </div>
      </div>

      <EditProfileModal
        isOpen={isEditProfileOpen}
        onClose={() => setIsEditProfileOpen(false)}
      />
    </div>
  );
}
