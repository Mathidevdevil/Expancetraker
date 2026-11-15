'use client';

import React, { useState, useMemo } from 'react';
import { useFinance } from '@/context/finance-context';
import { formatCurrency } from '@/lib/utils';
import { CategoryIcon } from '@/components/category-icon';
import { Modal } from '@/components/ui/modal';
import {
  AlertTriangle,
  AlertCircle,
  CheckCircle2,
  PieChart,
  Plus,
  ChevronLeft,
  ChevronRight,
  Trash2,
} from 'lucide-react';

export default function BudgetsPage() {
  const { budgets, categories, transactions, currency, setCategoryBudget, deleteBudget } = useFinance();

  const [currentDate, setCurrentDate] = useState(new Date());
  const selectedMonth = currentDate.getMonth() + 1;
  const selectedYear = currentDate.getFullYear();

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedCatId, setSelectedCatId] = useState('');
  const [budgetAmount, setBudgetAmount] = useState('');

  // Calculate spending per category for selected month/year
  const budgetStats = useMemo(() => {
    const monthBudgets = budgets.filter((b) => b.month === selectedMonth && b.year === selectedYear);

    const expenseMap = new Map<string, number>();
    transactions.forEach((tx) => {
      if (tx.type === 'expense' && tx.category_id) {
        const txDate = new Date(tx.date);
        if (txDate.getMonth() + 1 === selectedMonth && txDate.getFullYear() === selectedYear) {
          expenseMap.set(tx.category_id, (expenseMap.get(tx.category_id) || 0) + tx.amount);
        }
      }
    });

    const items = monthBudgets.map((b) => {
      const cat = categories.find((c) => c.id === b.category_id);
      const spent = expenseMap.get(b.category_id) || 0;
      const percent = b.amount > 0 ? (spent / b.amount) * 100 : 0;
      const remaining = b.amount - spent;

      let status: 'normal' | 'warning' | 'exceeded' = 'normal';
      if (percent >= 100) status = 'exceeded';
      else if (percent >= 80) status = 'warning';

      return {
        id: b.id,
        category: cat,
        budget: b.amount,
        spent,
        remaining,
        percent,
        status,
      };
    });

    const totalBudget = items.reduce((sum, item) => sum + item.budget, 0);
    const totalSpent = items.reduce((sum, item) => sum + item.spent, 0);

    return {
      items,
      totalBudget,
      totalSpent,
      overallPercent: totalBudget > 0 ? (totalSpent / totalBudget) * 100 : 0,
    };
  }, [budgets, categories, transactions, selectedMonth, selectedYear]);

  const availableCategories = useMemo(() => {
    const budgetedCatIds = new Set(
      budgets.filter((b) => b.month === selectedMonth && b.year === selectedYear).map((b) => b.category_id)
    );
    return categories.filter((c) => c.type === 'expense' && !budgetedCatIds.has(c.id));
  }, [categories, budgets, selectedMonth, selectedYear]);

  const handlePrevMonth = () => {
    setCurrentDate(new Date(selectedYear, selectedMonth - 2, 1));
  };

  const handleNextMonth = () => {
    setCurrentDate(new Date(selectedYear, selectedMonth, 1));
  };

  const handleSaveBudget = async (e: React.FormEvent) => {
    e.preventDefault();
    const amount = parseFloat(budgetAmount);
    if (!selectedCatId || !amount || amount <= 0) return;

    await setCategoryBudget(selectedCatId, amount, selectedMonth, selectedYear);
    setIsModalOpen(false);
    setSelectedCatId('');
    setBudgetAmount('');
  };

  const monthName = currentDate.toLocaleString('default', { month: 'long', year: 'numeric' });
  const overallColor =
    budgetStats.overallPercent >= 100
      ? 'text-rose-500'
      : budgetStats.overallPercent >= 80
      ? 'text-amber-500'
      : 'text-emerald-500';

  const overallBg =
    budgetStats.overallPercent >= 100
      ? 'bg-rose-500'
      : budgetStats.overallPercent >= 80
      ? 'bg-amber-500'
      : 'bg-emerald-500';

  return (
    <div className="space-y-6 animate-in fade-in duration-300 max-w-6xl mx-auto pb-16">
      {/* Header Row */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-foreground">
            Budgets
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Category spending limits and threshold alerts
          </p>
        </div>

        {/* Month Selector & Add Target */}
        <div className="flex items-center gap-3">
          <div className="flex items-center bg-secondary/80 border border-border rounded-2xl p-1">
            <button
              onClick={handlePrevMonth}
              className="p-1.5 rounded-xl hover:bg-card text-muted-foreground hover:text-foreground transition-colors"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <span className="px-3 text-xs font-bold text-foreground min-w-[130px] text-center">
              {monthName}
            </span>
            <button
              onClick={handleNextMonth}
              className="p-1.5 rounded-xl hover:bg-card text-muted-foreground hover:text-foreground transition-colors"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>

          <button
            onClick={() => setIsModalOpen(true)}
            className="px-4 py-2.5 rounded-2xl bg-primary hover:bg-primary/90 text-primary-foreground text-xs font-semibold flex items-center gap-2 shadow-sm transition-opacity"
          >
            <Plus className="w-4 h-4 stroke-[2.5]" />
            <span>Set Target</span>
          </button>
        </div>
      </div>

      {/* Overall Health Card */}
      <div className="p-7 rounded-3xl fintech-card space-y-4">
        <div className="flex items-center justify-between">
          <span className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
            Monthly Target Health
          </span>
          <span className={`text-xs font-bold ${overallColor}`}>
            {budgetStats.overallPercent.toFixed(0)}% Used
          </span>
        </div>

        <div className="text-3xl sm:text-4xl font-extrabold text-foreground tabular-nums">
          {formatCurrency(budgetStats.totalSpent, currency.symbol, false)}{' '}
          <span className="text-lg text-muted-foreground font-semibold">
            / {formatCurrency(budgetStats.totalBudget, currency.symbol, false)}
          </span>
        </div>

        {/* Slim Progress Bar */}
        <div className="h-2.5 w-full rounded-full bg-secondary overflow-hidden">
          <div
            className={`h-full rounded-full transition-all duration-500 ${overallBg}`}
            style={{ width: `${Math.min(budgetStats.overallPercent, 100)}%` }}
          />
        </div>
      </div>

      {/* Category Budgets Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {budgetStats.items.length === 0 ? (
          <div className="col-span-2 p-12 text-center rounded-3xl fintech-card text-muted-foreground text-sm">
            No category limits set for {monthName}. Click &ldquo;Set Target&rdquo; to add one.
          </div>
        ) : (
          budgetStats.items.map((item) => {
            const isExceeded = item.status === 'exceeded';
            const isWarning = item.status === 'warning';
            const barBg = isExceeded ? 'bg-rose-500' : isWarning ? 'bg-amber-500' : 'bg-emerald-500';
            const statusTextColor = isExceeded ? 'text-rose-500' : isWarning ? 'text-amber-500' : 'text-emerald-500';

            return (
              <div key={item.id} className="p-6 rounded-3xl fintech-card space-y-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <CategoryIcon
                      icon={item.category?.icon || 'Tag'}
                      color={item.category?.color || '#6366F1'}
                      size={40}
                    />
                    <div>
                      <h3 className="text-base font-bold text-foreground">
                        {item.category?.name}
                      </h3>
                      <p className="text-xs text-muted-foreground">
                        Limit: {formatCurrency(item.budget, currency.symbol, false)}
                      </p>
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    {isExceeded && (
                      <span className="px-2 py-0.5 rounded-lg text-[10px] font-bold bg-rose-500/10 text-rose-500 border border-rose-500/20">
                        Exceeded
                      </span>
                    )}
                    {isWarning && (
                      <span className="px-2 py-0.5 rounded-lg text-[10px] font-bold bg-amber-500/10 text-amber-500 border border-amber-500/20">
                        80%+ Warning
                      </span>
                    )}
                    <button
                      onClick={() => deleteBudget(item.id)}
                      className="p-1.5 rounded-lg text-muted-foreground hover:bg-rose-500/10 hover:text-rose-500 transition-colors"
                      title="Delete Budget"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>

                {/* Progress bar */}
                <div className="h-2 w-full rounded-full bg-secondary overflow-hidden">
                  <div
                    className={`h-full rounded-full transition-all duration-500 ${barBg}`}
                    style={{ width: `${Math.min(item.percent, 100)}%` }}
                  />
                </div>

                <div className="flex items-center justify-between text-xs">
                  <span className="text-muted-foreground">
                    Spent: <span className="font-semibold text-foreground">{formatCurrency(item.spent, currency.symbol, false)}</span>
                  </span>
                  <span className={`font-bold ${statusTextColor}`}>
                    {item.percent.toFixed(0)}%
                  </span>
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* Set Budget Modal */}
      <Modal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        title="Set Category Budget Target"
      >
        <form onSubmit={handleSaveBudget} className="space-y-4">
          <div className="space-y-1.5">
            <label className="block text-xs font-bold text-muted-foreground uppercase tracking-wider">
              Category
            </label>
            <select
              value={selectedCatId}
              onChange={(e) => setSelectedCatId(e.target.value)}
              required
              className="w-full px-3.5 py-2.5 text-sm bg-secondary/60 border border-border rounded-xl focus:ring-1 focus:ring-primary focus:outline-none text-foreground"
            >
              <option value="">Select an expense category...</option>
              {availableCategories.map((c) => (
                <option key={c.id} value={c.id} className="bg-card text-foreground">
                  {c.name}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-1.5">
            <label className="block text-xs font-bold text-muted-foreground uppercase tracking-wider">
              Monthly Limit ({currency.symbol})
            </label>
            <input
              type="number"
              step="any"
              placeholder="e.g. 5000"
              required
              value={budgetAmount}
              onChange={(e) => setBudgetAmount(e.target.value)}
              className="w-full px-3.5 py-2.5 text-sm bg-secondary/60 border border-border rounded-xl focus:ring-1 focus:ring-primary focus:outline-none text-foreground"
            />
          </div>

          <div className="flex items-center gap-3 pt-3">
            <button
              type="button"
              onClick={() => setIsModalOpen(false)}
              className="flex-1 py-2.5 px-4 rounded-xl bg-secondary text-foreground text-xs font-semibold"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={!selectedCatId || !budgetAmount || parseFloat(budgetAmount) <= 0}
              className="flex-1 py-2.5 px-4 rounded-xl bg-primary text-primary-foreground text-xs font-semibold shadow-sm disabled:opacity-50"
            >
              Save Target
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
