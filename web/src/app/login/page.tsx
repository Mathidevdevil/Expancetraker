'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { createClient, isSupabaseConfigured } from '@/lib/supabase/client';
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
} from 'lucide-react';

type AuthTab = 'otp' | 'password';
type PasswordMode = 'login' | 'signup';

export default function LoginPage() {
  const router = useRouter();
  const supabase = createClient();
  const isConfigured = isSupabaseConfigured();

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

  // Timer for OTP resend countdown
  useEffect(() => {
    if (countdown > 0) {
      const timer = setTimeout(() => setCountdown(countdown - 1), 1000);
      return () => clearTimeout(timer);
    }
  }, [countdown]);

  const saveProfileLocally = (userEmail: string) => {
    const namePart = userEmail.split('@')[0].replace('.', ' ');
    const cleanName =
      namePart
        .split(' ')
        .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
        .join(' ') || 'User';
    const initials =
      cleanName
        .split(' ')
        .slice(0, 2)
        .map((s) => s[0].toUpperCase())
        .join('') || 'SW';

    try {
      localStorage.setItem(
        'spendwise_user_profile',
        JSON.stringify({
          name: cleanName,
          email: userEmail,
          initials,
          bio: 'Personal Finance & Travel Budgeter',
        })
      );
    } catch (_) {}
  };

  // 1. Send Email OTP
  const handleSendOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email) return;
    setLoading(true);
    setErrorMsg('');
    setSuccessMsg('');

    try {
      if (!isConfigured) {
        // Local/demo simulation
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
        saveProfileLocally(email);
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
        saveProfileLocally(email);
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
        saveProfileLocally(email);
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
        const { error } = await supabase.auth.signInWithPassword({
          email,
          password,
        });
        if (error) throw error;
        saveProfileLocally(email);
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
    if (!isConfigured) {
      saveProfileLocally('mathi.dev@gmail.com');
      router.push('/');
      return;
    }

    try {
      setLoading(true);
      const { error } = await supabase.auth.signInWithOAuth({
        provider: 'google',
        options: {
          redirectTo: `${window.location.origin}/auth/callback`,
        },
      });
      if (error) throw error;
    } catch (err: any) {
      setErrorMsg(err.message || 'Google sign-in error.');
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4 bg-gradient-to-br from-background via-muted/30 to-background">
      <div className="w-full max-w-md space-y-6">
        <div className="flex items-center justify-between">
          <Link
            href="/"
            className="inline-flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground font-medium transition-colors"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            <span>Back to Dashboard</span>
          </Link>
          <span className="text-[11px] text-muted-foreground font-mono">Spendwise 2026</span>
        </div>

        {/* Brand Header */}
        <div className="text-center space-y-2">
          <div className="w-12 h-12 rounded-2xl bg-primary flex items-center justify-center text-white mx-auto shadow-xl shadow-primary/30">
            <Wallet className="w-6 h-6" />
          </div>
          <h1 className="text-2xl font-black tracking-tight text-foreground">
            Spendwise
          </h1>
          <p className="text-xs text-muted-foreground font-medium">
            Smart Income, Expense & Travel Tracker
          </p>
        </div>

        {/* Card Form */}
        <div className="p-6 sm:p-8 rounded-3xl bg-card border border-border shadow-2xl space-y-6">
          {/* Main Auth Mode Switcher (Email OTP vs Password) */}
          <div className="grid grid-cols-2 p-1 rounded-2xl bg-secondary/60 text-xs font-semibold">
            <button
              type="button"
              onClick={() => {
                setAuthTab('otp');
                setErrorMsg('');
                setSuccessMsg('');
              }}
              className={`py-2 rounded-xl transition-all flex items-center justify-center gap-1.5 ${
                authTab === 'otp'
                  ? 'bg-primary text-primary-foreground font-bold shadow-sm'
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
              className={`py-2 rounded-xl transition-all flex items-center justify-center gap-1.5 ${
                authTab === 'password'
                  ? 'bg-primary text-primary-foreground font-bold shadow-sm'
                  : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              <Lock className="w-3.5 h-3.5" />
              <span>Password</span>
            </button>
          </div>

          {/* Feedback Messages */}
          {errorMsg && (
            <div className="p-3.5 rounded-2xl bg-rose-500/10 border border-rose-500/20 text-xs text-rose-500 font-medium animate-in fade-in">
              {errorMsg}
            </div>
          )}

          {successMsg && (
            <div className="p-3.5 rounded-2xl bg-emerald-500/10 border border-emerald-500/20 text-xs text-emerald-500 font-medium flex items-center gap-2 animate-in fade-in">
              <CheckCircle2 className="w-4 h-4 shrink-0" />
              <span>{successMsg}</span>
            </div>
          )}

          {/* Google Sign-In Button */}
          <button
            type="button"
            onClick={handleGoogleSignIn}
            disabled={loading}
            className="w-full py-2.5 px-4 rounded-2xl border border-border bg-background hover:bg-secondary/60 font-semibold text-xs text-foreground flex items-center justify-center gap-2.5 transition-colors shadow-sm active:scale-[0.99]"
          >
            <svg className="w-4 h-4" viewBox="0 0 24 24">
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

          <div className="relative flex items-center justify-center">
            <div className="border-t border-border w-full" />
            <span className="bg-card px-3 text-[11px] font-semibold text-muted-foreground uppercase tracking-wider">
              Or with email
            </span>
          </div>

          {/* TAB 1: EMAIL OTP (Passwordless Verification Code) */}
          {authTab === 'otp' && (
            <div className="space-y-4">
              {!otpSent ? (
                // Step 1: Enter Email
                <form onSubmit={handleSendOtp} className="space-y-4">
                  <div>
                    <label className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1">
                      Your Email Address
                    </label>
                    <div className="relative">
                      <Mail className="w-4 h-4 text-muted-foreground absolute left-3.5 top-1/2 -translate-y-1/2" />
                      <input
                        type="email"
                        required
                        placeholder="mathi@example.com"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        className="w-full pl-10 pr-3 py-2.5 text-sm bg-background border border-border rounded-2xl focus:ring-2 focus:ring-primary focus:outline-none"
                      />
                    </div>
                    <p className="text-[11px] text-muted-foreground mt-1.5">
                      We will send a 6-digit verification code to this email to sign in or create an account.
                    </p>
                  </div>

                  <button
                    type="submit"
                    disabled={loading}
                    className="w-full py-3 rounded-2xl font-semibold text-white bg-primary hover:bg-primary/90 text-sm shadow-lg shadow-primary/25 transition-all flex items-center justify-center gap-2 active:scale-[0.99]"
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
                    <label className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1">
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
                        className="w-full pl-10 pr-3 py-2.5 text-base tracking-[0.25em] font-mono text-center bg-background border border-border rounded-2xl focus:ring-2 focus:ring-primary focus:outline-none"
                      />
                    </div>
                  </div>

                  <button
                    type="submit"
                    disabled={loading || otpCode.length < 6}
                    className="w-full py-3 rounded-2xl font-semibold text-white bg-primary hover:bg-primary/90 text-sm shadow-lg shadow-primary/25 transition-all flex items-center justify-center gap-2 active:scale-[0.99] disabled:opacity-50"
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
                  <label className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1">
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
                      className="w-full pl-10 pr-3 py-2.5 text-sm bg-background border border-border rounded-2xl focus:ring-2 focus:ring-primary focus:outline-none"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1">
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
                      className="w-full pl-10 pr-3 py-2.5 text-sm bg-background border border-border rounded-2xl focus:ring-2 focus:ring-primary focus:outline-none"
                    />
                  </div>
                </div>

                <button
                  type="submit"
                  disabled={loading}
                  className="w-full py-3 rounded-2xl font-semibold text-white bg-primary hover:bg-primary/90 text-sm shadow-lg shadow-primary/25 transition-all flex items-center justify-center gap-2 active:scale-[0.99]"
                >
                  <span>
                    {passwordMode === 'signup' ? 'Create Free Account' : 'Sign In'}
                  </span>
                  <ArrowRight className="w-4 h-4" />
                </button>
              </form>
            </div>
          )}

          {/* OR DIVIDER & GOOGLE SIGN-IN */}
          <div className="relative my-4">
            <div className="absolute inset-0 flex items-center">
              <div className="w-full border-t border-border/60" />
            </div>
            <div className="relative flex justify-center text-xs uppercase">
              <span className="bg-card px-2 text-muted-foreground font-medium">Or continue with</span>
            </div>
          </div>

          <button
            type="button"
            onClick={handleGoogleSignIn}
            disabled={loading}
            className="w-full py-2.5 px-4 rounded-2xl font-semibold border border-border/80 hover:bg-muted/50 text-sm transition-all flex items-center justify-center gap-3 active:scale-[0.99] bg-background/80 shadow-sm"
          >
            <svg className="w-5 h-5" viewBox="0 0 24 24">
              <path
                fill="#4285F4"
                d="M23.745 12.27c0-.7-.06-1.4-.19-2.07H12v4.51h6.6c-.29 1.52-1.14 2.82-2.4 3.68v3.05h3.88c2.27-2.09 3.66-5.17 3.66-9.17z"
              />
              <path
                fill="#34A853"
                d="M12 24c3.24 0 5.95-1.08 7.93-2.91l-3.88-3.05c-1.08.72-2.45 1.16-4.05 1.16-3.12 0-5.77-2.1-6.72-4.93H1.25v3.15C3.26 21.36 7.33 24 12 24z"
              />
              <path
                fill="#FBBC05"
                d="M5.28 14.27c-.25-.72-.38-1.49-.38-2.27s.13-1.55.38-2.27V6.58H1.25C.45 8.18 0 10.02 0 12s.45 3.82 1.25 5.42l4.03-3.15z"
              />
              <path
                fill="#EA4335"
                d="M12 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42C17.95 1.19 15.24 0 12 0 7.33 0 3.26 2.64 1.25 6.58l4.03 3.15c.95-2.83 3.6-4.98 6.72-4.98z"
              />
            </svg>
            <span>Continue with Google</span>
          </button>

          {/* Instant Demo Access Button */}
          <div className="pt-2 text-center border-t border-border/50">
            <button
              type="button"
              onClick={() => {
                saveProfileLocally('mathi.dev@gmail.com');
                router.push('/');
              }}
              className="text-xs font-semibold text-primary hover:underline inline-flex items-center gap-1.5"
            >
              <Sparkles className="w-3.5 h-3.5" />
              <span>Skip sign-in & explore offline (Demo Mode)</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
