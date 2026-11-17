'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams, usePathname } from 'next/navigation';
import { createClient, isSupabaseConfigured } from '@/lib/supabase/client';
import { useFinance } from '@/context/finance-context';
import { useTheme } from 'next-themes';
import {
  Wallet,
  Mail,
  Lock,
  User,
  Eye,
  EyeOff,
  ArrowRight,
  ArrowLeft,
  Sparkles,
  TrendingUp,
  Sun,
  Moon,
  CheckCircle2,
  KeyRound,
  RotateCcw,
} from 'lucide-react';

const GOOGLE_CLIENT_ID =
  process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID ||
  '176968816003-6f79gedb3hl43fnp62n74a1jn5mq6ii2.apps.googleusercontent.com';

type AuthMode = 'signin' | 'signup' | 'otp';

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const pathname = usePathname();
  const supabase = createClient();
  const isConfigured = isSupabaseConfigured();
  const { login, isLoggedIn } = useFinance();
  const { resolvedTheme, setTheme } = useTheme();

  // Primary mode: 'signin' | 'signup' | 'otp'
  const initialMode = searchParams.get('mode') === 'signup' || pathname?.includes('/signup') ? 'signup' : 'signin';
  const [authMode, setAuthMode] = useState<AuthMode>(initialMode);

  // Form states
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(true);

  // OTP flow states (optional secondary option)
  const [otpSent, setOtpSent] = useState(false);
  const [otpCode, setOtpCode] = useState('');
  const [countdown, setCountdown] = useState(0);

  // Status & Feedback
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [successMsg, setSuccessMsg] = useState('');
  const [shakeError, setShakeError] = useState(false);

  // Shake animation on error
  useEffect(() => {
    if (errorMsg) {
      setShakeError(true);
      const timer = setTimeout(() => setShakeError(false), 400);
      return () => clearTimeout(timer);
    }
  }, [errorMsg]);

  // Redirect if already logged in
  useEffect(() => {
    if (isLoggedIn) {
      router.push('/');
    }
  }, [isLoggedIn, router]);

  // Resend countdown timer
  useEffect(() => {
    if (countdown > 0) {
      const timer = setTimeout(() => setCountdown(countdown - 1), 1000);
      return () => clearTimeout(timer);
    }
  }, [countdown]);

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

  // 1. Regular Sign In Handler
  const handleSignIn = async (e: React.FormEvent) => {
    e.preventDefault();
    const cleanEmail = email.trim();
    if (!cleanEmail || !password) {
      setErrorMsg('Please enter both your email and password.');
      return;
    }

    setLoading(true);
    setErrorMsg('');
    setSuccessMsg('');

    try {
      if (!isConfigured) {
        login(cleanEmail);
        router.push('/');
        return;
      }

      const { data, error } = await supabase.auth.signInWithPassword({
        email: cleanEmail,
        password,
      });

      if (error) throw error;

      const metaName = data.session?.user?.user_metadata?.full_name || data.session?.user?.user_metadata?.name;
      login(cleanEmail, metaName);
      router.push('/');
    } catch (err: any) {
      setErrorMsg(err.message || 'Invalid email or password. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  // 2. Regular Sign Up Handler
  const handleSignUp = async (e: React.FormEvent) => {
    e.preventDefault();
    const cleanEmail = email.trim();
    if (!cleanEmail || !password) {
      setErrorMsg('Please enter an email and password.');
      return;
    }

    if (password.length < 6) {
      setErrorMsg('Password must be at least 6 characters.');
      return;
    }

    if (confirmPassword && password !== confirmPassword) {
      setErrorMsg('Passwords do not match.');
      return;
    }

    setLoading(true);
    setErrorMsg('');
    setSuccessMsg('');

    try {
      if (!isConfigured) {
        login(cleanEmail, fullName.trim() || undefined);
        router.push('/');
        return;
      }

      const { data, error } = await supabase.auth.signUp({
        email: cleanEmail,
        password,
        options: {
          data: {
            full_name: fullName.trim() || cleanEmail.split('@')[0],
          },
        },
      });

      if (error) throw error;

      if (data.session) {
        login(cleanEmail, fullName.trim() || undefined);
        router.push('/');
      } else {
        setSuccessMsg('Account created successfully! You can now sign in.');
        setAuthMode('signin');
      }
    } catch (err: any) {
      setErrorMsg(err.message || 'Failed to create account. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  // 3. Google OAuth Sign In
  const handleGoogleSignIn = async () => {
    setErrorMsg('');
    setLoading(true);

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
      setErrorMsg(err.message || 'Google sign-in error.');
      setLoading(false);
    }
  };

  // 4. Send Email OTP (Optional feature)
  const handleSendOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    const cleanEmail = email.trim();
    if (!cleanEmail) {
      setErrorMsg('Please enter your email address.');
      return;
    }

    setLoading(true);
    setErrorMsg('');

    try {
      if (!isConfigured) {
        setOtpSent(true);
        setCountdown(30);
        setSuccessMsg('Verification code sent! (Demo mode code: 123456)');
        return;
      }

      const { error } = await supabase.auth.signInWithOtp({
        email: cleanEmail,
        options: {
          shouldCreateUser: true,
          emailRedirectTo: `${window.location.origin}/auth/callback`,
        },
      });

      if (error) throw error;

      setOtpSent(true);
      setCountdown(30);
      setSuccessMsg(`6-digit code sent to ${cleanEmail}`);
    } catch (err: any) {
      setErrorMsg(err.message || 'Failed to send code.');
    } finally {
      setLoading(false);
    }
  };

  // 5. Verify Email OTP
  const handleVerifyOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!otpCode || otpCode.length < 6) {
      setErrorMsg('Please enter the 6-digit code.');
      return;
    }

    setLoading(true);
    setErrorMsg('');

    try {
      if (!isConfigured) {
        if (otpCode !== '123456') {
          throw new Error('Invalid code. For demo testing, enter 123456.');
        }
        login(email.trim());
        router.push('/');
        return;
      }

      const { data, error } = await supabase.auth.verifyOtp({
        email: email.trim(),
        token: otpCode.trim(),
        type: 'email',
      });

      if (error) throw error;

      const metaName = data.session?.user?.user_metadata?.full_name || data.session?.user?.user_metadata?.name;
      login(email.trim(), metaName);
      router.push('/');
    } catch (err: any) {
      setErrorMsg(err.message || 'Invalid or expired code.');
    } finally {
      setLoading(false);
    }
  };

  // 6. Demo Mode
  const handleDemoMode = () => {
    login('mathi.dev@gmail.com', 'Mathi Dev');
    router.push('/');
  };

  const isDark = resolvedTheme === 'dark';

  return (
    <div className="min-h-[100dvh] w-full bg-[#0B0B10] text-zinc-100 dark:bg-[#0B0B10] dark:text-zinc-100 flex flex-col justify-between selection:bg-emerald-500/20 selection:text-emerald-400 font-sans relative overflow-x-hidden">
      {/* Background Subtle Gradient Glow */}
      <div className="absolute top-0 left-1/2 -translate-x-1/2 w-full max-w-lg h-96 bg-gradient-to-b from-emerald-500/10 via-transparent to-transparent blur-3xl pointer-events-none" />

      {/* Main Container: Mobile column, Desktop 2-column layout */}
      <div className="w-full max-w-5xl mx-auto px-4 sm:px-8 pt-4 pb-4 sm:py-10 flex-1 flex flex-col lg:grid lg:grid-cols-2 lg:items-center lg:gap-16 relative z-10">
        
        {/* ================= LEFT / HERO AREA ================= */}
        <div className="flex flex-col justify-between pt-1 pb-4 sm:pb-6 lg:py-12">
          {/* Top Brand Header */}
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <div className="w-9 h-9 rounded-xl bg-emerald-500 flex items-center justify-center text-black shadow-md shadow-emerald-500/20">
                <Wallet className="w-5 h-5 stroke-[2.4]" />
              </div>
              <span className="text-xl font-bold tracking-tight text-white">Spendwise</span>
            </div>

            {/* Subtle Theme Toggle */}
            <button
              type="button"
              onClick={() => setTheme(isDark ? 'light' : 'dark')}
              aria-label="Toggle theme"
              className="w-9 h-9 rounded-full bg-zinc-900 border border-zinc-800 text-zinc-400 hover:text-zinc-200 flex items-center justify-center transition-colors active:scale-95"
            >
              {isDark ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
            </button>
          </div>

          {/* Hero Typography */}
          <div className="mt-4 sm:mt-8 lg:mt-12 space-y-1.5 sm:space-y-3">
            <h1 className="text-2xl sm:text-4xl lg:text-5xl font-extrabold tracking-tight text-white leading-tight">
              Know where every <span className="text-emerald-400">rupee</span> goes.
            </h1>
            <p className="text-xs sm:text-sm lg:text-base text-zinc-400 font-normal leading-relaxed">
              Track income, expenses and trips in one calm, unified place.
            </p>
          </div>

          {/* Decorative Mock Balance & Activity Preview */}
          <div className="mt-4 sm:mt-7 relative max-w-sm">
            <div className="animate-float p-3.5 sm:p-5 rounded-2xl bg-gradient-to-b from-zinc-900/90 to-zinc-950/90 border border-zinc-800/80 shadow-xl shadow-black/40 backdrop-blur-md">
              <div className="flex items-center justify-between text-[11px] sm:text-xs text-zinc-400">
                <span className="font-medium">Total Net Balance</span>
                <span className="inline-flex items-center gap-1 text-[10px] sm:text-[11px] font-semibold text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded-full border border-emerald-500/20">
                  <TrendingUp className="w-2.5 h-2.5 sm:w-3 sm:h-3" />
                  +14.2% this month
                </span>
              </div>
              <div className="mt-1.5 flex items-baseline gap-1.5">
                <span className="text-2xl sm:text-3xl font-extrabold tracking-tight text-white">₹ 97,650</span>
                <span className="text-[10px] sm:text-xs text-zinc-500 font-medium">INR</span>
              </div>

              {/* Category distribution bar */}
              <div className="mt-3 space-y-1.5">
                <div className="h-1.5 sm:h-2 w-full rounded-full bg-zinc-800 overflow-hidden flex gap-0.5 p-0.5">
                  <div className="h-full bg-emerald-400 rounded-full w-[42%]" />
                  <div className="h-full bg-amber-400 rounded-full w-[26%]" />
                  <div className="h-full bg-cyan-400 rounded-full w-[18%]" />
                  <div className="h-full bg-rose-400 rounded-full w-[14%]" />
                </div>
                <div className="flex items-center justify-between text-[10px] sm:text-[11px] text-zinc-400 pt-0.5">
                  <span className="inline-flex items-center gap-1.5">
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                    Food & Dining
                  </span>
                  <span className="font-medium text-zinc-300">₹8,250 (42%)</span>
                </div>
              </div>
            </div>

            {/* Floating Savings Chip */}
            <div className="absolute -bottom-2 right-1 sm:right-4 animate-float [animation-delay:1.5s] inline-flex items-center gap-1.5 px-2.5 sm:px-3.5 py-1 rounded-full bg-zinc-900/95 border border-emerald-500/30 shadow-lg shadow-emerald-500/10 text-[10px] sm:text-xs font-semibold text-emerald-400">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-ping" />
              <span>₹ 12,450 saved this month</span>
            </div>
          </div>
        </div>

        {/* ================= RIGHT / AUTH CONTAINER ================= */}
        <div className="w-full max-w-md mx-auto lg:max-w-none mt-auto lg:mt-0">
          <div className="rounded-t-[28px] lg:rounded-[28px] bg-[#12131A] border-t lg:border border-zinc-800/90 shadow-2xl p-5 sm:p-8 space-y-4 sm:space-y-5 transition-all">
            
            {/* 1. Regular Segmented Switcher: Sign In vs Sign Up */}
            {authMode !== 'otp' && (
              <div className="grid grid-cols-2 p-1 rounded-xl bg-zinc-900/90 border border-zinc-800 text-xs font-semibold">
                <button
                  type="button"
                  onClick={() => {
                    setAuthMode('signin');
                    setErrorMsg('');
                    setSuccessMsg('');
                  }}
                  className={`py-2.5 rounded-lg transition-all ${
                    authMode === 'signin'
                      ? 'bg-emerald-500 text-black font-bold shadow-sm shadow-emerald-500/20'
                      : 'text-zinc-400 hover:text-white'
                  }`}
                >
                  Sign In
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setAuthMode('signup');
                    setErrorMsg('');
                    setSuccessMsg('');
                  }}
                  className={`py-2.5 rounded-lg transition-all ${
                    authMode === 'signup'
                      ? 'bg-emerald-500 text-black font-bold shadow-sm shadow-emerald-500/20'
                      : 'text-zinc-400 hover:text-white'
                  }`}
                >
                  Sign Up
                </button>
              </div>
            )}

            {/* Error Message */}
            {errorMsg && (
              <div
                role="alert"
                className={`p-3.5 rounded-xl bg-rose-500/10 border border-rose-500/25 text-xs text-rose-400 flex items-start gap-2.5 ${
                  shakeError ? 'animate-shake' : ''
                }`}
              >
                <div className="w-1.5 h-1.5 rounded-full bg-rose-400 mt-1.5 shrink-0" />
                <span className="flex-1 font-medium leading-relaxed">{errorMsg}</span>
              </div>
            )}

            {/* Success Message */}
            {successMsg && (
              <div className="p-3.5 rounded-xl bg-emerald-500/10 border border-emerald-500/25 text-xs text-emerald-400 flex items-center gap-2 font-medium">
                <CheckCircle2 className="w-4 h-4 shrink-0" />
                <span>{successMsg}</span>
              </div>
            )}

            {/* ---------------- A. REGULAR SIGN IN FORM ---------------- */}
            {authMode === 'signin' && (
              <form onSubmit={handleSignIn} className="space-y-3.5 animate-in fade-in duration-200">
                <div className="space-y-1">
                  <h2 className="text-xl font-bold tracking-tight text-white">Welcome back</h2>
                  <p className="text-xs text-zinc-400">Enter your credentials to access your account</p>
                </div>

                {/* Email Input */}
                <div className="space-y-1.5">
                  <label className="block text-[11px] font-semibold text-zinc-400 uppercase tracking-wider">
                    Email Address
                  </label>
                  <div className="relative">
                    <Mail className="w-4 h-4 text-zinc-500 absolute left-3.5 top-1/2 -translate-y-1/2" />
                    <input
                      type="email"
                      required
                      placeholder="alex.morgan@spendwise.app"
                      value={email}
                      onChange={(e) => {
                        setEmail(e.target.value);
                        if (errorMsg) setErrorMsg('');
                      }}
                      className="w-full h-[52px] pl-10 pr-4 rounded-[14px] bg-zinc-900 border border-zinc-800 text-white placeholder-zinc-500 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/40 focus:border-emerald-500 transition-all"
                    />
                  </div>
                </div>

                {/* Password Input */}
                <div className="space-y-1.5">
                  <div className="flex items-center justify-between">
                    <label className="block text-[11px] font-semibold text-zinc-400 uppercase tracking-wider">
                      Password
                    </label>
                    <button
                      type="button"
                      onClick={() => setErrorMsg('For demo mode, simply click Sign In or use Demo Mode below.')}
                      className="text-[11px] text-emerald-400 hover:underline font-medium"
                    >
                      Forgot?
                    </button>
                  </div>
                  <div className="relative">
                    <Lock className="w-4 h-4 text-zinc-500 absolute left-3.5 top-1/2 -translate-y-1/2" />
                    <input
                      type={showPassword ? 'text' : 'password'}
                      required
                      placeholder="••••••••"
                      value={password}
                      onChange={(e) => {
                        setPassword(e.target.value);
                        if (errorMsg) setErrorMsg('');
                      }}
                      className="w-full h-[52px] pl-10 pr-11 rounded-[14px] bg-zinc-900 border border-zinc-800 text-white placeholder-zinc-500 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/40 focus:border-emerald-500 transition-all"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      className="absolute right-3.5 top-1/2 -translate-y-1/2 text-zinc-400 hover:text-zinc-200 transition-colors p-1"
                      aria-label={showPassword ? 'Hide password' : 'Show password'}
                    >
                      {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>

                {/* Primary Sign In Button */}
                <button
                  type="submit"
                  disabled={loading}
                  className="w-full h-[54px] rounded-[14px] bg-emerald-500 hover:bg-emerald-400 text-black font-semibold text-sm transition-transform active:scale-[0.98] disabled:opacity-40 disabled:pointer-events-none flex items-center justify-center gap-2 shadow-lg shadow-emerald-500/20"
                >
                  {loading ? (
                    <span className="inline-block w-4 h-4 border-2 border-black border-t-transparent rounded-full animate-spin" />
                  ) : (
                    <>
                      <span>Sign In</span>
                      <ArrowRight className="w-4 h-4" />
                    </>
                  )}
                </button>

                {/* Divider */}
                <div className="relative py-1">
                  <div className="absolute inset-0 flex items-center">
                    <div className="w-full border-t border-zinc-800/80" />
                  </div>
                  <div className="relative flex justify-center text-[11px] uppercase tracking-wider">
                    <span className="bg-[#12131A] px-3 text-zinc-500 font-medium">or continue with</span>
                  </div>
                </div>

                {/* Google Sign In Button */}
                <button
                  type="button"
                  onClick={handleGoogleSignIn}
                  disabled={loading}
                  className="w-full h-[54px] px-4 rounded-[14px] bg-white hover:bg-zinc-100 text-zinc-900 font-semibold text-sm border border-zinc-200 shadow-sm flex items-center justify-center gap-3 transition-transform active:scale-[0.98] disabled:opacity-50"
                >
                  <svg className="w-4 h-4 shrink-0" viewBox="0 0 24 24">
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

                {/* Footer Switcher */}
                <div className="flex items-center justify-between text-xs text-zinc-400 pt-1">
                  <span>Don't have an account?</span>
                  <button
                    type="button"
                    onClick={() => {
                      setAuthMode('signup');
                      setErrorMsg('');
                    }}
                    className="text-emerald-400 font-semibold hover:underline"
                  >
                    Sign Up
                  </button>
                </div>
              </form>
            )}

            {/* ---------------- B. REGULAR SIGN UP FORM ---------------- */}
            {authMode === 'signup' && (
              <form onSubmit={handleSignUp} className="space-y-3.5 animate-in fade-in duration-200">
                <div className="space-y-1">
                  <h2 className="text-xl font-bold tracking-tight text-white">Create your account</h2>
                  <p className="text-xs text-zinc-400">Join Spendwise to start tracking your wealth</p>
                </div>

                {/* Full Name */}
                <div className="space-y-1.5">
                  <label className="block text-[11px] font-semibold text-zinc-400 uppercase tracking-wider">
                    Full Name
                  </label>
                  <div className="relative">
                    <User className="w-4 h-4 text-zinc-500 absolute left-3.5 top-1/2 -translate-y-1/2" />
                    <input
                      type="text"
                      required
                      placeholder="Alex Morgan"
                      value={fullName}
                      onChange={(e) => setFullName(e.target.value)}
                      className="w-full h-[52px] pl-10 pr-4 rounded-[14px] bg-zinc-900 border border-zinc-800 text-white placeholder-zinc-500 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/40 focus:border-emerald-500 transition-all"
                    />
                  </div>
                </div>

                {/* Email Address */}
                <div className="space-y-1.5">
                  <label className="block text-[11px] font-semibold text-zinc-400 uppercase tracking-wider">
                    Email Address
                  </label>
                  <div className="relative">
                    <Mail className="w-4 h-4 text-zinc-500 absolute left-3.5 top-1/2 -translate-y-1/2" />
                    <input
                      type="email"
                      required
                      placeholder="alex.morgan@spendwise.app"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      className="w-full h-[52px] pl-10 pr-4 rounded-[14px] bg-zinc-900 border border-zinc-800 text-white placeholder-zinc-500 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/40 focus:border-emerald-500 transition-all"
                    />
                  </div>
                </div>

                {/* Password */}
                <div className="space-y-1.5">
                  <label className="block text-[11px] font-semibold text-zinc-400 uppercase tracking-wider">
                    Password
                  </label>
                  <div className="relative">
                    <Lock className="w-4 h-4 text-zinc-500 absolute left-3.5 top-1/2 -translate-y-1/2" />
                    <input
                      type={showPassword ? 'text' : 'password'}
                      required
                      placeholder="At least 6 characters"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      className="w-full h-[52px] pl-10 pr-11 rounded-[14px] bg-zinc-900 border border-zinc-800 text-white placeholder-zinc-500 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/40 focus:border-emerald-500 transition-all"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      className="absolute right-3.5 top-1/2 -translate-y-1/2 text-zinc-400 hover:text-zinc-200 transition-colors p-1"
                    >
                      {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>

                {/* Confirm Password */}
                <div className="space-y-1.5">
                  <label className="block text-[11px] font-semibold text-zinc-400 uppercase tracking-wider">
                    Confirm Password
                  </label>
                  <div className="relative">
                    <Lock className="w-4 h-4 text-zinc-500 absolute left-3.5 top-1/2 -translate-y-1/2" />
                    <input
                      type={showPassword ? 'text' : 'password'}
                      required
                      placeholder="Re-enter your password"
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      className="w-full h-[52px] pl-10 pr-4 rounded-[14px] bg-zinc-900 border border-zinc-800 text-white placeholder-zinc-500 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/40 focus:border-emerald-500 transition-all"
                    />
                  </div>
                </div>

                {/* Submit Sign Up */}
                <button
                  type="submit"
                  disabled={loading}
                  className="w-full h-[54px] rounded-[14px] bg-emerald-500 hover:bg-emerald-400 text-black font-semibold text-sm transition-transform active:scale-[0.98] disabled:opacity-40 disabled:pointer-events-none flex items-center justify-center gap-2 shadow-lg shadow-emerald-500/20"
                >
                  {loading ? (
                    <span className="inline-block w-4 h-4 border-2 border-black border-t-transparent rounded-full animate-spin" />
                  ) : (
                    <>
                      <span>Create Account</span>
                      <ArrowRight className="w-4 h-4" />
                    </>
                  )}
                </button>

                {/* Divider */}
                <div className="relative py-1">
                  <div className="absolute inset-0 flex items-center">
                    <div className="w-full border-t border-zinc-800/80" />
                  </div>
                  <div className="relative flex justify-center text-[11px] uppercase tracking-wider">
                    <span className="bg-[#12131A] px-3 text-zinc-500 font-medium">or continue with</span>
                  </div>
                </div>

                {/* Google Sign In */}
                <button
                  type="button"
                  onClick={handleGoogleSignIn}
                  disabled={loading}
                  className="w-full h-[54px] px-4 rounded-[14px] bg-white hover:bg-zinc-100 text-zinc-900 font-semibold text-sm border border-zinc-200 shadow-sm flex items-center justify-center gap-3 transition-transform active:scale-[0.98] disabled:opacity-50"
                >
                  <svg className="w-4 h-4 shrink-0" viewBox="0 0 24 24">
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

                {/* Switch to Sign In */}
                <div className="flex items-center justify-between text-xs text-zinc-400 pt-1">
                  <span>Already have an account?</span>
                  <button
                    type="button"
                    onClick={() => {
                      setAuthMode('signin');
                      setErrorMsg('');
                    }}
                    className="text-emerald-400 font-semibold hover:underline"
                  >
                    Sign In
                  </button>
                </div>
              </form>
            )}

            {/* ---------------- C. OPTIONAL EMAIL OTP STEP ---------------- */}
            {authMode === 'otp' && (
              <div className="space-y-4 animate-in fade-in duration-200">
                <div className="space-y-1">
                  <button
                    type="button"
                    onClick={() => {
                      setAuthMode('signin');
                      setOtpSent(false);
                      setErrorMsg('');
                    }}
                    className="inline-flex items-center gap-1.5 text-xs text-zinc-400 hover:text-white transition-colors"
                  >
                    <ArrowLeft className="w-3.5 h-3.5" />
                    <span>Back to standard Sign In</span>
                  </button>
                  <h2 className="text-xl font-bold tracking-tight text-white">Passwordless OTP</h2>
                  <p className="text-xs text-zinc-400">Sign in with a 6-digit email verification code</p>
                </div>

                {!otpSent ? (
                  <form onSubmit={handleSendOtp} className="space-y-3.5">
                    <div className="space-y-1.5">
                      <label className="block text-[11px] font-semibold text-zinc-400 uppercase tracking-wider">
                        Email Address
                      </label>
                      <div className="relative">
                        <Mail className="w-4 h-4 text-zinc-500 absolute left-3.5 top-1/2 -translate-y-1/2" />
                        <input
                          type="email"
                          required
                          placeholder="alex.morgan@spendwise.app"
                          value={email}
                          onChange={(e) => setEmail(e.target.value)}
                          className="w-full h-[52px] pl-10 pr-4 rounded-[14px] bg-zinc-900 border border-zinc-800 text-white placeholder-zinc-500 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/40 focus:border-emerald-500 transition-all"
                        />
                      </div>
                    </div>

                    <button
                      type="submit"
                      disabled={loading || !email}
                      className="w-full h-[54px] rounded-[14px] bg-emerald-500 hover:bg-emerald-400 text-black font-semibold text-sm transition-transform active:scale-[0.98] disabled:opacity-40 disabled:pointer-events-none flex items-center justify-center gap-2 shadow-lg shadow-emerald-500/20"
                    >
                      <span>Send 6-Digit Code</span>
                      <ArrowRight className="w-4 h-4" />
                    </button>
                  </form>
                ) : (
                  <form onSubmit={handleVerifyOtp} className="space-y-3.5">
                    <div className="space-y-1.5">
                      <div className="flex items-center justify-between text-xs">
                        <span className="text-zinc-400">Code sent to: {email}</span>
                        <button
                          type="button"
                          onClick={() => setOtpSent(false)}
                          className="text-emerald-400 hover:underline font-medium"
                        >
                          Change
                        </button>
                      </div>
                      <div className="relative">
                        <KeyRound className="w-4 h-4 text-emerald-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
                        <input
                          type="text"
                          maxLength={6}
                          required
                          autoFocus
                          placeholder="123456"
                          value={otpCode}
                          onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ''))}
                          className="w-full h-[52px] pl-10 pr-4 rounded-[14px] bg-zinc-900 border border-zinc-800 text-white text-center font-mono text-lg tracking-[0.25em] focus:outline-none focus:ring-2 focus:ring-emerald-500/40 focus:border-emerald-500 transition-all"
                        />
                      </div>
                    </div>

                    <button
                      type="submit"
                      disabled={loading || otpCode.length < 6}
                      className="w-full h-[54px] rounded-[14px] bg-emerald-500 hover:bg-emerald-400 text-black font-semibold text-sm transition-transform active:scale-[0.98] disabled:opacity-40 disabled:pointer-events-none flex items-center justify-center gap-2 shadow-lg shadow-emerald-500/20"
                    >
                      <span>Verify & Sign In</span>
                      <ArrowRight className="w-4 h-4" />
                    </button>

                    <div className="flex items-center justify-between text-xs text-zinc-400 pt-1">
                      <span>Didn't receive code?</span>
                      <button
                        type="button"
                        disabled={countdown > 0 || loading}
                        onClick={handleSendOtp}
                        className="inline-flex items-center gap-1.5 text-emerald-400 hover:underline font-medium disabled:opacity-40 disabled:no-underline"
                      >
                        <RotateCcw className="w-3 h-3" />
                        <span>{countdown > 0 ? `Resend in ${countdown}s` : 'Resend code'}</span>
                      </button>
                    </div>
                  </form>
                )}
              </div>
            )}

            {/* Secondary Option: Switch to OTP (or back to Sign In) */}
            <div className="pt-1 text-center">
              {authMode !== 'otp' ? (
                <button
                  type="button"
                  onClick={() => {
                    setAuthMode('otp');
                    setErrorMsg('');
                  }}
                  className="text-xs text-zinc-400 hover:text-emerald-400 transition-colors font-medium inline-flex items-center gap-1.5"
                >
                  <KeyRound className="w-3.5 h-3.5" />
                  <span>Sign in with Email OTP code instead</span>
                </button>
              ) : (
                <button
                  type="button"
                  onClick={() => {
                    setAuthMode('signin');
                    setErrorMsg('');
                  }}
                  className="text-xs text-zinc-400 hover:text-emerald-400 transition-colors font-medium inline-flex items-center gap-1.5"
                >
                  <Lock className="w-3.5 h-3.5" />
                  <span>Return to standard Password Sign In</span>
                </button>
              )}
            </div>

            {/* Demo Mode Button */}
            <div className="text-center">
              <button
                type="button"
                onClick={handleDemoMode}
                className="text-xs text-zinc-400 hover:text-white transition-colors font-medium inline-flex items-center gap-1.5"
              >
                <Sparkles className="w-3.5 h-3.5 text-emerald-400" />
                <span>Skip & Continue Offline (Demo Mode)</span>
              </button>
            </div>

            {/* Terms Footer */}
            <p className="text-[11px] text-zinc-500 text-center pt-1 leading-relaxed font-normal">
              By continuing you agree to Spendwise{' '}
              <span className="underline cursor-pointer hover:text-zinc-300">Terms of Service</span> &{' '}
              <span className="underline cursor-pointer hover:text-zinc-300">Privacy Policy</span>.
            </p>
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
        <div className="min-h-screen bg-[#0B0B10] flex items-center justify-center text-zinc-400">
          <div className="flex items-center gap-2">
            <div className="w-5 h-5 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin" />
            <span className="text-sm font-medium">Loading Spendwise...</span>
          </div>
        </div>
      }
    >
      <LoginForm />
    </React.Suspense>
  );
}
