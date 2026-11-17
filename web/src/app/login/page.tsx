'use client';

import React, { useState, useEffect, useRef } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { createClient, isSupabaseConfigured } from '@/lib/supabase/client';
import { useFinance } from '@/context/finance-context';
import { useTheme } from 'next-themes';
import {
  Wallet,
  ArrowRight,
  ArrowLeft,
  Lock,
  Eye,
  EyeOff,
  RotateCcw,
  Sparkles,
  TrendingUp,
  ShieldCheck,
  Sun,
  Moon,
  CheckCircle2,
} from 'lucide-react';

const GOOGLE_CLIENT_ID =
  process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID ||
  '176968816003-nshr9hpsr5egtbf5cs42bpp0am9d457k.apps.googleusercontent.com';

export default function LoginPage() {
  const router = useRouter();
  const supabase = createClient();
  const isConfigured = isSupabaseConfigured();
  const { login, isLoggedIn } = useFinance();
  const { resolvedTheme, setTheme } = useTheme();

  // Mode states: 'email' (default) | 'password'
  const [isPasswordMode, setIsPasswordMode] = useState(false);
  const [passwordMode, setPasswordMode] = useState<'login' | 'signup'>('login');
  const [showPassword, setShowPassword] = useState(false);

  // Form input states
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  
  // OTP states (6-box)
  const [otpSent, setOtpSent] = useState(false);
  const [otpDigits, setOtpDigits] = useState<string[]>(['', '', '', '', '', '']);
  const [countdown, setCountdown] = useState(0);
  const otpInputRefs = useRef<(HTMLInputElement | null)[]>([]);

  // Feedback & Loading
  const [errorMsg, setErrorMsg] = useState('');
  const [successMsg, setSuccessMsg] = useState('');
  const [loading, setLoading] = useState(false);
  const [shakeError, setShakeError] = useState(false);

  // Trigger shake animation on error
  useEffect(() => {
    if (errorMsg) {
      setShakeError(true);
      const timer = setTimeout(() => setShakeError(false), 400);
      return () => clearTimeout(timer);
    }
  }, [errorMsg]);

  // If already logged in, redirect to Dashboard
  useEffect(() => {
    if (isLoggedIn) {
      router.push('/');
    }
  }, [isLoggedIn, router]);

  // Timer for OTP resend countdown (30s)
  useEffect(() => {
    if (countdown > 0) {
      const timer = setTimeout(() => setCountdown(countdown - 1), 1000);
      return () => clearTimeout(timer);
    }
  }, [countdown]);

  // Load Google Identity Services script for Web Google Sign-In
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

  // Auto-focus first digit input when OTP sheet opens
  useEffect(() => {
    if (otpSent) {
      setTimeout(() => {
        otpInputRefs.current[0]?.focus();
      }, 150);
    }
  }, [otpSent]);

  // 1. Send Email OTP
  const handleSendOtp = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const cleanEmail = email.trim();
    if (!cleanEmail || !cleanEmail.includes('@')) {
      setErrorMsg('Please enter a valid email address.');
      return;
    }

    setLoading(true);
    setErrorMsg('');
    setSuccessMsg('');

    try {
      if (!isConfigured) {
        // Local / demo simulation
        setOtpSent(true);
        setOtpDigits(['', '', '', '', '', '']);
        setCountdown(30);
        setSuccessMsg('Demo code 123456 ready.');
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
      setOtpDigits(['', '', '', '', '', '']);
      setCountdown(30);
      setSuccessMsg(`Code sent to ${cleanEmail}`);
    } catch (err: any) {
      setErrorMsg(err.message || 'Failed to send verification code. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  // 2. Handle OTP digit input changes
  const handleOtpChange = (index: number, value: string) => {
    const char = value.replace(/\D/g, '').slice(-1);
    const updated = [...otpDigits];
    updated[index] = char;
    setOtpDigits(updated);
    setErrorMsg('');

    // Advance focus
    if (char && index < 5) {
      otpInputRefs.current[index + 1]?.focus();
    }

    // Auto-submit when all 6 digits are typed
    if (char && index === 5) {
      const fullCode = updated.join('');
      if (fullCode.length === 6) {
        verifyCode(fullCode);
      }
    }
  };

  const handleOtpKeyDown = (index: number, e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace' && !otpDigits[index] && index > 0) {
      otpInputRefs.current[index - 1]?.focus();
    }
  };

  const handleOtpPaste = (e: React.ClipboardEvent<HTMLInputElement>) => {
    e.preventDefault();
    const pasteData = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6);
    if (!pasteData) return;

    const updated = [...otpDigits];
    for (let i = 0; i < 6; i++) {
      updated[i] = pasteData[i] || '';
    }
    setOtpDigits(updated);
    setErrorMsg('');

    const nextIndex = Math.min(pasteData.length, 5);
    otpInputRefs.current[nextIndex]?.focus();

    if (pasteData.length === 6) {
      verifyCode(pasteData);
    }
  };

  // 3. Verify Email OTP
  const verifyCode = async (codeToVerify?: string) => {
    const code = codeToVerify || otpDigits.join('');
    if (code.length < 6) {
      setErrorMsg('Please enter all 6 digits of your verification code.');
      return;
    }

    setLoading(true);
    setErrorMsg('');

    try {
      if (!isConfigured) {
        if (code !== '123456') {
          throw new Error('Invalid code. For demo testing, please enter 123456.');
        }
        login(email.trim() || 'alex.morgan@spendwise.app');
        router.push('/');
        return;
      }

      const { data, error } = await supabase.auth.verifyOtp({
        email: email.trim(),
        token: code,
        type: 'email',
      });

      if (error) throw error;

      if (data.session?.user) {
        const metaName = data.session.user.user_metadata?.full_name || data.session.user.user_metadata?.name;
        login(email.trim(), metaName);
      } else {
        login(email.trim());
      }

      router.push('/');
    } catch (err: any) {
      setErrorMsg(err.message || 'Invalid or expired code. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  // 4. Password-Based Sign In / Sign Up
  const handlePasswordAuth = async (e: React.FormEvent) => {
    e.preventDefault();
    const cleanEmail = email.trim();
    if (!cleanEmail || !password) {
      setErrorMsg('Please provide both email and password.');
      return;
    }

    setLoading(true);
    setErrorMsg('');

    try {
      if (!isConfigured) {
        login(cleanEmail);
        router.push('/');
        return;
      }

      if (passwordMode === 'signup') {
        const { error } = await supabase.auth.signUp({
          email: cleanEmail,
          password,
        });
        if (error) throw error;
        setSuccessMsg('Account created! Please check your email to confirm or sign in.');
        setPasswordMode('login');
      } else {
        const { data, error } = await supabase.auth.signInWithPassword({
          email: cleanEmail,
          password,
        });
        if (error) throw error;
        const metaName = data.session?.user?.user_metadata?.full_name;
        login(cleanEmail, metaName);
        router.push('/');
      }
    } catch (err: any) {
      setErrorMsg(err.message || 'Authentication failed. Please check credentials.');
    } finally {
      setLoading(false);
    }
  };

  // 5. Google OAuth Sign-In
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

      // Try Google Identity prompt if script loaded
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

      // Offline fallback login
      login('mathi.dev@gmail.com', 'Mathi Dev');
      router.push('/');
    } catch (err: any) {
      setErrorMsg(err.message || 'Google sign-in error.');
      setLoading(false);
    }
  };

  // 6. Instant Demo Mode
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
      <div className="w-full max-w-5xl mx-auto px-4 sm:px-8 pt-4 pb-0 sm:py-8 flex-1 flex flex-col lg:grid lg:grid-cols-2 lg:items-center lg:gap-16 relative z-10">
        {/* ================= LEFT / HERO AREA ================= */}
        <div className="flex flex-col justify-between pt-1 pb-4 sm:pb-6 lg:py-12">
          {/* Top Brand Header */}
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 sm:w-9 sm:h-9 rounded-xl bg-emerald-500 flex items-center justify-center text-black shadow-md shadow-emerald-500/20">
                <Wallet className="w-4 h-4 sm:w-5 sm:h-5 stroke-[2.4]" />
              </div>
              <span className="text-lg sm:text-xl font-bold tracking-tight text-white">Spendwise</span>
            </div>

            {/* Subtle Theme Toggle */}
            <button
              type="button"
              onClick={() => setTheme(isDark ? 'light' : 'dark')}
              aria-label="Toggle theme"
              className="w-8 h-8 sm:w-9 sm:h-9 rounded-full bg-zinc-900 border border-zinc-800 text-zinc-400 hover:text-zinc-200 flex items-center justify-center transition-colors active:scale-95"
            >
              {isDark ? <Sun className="w-3.5 h-3.5 sm:w-4 sm:h-4" /> : <Moon className="w-3.5 h-3.5 sm:w-4 sm:h-4" />}
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

              {/* Mini Spending Distribution Bars */}
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

        {/* ================= RIGHT / BOTTOM SHEET AUTH CONTAINER ================= */}
        <div className="w-full max-w-md mx-auto lg:max-w-none mt-auto lg:mt-0">
          <div className="rounded-t-[28px] lg:rounded-[28px] bg-[#12131A] border-t lg:border border-zinc-800/90 shadow-2xl p-5 sm:p-8 space-y-4 sm:space-y-5 transition-all">
            
            {/* Inline Error Banner */}
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

            {/* Success Banner */}
            {successMsg && (
              <div className="p-3.5 rounded-xl bg-emerald-500/10 border border-emerald-500/25 text-xs text-emerald-400 flex items-center gap-2 font-medium">
                <CheckCircle2 className="w-4 h-4 shrink-0" />
                <span>{successMsg}</span>
              </div>
            )}

            {/* ---------------- STATE A: OTP VERIFICATION STEP ---------------- */}
            {otpSent ? (
              <div className="space-y-5 animate-in fade-in slide-in-from-right-4 duration-200">
                {/* Back Link & Title */}
                <div className="space-y-1.5">
                  <button
                    type="button"
                    onClick={() => {
                      setOtpSent(false);
                      setErrorMsg('');
                    }}
                    className="inline-flex items-center gap-1.5 text-xs text-zinc-400 hover:text-white transition-colors"
                  >
                    <ArrowLeft className="w-3.5 h-3.5" />
                    <span>Back to email</span>
                  </button>
                  <h2 className="text-xl font-bold tracking-tight text-white">Enter 6-digit code</h2>
                  <p className="text-xs text-zinc-400">
                    We sent a verification code to <span className="font-semibold text-zinc-200">{email}</span>.{' '}
                    <button
                      type="button"
                      onClick={() => {
                        setOtpSent(false);
                        setErrorMsg('');
                      }}
                      className="text-emerald-400 hover:underline font-medium"
                    >
                      Change
                    </button>
                  </p>
                </div>

                {/* 6 Digit Input Boxes */}
                <div>
                  <label className="block text-[11px] font-semibold text-zinc-400 uppercase tracking-wider mb-2.5">
                    Security Code
                  </label>
                  <div className="grid grid-cols-6 gap-2 sm:gap-2.5" onPaste={handleOtpPaste}>
                    {otpDigits.map((digit, idx) => (
                      <input
                        key={idx}
                        ref={(el) => {
                          otpInputRefs.current[idx] = el;
                        }}
                        type="text"
                        inputMode="numeric"
                        pattern="[0-9]*"
                        maxLength={1}
                        value={digit}
                        aria-label={`Digit ${idx + 1}`}
                        onChange={(e) => handleOtpChange(idx, e.target.value)}
                        onKeyDown={(e) => handleOtpKeyDown(idx, e)}
                        className={`h-[52px] text-center text-xl font-mono font-bold rounded-[14px] bg-zinc-900 border text-white transition-all focus:outline-none focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500 ${
                          digit ? 'border-emerald-500/60 bg-emerald-500/5' : 'border-zinc-800'
                        }`}
                      />
                    ))}
                  </div>
                </div>

                {/* Verify Button */}
                <button
                  type="button"
                  disabled={loading || otpDigits.join('').length < 6}
                  onClick={() => verifyCode()}
                  className="w-full h-[54px] rounded-[14px] bg-emerald-500 hover:bg-emerald-400 text-black font-semibold text-sm transition-transform active:scale-[0.98] disabled:opacity-40 disabled:pointer-events-none flex items-center justify-center gap-2 shadow-lg shadow-emerald-500/20"
                >
                  {loading ? (
                    <span className="inline-block w-4 h-4 border-2 border-black border-t-transparent rounded-full animate-spin" />
                  ) : (
                    <>
                      <span>Verify & Continue</span>
                      <ArrowRight className="w-4 h-4" />
                    </>
                  )}
                </button>

                {/* Resend & Demo Helper */}
                <div className="flex items-center justify-between text-xs text-zinc-400 pt-1">
                  <span>Didn't receive code?</span>
                  <button
                    type="button"
                    disabled={countdown > 0 || loading}
                    onClick={() => handleSendOtp()}
                    className="inline-flex items-center gap-1.5 text-emerald-400 hover:underline font-medium disabled:opacity-40 disabled:no-underline"
                  >
                    <RotateCcw className="w-3 h-3" />
                    <span>{countdown > 0 ? `Resend in ${countdown}s` : 'Resend code'}</span>
                  </button>
                </div>

                {/* Demo autofill hint */}
                {!isConfigured && (
                  <button
                    type="button"
                    onClick={() => {
                      setOtpDigits(['1', '2', '3', '4', '5', '6']);
                      verifyCode('123456');
                    }}
                    className="w-full py-2.5 rounded-xl bg-zinc-900/80 border border-zinc-800 text-[11px] text-zinc-400 hover:text-emerald-400 transition-colors text-center font-mono"
                  >
                    Demo quick-fill: click to test code <strong className="text-white">123456</strong>
                  </button>
                )}
              </div>
            ) : (
              /* ---------------- STATE B: INITIAL SIGN-IN SHEET ---------------- */
              <div className="space-y-4">
                {/* 1. Google One-Tap / OAuth Button */}
                <button
                  type="button"
                  onClick={handleGoogleSignIn}
                  disabled={loading}
                  aria-label="Continue with Google"
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

                {/* Divider "or" */}
                <div className="relative py-1">
                  <div className="absolute inset-0 flex items-center">
                    <div className="w-full border-t border-zinc-800/80" />
                  </div>
                  <div className="relative flex justify-center text-[11px] uppercase tracking-wider">
                    <span className="bg-[#12131A] px-3 text-zinc-500 font-medium">or</span>
                  </div>
                </div>

                {/* 2. Email / Password Inline Forms */}
                {!isPasswordMode ? (
                  /* --- EMAIL OTP MODE --- */
                  <form onSubmit={handleSendOtp} className="space-y-3.5">
                    {/* Floating Label Email Input */}
                    <div className="relative">
                      <input
                        id="email-input"
                        type="email"
                        required
                        value={email}
                        placeholder=" "
                        onChange={(e) => {
                          setEmail(e.target.value);
                          if (errorMsg) setErrorMsg('');
                        }}
                        className="peer w-full h-[52px] pt-4 pb-1 px-4 rounded-[14px] bg-zinc-900 border border-zinc-800 text-white placeholder-transparent text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/40 focus:border-emerald-500 transition-all"
                      />
                      <label
                        htmlFor="email-input"
                        className="absolute left-4 top-2 text-[10px] font-medium text-zinc-400 transition-all peer-placeholder-shown:top-3.5 peer-placeholder-shown:text-sm peer-placeholder-shown:text-zinc-500 peer-focus:top-2 peer-focus:text-[10px] peer-focus:text-emerald-400 pointer-events-none"
                      >
                        Email address
                      </label>
                    </div>

                    {/* Continue With Email Button */}
                    <button
                      type="submit"
                      disabled={loading || !email}
                      className="w-full h-[54px] rounded-[14px] bg-emerald-500 hover:bg-emerald-400 text-black font-semibold text-sm transition-transform active:scale-[0.98] disabled:opacity-40 disabled:pointer-events-none flex items-center justify-center gap-2 shadow-lg shadow-emerald-500/20"
                    >
                      {loading ? (
                        <span className="inline-block w-4 h-4 border-2 border-black border-t-transparent rounded-full animate-spin" />
                      ) : (
                        <>
                          <span>Continue with email</span>
                          <ArrowRight className="w-4 h-4" />
                        </>
                      )}
                    </button>
                  </form>
                ) : (
                  /* --- PASSWORD MODE (TOGGLED IN PLACE) --- */
                  <form onSubmit={handlePasswordAuth} className="space-y-3.5 animate-in fade-in duration-200">
                    {/* Floating Label Email */}
                    <div className="relative">
                      <input
                        id="email-pw-input"
                        type="email"
                        required
                        value={email}
                        placeholder=" "
                        onChange={(e) => {
                          setEmail(e.target.value);
                          if (errorMsg) setErrorMsg('');
                        }}
                        className="peer w-full h-[52px] pt-4 pb-1 px-4 rounded-[14px] bg-zinc-900 border border-zinc-800 text-white placeholder-transparent text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/40 focus:border-emerald-500 transition-all"
                      />
                      <label
                        htmlFor="email-pw-input"
                        className="absolute left-4 top-2 text-[10px] font-medium text-zinc-400 transition-all peer-placeholder-shown:top-3.5 peer-placeholder-shown:text-sm peer-placeholder-shown:text-zinc-500 peer-focus:top-2 peer-focus:text-[10px] peer-focus:text-emerald-400 pointer-events-none"
                      >
                        Email address
                      </label>
                    </div>

                    {/* Floating Label Password with Toggle Visibility */}
                    <div className="relative">
                      <input
                        id="password-input"
                        type={showPassword ? 'text' : 'password'}
                        required
                        value={password}
                        placeholder=" "
                        onChange={(e) => {
                          setPassword(e.target.value);
                          if (errorMsg) setErrorMsg('');
                        }}
                        className="peer w-full h-[52px] pt-4 pb-1 pl-4 pr-11 rounded-[14px] bg-zinc-900 border border-zinc-800 text-white placeholder-transparent text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/40 focus:border-emerald-500 transition-all"
                      />
                      <label
                        htmlFor="password-input"
                        className="absolute left-4 top-2 text-[10px] font-medium text-zinc-400 transition-all peer-placeholder-shown:top-3.5 peer-placeholder-shown:text-sm peer-placeholder-shown:text-zinc-500 peer-focus:top-2 peer-focus:text-[10px] peer-focus:text-emerald-400 pointer-events-none"
                      >
                        Password
                      </label>
                      <button
                        type="button"
                        onClick={() => setShowPassword(!showPassword)}
                        className="absolute right-3.5 top-1/2 -translate-y-1/2 text-zinc-400 hover:text-zinc-200 transition-colors p-1"
                        aria-label={showPassword ? 'Hide password' : 'Show password'}
                      >
                        {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                      </button>
                    </div>

                    {/* Submit Button */}
                    <button
                      type="submit"
                      disabled={loading || !email || !password}
                      className="w-full h-[54px] rounded-[14px] bg-emerald-500 hover:bg-emerald-400 text-black font-semibold text-sm transition-transform active:scale-[0.98] disabled:opacity-40 disabled:pointer-events-none flex items-center justify-center gap-2 shadow-lg shadow-emerald-500/20"
                    >
                      {loading ? (
                        <span className="inline-block w-4 h-4 border-2 border-black border-t-transparent rounded-full animate-spin" />
                      ) : (
                        <>
                          <span>{passwordMode === 'signup' ? 'Create Account' : 'Sign In'}</span>
                          <ArrowRight className="w-4 h-4" />
                        </>
                      )}
                    </button>

                    {/* Switch between Sign In and Sign Up */}
                    <div className="flex items-center justify-between text-xs text-zinc-400 pt-0.5">
                      <button
                        type="button"
                        onClick={() => setPasswordMode(passwordMode === 'login' ? 'signup' : 'login')}
                        className="hover:underline text-zinc-300 font-medium"
                      >
                        {passwordMode === 'login' ? 'Need an account? Sign up' : 'Have an account? Sign in'}
                      </button>
                    </div>
                  </form>
                )}

                {/* Mode Toggle Link (Password vs OTP) */}
                <div className="text-center pt-1">
                  <button
                    type="button"
                    onClick={() => {
                      setIsPasswordMode(!isPasswordMode);
                      setErrorMsg('');
                    }}
                    className="text-xs text-zinc-400 hover:text-emerald-400 transition-colors font-medium inline-flex items-center gap-1.5"
                  >
                    <Lock className="w-3.5 h-3.5" />
                    <span>{isPasswordMode ? 'Sign in with email OTP instead' : 'Use password instead'}</span>
                  </button>
                </div>

                {/* Demo Mode Link */}
                <div className="text-center pt-1">
                  <button
                    type="button"
                    onClick={handleDemoMode}
                    className="text-xs text-zinc-400 hover:text-white transition-colors font-medium inline-flex items-center gap-1.5"
                  >
                    <Sparkles className="w-3.5 h-3.5 text-emerald-400" />
                    <span>Try demo mode (offline)</span>
                  </button>
                </div>

                {/* Footer Terms */}
                <p className="text-[11px] text-zinc-400 dark:text-zinc-400 text-center pt-2 leading-relaxed font-normal">
                  By continuing you agree to Spendwise{' '}
                  <span className="underline cursor-pointer hover:text-zinc-300">Terms of Service</span> &{' '}
                  <span className="underline cursor-pointer hover:text-zinc-300">Privacy Policy</span>.
                </p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
