'use client';

import React, { useState, useEffect } from 'react';
import { useFinance } from '@/context/finance-context';
import { Modal } from './ui/modal';
import { CategoryIcon } from './category-icon';
import { TransactionType, PaymentMethod, Transaction } from '@/types/database';
import { Calendar, CreditCard, Tag, Plane, Plus } from 'lucide-react';

interface QuickAddModalProps {
  isOpen: boolean;
  onClose: () => void;
  initialType?: TransactionType;
  editingTransaction?: Transaction | null;
}

const PAYMENT_METHODS: { id: PaymentMethod; label: string }[] = [
  { id: 'upi', label: 'UPI' },
  { id: 'card', label: 'Card' },
  { id: 'cash', label: 'Cash' },
  { id: 'bank_transfer', label: 'Bank' },
  { id: 'net_banking', label: 'NetBanking' },
];

export function QuickAddModal({
  isOpen,
  onClose,
  initialType = 'expense',
  editingTransaction = null,
}: QuickAddModalProps) {
  const { categories, trips, currency, addTransaction, updateTransaction } = useFinance();

  const [type, setType] = useState<TransactionType>(initialType);
  const [amount, setAmount] = useState<string>('');
  const [categoryId, setCategoryId] = useState<string>('');
  const [tripId, setTripId] = useState<string>('');
  const [date, setDate] = useState<string>(new Date().toISOString().split('T')[0]);
  const [note, setNote] = useState<string>('');
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('upi');
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);

  useEffect(() => {
    if (editingTransaction) {
      setType(editingTransaction.type);
      setAmount(String(editingTransaction.amount));
      setCategoryId(editingTransaction.category_id || '');
      setTripId(editingTransaction.trip_id || '');
      setDate(editingTransaction.date);
      setNote(editingTransaction.note || '');
      setPaymentMethod(editingTransaction.payment_method);
    } else {
      setType(initialType);
      setAmount('');
      const defaultCat = categories.find((c) => c.type === initialType);
      setCategoryId(defaultCat?.id || '');
      setTripId('');
      setDate(new Date().toISOString().split('T')[0]);
      setNote('');
      setPaymentMethod('upi');
    }
  }, [editingTransaction, initialType, categories, isOpen]);

  const filteredCategories = categories.filter((c) => c.type === type);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const numAmount = parseFloat(amount);
    if (!numAmount || numAmount <= 0) return;

    setIsSubmitting(true);
    try {
      if (editingTransaction) {
        await updateTransaction(editingTransaction.id, {
          type,
          amount: numAmount,
          category_id: categoryId || null,
          trip_id: tripId || null,
          date,
          note: note.trim() || null,
          payment_method: paymentMethod,
        });
      } else {
        await addTransaction({
          type,
          amount: numAmount,
          category_id: categoryId || null,
          trip_id: tripId || null,
          date,
          note: note.trim() || null,
          payment_method: paymentMethod,
        });
      }
      onClose();
    } catch (err) {
      console.error(err);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleQuickAddAmount = (add: number) => {
    const current = parseFloat(amount) || 0;
    setAmount(String(current + add));
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={editingTransaction ? 'Edit Transaction' : 'Add Transaction'}
      maxWidth="max-w-lg"
    >
      <form onSubmit={handleSubmit} className="space-y-6">
        {/* Type Toggle: Expense / Income */}
        <div className="grid grid-cols-2 gap-2 p-1 bg-secondary rounded-2xl">
          <button
            type="button"
            onClick={() => {
              setType('expense');
              const firstExp = categories.find((c) => c.type === 'expense');
              if (firstExp) setCategoryId(firstExp.id);
            }}
            className={`py-2 text-xs font-bold rounded-xl transition-all ${
              type === 'expense'
                ? 'bg-rose-500 text-white shadow-sm'
                : 'text-muted-foreground hover:text-foreground'
            }`}
          >
            Expense
          </button>
          <button
            type="button"
            onClick={() => {
              setType('income');
              const firstInc = categories.find((c) => c.type === 'income');
              if (firstInc) setCategoryId(firstInc.id);
            }}
            className={`py-2 text-xs font-bold rounded-xl transition-all ${
              type === 'income'
                ? 'bg-emerald-500 text-white shadow-sm'
                : 'text-muted-foreground hover:text-foreground'
            }`}
          >
            Income
          </button>
        </div>

        {/* Amount Input */}
        <div className="space-y-2">
          <label className="block text-xs font-bold text-muted-foreground uppercase tracking-wider">
            Amount ({currency.symbol})
          </label>
          <div className="relative">
            <span className="absolute left-4 top-1/2 -translate-y-1/2 text-2xl font-black text-primary">
              {currency.symbol}
            </span>
            <input
              type="number"
              step="any"
              required
              autoFocus
              placeholder="0"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              className="w-full pl-12 pr-4 py-3 text-3xl font-black bg-secondary/60 border border-border rounded-2xl focus:ring-2 focus:ring-primary focus:border-primary focus:outline-none transition-all text-foreground tabular-nums"
            />
          </div>

          {/* Quick Increment Chips */}
          <div className="flex gap-2 pt-1 overflow-x-auto pb-1">
            {[100, 200, 500, 1000].map((val) => (
              <button
                key={val}
                type="button"
                onClick={() => handleQuickAddAmount(val)}
                className="px-3 py-1.5 text-xs font-bold rounded-xl bg-secondary hover:bg-secondary/80 text-foreground transition-all border border-border/60 active:scale-95"
              >
                +{currency.symbol}{val}
              </button>
            ))}
          </div>
        </div>

        {/* Category Picker */}
        <div className="space-y-2">
          <label className="block text-xs font-bold text-muted-foreground uppercase tracking-wider">
            Select Category
          </label>
          <div className="grid grid-cols-4 sm:grid-cols-5 gap-2 max-h-40 overflow-y-auto p-1.5 border border-border rounded-2xl bg-secondary/30">
            <button
              type="button"
              onClick={async () => {
                const name = window.prompt(`New ${type} category name:`);
                if (name && name.trim()) {
                  try {
                    const newCat = await addCategory({
                      name: name.trim(),
                      type,
                      icon: type === 'income' ? 'DollarSign' : 'Tag',
                      color: type === 'income' ? '#10B981' : '#5B4BE8'
                    });
                    if (newCat?.id) setCategoryId(newCat.id);
                  } catch (e) {
                    console.error('Failed to create category:', e);
                  }
                }
              }}
              className="flex flex-col items-center justify-center p-2 rounded-xl border border-dashed border-primary/50 text-center transition-all hover:bg-primary/10 text-primary cursor-pointer"
            >
              <div className="w-[22px] h-[22px] flex items-center justify-center rounded-full bg-primary/15">
                <Plus size={14} className="text-primary" />
              </div>
              <span className="text-[11px] font-bold mt-1 text-primary">
                + New
              </span>
            </button>
            {filteredCategories.map((cat) => {
              const isSelected = categoryId === cat.id;
              return (
                <button
                  key={cat.id}
                  type="button"
                  onClick={() => setCategoryId(cat.id)}
                  className={`flex flex-col items-center p-2 rounded-xl border text-center transition-all ${
                    isSelected
                      ? 'border-primary bg-primary/10 text-primary font-bold shadow-sm'
                      : 'border-transparent hover:bg-secondary/60 text-muted-foreground'
                  }`}
                >
                  <CategoryIcon icon={cat.icon} color={cat.color} size={22} />
                  <span className="text-[11px] font-medium mt-1 truncate max-w-full">
                    {cat.name}
                  </span>
                </button>
              );
            })}
          </div>
        </div>

        {/* Date & Payment Method */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <label className="block text-xs font-bold text-muted-foreground uppercase tracking-wider">
              Date
            </label>
            <input
              type="date"
              required
              value={date}
              onChange={(e) => setDate(e.target.value)}
              className="w-full px-3.5 py-2.5 text-sm bg-secondary/60 border border-border rounded-xl focus:ring-1 focus:ring-primary focus:outline-none text-foreground"
            />
          </div>

          <div className="space-y-1.5">
            <label className="block text-xs font-bold text-muted-foreground uppercase tracking-wider">
              Payment Method
            </label>
            <select
              value={paymentMethod}
              onChange={(e) => setPaymentMethod(e.target.value as PaymentMethod)}
              className="w-full px-3.5 py-2.5 text-sm bg-secondary/60 border border-border rounded-xl focus:ring-1 focus:ring-primary focus:outline-none text-foreground"
            >
              {PAYMENT_METHODS.map((m) => (
                <option key={m.id} value={m.id} className="bg-card text-foreground">
                  {m.label}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* Trip Tagging (Optional) */}
        {trips.length > 0 && (
          <div className="space-y-1.5">
            <label className="block text-xs font-bold text-muted-foreground uppercase tracking-wider flex items-center gap-1.5">
              <Plane className="w-3.5 h-3.5 text-cyan-500" />
              Tag to Travel Plan (Optional)
            </label>
            <select
              value={tripId}
              onChange={(e) => setTripId(e.target.value)}
              className="w-full px-3.5 py-2.5 text-sm bg-secondary/60 border border-border rounded-xl focus:ring-1 focus:ring-primary focus:outline-none text-foreground"
            >
              <option value="" className="bg-card text-foreground">No Travel Plan</option>
              {trips.map((t) => (
                <option key={t.id} value={t.id} className="bg-card text-foreground">
                  {t.destination} ({t.start_date})
                </option>
              ))}
            </select>
          </div>
        )}

        {/* Note */}
        <div className="space-y-1.5">
          <label className="block text-xs font-bold text-muted-foreground uppercase tracking-wider">
            Note (Optional)
          </label>
          <input
            type="text"
            placeholder="e.g. Starbucks, Groceries, Flight Ticket"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            className="w-full px-3.5 py-2.5 text-sm bg-secondary/60 border border-border rounded-xl focus:ring-1 focus:ring-primary focus:outline-none text-foreground placeholder:text-muted-foreground/60"
          />
        </div>

        {/* Action Buttons */}
        <div className="flex items-center gap-3 pt-2">
          <button
            type="button"
            onClick={onClose}
            className="flex-1 py-3 px-4 rounded-2xl bg-secondary hover:bg-secondary/80 text-foreground font-semibold text-sm transition-all"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={isSubmitting || !amount || parseFloat(amount) <= 0}
            className="flex-1 py-3 px-4 rounded-2xl bg-primary hover:bg-primary/90 text-primary-foreground font-semibold text-sm transition-all disabled:opacity-50 shadow-sm"
          >
            {isSubmitting ? 'Saving...' : editingTransaction ? 'Update' : 'Save'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
