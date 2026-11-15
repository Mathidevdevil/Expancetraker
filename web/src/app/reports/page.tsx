'use client';

import React, { useMemo } from 'react';
import { useFinance } from '@/context/finance-context';
import { formatCurrency } from '@/lib/utils';
import { CategoryIcon } from '@/components/category-icon';
import {
  Download,
  TrendingUp,
  TrendingDown,
  Percent,
} from 'lucide-react';

export default function ReportsPage() {
  const { transactions, categories, currency, exportToCSV } = useFinance();

  const monthlyFlow = useMemo(() => {
    const map = new Map<string, { monthKey: string; label: string; income: number; expense: number }>();

    const now = new Date();
    for (let i = 5; i >= 0; i--) {
      const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
      const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
      const label = d.toLocaleString('default', { month: 'short' });
      map.set(key, { monthKey: key, label, income: 0, expense: 0 });
    }

    transactions.forEach((tx) => {
      const txDate = new Date(tx.date);
      const key = `${txDate.getFullYear()}-${String(txDate.getMonth() + 1).padStart(2, '0')}`;
      if (map.has(key)) {
        const item = map.get(key)!;
        if (tx.type === 'income') {
          item.income += tx.amount;
        } else {
          item.expense += tx.amount;
        }
      }
    });

    return Array.from(map.values());
  }, [transactions]);

  const maxFlowValue = useMemo(() => {
    let max = 1000;
    monthlyFlow.forEach((f) => {
      if (f.income > max) max = f.income;
      if (f.expense > max) max = f.expense;
    });
    return max;
  }, [monthlyFlow]);

  const categorySummary = useMemo(() => {
    const spendingMap = new Map<string, number>();
    let totalExpense = 0;

    transactions.forEach((tx) => {
      if (tx.type === 'expense' && tx.category_id) {
        spendingMap.set(tx.category_id, (spendingMap.get(tx.category_id) || 0) + tx.amount);
        totalExpense += tx.amount;
      }
    });

    return Array.from(spendingMap.entries())
      .map(([catId, amount]) => {
        const cat = categories.find((c) => c.id === catId);
        return {
          id: catId,
          name: cat?.name || 'Uncategorized',
          color: cat?.color || '#6366F1',
          icon: cat?.icon || 'Tag',
          amount,
          percentage: totalExpense > 0 ? (amount / totalExpense) * 100 : 0,
        };
      })
      .sort((a, b) => b.amount - a.amount);
  }, [transactions, categories]);

  const totalAllIncome = useMemo(() => {
    return transactions.filter((t) => t.type === 'income').reduce((sum, t) => sum + t.amount, 0);
  }, [transactions]);

  const totalAllExpense = useMemo(() => {
    return transactions.filter((t) => t.type === 'expense').reduce((sum, t) => sum + t.amount, 0);
  }, [transactions]);

  const savingsRate = totalAllIncome > 0 ? ((totalAllIncome - totalAllExpense) / totalAllIncome) * 100 : 0;

  return (
    <div className="space-y-6 animate-in fade-in duration-300 max-w-6xl mx-auto pb-16">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-foreground">
            Financial Analytics
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Cashflow velocity, savings trajectory, and category distribution
          </p>
        </div>

        <button
          onClick={() => exportToCSV(transactions)}
          className="px-4 py-2.5 rounded-2xl bg-secondary hover:bg-secondary/80 text-foreground text-xs font-semibold flex items-center gap-2 border border-border transition-colors self-start sm:self-auto"
        >
          <Download className="w-3.5 h-3.5" />
          <span>Export All Data (CSV)</span>
        </button>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="p-6 rounded-3xl fintech-card space-y-2">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
              Cumulative Inflow
            </span>
            <TrendingUp className="w-4 h-4 text-emerald-500" />
          </div>
          <p className="text-2xl font-bold text-emerald-500 tabular-nums">
            +{formatCurrency(totalAllIncome, currency.symbol, false)}
          </p>
        </div>

        <div className="p-6 rounded-3xl fintech-card space-y-2">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
              Cumulative Outflow
            </span>
            <TrendingDown className="w-4 h-4 text-rose-500" />
          </div>
          <p className="text-2xl font-bold text-rose-500 tabular-nums">
            {totalAllExpense > 0 ? '-' : ''}
            {formatCurrency(totalAllExpense, currency.symbol, false)}
          </p>
        </div>

        <div className="p-6 rounded-3xl fintech-card space-y-2">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
              Overall Savings Rate
            </span>
            <Percent className="w-4 h-4 text-primary" />
          </div>
          <p className="text-2xl font-bold text-primary tabular-nums">
            {savingsRate.toFixed(1)}%
          </p>
        </div>
      </div>

      {/* 6-Month Cash Flow Bar Chart */}
      <div className="p-7 rounded-3xl fintech-card space-y-6">
        <h2 className="text-base font-bold text-foreground">
          6-Month Cash Flow History
        </h2>

        <div className="grid grid-cols-6 gap-3 sm:gap-6 items-end h-60 pt-6 px-2">
          {monthlyFlow.map((flow) => {
            const incPct = maxFlowValue > 0 ? (flow.income / maxFlowValue) * 100 : 0;
            const expPct = maxFlowValue > 0 ? (flow.expense / maxFlowValue) * 100 : 0;

            return (
              <div key={flow.monthKey} className="flex flex-col items-center gap-2 h-full justify-end">
                <div className="flex items-end gap-1.5 sm:gap-2.5 w-full justify-center h-full">
                  {/* Income Bar */}
                  <div className="w-3.5 sm:w-6 bg-secondary rounded-t-lg relative flex items-end h-full">
                    <div
                      className="w-full bg-emerald-500 rounded-t-lg transition-all duration-500"
                      style={{ height: `${Math.max(incPct, 4)}%` }}
                      title={`Income: ${formatCurrency(flow.income, currency.symbol, false)}`}
                    />
                  </div>

                  {/* Expense Bar */}
                  <div className="w-3.5 sm:w-6 bg-secondary rounded-t-lg relative flex items-end h-full">
                    <div
                      className="w-full bg-rose-500 rounded-t-lg transition-all duration-500"
                      style={{ height: `${Math.max(expPct, 4)}%` }}
                      title={`Expense: ${formatCurrency(flow.expense, currency.symbol, false)}`}
                    />
                  </div>
                </div>

                <span className="text-xs font-semibold text-muted-foreground mt-2">
                  {flow.label}
                </span>
              </div>
            );
          })}
        </div>

        <div className="flex items-center justify-center gap-6 text-xs font-semibold pt-4 border-t border-border">
          <div className="flex items-center gap-2">
            <div className="w-3 h-3 rounded-full bg-emerald-500" />
            <span className="text-muted-foreground">Inflow</span>
          </div>
          <div className="flex items-center gap-2">
            <div className="w-3 h-3 rounded-full bg-rose-500" />
            <span className="text-muted-foreground">Outflow</span>
          </div>
        </div>
      </div>

      {/* Lifetime Category Distribution */}
      <div className="p-7 rounded-3xl fintech-card space-y-5">
        <h2 className="text-base font-bold text-foreground">
          All-Time Category Distribution
        </h2>

        {categorySummary.length === 0 ? (
          <p className="text-xs text-muted-foreground py-8 text-center">No expenses recorded yet.</p>
        ) : (
          <div className="space-y-3.5">
            {categorySummary.map((cat) => (
              <div key={cat.id} className="space-y-1.5">
                <div className="flex items-center justify-between text-xs">
                  <div className="flex items-center gap-2.5 font-medium text-foreground">
                    <span
                      className="w-2.5 h-2.5 rounded-full"
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
        )}
      </div>
    </div>
  );
}
