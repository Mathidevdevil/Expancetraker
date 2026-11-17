'use client';

import React, { useState } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import { createClient, isSupabaseConfigured } from '@/lib/supabase/client';
import { AlertCircle, CheckCircle2, Loader2, ArrowLeft } from 'lucide-react';

function ForgotPasswordForm() {
  const supabase = createClient();
  const isConfigured = isSupabaseConfigured();

  const [email, setEmail] = useState('');
  const [emailError, setEmailError] = useState('');
  const [statusMessage, setStatusMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const handleResetPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setEmailError('');
    setStatusMessage(null);

    const cleanEmail = email.trim();
    if (!cleanEmail) {
      setEmailError('Email is required.');
      return;
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(cleanEmail)) {
      setEmailError('Please enter a valid email address.');
      return;
    }

    setIsLoading(true);

    try {
      if (isConfigured) {
        const { error } = await supabase.auth.resetPasswordForEmail(cleanEmail, {
          redirectTo: `${window.location.origin}/auth/callback?next=/settings`,
        });
        if (error) throw error;
      }

      setStatusMessage({
        type: 'success',
        text: `If an account exists for ${cleanEmail}, we have sent password reset instructions.`,
      });
    } catch (err: any) {
      setStatusMessage({
        type: 'error',
        text: err.message || 'Failed to send reset email. Please try again.',
      });
    } finally {
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
            Forgot Password
          </h1>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1.5 leading-relaxed max-w-xs">
            Enter your email and we'll send a password reset link.
          </p>
        </div>

        {/* Status Message */}
        {statusMessage && (
          <div
            className={`mt-6 p-3 rounded-[10px] border flex items-center gap-2.5 text-xs font-medium ${
              statusMessage.type === 'success'
                ? 'bg-emerald-50 dark:bg-emerald-950/40 border-emerald-200 dark:border-emerald-900/60 text-emerald-700 dark:text-emerald-400'
                : 'bg-rose-50 dark:bg-rose-950/40 border-rose-200 dark:border-rose-900/60 text-rose-600 dark:text-rose-400'
            }`}
          >
            {statusMessage.type === 'success' ? (
              <CheckCircle2 className="w-4 h-4 shrink-0 text-emerald-500" />
            ) : (
              <AlertCircle className="w-4 h-4 shrink-0 text-rose-500" />
            )}
            <span>{statusMessage.text}</span>
          </div>
        )}

        {/* Form */}
        <form onSubmit={handleResetPassword} className="mt-6 space-y-4" noValidate>
          <div>
            <label
              htmlFor="reset-email"
              className="block text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300 mb-1.5"
            >
              Email
            </label>
            <input
              id="reset-email"
              type="email"
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
                if (emailError) setEmailError('');
                if (statusMessage) setStatusMessage(null);
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

          <button
            type="submit"
            disabled={isLoading}
            className="w-full h-[52px] mt-2 rounded-[10px] bg-[#E7B94F] hover:bg-[#DDAE42] active:bg-[#CE9F34] text-[#0D0F1F] font-bold text-sm uppercase tracking-wider transition-all duration-150 shadow-md shadow-[#E7B94F]/20 flex items-center justify-center gap-2 disabled:opacity-60 disabled:cursor-not-allowed cursor-pointer"
          >
            {isLoading ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin text-[#0D0F1F]" />
                <span>SENDING LINK...</span>
              </>
            ) : (
              <span>SEND RESET LINK</span>
            )}
          </button>
        </form>

        {/* Back to Login */}
        <div className="mt-6 text-center">
          <Link
            href="/login"
            className="inline-flex items-center gap-1.5 text-xs font-bold text-[#E7B94F] hover:text-[#DDAE42] transition-colors underline underline-offset-2"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            <span>Back to Login</span>
          </Link>
        </div>

      </div>
    </div>
  );
}

export default function ForgotPasswordPage() {
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
      <ForgotPasswordForm />
    </React.Suspense>
  );
}
