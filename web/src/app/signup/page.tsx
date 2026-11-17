'use client';

import React, { Suspense } from 'react';
import LoginPage from '../login/page';

export default function SignUpPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen bg-[#0B0B10] flex items-center justify-center text-zinc-400">
          <div className="flex items-center gap-2">
            <div className="w-5 h-5 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin" />
            <span className="text-sm font-medium">Loading Spendwise...</span>
          </div>
        </div>
      }
    >
      <LoginPage />
    </Suspense>
  );
}
