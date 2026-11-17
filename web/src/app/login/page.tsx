'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { createClient, isSupabaseConfigured } from '@/lib/supabase/client';
import { useFinance } from '@/context/finance-context';
import {
  Wallet,
  Mail,
  Lock,
  ArrowRight,
  ArrowLeft,
  KeyRound,
  Sparkles,
  CheckCircle2,
  RotateCcw,
  ShieldCheck,
} from 'lucide-react';

type AuthTab = 'otp' | 'password';
type PasswordMode = 'login' | 'signup';

const GOOGLE_CLIENT_ID =
  process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID ||
  '176968816003-nshr9hpsr5egtbf5cs42bpp0am9d457k.apps.googleusercontent.com';

export default function LoginPage() {
  const router = useRouter();
  const supabase = createClient();
  const isConfigured = isSupabaseConfigured();
  const { login, isLoggedIn } = useFinance();

  const [authTab, setAuthTab] = useState<AuthTab>('otp');
  const [passwordMode, setPasswordMode] = useState<PasswordMode>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [otpCode, setOtpCode] = useState('');
  const [otpSent, setOtpSent] = useState(false);
  const [demoCode, setDemoCode] = useState('123456');
  const [countdown, setCountdown] = useState(0);

  const [errorMsg, setErrorMsg] = useState('');
  const [successMsg, setSuccessMsg] = useState('');
  const [loading, setLoading] = useState(false);

  // If already logged in, redirect to Dashboard
  useEffect(() => {
    if (isLoggedIn) {
      router.push('/');
    }
  }, [isLoggedIn, router]);

  // Timer for OTP resend countdown
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
          // Decode Google JWT ID token
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

  // 1. Send Email OTP
  const handleSendOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email) return;
    setLoading(true);
    setErrorMsg('');
    setSuccessMsg('');

    try {
      if (!isConfigured) {
        // Local / demo simulation
        setOtpSent(true);
        setDemoCode('123456');
        setCountdown(60);
        setSuccessMsg('Verification code sent! (Demo Mode: Use code 123456)');
        return;
      }

      const { error } = await supabase.auth.signInWithOtp({
        email,
        options: {
          shouldCreateUser: true,
          emailRedirectTo: `${window.location.origin}/auth/callback`,
        },
      });

      if (error) throw error;

      setOtpSent(true);
      setCountdown(60);
      setSuccessMsg(`6-digit verification code sent to ${email}`);
    } catch (err: any) {
      setErrorMsg(err.message || 'Failed to send OTP code. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  // 2. Verify Email OTP
  const handleVerifyOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!otpCode || otpCode.length < 6) {
      setErrorMsg('Please enter a valid 6-digit verification code.');
      return;
    }
    setLoading(true);
    setErrorMsg('');

    try {
      if (!isConfigured) {
        // Demo verification check
        if (otpCode.trim() !== '123456' && otpCode.trim() !== demoCode) {
          throw new Error('Invalid code. For testing, please enter 123456.');
        }
        login(email);
        router.push('/');
        return;
      }

      const { data, error } = await supabase.auth.verifyOtp({
        email,
        token: otpCode.trim(),
        type: 'email',
      });

      if (error) throw error;

      if (data.session?.user) {
        const metaName = data.session.user.user_metadata?.full_name || data.session.user.user_metadata?.name;
        login(email, metaName);
      } else {
        login(email);
      }

      router.push('/');
    } catch (err: any) {
      setErrorMsg(err.message || 'Verification failed. Code may be expired or invalid.');
    } finally {
      setLoading(false);
    }
  };

  // 3. Password-Based Sign In / Sign Up
  const handlePasswordAuth = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !password) return;
    setLoading(true);
    setErrorMsg('');

    try {
      if (!isConfigured) {
        login(email);
        router.push('/');
        return;
      }

      if (passwordMode === 'signup') {
        const { error } = await supabase.auth.signUp({
          email,
          password,
        });
        if (error) throw error;
        setSuccessMsg('Account created! Please check your email to confirm or sign in.');
        setPasswordMode('login');
      } else {
        const { data, error } = await supabase.auth.signInWithPassword({
          email,
          password,
        });
        if (error) throw error;
        const metaName = data.session?.user?.user_metadata?.full_name;
        login(email, metaName);
        router.push('/');
      }
    } catch (err: any) {
      setErrorMsg(err.message || 'Authentication failed. Please check credentials.');
    } finally {
      setLoading(false);
    }
  };

  // 4. Google OAuth Sign-In
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
              // Fallback to demo Google user if prompt suppressed or origin not whitelisted yet
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

  return (
    <div className="min-h-screen flex items-center justify-center p-4 relative overflow-hidden bg-background">
      {/* 1. Liquid Aurora Chromatic Background Blobs */}
      <div className="absolute -top-24 -left-24 w-96 h-96 rounded-full bg-primary/25 dark:bg-primary/20 blur-3xl pointer-events-none animate-pulse duration-1000" />
      <div className="absolute top-1/2 -right-24 w-80 h-80 rounded-full bg-sky-500/20 dark:bg-sky-500/15 blur-3xl pointer-events-none" />
      <div className="absolute -bottom-24 left-1/3 w-88 h-88 rounded-full bg-emerald-500/20 dark:bg-emerald-500/15 blur-3xl pointer-events-none" />

      <div className="w-full max-w-md space-y-6 relative z-10 animate-in fade-in duration-300">
        {/* Brand Header */}
        <div className="text-center space-y-2">
          <div className="w-14 h-14 rounded-3xl bg-primary flex items-center justify-center text-primary-foreground mx-auto shadow-xl shadow-primary/30 border border-white/20">
            <Wallet className="w-7 h-7 stroke-[2.2]" />
          </div>
          <h1 className="text-3xl font-black tracking-tight text-foreground">
            Spendwise
          </h1>
          <p className="text-xs text-muted-foreground font-medium">
            Smart Income, Expense & Travel Tracker
          </p>
        </div>

        {/* Apple-Style Liquid Glass Card */}
        <div className="p-6 sm:p-8 rounded-3xl bg-card/75 dark:bg-card/45 backdrop-blur-2xl border border-white/30 dark:border-white/10 shadow-2xl space-y-6">
          {/* Main Auth Mode Switcher (Email OTP vs Password) */}
          <div className="grid grid-cols-2 p-1 rounded-2xl bg-secondary/70 dark:bg-secondary/40 backdrop-blur-md text-xs font-semibold border border-border/40">
            <button
              type="button"
              onClick={() => {
                setAuthTab('otp');
                setErrorMsg('');
                setSuccessMsg('');
              }}
              className={`py-2.5 rounded-xl transition-all flex items-center justify-center gap-1.5 ${
                authTab === 'otp'
                  ? 'bg-primary text-primary-foreground font-bold shadow-md shadow-primary/25'
                  : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              <KeyRound className="w-3.5 h-3.5" />
              <span>Email OTP</span>
            </button>
            <button
              type="button"
              onClick={() => {
                setAuthTab('password');
                setErrorMsg('');
                setSuccessMsg('');
              }}
              className={`py-2.5 rounded-xl transition-all flex items-center justify-center gap-1.5 ${
                authTab === 'password'
                  ? 'bg-primary text-primary-foreground font-bold shadow-md shadow-primary/25'
                  : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              <Lock className="w-3.5 h-3.5" />
              <span>Password</span>
            </button>
          </div>

          {/* Feedback Messages */}
          {errorMsg && (
            <div className="p-3.5 rounded-2xl bg-rose-500/15 border border-rose-500/30 text-xs text-rose-500 font-medium animate-in fade-in">
              {errorMsg}
            </div>
          )}

          {successMsg && (
            <div className="p-3.5 rounded-2xl bg-emerald-500/15 border border-emerald-500/30 text-xs text-emerald-500 font-medium flex items-center gap-2 animate-in fade-in">
              <CheckCircle2 className="w-4 h-4 shrink-0" />
              <span>{successMsg}</span>
            </div>
          )}

          {/* TAB 1: EMAIL OTP (Passwordless Verification Code) */}
          {authTab === 'otp' && (
            <div className="space-y-4">
              <p className="text-xs text-muted-foreground text-center">
                Passwordless login: enter your email and we'll send a 6-digit code to log in or create your account.
              </p>

              {!otpSent ? (
                // Step 1: Enter Email
                <form onSubmit={handleSendOtp} className="space-y-4">
                  <div>
                    <label className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1.5">
                      Email Address
                    </label>
                    <div className="relative">
                      <Mail className="w-4 h-4 text-muted-foreground absolute left-3.5 top-1/2 -translate-y-1/2" />
                      <input
                        type="email"
                        required
                        placeholder="mathi@example.com"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        className="w-full pl-10 pr-3 py-3 text-sm bg-background/80 dark:bg-background/50 border border-border/80 rounded-2xl focus:ring-2 focus:ring-primary focus:outline-none transition-all"
                      />
                    </div>
                  </div>

                  <button
                    type="submit"
                    disabled={loading}
                    className="w-full py-3.5 rounded-2xl font-semibold text-primary-foreground bg-primary hover:bg-primary/90 text-sm shadow-lg shadow-primary/25 transition-all flex items-center justify-center gap-2 active:scale-[0.99]"
                  >
                    <span>{loading ? 'Sending code...' : 'Send 6-Digit OTP Code'}</span>
                    <ArrowRight className="w-4 h-4" />
                  </button>
                </form>
              ) : (
                // Step 2: Enter 6-Digit Code
                <form onSubmit={handleVerifyOtp} className="space-y-4 animate-in fade-in duration-300">
                  <div className="p-3 rounded-2xl bg-secondary/40 border border-border/60 flex items-center justify-between text-xs">
                    <div>
                      <span className="text-muted-foreground">Code sent to: </span>
                      <strong className="text-foreground">{email}</strong>
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        setOtpSent(false);
                        setOtpCode('');
                      }}
                      className="text-primary hover:underline font-semibold"
                    >
                      Change
                    </button>
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1.5">
                      Enter 6-Digit Verification Code
                    </label>
                    <div className="relative">
                      <KeyRound className="w-4 h-4 text-primary absolute left-3.5 top-1/2 -translate-y-1/2" />
                      <input
                        type="text"
                        maxLength={6}
                        required
                        autoFocus
                        placeholder="123456"
                        value={otpCode}
                        onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ''))}
                        className="w-full pl-10 pr-3 py-3 text-base tracking-[0.3em] font-mono text-center bg-background/80 dark:bg-background/50 border border-border/80 rounded-2xl focus:ring-2 focus:ring-primary focus:outline-none"
                      />
                    </div>
                  </div>

                  <button
                    type="submit"
                    disabled={loading || otpCode.length < 6}
                    className="w-full py-3.5 rounded-2xl font-semibold text-primary-foreground bg-primary hover:bg-primary/90 text-sm shadow-lg shadow-primary/25 transition-all flex items-center justify-center gap-2 active:scale-[0.99] disabled:opacity-50"
                  >
                    <span>{loading ? 'Verifying...' : 'Verify Code & Enter'}</span>
                    <ArrowRight className="w-4 h-4" />
                  </button>

                  <div className="flex items-center justify-between text-xs text-muted-foreground pt-1">
                    <span>Didn't receive the email?</span>
                    <button
                      type="button"
                      disabled={countdown > 0 || loading}
                      onClick={handleSendOtp}
                      className="text-primary hover:underline font-semibold flex items-center gap-1 disabled:opacity-50 disabled:no-underline"
                    >
                      <RotateCcw className="w-3 h-3" />
                      <span>{countdown > 0 ? `Resend in ${countdown}s` : 'Resend Code'}</span>
                    </button>
                  </div>
                </form>
              )}
            </div>
          )}

          {/* TAB 2: PASSWORD-BASED SIGN IN / SIGN UP */}
          {authTab === 'password' && (
            <div className="space-y-4">
              <div className="flex border-b border-border/60 pb-2 justify-around text-xs font-semibold">
                <button
                  type="button"
                  onClick={() => {
                    setPasswordMode('login');
                    setErrorMsg('');
                  }}
                  className={`pb-1.5 relative transition-colors ${
                    passwordMode === 'login' ? 'text-primary font-bold' : 'text-muted-foreground'
                  }`}
                >
                  Sign In
                  {passwordMode === 'login' && (
                    <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-primary rounded-full" />
                  )}
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setPasswordMode('signup');
                    setErrorMsg('');
                  }}
                  className={`pb-1.5 relative transition-colors ${
                    passwordMode === 'signup' ? 'text-primary font-bold' : 'text-muted-foreground'
                  }`}
                >
                  Create Account
                  {passwordMode === 'signup' && (
                    <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-primary rounded-full" />
                  )}
                </button>
              </div>

              <form onSubmit={handlePasswordAuth} className="space-y-4">
                <div>
                  <label className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1.5">
                    Email Address
                  </label>
                  <div className="relative">
                    <Mail className="w-4 h-4 text-muted-foreground absolute left-3.5 top-1/2 -translate-y-1/2" />
                    <input
                      type="email"
                      required
                      placeholder="mathi@example.com"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      className="w-full pl-10 pr-3 py-3 text-sm bg-background/80 dark:bg-background/50 border border-border/80 rounded-2xl focus:ring-2 focus:ring-primary focus:outline-none"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1.5">
                    Password
                  </label>
                  <div className="relative">
                    <Lock className="w-4 h-4 text-muted-foreground absolute left-3.5 top-1/2 -translate-y-1/2" />
                    <input
                      type="password"
                      required
                      placeholder="••••••••"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      className="w-full pl-10 pr-3 py-3 text-sm bg-background/80 dark:bg-background/50 border border-border/80 rounded-2xl focus:ring-2 focus:ring-primary focus:outline-none"
                    />
                  </div>
                </div>

                <button
                  type="submit"
                  disabled={loading}
                  className="w-full py-3.5 rounded-2xl font-semibold text-primary-foreground bg-primary hover:bg-primary/90 text-sm shadow-lg shadow-primary/25 transition-all flex items-center justify-center gap-2 active:scale-[0.99]"
                >
                  <span>
                    {passwordMode === 'signup' ? 'Create Free Account' : 'Sign In'}
                  </span>
                  <ArrowRight className="w-4 h-4" />
                </button>
              </form>
            </div>
          )}

          {/* OR DIVIDER */}
          <div className="relative my-4">
            <div className="absolute inset-0 flex items-center">
              <div className="w-full border-t border-border/60" />
            </div>
            <div className="relative flex justify-center text-[11px] uppercase">
              <span className="bg-card/90 px-3 text-muted-foreground font-semibold">OR</span>
            </div>
          </div>

          {/* Google Sign-In Button with Official 4-Color Google "G" Logo */}
          <button
            type="button"
            onClick={handleGoogleSignIn}
            disabled={loading}
            className="w-full py-3 px-4 rounded-2xl font-semibold border border-border/80 hover:bg-secondary/70 text-xs text-foreground flex items-center justify-center gap-3 transition-all active:scale-[0.99] bg-card/60 backdrop-blur-md shadow-sm"
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

          {/* Instant Demo Access Button */}
          <div className="pt-2 text-center border-t border-border/40">
            <button
              type="button"
              onClick={() => {
                login('mathi.dev@gmail.com', 'Mathi Dev');
                router.push('/');
              }}
              className="text-xs font-semibold text-primary hover:underline inline-flex items-center gap-1.5 transition-colors"
            >
              <Sparkles className="w-3.5 h-3.5" />
              <span>Skip & Continue Offline (Demo Mode)</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
