'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useFinance } from '@/context/finance-context';
import { QuickAddModal } from './quick-add-modal';
import { EditProfileModal } from './edit-profile-modal';
import {
  LayoutDashboard,
  ArrowLeftRight,
  PieChart,
  Tags,
  Plane,
  BarChart3,
  Settings,
  Plus,
  Sun,
  Moon,
  Wallet,
  LogIn,
} from 'lucide-react';
import { useTheme } from 'next-themes';

const NAV_ITEMS = [
  { href: '/', label: 'Overview', icon: LayoutDashboard },
  { href: '/transactions', label: 'Activity', icon: ArrowLeftRight },
  { href: '/budgets', label: 'Budgets', icon: PieChart },
  { href: '/trips', label: 'Travel', icon: Plane },
  { href: '/categories', label: 'Categories', icon: Tags },
  { href: '/reports', label: 'Analytics', icon: BarChart3 },
  { href: '/settings', label: 'Settings', icon: Settings },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const { isRealtimeConnected, isSupabaseLive, currency, userProfile, user } = useFinance();
  const { theme, setTheme } = useTheme();
  const [isQuickAddOpen, setIsQuickAddOpen] = useState(false);
  const [isEditProfileOpen, setIsEditProfileOpen] = useState(false);

  if (pathname === '/login' || pathname === '/signup') {
    return <main className="min-h-screen bg-background">{children}</main>;
  }

  return (
    <div className="min-h-screen flex flex-col md:flex-row bg-background text-foreground relative overflow-x-hidden transition-colors duration-200">
      {/* Desktop Left Sidebar */}
      <aside className="hidden md:flex flex-col w-64 border-r border-border bg-card/60 backdrop-blur-xl p-5 sticky top-0 h-screen justify-between shrink-0 z-30">
        <div className="space-y-6">
          {/* Brand Logo */}
          <Link href="/" className="flex items-center gap-3 px-2 group">
            <div className="w-10 h-10 rounded-2xl bg-primary flex items-center justify-center text-primary-foreground shadow-sm group-hover:scale-105 transition-transform">
              <Wallet className="w-5 h-5 stroke-[2.2]" />
            </div>
            <div>
              <div className="flex items-center gap-1.5">
                <span className="text-xl font-extrabold tracking-tight text-foreground">Spendwise</span>
              </div>
              <p className="text-[11px] text-muted-foreground font-medium">
                Personal Finance & Travel
              </p>
            </div>
          </Link>

          {/* Calm Indigo Quick Log Button */}
          <button
            onClick={() => setIsQuickAddOpen(true)}
            className="w-full py-3 px-4 rounded-2xl bg-primary hover:bg-primary/90 text-primary-foreground font-semibold text-sm flex items-center justify-center gap-2 shadow-sm transition-all active:scale-[0.98]"
          >
            <Plus className="w-4 h-4 stroke-[2.5]" />
            <span>Add Transaction</span>
          </button>

          {/* Navigation Links with Active Pill */}
          <nav className="space-y-1">
            {NAV_ITEMS.map((item) => {
              const Icon = item.icon;
              const isActive = pathname === item.href;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`flex items-center gap-3 px-3.5 py-2.5 rounded-2xl text-sm font-medium transition-all ${
                    isActive
                      ? 'bg-secondary text-primary font-bold shadow-sm'
                      : 'text-muted-foreground hover:bg-secondary/50 hover:text-foreground'
                  }`}
                >
                  <Icon className={`w-4 h-4 ${isActive ? 'text-primary' : 'text-muted-foreground'}`} />
                  <span>{item.label}</span>
                  {isActive && (
                    <div className="ml-auto w-1.5 h-1.5 rounded-full bg-primary" />
                  )}
                </Link>
              );
            })}
          </nav>
        </div>

        {/* Footer info: User Profile, Sync & Theme */}
        <div className="pt-4 border-t border-border space-y-3">
          {/* User Profile Pill */}
          <button
            onClick={() => setIsEditProfileOpen(true)}
            className="w-full p-2.5 rounded-2xl bg-secondary/40 hover:bg-secondary/70 border border-border/50 flex items-center gap-3 transition-all text-left group"
            title="Click to edit profile"
          >
            <div className="w-9 h-9 rounded-xl bg-primary/15 text-primary border border-primary/30 flex items-center justify-center font-bold text-xs shrink-0 group-hover:scale-105 transition-transform">
              {userProfile.initials}
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-xs font-bold text-foreground truncate">{userProfile.name}</p>
              <p className="text-[11px] text-muted-foreground truncate">{userProfile.email}</p>
            </div>
          </button>

          {/* Quick Login / Account Link */}
          <Link
            href="/login"
            className="w-full py-2 px-3 rounded-2xl bg-secondary/60 hover:bg-secondary text-foreground hover:text-primary font-semibold text-xs flex items-center justify-center gap-2 border border-border/60 transition-all active:scale-[0.98]"
          >
            <LogIn className="w-3.5 h-3.5 text-primary" />
            <span>{user ? 'Switch Account / Login' : 'Sign In / Create Account'}</span>
          </Link>

          <div className="p-3 rounded-2xl bg-secondary/50 flex items-center justify-between text-xs">
            <div className="flex items-center gap-2">
              <div
                className={`w-2 h-2 rounded-full ${
                  isRealtimeConnected ? 'bg-emerald-500 animate-pulse' : 'bg-muted-foreground'
                }`}
              />
              <span className="font-medium text-muted-foreground">
                {isRealtimeConnected ? 'Realtime Sync' : isSupabaseLive ? 'Connected' : 'Offline Cache'}
              </span>
            </div>
            <span className="font-bold text-foreground">
              {currency.symbol} ({currency.code})
            </span>
          </div>

          <div className="flex items-center justify-between text-xs px-1 text-muted-foreground">
            <span>Spendwise 2026</span>
            <button
              onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
              className="p-2 rounded-xl hover:bg-secondary transition-colors text-muted-foreground hover:text-foreground"
              title="Toggle Theme"
            >
              {theme === 'dark' ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
            </button>
          </div>
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col min-w-0 pb-24 md:pb-8">
        {/* Mobile Header */}
        <header className="md:hidden flex items-center justify-between px-5 py-4 border-b border-border bg-card/80 backdrop-blur-md sticky top-0 z-40">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-xl bg-primary flex items-center justify-center text-primary-foreground">
              <Wallet className="w-4 h-4 stroke-[2.2]" />
            </div>
            <span className="font-bold text-base tracking-tight text-foreground">Spendwise</span>
          </div>

          <div className="flex items-center gap-2">
            <Link
              href="/login"
              className="p-2 rounded-xl hover:bg-secondary transition-colors text-muted-foreground hover:text-foreground"
              title="Sign In / Login"
            >
              <LogIn className="w-4 h-4 text-primary" />
            </Link>
            <button
              onClick={() => setIsEditProfileOpen(true)}
              className="w-8 h-8 rounded-xl bg-primary/15 text-primary border border-primary/30 flex items-center justify-center font-bold text-xs"
              title="Edit Profile"
            >
              {userProfile.initials}
            </button>
            <button
              onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
              className="p-2 rounded-xl hover:bg-secondary transition-colors text-muted-foreground"
            >
              {theme === 'dark' ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
            </button>
            <button
              onClick={() => setIsQuickAddOpen(true)}
              className="p-2 rounded-xl bg-primary text-primary-foreground font-semibold text-xs flex items-center gap-1.5"
            >
              <Plus className="w-4 h-4 stroke-[2.5]" />
              <span>Add</span>
            </button>
          </div>
        </header>

        {/* Page Content */}
        <main className="flex-1 p-4 sm:p-8 max-w-7xl mx-auto w-full">
          {children}
        </main>
      </div>

      {/* Mobile Bottom Navigation Bar (Matches Android Tabs) */}
      <nav className="md:hidden fixed bottom-0 left-0 right-0 z-40 bg-card/90 backdrop-blur-xl border-t border-border px-3 py-2 flex items-center justify-around">
        {NAV_ITEMS.slice(0, 5).map((item) => {
          const Icon = item.icon;
          const isActive = pathname === item.href;
          return (
            <Link
              key={item.href}
              href={item.href}
              className={`flex flex-col items-center gap-1 py-1 px-3 rounded-2xl transition-colors ${
                isActive ? 'text-primary font-bold' : 'text-muted-foreground'
              }`}
            >
              <Icon className="w-5 h-5" />
              <span className="text-[10px] tracking-tight">{item.label}</span>
            </Link>
          );
        })}
      </nav>

      {/* Quick Add Modal */}
      <QuickAddModal
        isOpen={isQuickAddOpen}
        onClose={() => setIsQuickAddOpen(false)}
      />

      {/* Edit Profile Modal */}
      <EditProfileModal
        isOpen={isEditProfileOpen}
        onClose={() => setIsEditProfileOpen(false)}
      />
    </div>
  );
}
