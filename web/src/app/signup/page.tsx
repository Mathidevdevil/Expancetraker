'use client';

import React, { useState, useEffect } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { createClient, isSupabaseConfigured } from '@/lib/supabase/client';
import { useFinance } from '@/context/finance-context';
import { AlertCircle, Loader2 } from 'lucide-react';

const GOOGLE_CLIENT_ID =
  process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID ||
  '314420522490-1fj4onjt5vp556fbbbnrs1audgcoefde.apps.googleusercontent.com';

function SignUpForm() {
  const router = useRouter();
  const supabase = createClient();
  const isConfigured = isSupabaseConfigured();
  const { login, isLoggedIn } = useFinance();

  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  const [nameError, setNameError] = useState('');
  const [emailError, setEmailError] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [confirmPasswordError, setConfirmPasswordError] = useState('');
  const [authError, setAuthError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  // Redirect if already logged in
  useEffect(() => {
    if (isLoggedIn) {
      router.push('/');
    }
  }, [isLoggedIn, router]);

  const validateForm = (): boolean => {
    let valid = true;
    setNameError('');
    setEmailError('');
    setPasswordError('');
    setConfirmPasswordError('');
    setAuthError('');

    if (!fullName.trim()) {
      setNameError('Name is required.');
      valid = false;
    }

    const cleanEmail = email.trim();
    if (!cleanEmail) {
      setEmailError('Email is required.');
      valid = false;
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(cleanEmail)) {
      setEmailError('Please enter a valid email address.');
      valid = false;
    }

    if (!password) {
      setPasswordError('Password is required.');
      valid = false;
    } else if (password.length < 6) {
      setPasswordError('Password must be at least 6 characters.');
      valid = false;
    }

    if (!confirmPassword) {
      setConfirmPasswordError('Please confirm your password.');
      valid = false;
    } else if (password !== confirmPassword) {
      setConfirmPasswordError('Passwords do not match.');
      valid = false;
    }

    return valid;
  };

  const handleSignUp = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validateForm()) return;

    const cleanEmail = email.trim();
    const cleanName = fullName.trim();
    setIsLoading(true);
    setAuthError('');

    try {
      if (isConfigured) {
        const { data, error } = await supabase.auth.signUp({
          email: cleanEmail,
          password: password,
          options: {
            data: { full_name: cleanName },
          },
        });

        if (error) {
          throw error;
        }

        login(cleanEmail, cleanName);
        router.push('/');
        return;
      }

      // Offline / Local Demo fallback
      login(cleanEmail, cleanName || 'Spendwise User');
      router.push('/');
    } catch (err: any) {
      setAuthError(err.message || 'Failed to create account.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleGoogleSignIn = async () => {
    setAuthError('');
    setIsLoading(true);

    try {
      if (isConfigured) {
        const { error } = await supabase.auth.signInWithOAuth({
          provider: 'google',
          options: {
            redirectTo: `${window.location.origin}/auth/callback`,
          },
        });
        if (error) throw error;
        return;
      }

      login('mathi.dev@gmail.com', 'Mathi Dev');
      router.push('/');
    } catch (err: any) {
      setAuthError(err.message || 'Google sign-in error.');
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen w-full flex items-center justify-center p-4 sm:p-6 bg-gradient-to-b from-[#0D0F1F] via-[#111428] to-[#0A0C19] text-slate-100">
      {/* Centered Card (max-width 440px) */}
      <div className="w-full max-w-[440px] bg-white dark:bg-[#131629] text-[#0F172A] dark:text-[#F8FAFC] rounded-[16px] shadow-2xl shadow-black/50 border border-slate-200/80 dark:border-white/10 p-8 sm:p-10 transition-all duration-200">
        
        {/* Top: Spendwise Logo Icon (56px) */}
        <div className="flex flex-col items-center text-center">
          <div className="w-14 h-14 relative mb-3">
            <Image
              src="/spendwise-icon.svg"
              alt="Spendwise"
              width={56}
              height={56}
              priority
              className="w-14 h-14 object-contain drop-shadow-md"
            />
          </div>

          <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
            Create Account
          </h1>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1.5 leading-relaxed max-w-xs">
            Join Spendwise to master your personal finance & trips.
          </p>
        </div>

        {/* Auth Error Banner */}
        {authError && (
          <div className="mt-5 p-3 rounded-[10px] bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900/60 flex items-center gap-2.5 text-rose-600 dark:text-rose-400 text-xs font-medium animate-shake">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{authError}</span>
          </div>
        )}

        {/* Sign-Up Form */}
        <form onSubmit={handleSignUp} className="mt-5 space-y-3.5" noValidate>
          {/* Name Field */}
          <div>
            <label
              htmlFor="fullName"
              className="block text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300 mb-1"
            >
              Name
            </label>
            <input
              id="fullName"
              type="text"
              value={fullName}
              onChange={(e) => {
                setFullName(e.target.value);
                if (nameError) setNameError('');
              }}
              placeholder="Enter name"
              autoComplete="name"
              className={`w-full h-[52px] px-3.5 rounded-[10px] bg-slate-50 dark:bg-[#1A1E36] border text-sm text-slate-900 dark:text-white placeholder:text-slate-400 dark:placeholder:text-slate-500 transition-all duration-150 outline-none ${
                nameError
                  ? 'border-rose-500 ring-2 ring-rose-500/20'
                  : 'border-slate-300 dark:border-slate-700/80 focus:border-[#E7B94F] focus:ring-2 focus:ring-[#E7B94F]/25'
              }`}
            />
            {nameError && (
              <p className="text-xs text-rose-500 mt-1 font-medium pl-0.5">{nameError}</p>
            )}
          </div>

          {/* Email Field */}
          <div>
            <label
              htmlFor="email"
              className="block text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300 mb-1"
            >
              Email
            </label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
                if (emailError) setEmailError('');
                if (authError) setAuthError('');
              }}
              placeholder="Enter email"
              autoComplete="email"
              className={`w-full h-[52px] px-3.5 rounded-[10px] bg-slate-50 dark:bg-[#1A1E36] border text-sm text-slate-900 dark:text-white placeholder:text-slate-400 dark:placeholder:text-slate-500 transition-all duration-150 outline-none ${
                emailError
                  ? 'border-rose-500 ring-2 ring-rose-500/20'
                  : 'border-slate-300 dark:border-slate-700/80 focus:border-[#E7B94F] focus:ring-2 focus:ring-[#E7B94F]/25'
              }`}
            />
            {emailError && (
              <p className="text-xs text-rose-500 mt-1 font-medium pl-0.5">{emailError}</p>
            )}
          </div>

          {/* Password Field */}
          <div>
            <label
              htmlFor="password"
              className="block text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300 mb-1"
            >
              Password
            </label>
            <input
              id="password"
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
                if (passwordError) setPasswordError('');
              }}
              placeholder="Enter password"
              autoComplete="new-password"
              className={`w-full h-[52px] px-3.5 rounded-[10px] bg-slate-50 dark:bg-[#1A1E36] border text-sm text-slate-900 dark:text-white placeholder:text-slate-400 dark:placeholder:text-slate-500 transition-all duration-150 outline-none ${
                passwordError
                  ? 'border-rose-500 ring-2 ring-rose-500/20'
                  : 'border-slate-300 dark:border-slate-700/80 focus:border-[#E7B94F] focus:ring-2 focus:ring-[#E7B94F]/25'
              }`}
            />
            {passwordError && (
              <p className="text-xs text-rose-500 mt-1 font-medium pl-0.5">{passwordError}</p>
            )}
          </div>

          {/* Confirm Password Field */}
          <div>
            <label
              htmlFor="confirmPassword"
              className="block text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300 mb-1"
            >
              Confirm Password
            </label>
            <input
              id="confirmPassword"
              type={showPassword ? 'text' : 'password'}
              value={confirmPassword}
              onChange={(e) => {
                setConfirmPassword(e.target.value);
                if (confirmPasswordError) setConfirmPasswordError('');
              }}
              placeholder="Confirm password"
              autoComplete="new-password"
              className={`w-full h-[52px] px-3.5 rounded-[10px] bg-slate-50 dark:bg-[#1A1E36] border text-sm text-slate-900 dark:text-white placeholder:text-slate-400 dark:placeholder:text-slate-500 transition-all duration-150 outline-none ${
                confirmPasswordError
                  ? 'border-rose-500 ring-2 ring-rose-500/20'
                  : 'border-slate-300 dark:border-slate-700/80 focus:border-[#E7B94F] focus:ring-2 focus:ring-[#E7B94F]/25'
              }`}
            />
            {confirmPasswordError && (
              <p className="text-xs text-rose-500 mt-1 font-medium pl-0.5">{confirmPasswordError}</p>
            )}
          </div>

          {/* Show Password Checkbox */}
          <div className="flex items-center gap-2 pt-0.5">
            <input
              id="show-password-signup"
              type="checkbox"
              checked={showPassword}
              onChange={(e) => setShowPassword(e.target.checked)}
              className="w-4 h-4 rounded border-slate-300 dark:border-slate-600 text-[#E7B94F] focus:ring-[#E7B94F] focus:ring-offset-0 cursor-pointer accent-[#E7B94F]"
            />
            <label
              htmlFor="show-password-signup"
              className="text-xs font-medium text-slate-600 dark:text-slate-300 cursor-pointer select-none"
            >
              Show Password
            </label>
          </div>

          {/* Primary Action Button: CREATE ACCOUNT */}
          <button
            type="submit"
            disabled={isLoading}
            className="w-full h-[52px] mt-2 rounded-[10px] bg-[#E7B94F] hover:bg-[#DDAE42] active:bg-[#CE9F34] text-[#0D0F1F] font-bold text-sm uppercase tracking-wider transition-all duration-150 shadow-md shadow-[#E7B94F]/20 flex items-center justify-center gap-2 disabled:opacity-60 disabled:cursor-not-allowed cursor-pointer"
          >
            {isLoading ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin text-[#0D0F1F]" />
                <span>CREATING ACCOUNT...</span>
              </>
            ) : (
              <span>CREATE ACCOUNT</span>
            )}
          </button>
        </form>

        {/* OR Divider */}
        <div className="relative my-4 flex items-center justify-center">
          <div className="w-full border-t border-slate-200 dark:border-slate-800" />
          <span className="absolute px-3 bg-white dark:bg-[#131629] text-[11px] font-bold text-slate-400 dark:text-slate-500 uppercase tracking-widest">
            OR
          </span>
        </div>

        {/* Continue with Google Button */}
        <button
          type="button"
          onClick={handleGoogleSignIn}
          disabled={isLoading}
          className="w-full h-[52px] rounded-[10px] bg-slate-50 hover:bg-slate-100 dark:bg-[#1A1E36] dark:hover:bg-[#202542] border border-slate-200 dark:border-slate-700/80 text-sm font-semibold text-slate-800 dark:text-slate-100 flex items-center justify-center gap-3 transition-colors duration-150 shadow-sm cursor-pointer disabled:opacity-60"
        >
          <svg className="w-5 h-5 shrink-0" viewBox="0 0 24 24">
            <path
              fill="#4285F4"
              d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
            />
            <path
              fill="#34A853"
              d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
            />
            <path
              fill="#FBBC05"
              d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
            />
            <path
              fill="#EA4335"
              d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
            />
          </svg>
          <span>Continue with Google</span>
        </button>

        {/* Footer Link */}
        <div className="mt-5 text-center">
          <span className="text-xs text-slate-500 dark:text-slate-400">
            Already have an account?{' '}
          </span>
          <Link
            href="/login"
            className="text-xs font-bold text-[#E7B94F] hover:text-[#DDAE42] transition-colors underline underline-offset-2"
          >
            Log in
          </Link>
        </div>

      </div>
    </div>
  );
}

export default function SignUpPage() {
  return (
    <React.Suspense
      fallback={
        <div className="min-h-screen bg-[#0D0F1F] flex items-center justify-center text-slate-400">
          <div className="flex items-center gap-2">
            <div className="w-5 h-5 border-2 border-[#E7B94F] border-t-transparent rounded-full animate-spin" />
            <span className="text-sm font-medium">Loading Spendwise...</span>
          </div>
        </div>
      }
    >
      <SignUpForm />
    </React.Suspense>
  );
}
