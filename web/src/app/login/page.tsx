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

function LoginForm() {
  const router = useRouter();
  const supabase = createClient();
  const isConfigured = isSupabaseConfigured();
  const { login, isLoggedIn } = useFinance();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  const [emailError, setEmailError] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [authError, setAuthError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  // Redirect if already logged in
  useEffect(() => {
    if (isLoggedIn) {
      router.push('/');
    }
  }, [isLoggedIn, router]);

  // Google Identity Services setup
  useEffect(() => {
    if (typeof window === 'undefined') return;

    const handleCredentialResponse = (response: any) => {
      try {
        if (response.credential) {
          const base64Url = response.credential.split('.')[1];
          const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
          const jsonPayload = decodeURIComponent(
            atob(base64)
              .split('')
              .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
              .join('')
          );
          const decoded = JSON.parse(jsonPayload);
          const googleEmail = decoded.email || 'mathi.dev@gmail.com';
          const googleName = decoded.name || decoded.given_name || 'Google User';

          login(googleEmail, googleName);
          router.push('/');
        }
      } catch (err) {
        console.warn('Error parsing Google Credential', err);
        login('mathi.dev@gmail.com', 'Mathi Dev');
        router.push('/');
      }
    };

    const script = document.createElement('script');
    script.src = 'https://accounts.google.com/gsi/client';
    script.async = true;
    script.defer = true;
    script.onload = () => {
      try {
        if ((window as any).google?.accounts?.id) {
          (window as any).google.accounts.id.initialize({
            client_id: GOOGLE_CLIENT_ID,
            callback: handleCredentialResponse,
            auto_select: false,
            cancel_on_tap_outside: true,
          });
        }
      } catch (err) {
        console.warn('Google Identity initialize error', err);
      }
    };
    document.head.appendChild(script);

    return () => {
      try {
        document.head.removeChild(script);
      } catch (_) {}
    };
  }, [login, router]);

  const validateForm = (): boolean => {
    let valid = true;
    setEmailError('');
    setPasswordError('');
    setAuthError('');

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

    return valid;
  };

  const handleSignIn = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validateForm()) return;

    const cleanEmail = email.trim();
    setIsLoading(true);
    setAuthError('');

    try {
      if (isConfigured) {
        const { data, error } = await supabase.auth.signInWithPassword({
          email: cleanEmail,
          password: password,
        });

        if (error) {
          throw new Error('Incorrect email or password');
        }

        const displayName = data.user?.user_metadata?.full_name || cleanEmail.split('@')[0];
        login(cleanEmail, displayName);
        router.push('/');
        return;
      }

      // Offline / Local Demo fallback
      const cleanName = cleanEmail.split('@')[0].replace('.', ' ');
      const formattedName = cleanName.charAt(0).toUpperCase() + cleanName.slice(1);
      login(cleanEmail, formattedName || 'Spendwise User');
      router.push('/');
    } catch (err: any) {
      setAuthError(err.message === 'Incorrect email or password' ? err.message : 'Incorrect email or password');
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

      if (typeof window !== 'undefined' && (window as any).google?.accounts?.id) {
        try {
          (window as any).google.accounts.id.prompt((notification: any) => {
            if (notification.isNotDisplayed() || notification.isSkippedMoment()) {
              login('mathi.dev@gmail.com', 'Mathi Dev');
              router.push('/');
            }
          });
          return;
        } catch (_) {}
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
            Login
          </h1>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1.5 leading-relaxed max-w-xs">
            Welcome back. Track your income, expenses and trips.
          </p>
        </div>

        {/* Credential Error Banner */}
        {authError && (
          <div className="mt-6 p-3 rounded-[10px] bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900/60 flex items-center gap-2.5 text-rose-600 dark:text-rose-400 text-xs font-medium animate-shake">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{authError}</span>
          </div>
        )}

        {/* Login Form */}
        <form onSubmit={handleSignIn} className="mt-6 space-y-4" noValidate>
          {/* Email Field */}
          <div>
            <label
              htmlFor="email"
              className="block text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300 mb-1.5"
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
              className="block text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300 mb-1.5"
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
                if (authError) setAuthError('');
              }}
              placeholder="Enter password"
              autoComplete="current-password"
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

          {/* Show Password Checkbox */}
          <div className="flex items-center gap-2 pt-0.5">
            <input
              id="show-password"
              type="checkbox"
              checked={showPassword}
              onChange={(e) => setShowPassword(e.target.checked)}
              className="w-4 h-4 rounded border-slate-300 dark:border-slate-600 text-[#E7B94F] focus:ring-[#E7B94F] focus:ring-offset-0 cursor-pointer accent-[#E7B94F]"
            />
            <label
              htmlFor="show-password"
              className="text-xs font-medium text-slate-600 dark:text-slate-300 cursor-pointer select-none"
            >
              Show Password
            </label>
          </div>

          {/* Primary Action Button: SIGN IN */}
          <button
            type="submit"
            disabled={isLoading}
            className="w-full h-[52px] mt-2 rounded-[10px] bg-[#E7B94F] hover:bg-[#DDAE42] active:bg-[#CE9F34] text-[#0D0F1F] font-bold text-sm uppercase tracking-wider transition-all duration-150 shadow-md shadow-[#E7B94F]/20 flex items-center justify-center gap-2 disabled:opacity-60 disabled:cursor-not-allowed cursor-pointer"
          >
            {isLoading ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin text-[#0D0F1F]" />
                <span>SIGNING IN...</span>
              </>
            ) : (
              <span>SIGN IN</span>
            )}
          </button>
        </form>

        {/* OR Divider */}
        <div className="relative my-5 flex items-center justify-center">
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

        {/* Footer Navigation Links */}
        <div className="mt-6 text-center space-y-2">
          <div>
            <Link
              href="/forgot-password"
              className="text-xs font-semibold text-[#E7B94F] hover:text-[#DDAE42] transition-colors underline underline-offset-2"
            >
              Forgot Password?
            </Link>
          </div>
          <div>
            <span className="text-xs text-slate-500 dark:text-slate-400">
              Don't have an account?{' '}
            </span>
            <Link
              href="/signup"
              className="text-xs font-bold text-[#E7B94F] hover:text-[#DDAE42] transition-colors underline underline-offset-2"
            >
              Sign up
            </Link>
          </div>
        </div>

      </div>
    </div>
  );
}

export default function LoginPage() {
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
      <LoginForm />
    </React.Suspense>
  );
}
