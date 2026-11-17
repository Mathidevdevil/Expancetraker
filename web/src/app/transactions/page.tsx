'use client';

import React, { useState, useMemo } from 'react';
import { useFinance } from '@/context/finance-context';
import { formatCurrency, formatFriendlyDate, formatDate } from '@/lib/utils';
import { CategoryIcon } from '@/components/category-icon';
import { QuickAddModal } from '@/components/quick-add-modal';
import { Transaction } from '@/types/database';
import {
  Search,
  Plus,
  Trash2,
  Edit2,
  Download,
  Plane,
  X,
  Receipt,
} from 'lucide-react';

export default function TransactionsPage() {
  const { transactions, categories, trips, currency, deleteTransaction, exportToCSV } = useFinance();

  const [searchQuery, setSearchQuery] = useState('');
  const [selectedType, setSelectedType] = useState<'all' | 'expense' | 'income'>('all');
  const [selectedCategoryId, setSelectedCategoryId] = useState<string>('all');
  const [selectedTripId, setSelectedTripId] = useState<string>('all');
  const [dateFilter, setDateFilter] = useState<'all' | 'this_month' | 'last_month'>('all');

  const [editingTransaction, setEditingTransaction] = useState<Transaction | null>(null);
  const [isAddOpen, setIsAddOpen] = useState(false);

  // Filter transactions
  const filteredTransactions = useMemo(() => {
    const now = new Date();
    const currentMonth = now.getMonth();
    const currentYear = now.getFullYear();

    return transactions.filter((tx) => {
      if (selectedType !== 'all' && tx.type !== selectedType) return false;
      if (selectedCategoryId !== 'all' && tx.category_id !== selectedCategoryId) return false;
      if (selectedTripId !== 'all' && tx.trip_id !== selectedTripId) return false;

      if (dateFilter !== 'all') {
        const txDate = new Date(tx.date);
        if (dateFilter === 'this_month') {
          if (txDate.getMonth() !== currentMonth || txDate.getFullYear() !== currentYear) return false;
        } else if (dateFilter === 'last_month') {
          const lastMonth = currentMonth === 0 ? 11 : currentMonth - 1;
          const lastMonthYear = currentMonth === 0 ? currentYear - 1 : currentYear;
          if (txDate.getMonth() !== lastMonth || txDate.getFullYear() !== lastMonthYear) return false;
        }
      }

      if (searchQuery.trim()) {
        const query = searchQuery.toLowerCase();
        return (
          tx.note?.toLowerCase().includes(query) ||
          tx.category?.name?.toLowerCase().includes(query) ||
          String(tx.amount).includes(query) ||
          tx.payment_method.toLowerCase().includes(query) ||
          tx.trip?.destination.toLowerCase().includes(query)
        );
      }

      return true;
    });
  }, [transactions, selectedType, selectedCategoryId, selectedTripId, dateFilter, searchQuery]);

  // Group filtered transactions by friendly date
  const groupedTransactions = useMemo(() => {
    const groups: { [date: string]: typeof filteredTransactions } = {};
    filteredTransactions.forEach((tx) => {
      const header = formatFriendlyDate(tx.date);
      if (!groups[header]) groups[header] = [];
      groups[header].push(tx);
    });
    return groups;
  }, [filteredTransactions]);

  const totalFilteredExpense = useMemo(() => {
    return filteredTransactions.filter((t) => t.type === 'expense').reduce((sum, t) => sum + t.amount, 0);
  }, [filteredTransactions]);

  const totalFilteredIncome = useMemo(() => {
    return filteredTransactions.filter((t) => t.type === 'income').reduce((sum, t) => sum + t.amount, 0);
  }, [filteredTransactions]);

  return (
    <div className="space-y-6 animate-in fade-in duration-300 max-w-6xl mx-auto pb-16">
      {/* Header & Actions */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-foreground">
            Activity
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Search, filter, and audit all expenditures and inflows
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={() => exportToCSV(filteredTransactions)}
            className="px-4 py-2.5 rounded-2xl bg-secondary hover:bg-secondary/80 text-foreground text-xs font-semibold flex items-center gap-2 border border-border transition-colors"
          >
            <Download className="w-3.5 h-3.5" />
            <span>Export CSV</span>
          </button>
          <button
            onClick={() => setIsAddOpen(true)}
            className="px-4 py-2.5 rounded-2xl bg-primary hover:bg-primary/90 text-primary-foreground text-xs font-semibold flex items-center gap-2 shadow-sm transition-opacity"
          >
            <Plus className="w-4 h-4 stroke-[2.5]" />
            <span>Add Transaction</span>
          </button>
        </div>
      </div>

      {/* Summary Bar */}
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
        <div className="p-4 rounded-2xl fintech-card space-y-1">
          <span className="text-xs font-medium text-muted-foreground">Showing</span>
          <p className="text-xl font-bold text-foreground">
            {filteredTransactions.length} <span className="text-xs font-normal text-muted-foreground">records</span>
          </p>
        </div>
        <div className="p-4 rounded-2xl fintech-card space-y-1">
          <span className="text-xs font-medium text-muted-foreground">Filtered Inflow</span>
          <p className="text-xl font-bold text-emerald-500 tabular-nums">
            +{formatCurrency(totalFilteredIncome, currency.symbol, false)}
          </p>
        </div>
        <div className="p-4 rounded-2xl fintech-card space-y-1 col-span-2 sm:col-span-1">
          <span className="text-xs font-medium text-muted-foreground">Filtered Outflow</span>
          <p className="text-xl font-bold text-rose-500 tabular-nums">
            {totalFilteredExpense > 0 ? '-' : ''}
            {formatCurrency(totalFilteredExpense, currency.symbol, false)}
          </p>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="p-5 rounded-3xl fintech-card space-y-4">
        <div className="relative">
          <Search className="w-4 h-4 text-muted-foreground absolute left-4 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            placeholder="Search by note, category, trip..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-11 pr-10 py-2.5 text-sm bg-secondary/50 border border-border rounded-2xl focus:ring-2 focus:ring-primary focus:outline-none text-foreground placeholder:text-muted-foreground"
          />
          {searchQuery && (
            <button
              onClick={() => setSearchQuery('')}
              className="absolute right-3.5 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
            >
              <X className="w-4 h-4" />
            </button>
          )}
        </div>

        {/* Filter Chips */}
        <div className="flex flex-wrap items-center gap-2 pt-1">
          {/* Type filters */}
          {(['all', 'expense', 'income'] as const).map((type) => (
            <button
              key={type}
              onClick={() => setSelectedType(type)}
              className={`px-3.5 py-1.5 rounded-xl text-xs font-semibold capitalize transition-colors ${
                selectedType === type
                  ? type === 'expense'
                    ? 'bg-rose-500 text-white'
                    : type === 'income'
                    ? 'bg-emerald-500 text-white'
                    : 'bg-primary text-primary-foreground'
                  : 'bg-secondary text-muted-foreground hover:text-foreground'
              }`}
            >
              {type === 'all' ? 'All Types' : `${type}s`}
            </button>
          ))}

          {/* Category dropdown */}
          <select
            value={selectedCategoryId}
            onChange={(e) => setSelectedCategoryId(e.target.value)}
            className="px-3 py-1.5 text-xs bg-secondary border border-border rounded-xl text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
          >
            <option value="all">All Categories</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id} className="bg-card text-foreground">
                {c.name}
              </option>
            ))}
          </select>

          {/* Trip dropdown */}
          {trips.length > 0 && (
            <select
              value={selectedTripId}
              onChange={(e) => setSelectedTripId(e.target.value)}
              className="px-3 py-1.5 text-xs bg-secondary border border-border rounded-xl text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
            >
              <option value="all">All Trips</option>
              {trips.map((t) => (
                <option key={t.id} value={t.id} className="bg-card text-foreground">
                  {t.destination}
                </option>
              ))}
            </select>
          )}
        </div>
      </div>

      {/* Grouped Transactions List */}
      <div className="p-6 rounded-3xl fintech-card space-y-6">
        {Object.keys(groupedTransactions).length === 0 ? (
          <div className="py-16 text-center space-y-3">
            <div className="w-12 h-12 rounded-2xl bg-secondary flex items-center justify-center text-muted-foreground mx-auto">
              <Receipt className="w-6 h-6" />
            </div>
            <p className="text-sm font-semibold text-foreground">
              {transactions.length === 0 ? 'No transactions yet' : 'No transactions match your search'}
            </p>
            <p className="text-xs text-muted-foreground">
              {transactions.length === 0 ? 'Click + Add or Quick Add to record your first income or expense' : 'Try clearing filters or search keywords'}
            </p>
          </div>
        ) : (
          Object.entries(groupedTransactions).map(([dateGroup, txList]) => (
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
                      className="p-3.5 rounded-2xl bg-secondary/30 hover:bg-secondary/60 transition-colors flex items-center justify-between border border-border/40 group"
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
                            {tx.trip && ` • Trip: ${tx.trip.destination}`}
                          </p>
                        </div>
                      </div>

                      <div className="flex items-center gap-4">
                        <div
                          className={`text-sm font-bold tabular-nums ${
                            isIncome ? 'text-emerald-500' : 'text-foreground'
                          }`}
                        >
                          {isIncome ? '+' : '-'}
                          {formatCurrency(tx.amount, currency.symbol)}
                        </div>

                        <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                          <button
                            onClick={() => setEditingTransaction(tx)}
                            className="p-1.5 rounded-lg text-muted-foreground hover:bg-secondary hover:text-foreground transition-colors"
                            title="Edit"
                          >
                            <Edit2 className="w-3.5 h-3.5" />
                          </button>
                          <button
                            onClick={() => deleteTransaction(tx.id)}
                            className="p-1.5 rounded-lg text-muted-foreground hover:bg-rose-500/10 hover:text-rose-500 transition-colors"
                            title="Delete"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          ))
        )}
      </div>

      {/* Quick Add Modal */}
      <QuickAddModal
        isOpen={isAddOpen || Boolean(editingTransaction)}
        onClose={() => {
          setIsAddOpen(false);
          setEditingTransaction(null);
        }}
        editingTransaction={editingTransaction}
      />
    </div>
  );
}
