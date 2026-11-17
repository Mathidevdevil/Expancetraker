'use client';

import React, { useMemo, useState } from 'react';
import Link from 'next/link';
import { useFinance } from '@/context/finance-context';
import { formatCurrency, formatFriendlyDate, formatDate } from '@/lib/utils';
import { CategoryIcon } from '@/components/category-icon';
import { EditProfileModal } from '@/components/edit-profile-modal';
import {
  TrendingUp,
  TrendingDown,
  ArrowRight,
  Plane,
  Receipt,
  Plus,
  ShieldCheck,
  Calendar,
  Wallet,
} from 'lucide-react';

export default function DashboardPage() {
  const { transactions, categories, budgets, trips, currency, userProfile } = useFinance();
  const [isEditProfileOpen, setIsEditProfileOpen] = useState(false);

  const currentDate = new Date();
  const currentMonth = currentDate.getMonth() + 1;
  const currentYear = currentDate.getFullYear();

  // Statistics calculation
  const {
    totalBalance,
    monthlyIncome,
    monthlyExpense,
    categorySpending,
    groupedTransactions,
  } = useMemo(() => {
    let balance = 0;
    let mIncome = 0;
    let mExpense = 0;
    const spendingMap = new Map<string, number>();

    transactions.forEach((tx) => {
      if (tx.type === 'income') {
        balance += tx.amount;
      } else {
        balance -= tx.amount;
      }

      const txDate = new Date(tx.date);
      if (txDate.getMonth() + 1 === currentMonth && txDate.getFullYear() === currentYear) {
        if (tx.type === 'income') {
          mIncome += tx.amount;
        } else {
          mExpense += tx.amount;
          if (tx.category_id) {
            spendingMap.set(tx.category_id, (spendingMap.get(tx.category_id) || 0) + tx.amount);
          }
        }
      }
    });

    const categoryList = Array.from(spendingMap.entries())
      .map(([catId, amount]) => {
        const cat = categories.find((c) => c.id === catId);
        return {
          id: catId,
          name: cat?.name || 'Uncategorized',
          color: cat?.color || '#6366F1',
          icon: cat?.icon || 'Tag',
          amount,
          percentage: mExpense > 0 ? (amount / mExpense) * 100 : 0,
        };
      })
      .sort((a, b) => b.amount - a.amount);

    // Group recent transactions by friendly date
    const recent = transactions.slice(0, 10);
    const groups: { [date: string]: typeof recent } = {};
    recent.forEach((tx) => {
      const header = formatFriendlyDate(tx.date);
      if (!groups[header]) groups[header] = [];
      groups[header].push(tx);
    });

    return {
      totalBalance: balance,
      monthlyIncome: mIncome,
      monthlyExpense: mExpense,
      categorySpending: categoryList,
      groupedTransactions: groups,
    };
  }, [transactions, categories, currentMonth, currentYear]);

  // Greeting based on time
  const greeting = useMemo(() => {
    const hour = new Date().getHours();
    if (hour < 12) return 'Good morning';
    if (hour < 17) return 'Good afternoon';
    return 'Good evening';
  }, []);

  // Net flow this month
  const netSavings = monthlyIncome - monthlyExpense;
  const isPositiveNet = netSavings >= 0;

  return (
    <div className="space-y-8 animate-in fade-in duration-300 max-w-6xl mx-auto pb-16">
      {/* 1. Header Row */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <p className="text-sm font-medium text-muted-foreground">
            {greeting}, {userProfile.name} 👋
          </p>
          <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-foreground">
            Financial Overview
          </h1>
        </div>

        <div className="flex items-center gap-3">
          <div className="px-3.5 py-1.5 rounded-full bg-secondary/80 border border-border text-xs font-semibold text-muted-foreground flex items-center gap-2">
            <ShieldCheck className="w-4 h-4 text-emerald-500" />
            <span>Supabase RLS Active</span>
          </div>

          <button
            onClick={() => setIsEditProfileOpen(true)}
            className="w-10 h-10 rounded-2xl bg-primary/15 hover:bg-primary/25 border border-primary/30 text-primary flex items-center justify-center font-bold text-sm shadow-sm transition-all hover:scale-105 active:scale-95"
            title="Edit Profile"
          >
            {userProfile.initials}
          </button>
        </div>
      </div>

      {/* First Login Empty Welcome Banner */}
      {transactions.length === 0 && (
        <div className="p-5 sm:p-6 rounded-3xl bg-secondary/40 border border-border/70 flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 rounded-2xl bg-primary/10 text-primary flex items-center justify-center shrink-0">
              <Wallet className="w-6 h-6" />
            </div>
            <div>
              <h3 className="text-sm sm:text-base font-bold text-foreground">
                Welcome to Spendwise!
              </h3>
              <p className="text-xs text-muted-foreground">
                Your workspace is empty and ready. Use the <strong>+ Quick Add</strong> button in the navigation bar to log your first income or expense.
              </p>
            </div>
          </div>
        </div>
      )}

      {/* 2. Modern 2026 Hero Balance Card */}
      <div className="p-7 sm:p-8 rounded-3xl fintech-card bg-gradient-to-b from-card to-secondary/30 relative overflow-hidden">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-6">
          <div className="space-y-3">
            <span className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
              Total Net Balance
            </span>
            <div className="text-4xl sm:text-5xl font-black tracking-tight text-foreground tabular-nums">
              {formatCurrency(totalBalance, currency.symbol)}
            </div>

            {/* Monthly Trend Indicator Pill */}
            <div className="flex items-center gap-2 pt-1">
              <span
                className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-xl text-xs font-bold ${
                  isPositiveNet
                    ? 'bg-emerald-500/10 text-emerald-500 border border-emerald-500/20'
                    : 'bg-rose-500/10 text-rose-500 border border-rose-500/20'
                }`}
              >
                {isPositiveNet ? (
                  <TrendingUp className="w-3.5 h-3.5 stroke-[2.5]" />
                ) : (
                  <TrendingDown className="w-3.5 h-3.5 stroke-[2.5]" />
                )}
                <span>
                  {isPositiveNet ? '+' : ''}
                  {formatCurrency(netSavings, currency.symbol, false)} this month
                </span>
              </span>
              <span className="text-xs text-muted-foreground">
                ({currentDate.toLocaleString('default', { month: 'short' })} flow)
              </span>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <Link
              href="/transactions"
              className="px-5 py-3 rounded-2xl bg-primary text-primary-foreground font-semibold text-sm hover:opacity-90 transition-opacity flex items-center gap-2 shadow-sm"
            >
              <span>View All Transactions</span>
              <ArrowRight className="w-4 h-4" />
            </Link>
          </div>
        </div>
      </div>

      {/* 3. Compact Stat Cards with Mini Sparklines (Income & Expense) */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
        {/* Monthly Inflow */}
        <div className="p-6 rounded-3xl fintech-card fintech-card-hover space-y-4">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
              Monthly Inflow
            </span>
            <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.5)]" />
          </div>
          <div className="text-2xl sm:text-3xl font-bold text-emerald-500 tabular-nums">
            +{formatCurrency(monthlyIncome, currency.symbol)}
          </div>
          {/* Smooth SVG Sparkline */}
          <div className="h-8 w-full pt-1">
            <svg viewBox="0 0 100 25" className="w-full h-full stroke-emerald-500 fill-none" preserveAspectRatio="none">
              <path
                d="M0,20 Q15,18 25,22 T50,12 T75,8 T100,2"
                strokeWidth="2.5"
                strokeLinecap="round"
              />
            </svg>
          </div>
        </div>

        {/* Monthly Outflow */}
        <div className="p-6 rounded-3xl fintech-card fintech-card-hover space-y-4">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
              Monthly Outflow
            </span>
            <span className="w-2.5 h-2.5 rounded-full bg-rose-500 shadow-[0_0_8px_rgba(244,63,94,0.5)]" />
          </div>
          <div className="text-2xl sm:text-3xl font-bold text-rose-500 tabular-nums">
            {monthlyExpense > 0 ? '-' : ''}
            {formatCurrency(monthlyExpense, currency.symbol)}
          </div>
          {/* Smooth SVG Sparkline */}
          <div className="h-8 w-full pt-1">
            <svg viewBox="0 0 100 25" className="w-full h-full stroke-rose-500 fill-none" preserveAspectRatio="none">
              <path
                d="M0,5 Q20,10 35,6 T65,18 T85,15 T100,23"
                strokeWidth="2.5"
                strokeLinecap="round"
              />
            </svg>
          </div>
        </div>
      </div>

      {/* 4. Category Spending Donut & Upcoming Trip */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Category Breakdown (7 cols) */}
        <div className="lg:col-span-7 p-6 rounded-3xl fintech-card space-y-5">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-bold text-foreground">
              Spending by Category
            </h2>
            <Link
              href="/budgets"
              className="text-xs font-semibold text-primary hover:underline"
            >
              Manage Budgets
            </Link>
          </div>

          {categorySpending.length === 0 ? (
            <div className="py-12 text-center text-sm text-muted-foreground">
              No expenses recorded this month
            </div>
          ) : (
            <div className="space-y-4">
              {/* Donut representation & bars */}
              <div className="space-y-3">
                {categorySpending.slice(0, 5).map((cat) => (
                  <div key={cat.id} className="space-y-1.5">
                    <div className="flex items-center justify-between text-xs">
                      <div className="flex items-center gap-2 font-medium text-foreground">
                        <span
                          className="w-2.5 h-2.5 rounded-full shrink-0"
                          style={{ backgroundColor: cat.color }}
                        />
                        <span>{cat.name}</span>
                      </div>
                      <div className="text-muted-foreground font-semibold">
                        {formatCurrency(cat.amount, currency.symbol, false)}{' '}
                        <span className="text-[11px] font-normal text-muted-foreground/80">
                          ({cat.percentage.toFixed(0)}%)
                        </span>
                      </div>
                    </div>
                    {/* Slim progress bar */}
                    <div className="h-2 w-full rounded-full bg-secondary overflow-hidden">
                      <div
                        className="h-full rounded-full transition-all duration-500"
                        style={{
                          width: `${Math.min(cat.percentage, 100)}%`,
                          backgroundColor: cat.color,
                        }}
                      />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Upcoming Travel Plans (5 cols) */}
        <div className="lg:col-span-5 p-6 rounded-3xl fintech-card flex flex-col justify-between space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Plane className="w-4 h-4 text-cyan-500" />
              <h2 className="text-base font-bold text-foreground">
                Upcoming Travel
              </h2>
            </div>
            <Link
              href="/trips"
              className="text-xs font-semibold text-primary hover:underline"
            >
              All Trips
            </Link>
          </div>

          {trips.length === 0 ? (
            <div className="py-10 text-center text-sm text-muted-foreground">
              No upcoming travel plans scheduled
            </div>
          ) : (
            <div className="p-4 rounded-2xl bg-secondary/50 border border-border/50 space-y-3">
              <div className="flex items-center justify-between">
                <span className="font-bold text-foreground text-base">
                  {trips[0].destination}
                </span>
                <span className="px-2.5 py-0.5 rounded-lg text-xs font-semibold bg-cyan-500/10 text-cyan-500 border border-cyan-500/20">
                  {trips[0].start_date}
                </span>
              </div>
              <p className="text-xs text-muted-foreground">
                {trips[0].notes || 'Personal vacation'}
              </p>
              <div className="flex items-center justify-between text-xs font-medium pt-2 border-t border-border">
                <span className="text-muted-foreground">Target Budget</span>
                <span className="font-bold text-foreground">
                  {formatCurrency(trips[0].budget, currency.symbol, false)}
                </span>
              </div>
            </div>
          )}

          <div className="pt-2">
            <Link
              href="/trips"
              className="w-full py-2.5 px-4 rounded-2xl bg-secondary hover:bg-secondary/80 text-foreground font-semibold text-xs flex items-center justify-center gap-2 transition-colors border border-border"
            >
              <span>Open Travel OS</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>
        </div>
      </div>

      {/* 5. Recent Activity List Grouped by Date */}
      <div className="p-6 rounded-3xl fintech-card space-y-5">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Receipt className="w-4 h-4 text-primary" />
            <h2 className="text-base font-bold text-foreground">
              Recent Activity
            </h2>
          </div>
          <Link
            href="/transactions"
            className="text-xs font-semibold text-primary hover:underline"
          >
            View History
          </Link>
        </div>

        {Object.keys(groupedTransactions).length === 0 ? (
          <div className="py-12 text-center text-sm text-muted-foreground">
            No recent activity recorded
          </div>
        ) : (
          <div className="space-y-6">
            {Object.entries(groupedTransactions).map(([dateGroup, txList]) => (
              <div key={dateGroup} className="space-y-2">
                <div className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground px-1">
                  {dateGroup}
                </div>
                <div className="space-y-1.5">
                  {txList.map((tx) => {
                    const cat = categories.find((c) => c.id === tx.category_id);
                    const isIncome = tx.type === 'income';

                    return (
                      <div
                        key={tx.id}
                        className="p-3.5 rounded-2xl bg-secondary/30 hover:bg-secondary/60 transition-colors flex items-center justify-between border border-border/40"
                      >
                        <div className="flex items-center gap-3.5">
                          <CategoryIcon
                            icon={cat?.icon || 'Tag'}
                            color={cat?.color || '#6366F1'}
                            size={40}
                          />
                          <div>
                            <p className="text-sm font-semibold text-foreground">
                              {tx.note || cat?.name || 'Transaction'}
                            </p>
                            <p className="text-xs text-muted-foreground">
                              {cat?.name || 'General'} • {tx.payment_method}
                            </p>
                          </div>
                        </div>

                        <div
                          className={`text-sm font-bold tabular-nums ${
                            isIncome ? 'text-emerald-500' : 'text-foreground'
                          }`}
                        >
                          {isIncome ? '+' : '-'}
                          {formatCurrency(tx.amount, currency.symbol)}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <EditProfileModal
        isOpen={isEditProfileOpen}
        onClose={() => setIsEditProfileOpen(false)}
      />
    </div>
  );
}
