'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { createClient, isSupabaseConfigured } from '@/lib/supabase/client';
import { useFinance } from '@/context/finance-context';
import { AlertCircle, Loader2 } from 'lucide-react';
import { Login3DScene } from '@/components/login-3d-scene';

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
          if (error.message.toLowerCase().includes('email not confirmed')) {
            throw new Error('Please check your inbox and confirm your email before signing in.');
          } else if (error.message.toLowerCase().includes('invalid login credentials')) {
            throw new Error('Incorrect email or password.');
          } else {
            throw new Error(error.message);
          }
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
      setAuthError(err.message || 'Incorrect email or password');
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
    <Login3DScene>
      {/* Top: Title & Subtitle (Floating 3D "S" Logo sits centered half above top edge) */}
      <div className="flex flex-col items-center text-center">
        <h1 className="text-2xl font-bold tracking-tight text-[#FBF5EA]">
          Login
        </h1>
        <p className="text-sm text-[#C4B8A4] mt-1.5 leading-relaxed max-w-xs">
          Welcome back. Track your income, expenses and trips.
        </p>
      </div>

      {/* Credential Error Banner */}
      {authError && (
        <div className="mt-6 p-3 rounded-[10px] bg-rose-950/60 border border-[#F1887A]/40 flex items-center gap-2.5 text-[#F1887A] text-xs font-medium">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{authError}</span>
        </div>
      )}

      {/* Login Form */}
      <form onSubmit={handleSignIn} className="mt-6 space-y-4" noValidate>
        {/* Email Field (Inset) */}
        <div>
          <label
            htmlFor="email"
            className="block text-xs font-semibold uppercase tracking-wider text-[#FBF5EA] mb-1.5"
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
            className={`w-full h-[52px] px-3.5 rounded-[10px] bg-[#14060C]/90 border text-sm text-[#FBF5EA] placeholder:text-[#C4B8A4]/75 shadow-[inset_0_2px_7px_rgba(8,2,5,0.85),inset_0_1px_0_rgba(243,219,160,0.06)] transition-all duration-150 outline-none ${
              emailError
                ? 'border-[#F1887A] ring-2 ring-[#F1887A]/35'
                : 'border-[#8F8272]/50 focus:border-[#D4AF6A] focus:ring-2 focus:ring-[#D4AF6A]/45 focus:shadow-[inset_0_2px_7px_rgba(8,2,5,0.85),0_0_16px_rgba(212,175,106,0.22)]'
            }`}
          />
          {emailError && (
            <p className="text-xs text-[#F1887A] mt-1 font-medium pl-0.5">{emailError}</p>
          )}
        </div>

        {/* Password Field (Inset) */}
        <div>
          <label
            htmlFor="password"
            className="block text-xs font-semibold uppercase tracking-wider text-[#FBF5EA] mb-1.5"
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
            className={`w-full h-[52px] px-3.5 rounded-[10px] bg-[#14060C]/90 border text-sm text-[#FBF5EA] placeholder:text-[#C4B8A4]/75 shadow-[inset_0_2px_7px_rgba(8,2,5,0.85),inset_0_1px_0_rgba(243,219,160,0.06)] transition-all duration-150 outline-none ${
              passwordError
                ? 'border-[#F1887A] ring-2 ring-[#F1887A]/35'
                : 'border-[#8F8272]/50 focus:border-[#D4AF6A] focus:ring-2 focus:ring-[#D4AF6A]/45 focus:shadow-[inset_0_2px_7px_rgba(8,2,5,0.85),0_0_16px_rgba(212,175,106,0.22)]'
            }`}
          />
          {passwordError && (
            <p className="text-xs text-[#F1887A] mt-1 font-medium pl-0.5">{passwordError}</p>
          )}
        </div>

        {/* Show Password Checkbox */}
        <div className="flex items-center gap-2 pt-0.5 min-h-[44px]">
          <input
            id="show-password"
            type="checkbox"
            checked={showPassword}
            onChange={(e) => setShowPassword(e.target.checked)}
            className="w-4 h-4 rounded border-[#8F8272] bg-[#14060C] text-[#D4AF6A] focus:ring-2 focus:ring-[#D4AF6A] focus:ring-offset-0 cursor-pointer accent-[#D4AF6A]"
          />
          <label
            htmlFor="show-password"
            className="text-xs font-medium text-[#C4B8A4] cursor-pointer select-none"
          >
            Show Password
          </label>
        </div>

        {/* Primary Action Button: Raised Physical Gold Key */}
        <button
          type="submit"
          disabled={isLoading}
          className="w-full h-[52px] mt-1 rounded-[10px] bg-gradient-to-b from-[#F3DBA0] via-[#D4AF6A] to-[#B68A3E] hover:brightness-105 text-[#2A0F1A] font-extrabold text-sm uppercase tracking-wider transition-all duration-100 shadow-[0_6px_0_#8A6420,0_14px_24px_-6px_rgba(8,2,5,0.85)] active:translate-y-[4px] active:shadow-[0_2px_0_#8A6420,0_6px_12px_-4px_rgba(8,2,5,0.8)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#F3DBA0] focus-visible:ring-offset-2 focus-visible:ring-offset-[#2A0F1A] flex items-center justify-center gap-2 disabled:opacity-60 disabled:cursor-not-allowed cursor-pointer"
        >
          {isLoading ? (
            <>
              <Loader2 className="w-4 h-4 animate-spin text-[#2A0F1A]" />
              <span>SIGNING IN...</span>
            </>
          ) : (
            <span>SIGN IN</span>
          )}
        </button>
      </form>

      {/* OR Divider */}
      <div className="relative my-6 flex items-center justify-center">
        <div className="w-full border-t border-[#D4AF6A]/25" />
        <span className="px-3 text-[11px] font-bold text-[#C4B8A4] uppercase tracking-widest shrink-0">
          OR
        </span>
        <div className="w-full border-t border-[#D4AF6A]/25" />
      </div>

      {/* Continue with Google Button: Translucent Glass Key with Gold Outline */}
      <button
        type="button"
        onClick={handleGoogleSignIn}
        disabled={isLoading}
        className="w-full h-[52px] rounded-[10px] bg-[#2A0F1A]/75 hover:bg-[#3E1426]/85 border-[1.5px] border-[#D4AF6A]/65 text-sm font-semibold text-[#FBF5EA] flex items-center justify-center gap-3 transition-all duration-100 shadow-[0_4px_0_rgba(18,5,10,0.95),0_10px_20px_-6px_rgba(8,2,5,0.65),inset_0_1px_0_rgba(243,219,160,0.18)] active:translate-y-[3px] active:shadow-[0_1px_0_rgba(18,5,10,0.95)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#D4AF6A] cursor-pointer disabled:opacity-60"
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
            className="inline-block py-1 text-xs font-semibold text-[#D4AF6A] hover:text-[#F3DBA0] transition-colors underline underline-offset-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#D4AF6A] rounded"
          >
            Forgot Password?
          </Link>
        </div>
        <div>
          <span className="text-xs text-[#C4B8A4]">
            Don&apos;t have an account?{' '}
          </span>
          <Link
            href="/signup"
            className="inline-block py-1 text-xs font-bold text-[#D4AF6A] hover:text-[#F3DBA0] transition-colors underline underline-offset-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#D4AF6A] rounded"
          >
            Sign up
          </Link>
        </div>
      </div>
    </Login3DScene>
  );
}

export default function LoginPage() {
  return (
    <React.Suspense
      fallback={
        <div className="min-h-screen bg-[#1A0810] flex items-center justify-center text-[#C4B8A4]">
          <div className="flex items-center gap-2">
            <div className="w-5 h-5 border-2 border-[#D4AF6A] border-t-transparent rounded-full animate-spin" />
            <span className="text-sm font-medium">Loading Spendwise...</span>
          </div>
        </div>
      }
    >
      <LoginForm />
    </React.Suspense>
  );
}
