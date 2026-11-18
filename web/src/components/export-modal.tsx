'use client';

import React, { useState, useMemo } from 'react';
import { useFinance } from '@/context/finance-context';
import { Modal } from '@/components/ui/modal';
import { formatCurrency } from '@/lib/utils';
import { FileSpreadsheet, FileText, Download, Calendar, Layers } from 'lucide-react';
import { Transaction } from '@/types/database';

interface ExportModalProps {
  isOpen: boolean;
  onClose: () => void;
  transactionsToFilter?: Transaction[];
}

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'
];

export function ExportModal({ isOpen, onClose, transactionsToFilter }: ExportModalProps) {
  const { transactions, currency, exportToCSV, exportToExcel } = useFinance();
  const allTxs = transactionsToFilter || transactions;

  const now = new Date();
  const currentYear = now.getFullYear();
  const currentMonth = now.getMonth() + 1; // 1-12

  const [mode, setMode] = useState<'monthly' | 'yearly' | 'date_range' | 'all'>('monthly');
  const [selectedFormat, setSelectedFormat] = useState<'excel' | 'csv'>('excel');
  const [selectedYear, setSelectedYear] = useState<number>(currentYear);
  const [selectedMonth, setSelectedMonth] = useState<number>(currentMonth);

  const [startDate, setStartDate] = useState<string>(
    `${currentYear}-${String(currentMonth).padStart(2, '0')}-01`
  );
  const [endDate, setEndDate] = useState<string>(
    now.toISOString().split('T')[0]
  );

  const availableYears = useMemo(() => {
    return [currentYear, currentYear - 1, currentYear - 2, currentYear - 3, currentYear - 4];
  }, [currentYear]);

  // Filter transactions according to selected range
  const filtered = useMemo(() => {
    return allTxs.filter((t) => {
      const parts = t.date.split('-');
      const y = parseInt(parts[0], 10);
      const m = parseInt(parts[1], 10);

      if (mode === 'monthly') {
        return y === selectedYear && m === selectedMonth;
      }
      if (mode === 'yearly') {
        return y === selectedYear;
      }
      if (mode === 'date_range') {
        if (startDate && endDate) {
          return t.date >= startDate && t.date <= endDate;
        }
        if (startDate) return t.date >= startDate;
        if (endDate) return t.date <= endDate;
        return true;
      }
      return true;
    }).sort((a, b) => b.date.localeCompare(a.date));
  }, [allTxs, mode, selectedYear, selectedMonth, startDate, endDate]);

  const reportTitle = useMemo(() => {
    if (mode === 'monthly') return `${MONTH_NAMES[selectedMonth - 1]} ${selectedYear}`;
    if (mode === 'yearly') return `Year ${selectedYear}`;
    if (mode === 'date_range') return `${startDate} to ${endDate}`;
    return 'All Time';
  }, [mode, selectedMonth, selectedYear, startDate, endDate]);

  const summary = useMemo(() => {
    let income = 0;
    let expense = 0;
    filtered.forEach((t) => {
      if (t.type === 'income') income += t.amount;
      else expense += t.amount;
    });
    return {
      income,
      expense,
      net: income - expense,
      count: filtered.length
    };
  }, [filtered]);

  const handleDownload = () => {
    if (filtered.length === 0) {
      alert(`No transactions found for ${reportTitle}.`);
      return;
    }

    if (selectedFormat === 'excel') {
      exportToExcel(filtered, reportTitle);
    } else {
      exportToCSV(filtered, reportTitle);
    }
    onClose();
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Export Financial Statement" maxWidth="max-w-lg">
      <div className="space-y-5">
        {/* Subtitle */}
        <p className="text-xs text-muted-foreground -mt-3">
          Download formatted Microsoft Excel (.xls) or universal CSV sheets for tax, auditing, and personal bookkeeping.
        </p>

        {/* 1. Timeframe Mode Selector */}
        <div className="space-y-2">
          <label className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider flex items-center gap-1.5">
            <Calendar className="w-3.5 h-3.5 text-primary" />
            <span>Report Timeframe</span>
          </label>
          <div className="grid grid-cols-4 gap-1.5 p-1 rounded-2xl bg-secondary/60 border border-border">
            {(['monthly', 'yearly', 'date_range', 'all'] as const).map((m) => {
              const labels = {
                monthly: 'Monthly',
                yearly: 'Yearly',
                date_range: 'Date Range',
                all: 'All Time'
              };
              const active = mode === m;
              return (
                <button
                  key={m}
                  type="button"
                  onClick={() => setMode(m)}
                  className={`py-2 text-xs font-semibold rounded-xl transition-all ${
                    active
                      ? 'bg-primary text-primary-foreground shadow-sm'
                      : 'text-muted-foreground hover:text-foreground'
                  }`}
                >
                  {labels[m]}
                </button>
              );
            })}
          </div>
        </div>

        {/* 2. Parameters for timeframe */}
        {mode === 'monthly' && (
          <div className="space-y-3 p-3.5 rounded-2xl bg-secondary/30 border border-border/60">
            <div>
              <span className="text-[11px] font-medium text-muted-foreground mb-1.5 block">Select Month</span>
              <div className="grid grid-cols-4 sm:grid-cols-6 gap-1.5">
                {MONTH_NAMES.map((name, idx) => {
                  const mNum = idx + 1;
                  const isSel = selectedMonth === mNum;
                  return (
                    <button
                      key={name}
                      type="button"
                      onClick={() => setSelectedMonth(mNum)}
                      className={`py-1.5 text-xs font-medium rounded-lg border transition-all ${
                        isSel
                          ? 'bg-primary text-primary-foreground border-primary'
                          : 'bg-background hover:bg-secondary text-foreground border-border'
                      }`}
                    >
                      {name.slice(0, 3)}
                    </button>
                  );
                })}
              </div>
            </div>

            <div>
              <span className="text-[11px] font-medium text-muted-foreground mb-1.5 block">Select Year</span>
              <div className="grid grid-cols-5 gap-1.5">
                {availableYears.map((yr) => (
                  <button
                    key={yr}
                    type="button"
                    onClick={() => setSelectedYear(yr)}
                    className={`py-1.5 text-xs font-medium rounded-lg border transition-all ${
                      selectedYear === yr
                        ? 'bg-primary text-primary-foreground border-primary'
                        : 'bg-background hover:bg-secondary text-foreground border-border'
                    }`}
                  >
                    {yr}
                  </button>
                ))}
              </div>
            </div>
          </div>
        )}

        {mode === 'yearly' && (
          <div className="p-3.5 rounded-2xl bg-secondary/30 border border-border/60 space-y-2">
            <span className="text-[11px] font-medium text-muted-foreground block">Select Financial Year</span>
            <div className="grid grid-cols-5 gap-2">
              {availableYears.map((yr) => (
                <button
                  key={yr}
                  type="button"
                  onClick={() => setSelectedYear(yr)}
                  className={`py-2 text-xs font-semibold rounded-xl border transition-all ${
                    selectedYear === yr
                      ? 'bg-primary text-primary-foreground border-primary shadow-sm'
                      : 'bg-background hover:bg-secondary text-foreground border-border'
                  }`}
                >
                  {yr}
                </button>
              ))}
            </div>
          </div>
        )}

        {mode === 'date_range' && (
          <div className="grid grid-cols-2 gap-3 p-3.5 rounded-2xl bg-secondary/30 border border-border/60">
            <div>
              <label className="text-[11px] font-medium text-muted-foreground mb-1 block">Start Date</label>
              <input
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="w-full px-3 py-2 text-xs rounded-xl bg-background border border-border text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
              />
            </div>
            <div>
              <label className="text-[11px] font-medium text-muted-foreground mb-1 block">End Date</label>
              <input
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="w-full px-3 py-2 text-xs rounded-xl bg-background border border-border text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
              />
            </div>
          </div>
        )}

        {/* 3. File Format Picker */}
        <div className="space-y-2">
          <label className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider flex items-center gap-1.5">
            <Layers className="w-3.5 h-3.5 text-primary" />
            <span>File Format</span>
          </label>
          <div className="grid grid-cols-2 gap-3">
            <button
              type="button"
              onClick={() => setSelectedFormat('excel')}
              className={`p-3.5 rounded-2xl border text-left flex items-center gap-3 transition-all ${
                selectedFormat === 'excel'
                  ? 'border-emerald-500 bg-emerald-500/10 ring-1 ring-emerald-500'
                  : 'border-border bg-card hover:bg-secondary/50'
              }`}
            >
              <div className="w-9 h-9 rounded-xl bg-emerald-600 text-white flex items-center justify-center shrink-0 shadow-sm">
                <FileSpreadsheet className="w-5 h-5" />
              </div>
              <div>
                <p className="text-xs font-bold text-foreground">Excel Sheet</p>
                <p className="text-[11px] font-semibold text-emerald-600 dark:text-emerald-400">.xls (Formatted)</p>
              </div>
            </button>

            <button
              type="button"
              onClick={() => setSelectedFormat('csv')}
              className={`p-3.5 rounded-2xl border text-left flex items-center gap-3 transition-all ${
                selectedFormat === 'csv'
                  ? 'border-primary bg-primary/10 ring-1 ring-primary'
                  : 'border-border bg-card hover:bg-secondary/50'
              }`}
            >
              <div className="w-9 h-9 rounded-xl bg-primary text-primary-foreground flex items-center justify-center shrink-0 shadow-sm">
                <FileText className="w-5 h-5" />
              </div>
              <div>
                <p className="text-xs font-bold text-foreground">CSV Sheet</p>
                <p className="text-[11px] font-semibold text-primary">.csv (Universal)</p>
              </div>
            </button>
          </div>
        </div>

        {/* 4. Live Summary Card */}
        <div className="p-4 rounded-2xl bg-secondary/50 border border-border space-y-2">
          <div className="flex items-center justify-between text-xs font-bold">
            <span className="text-foreground">{reportTitle}</span>
            <span className={summary.count > 0 ? 'text-emerald-500' : 'text-rose-500'}>
              {summary.count} {summary.count === 1 ? 'transaction' : 'transactions'}
            </span>
          </div>
          <div className="grid grid-cols-3 gap-2 pt-1 border-t border-border/50 text-[11px]">
            <div>
              <span className="text-muted-foreground block">Inflow</span>
              <span className="font-bold text-emerald-500">
                +{formatCurrency(summary.income, currency.symbol, false)}
              </span>
            </div>
            <div>
              <span className="text-muted-foreground block">Outflow</span>
              <span className="font-bold text-rose-500">
                -{formatCurrency(summary.expense, currency.symbol, false)}
              </span>
            </div>
            <div>
              <span className="text-muted-foreground block">Net Balance</span>
              <span className={`font-bold ${summary.net >= 0 ? 'text-emerald-500' : 'text-rose-500'}`}>
                {summary.net >= 0 ? '+' : ''}{formatCurrency(summary.net, currency.symbol, false)}
              </span>
            </div>
          </div>
        </div>

        {/* 5. Download Button */}
        <button
          type="button"
          onClick={handleDownload}
          className={`w-full py-3.5 rounded-2xl text-xs font-bold text-white flex items-center justify-center gap-2 shadow-lg transition-transform active:scale-[0.98] ${
            selectedFormat === 'excel'
              ? 'bg-emerald-600 hover:bg-emerald-700 shadow-emerald-500/20'
              : 'bg-primary hover:bg-primary/90 shadow-primary/20'
          }`}
        >
          <Download className="w-4 h-4" />
          <span>
            {selectedFormat === 'excel' ? 'Download Excel Workbook (.xls)' : 'Download CSV Spreadsheet (.csv)'}
          </span>
        </button>
      </div>
    </Modal>
  );
}
