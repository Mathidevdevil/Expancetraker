'use client';

import React, { useState, useMemo } from 'react';
import { useFinance } from '@/context/finance-context';
import { formatCurrency, formatFriendlyDate, formatDate } from '@/lib/utils';
import { Modal } from '@/components/ui/modal';
import { Trip } from '@/types/database';
import {
  Plane,
  Plus,
  Trash2,
  Edit2,
  MapPin,
  Calendar,
  Receipt,
} from 'lucide-react';

export default function TripsPage() {
  const { trips, transactions, currency, addTrip, updateTrip, deleteTrip } = useFinance();

  const [selectedTripId, setSelectedTripId] = useState<string | null>(trips[0]?.id || null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingTrip, setEditingTrip] = useState<Trip | null>(null);

  const [destination, setDestination] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [budget, setBudget] = useState('');
  const [notes, setNotes] = useState('');

  const selectedTrip = trips.find((t) => t.id === selectedTripId) || trips[0];

  const tripStats = useMemo(() => {
    if (!selectedTrip) return null;

    const tripTxs = transactions.filter((t) => t.trip_id === selectedTrip.id);
    const totalSpent = tripTxs
      .filter((t) => t.type === 'expense')
      .reduce((sum, t) => sum + t.amount, 0);

    const start = new Date(selectedTrip.start_date);
    const end = new Date(selectedTrip.end_date);
    const diffTime = Math.abs(end.getTime() - start.getTime());
    const totalDays = Math.max(1, Math.ceil(diffTime / (1000 * 60 * 60 * 24)) + 1);

    const spendPerDay = totalSpent / totalDays;
    const remainingBudget = selectedTrip.budget - totalSpent;
    const budgetUsagePercent = selectedTrip.budget > 0 ? (totalSpent / selectedTrip.budget) * 100 : 0;

    return {
      totalSpent,
      totalDays,
      spendPerDay,
      remainingBudget,
      budgetUsagePercent,
      transactions: tripTxs,
    };
  }, [selectedTrip, transactions]);

  const openCreateModal = () => {
    setEditingTrip(null);
    setDestination('');
    setStartDate(new Date().toISOString().split('T')[0]);
    setEndDate(new Date(Date.now() + 5 * 86400000).toISOString().split('T')[0]);
    setBudget('');
    setNotes('');
    setIsModalOpen(true);
  };

  const openEditModal = (t: Trip) => {
    setEditingTrip(t);
    setDestination(t.destination);
    setStartDate(t.start_date);
    setEndDate(t.end_date);
    setBudget(String(t.budget));
    setNotes(t.notes || '');
    setIsModalOpen(true);
  };

  const handleSaveTrip = async (e: React.FormEvent) => {
    e.preventDefault();
    const numBudget = parseFloat(budget) || 0;
    if (!destination.trim() || !startDate || !endDate) return;

    if (editingTrip) {
      await updateTrip(editingTrip.id, {
        destination: destination.trim(),
        start_date: startDate,
        end_date: endDate,
        budget: numBudget,
        notes: notes.trim() || null,
      });
    } else {
      await addTrip({
        destination: destination.trim(),
        start_date: startDate,
        end_date: endDate,
        budget: numBudget,
        notes: notes.trim() || null,
      });
    }

    setIsModalOpen(false);
  };

  return (
    <div className="space-y-6 animate-in fade-in duration-300 max-w-6xl mx-auto pb-16">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-foreground">
            Travel Plans
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Trip budgets, itinerary notes, and daily burn tracking
          </p>
        </div>

        <button
          onClick={openCreateModal}
          className="px-4 py-2.5 rounded-2xl bg-primary hover:bg-primary/90 text-primary-foreground text-xs font-semibold flex items-center gap-2 shadow-sm transition-opacity"
        >
          <Plus className="w-4 h-4 stroke-[2.5]" />
          <span>New Travel Plan</span>
        </button>
      </div>

      {trips.length === 0 ? (
        <div className="p-16 rounded-3xl fintech-card text-center space-y-4">
          <div className="w-12 h-12 rounded-2xl bg-cyan-500/10 text-cyan-500 flex items-center justify-center mx-auto">
            <Plane className="w-6 h-6" />
          </div>
          <h2 className="text-base font-bold text-foreground">No upcoming travel plans</h2>
          <p className="text-xs text-muted-foreground max-w-md mx-auto">
            Tag expenses to dedicated travel plans to calculate daily spend averages and stay within your vacation budgets.
          </p>
          <button
            onClick={openCreateModal}
            className="px-4 py-2 rounded-xl bg-primary text-primary-foreground text-xs font-semibold"
          >
            Create Your First Trip
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* Trips Carousel / Sidebar (4 cols) */}
          <div className="lg:col-span-4 space-y-3">
            <span className="text-xs font-bold uppercase tracking-wider text-muted-foreground px-1">
              Your Itineraries ({trips.length})
            </span>
            <div className="space-y-2.5">
              {trips.map((t) => {
                const isSelected = selectedTrip?.id === t.id;
                return (
                  <div
                    key={t.id}
                    onClick={() => setSelectedTripId(t.id)}
                    className={`p-4 rounded-2xl cursor-pointer border transition-all ${
                      isSelected
                        ? 'bg-secondary border-cyan-500/40 shadow-sm'
                        : 'bg-card border-border hover:bg-secondary/40'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2.5 font-bold text-foreground">
                        <MapPin className="w-4 h-4 text-cyan-500" />
                        <span>{t.destination}</span>
                      </div>
                      <span className="text-xs font-semibold text-muted-foreground">
                        {formatCurrency(t.budget, currency.symbol, false)}
                      </span>
                    </div>
                    <p className="text-xs text-muted-foreground mt-1.5 pl-6">
                      {t.start_date} → {t.end_date}
                    </p>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Selected Trip Details (8 cols) */}
          {selectedTrip && tripStats && (
            <div className="lg:col-span-8 space-y-5">
              {/* Trip Hero Card */}
              <div className="p-7 rounded-3xl fintech-card space-y-5">
                <div className="flex items-center justify-between">
                  <div>
                    <span className="px-2.5 py-1 rounded-xl text-xs font-bold bg-cyan-500/10 text-cyan-500 border border-cyan-500/20">
                      {selectedTrip.start_date} → {selectedTrip.end_date} ({tripStats.totalDays} Days)
                    </span>
                    <h2 className="text-2xl font-extrabold text-foreground mt-2">
                      {selectedTrip.destination}
                    </h2>
                  </div>

                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => openEditModal(selectedTrip)}
                      className="p-2 rounded-xl text-muted-foreground hover:bg-secondary transition-colors"
                      title="Edit Trip"
                    >
                      <Edit2 className="w-4 h-4" />
                    </button>
                    <button
                      onClick={() => deleteTrip(selectedTrip.id)}
                      className="p-2 rounded-xl text-muted-foreground hover:bg-rose-500/10 hover:text-rose-500 transition-colors"
                      title="Delete Trip"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>

                {selectedTrip.notes && (
                  <p className="text-xs text-muted-foreground bg-secondary/50 p-3.5 rounded-2xl border border-border/50">
                    {selectedTrip.notes}
                  </p>
                )}

                {/* Metrics Grid */}
                <div className="grid grid-cols-3 gap-3 pt-2">
                  <div className="p-3.5 rounded-2xl bg-secondary/40 space-y-1">
                    <span className="text-[11px] font-semibold text-muted-foreground">Total Spent</span>
                    <p className="text-lg font-bold text-foreground tabular-nums">
                      {formatCurrency(tripStats.totalSpent, currency.symbol, false)}
                    </p>
                  </div>
                  <div className="p-3.5 rounded-2xl bg-secondary/40 space-y-1">
                    <span className="text-[11px] font-semibold text-muted-foreground">Trip Budget</span>
                    <p className="text-lg font-bold text-foreground tabular-nums">
                      {formatCurrency(selectedTrip.budget, currency.symbol, false)}
                    </p>
                  </div>
                  <div className="p-3.5 rounded-2xl bg-cyan-500/10 border border-cyan-500/20 space-y-1">
                    <span className="text-[11px] font-semibold text-cyan-500">Daily Burn</span>
                    <p className="text-lg font-bold text-cyan-500 tabular-nums">
                      {formatCurrency(tripStats.spendPerDay, currency.symbol, false)}/day
                    </p>
                  </div>
                </div>

                {/* Progress Bar */}
                <div className="space-y-1.5 pt-1">
                  <div className="flex items-center justify-between text-xs text-muted-foreground">
                    <span>Budget Consumed</span>
                    <span className="font-bold text-foreground">
                      {tripStats.budgetUsagePercent.toFixed(0)}%
                    </span>
                  </div>
                  <div className="h-2 w-full rounded-full bg-secondary overflow-hidden">
                    <div
                      className="h-full rounded-full bg-cyan-500 transition-all duration-500"
                      style={{ width: `${Math.min(tripStats.budgetUsagePercent, 100)}%` }}
                    />
                  </div>
                </div>
              </div>

              {/* Tagged Trip Transactions */}
              <div className="p-6 rounded-3xl fintech-card space-y-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Receipt className="w-4 h-4 text-cyan-500" />
                    <h3 className="text-sm font-bold text-foreground">
                      Expenses Tagged to this Trip ({tripStats.transactions.length})
                    </h3>
                  </div>
                </div>

                {tripStats.transactions.length === 0 ? (
                  <p className="text-xs text-muted-foreground py-6 text-center">
                    No transactions tagged to this trip yet. When adding an expense, select &ldquo;{selectedTrip.destination}&rdquo;.
                  </p>
                ) : (
                  <div className="space-y-2">
                    {tripStats.transactions.map((tx) => (
                      <div
                        key={tx.id}
                        className="p-3 rounded-2xl bg-secondary/30 flex items-center justify-between border border-border/40 text-xs"
                      >
                        <div>
                          <p className="font-semibold text-foreground">{tx.note || 'Expense'}</p>
                          <p className="text-muted-foreground text-[11px]">{tx.date} • {tx.payment_method}</p>
                        </div>
                        <span className="font-bold text-foreground tabular-nums">
                          -{formatCurrency(tx.amount, currency.symbol)}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      )}

      {/* Add / Edit Trip Modal */}
      <Modal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        title={editingTrip ? 'Edit Travel Plan' : 'New Travel Plan'}
      >
        <form onSubmit={handleSaveTrip} className="space-y-4">
          <div className="space-y-1.5">
            <label className="block text-xs font-bold text-muted-foreground uppercase tracking-wider">
              Destination
            </label>
            <input
              type="text"
              placeholder="e.g. Goa, Tokyo, Paris"
              required
              value={destination}
              onChange={(e) => setDestination(e.target.value)}
              className="w-full px-3.5 py-2.5 text-sm bg-secondary/60 border border-border rounded-xl focus:ring-1 focus:ring-primary focus:outline-none text-foreground"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <label className="block text-xs font-bold text-muted-foreground uppercase tracking-wider">
                Start Date
              </label>
              <input
                type="date"
                required
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="w-full px-3.5 py-2.5 text-sm bg-secondary/60 border border-border rounded-xl focus:ring-1 focus:ring-primary focus:outline-none text-foreground"
              />
            </div>
            <div className="space-y-1.5">
              <label className="block text-xs font-bold text-muted-foreground uppercase tracking-wider">
                End Date
              </label>
              <input
                type="date"
                required
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="w-full px-3.5 py-2.5 text-sm bg-secondary/60 border border-border rounded-xl focus:ring-1 focus:ring-primary focus:outline-none text-foreground"
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <label className="block text-xs font-bold text-muted-foreground uppercase tracking-wider">
              Total Budget ({currency.symbol})
            </label>
            <input
              type="number"
              step="any"
              placeholder="e.g. 35000"
              required
              value={budget}
              onChange={(e) => setBudget(e.target.value)}
              className="w-full px-3.5 py-2.5 text-sm bg-secondary/60 border border-border rounded-xl focus:ring-1 focus:ring-primary focus:outline-none text-foreground"
            />
          </div>

          <div className="space-y-1.5">
            <label className="block text-xs font-bold text-muted-foreground uppercase tracking-wider">
              Itinerary / Notes (Optional)
            </label>
            <textarea
              rows={2}
              placeholder="e.g. Beach resort, scuba diving, food tour"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
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
              className="flex-1 py-2.5 px-4 rounded-xl bg-primary text-primary-foreground text-xs font-semibold shadow-sm"
            >
              {editingTrip ? 'Save Changes' : 'Create Plan'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
