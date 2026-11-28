'use client';

import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Mic,
  MicOff,
  X,
  Edit3,
  RefreshCw,
  Check,
  AlertTriangle,
  Info,
  Keyboard,
} from 'lucide-react';
import { useFinance } from '@/context/finance-context';
import { createClient, isSupabaseConfigured } from '@/lib/supabase/client';
import type { PaymentMethod, TransactionType } from '@/types/database';
import {
  formatInrAmount,
  mapWebSpeechError,
  parseVoiceTranscriptWithSmartFallback,
  type VoiceErrorKind,
} from '@/lib/voice-parser';

type VoiceStage = 'listening' | 'parsing' | 'confirmation' | 'error';

interface SpeechRecognitionAlternativeLike {
  transcript: string;
}

interface SpeechRecognitionResultLike {
  isFinal: boolean;
  length: number;
  0: SpeechRecognitionAlternativeLike;
}

interface SpeechRecognitionEventLike {
  resultIndex: number;
  results: {
    length: number;
    [index: number]: SpeechRecognitionResultLike;
  };
}

interface SpeechRecognitionErrorEventLike {
  error: string;
}

interface SpeechRecognitionLike {
  lang: string;
  interimResults: boolean;
  continuous: boolean;
  maxAlternatives: number;
  onresult: ((event: SpeechRecognitionEventLike) => void) | null;
  onerror: ((event: SpeechRecognitionErrorEventLike) => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
  abort: () => void;
}

function getSpeechRecognitionConstructor(): (new () => SpeechRecognitionLike) | null {
  if (typeof window === 'undefined') return null;
  const w = window as unknown as {
    SpeechRecognition?: new () => SpeechRecognitionLike;
    webkitSpeechRecognition?: new () => SpeechRecognitionLike;
  };
  return w.SpeechRecognition || w.webkitSpeechRecognition || null;
}

export function useWebSpeechSupported(): boolean {
  const [supported, setSupported] = useState<boolean>(true);
  useEffect(() => {
    setSupported(Boolean(getSpeechRecognitionConstructor()));
  }, []);
  return supported;
}

interface VoiceMicTriggerProps {
  onClick: () => void;
  className?: string;
  label?: string;
}

/**
 * Gold circular mic button. Hides itself cleanly and displays an informative tooltip
 * when the current browser lacks Web Speech API support.
 */
export function VoiceMicTrigger({ onClick, className = '', label = 'Add transaction by voice' }: VoiceMicTriggerProps) {
  const supported = useWebSpeechSupported();

  if (!supported) {
    return (
      <div
        className={`relative group inline-flex items-center ${className}`}
        tabIndex={0}
        aria-label="Voice entry is not supported in this browser. Use Chrome or Safari."
      >
        <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-secondary/70 border border-border text-[11px] font-medium text-muted-foreground cursor-help">
          <Info className="w-3.5 h-3.5 text-[#D4AF6A]" />
          <span>Voice unavailable</span>
        </span>
        <div
          role="tooltip"
          className="pointer-events-none opacity-0 group-hover:opacity-100 group-focus:opacity-100 transition-opacity absolute right-0 top-full mt-2 w-56 p-2.5 rounded-xl bg-[#2A0F1A] border border-[#D4AF6A]/40 text-[#FBF5EA] text-[11px] shadow-xl z-40"
        >
          Voice input requires a browser supporting Web Speech API (Chrome, Edge, or Safari). Please enter transactions manually.
        </div>
      </div>
    );
  }

  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      title={label}
      className={`w-11 h-11 rounded-full bg-gradient-to-br from-[#F3DBA0] via-[#D4AF6A] to-[#8A6420] text-[#2A0F1A] flex items-center justify-center shadow-lg shadow-[#D4AF6A]/25 border border-[#F3DBA0]/60 hover:scale-105 active:scale-95 transition-transform ${className}`}
    >
      <Mic className="w-5 h-5 stroke-[2.2]" />
    </button>
  );
}

interface VoiceEntryModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSwitchToManual?: () => void;
}

const PAYMENT_OPTIONS: { value: PaymentMethod; label: string }[] = [
  { value: 'upi', label: 'UPI' },
  { value: 'cash', label: 'Cash' },
  { value: 'card', label: 'Card' },
  { value: 'net_banking', label: 'Net Banking' },
  { value: 'bank_transfer', label: 'Bank Transfer' },
];

export function VoiceEntryModal({ isOpen, onClose, onSwitchToManual }: VoiceEntryModalProps) {
  const { categories, voiceLanguage, addTransaction } = useFinance();

  const [stage, setStage] = useState<VoiceStage>('listening');
  const [partialText, setPartialText] = useState('');
  const [rawTranscript, setRawTranscript] = useState('');
  const [errorInfo, setErrorInfo] = useState<{ kind: VoiceErrorKind; message: string } | null>(null);

  // Editable confirmation state
  const [txType, setTxType] = useState<TransactionType | null>(null);
  const [amountInput, setAmountInput] = useState('');
  const [editingAmount, setEditingAmount] = useState(false);
  const [categoryId, setCategoryId] = useState<string>('');
  const [editingCategory, setEditingCategory] = useState(false);
  const [date, setDate] = useState(() => new Date().toISOString().split('T')[0]);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('upi');
  const [note, setNote] = useState('');
  const [editingNote, setEditingNote] = useState(false);
  const [usedSmartParser, setUsedSmartParser] = useState(false);
  const [isSaving, setIsSaving] = useState(false);

  const recognitionRef = useRef<SpeechRecognitionLike | null>(null);
  const finalHandledRef = useRef(false);
  const latestTranscriptRef = useRef('');
  const categoriesRef = useRef(categories);
  const voiceLanguageRef = useRef(voiceLanguage);
  categoriesRef.current = categories;
  voiceLanguageRef.current = voiceLanguage;

  const todayStr = useMemo(() => new Date().toISOString().split('T')[0], []);
  const yesterdayStr = useMemo(() => {
    const d = new Date();
    d.setDate(d.getDate() - 1);
    return d.toISOString().split('T')[0];
  }, []);

  const stopRecognition = useCallback(() => {
    try {
      recognitionRef.current?.abort();
    } catch (_) {}
    recognitionRef.current = null;
  }, []);

  const processTranscript = useCallback(async (transcript: string) => {
    const clean = transcript.trim();
    if (!clean) return;
    finalHandledRef.current = true;
    setRawTranscript(clean);
    setStage('parsing');

    const parsed = await (async () => {
      let accessToken: string | null = null;
      if (isSupabaseConfigured()) {
        try {
          const { data } = await createClient().auth.getSession();
          accessToken = data?.session?.access_token ?? null;
        } catch (_) {}
      }
      return parseVoiceTranscriptWithSmartFallback(clean, categoriesRef.current, new Date(), accessToken);
    })();
    setTxType(parsed.type);
    setAmountInput(parsed.amount && parsed.amount > 0 ? String(parsed.amount) : '');
    setEditingAmount(!parsed.amount || parsed.amount <= 0);
    setCategoryId(parsed.categoryId || '');
    setEditingCategory(false);
    setDate(parsed.date);
    setPaymentMethod(parsed.paymentMethod);
    setNote(parsed.note);
    setEditingNote(false);
    setUsedSmartParser(Boolean(parsed.usedSmartParser));
    setStage('confirmation');
  }, []);

  const startListening = useCallback(() => {
    stopRecognition();
    finalHandledRef.current = false;
    latestTranscriptRef.current = '';
    setPartialText('');
    setErrorInfo(null);

    const Ctor = getSpeechRecognitionConstructor();
    if (!Ctor) {
      setErrorInfo({
        kind: 'unsupported',
        message: 'Speech recognition is not supported in this browser. Please enter your transaction manually.',
      });
      setStage('error');
      return;
    }

    setStage('listening');
    const recognition = new Ctor();
    recognition.lang = voiceLanguageRef.current;
    recognition.interimResults = true;
    recognition.continuous = false;
    recognition.maxAlternatives = 1;

    recognition.onresult = (event: SpeechRecognitionEventLike) => {
      let interim = '';
      let finalChunk = '';
      for (let i = 0; i < event.results.length; i++) {
        const res = event.results[i];
        const txt = res[0]?.transcript || '';
        if (res.isFinal) {
          finalChunk += `${txt} `;
        } else {
          interim += txt;
        }
      }
      const display = (finalChunk || interim).trim();
      latestTranscriptRef.current = display;
      setPartialText(display);

      if (finalChunk.trim() && !finalHandledRef.current) {
        stopRecognition();
        void processTranscript(finalChunk.trim());
      }
    };

    recognition.onerror = (event: SpeechRecognitionErrorEventLike) => {
      if (finalHandledRef.current) return;
      const captured = latestTranscriptRef.current.trim();
      if (captured && (event.error === 'no-speech' || event.error === 'aborted')) {
        stopRecognition();
        void processTranscript(captured);
        return;
      }
      stopRecognition();
      setErrorInfo(mapWebSpeechError(event.error));
      setStage('error');
    };

    recognition.onend = () => {
      if (finalHandledRef.current) return;
      if (latestTranscriptRef.current.trim()) {
        void processTranscript(latestTranscriptRef.current.trim());
      } else {
        setStage((prev) => {
          if (prev === 'listening') {
            setErrorInfo(mapWebSpeechError('no-speech'));
            return 'error';
          }
          return prev;
        });
      }
    };

    recognitionRef.current = recognition;
    try {
      recognition.start();
    } catch (_) {
      setErrorInfo(mapWebSpeechError('unknown'));
      setStage('error');
    }
  }, [processTranscript, stopRecognition]);

  useEffect(() => {
    if (!isOpen) {
      stopRecognition();
      return;
    }
    startListening();
    return () => {
      stopRecognition();
    };
  }, [isOpen, startListening, stopRecognition]);

  if (!isOpen) return null;

  const filteredCategories = txType
    ? categories.filter((c) => c.type === txType)
    : categories;
  const selectedCategory =
    categories.find((c) => c.id === categoryId) || filteredCategories[0] || null;

  const parsedAmountNum = parseFloat(amountInput);
  const missingType = txType === null;
  const missingAmount = !Number.isFinite(parsedAmountNum) || parsedAmountNum <= 0;

  const handleSave = async () => {
    if (missingType || missingAmount || !txType) return;
    setIsSaving(true);
    try {
      await addTransaction({
        type: txType,
        amount: parsedAmountNum,
        category_id: selectedCategory?.id || null,
        trip_id: null,
        date,
        note: note.trim() || null,
        payment_method: paymentMethod,
      });
      onClose();
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center sm:p-4">
      <div
        className="fixed inset-0 bg-[#1A0810]/75 backdrop-blur-sm animate-in fade-in"
        onClick={() => {
          stopRecognition();
          onClose();
        }}
      />

      {/* Royal Plum Glass Bottom Sheet / Card */}
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Voice Entry"
        className="relative w-full sm:max-w-lg rounded-t-3xl sm:rounded-3xl bg-gradient-to-b from-[#2A0F1A]/95 to-[#1A0810]/98 border border-[#D4AF6A]/35 p-6 shadow-2xl backdrop-blur-xl text-[#FBF5EA] max-h-[90vh] overflow-y-auto"
      >
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-[#D4AF6A]/20 mb-5">
          <div className="flex items-center gap-2.5">
            <h2 className="text-lg font-bold tracking-tight text-[#FBF5EA]">
              {stage === 'listening' && 'Voice Entry'}
              {stage === 'parsing' && 'Understanding Speech'}
              {stage === 'confirmation' && 'Confirm Transaction'}
              {stage === 'error' && 'Voice Input Issue'}
            </h2>
            <span className="px-2 py-0.5 rounded-lg bg-[#D4AF6A]/15 border border-[#D4AF6A]/40 text-[11px] font-bold text-[#F3DBA0]">
              {voiceLanguage === 'ta-IN' ? 'TA-IN' : 'EN-IN'}
            </span>
          </div>
          <button
            type="button"
            onClick={() => {
              stopRecognition();
              onClose();
            }}
            aria-label="Close voice entry"
            className="w-10 h-10 rounded-xl flex items-center justify-center text-[#C4B8A4] hover:bg-[#5A1A2E]/50 hover:text-[#FBF5EA] transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* LISTENING STAGE */}
        {stage === 'listening' && (
          <div className="flex flex-col items-center text-center space-y-5 py-3">
            <div className="relative flex items-center justify-center w-28 h-28">
              <span className="absolute inset-0 rounded-full bg-[#D4AF6A]/20 animate-ping" />
              <span className="absolute inset-2 rounded-full bg-[#D4AF6A]/15 border border-[#D4AF6A]/40" />
              <div className="relative w-18 h-18 w-[72px] h-[72px] rounded-full bg-gradient-to-br from-[#F3DBA0] via-[#D4AF6A] to-[#8A6420] flex items-center justify-center shadow-xl shadow-[#D4AF6A]/30">
                <Mic className="w-8 h-8 text-[#2A0F1A]" />
              </div>
            </div>

            <div>
              <p className="text-base font-bold text-[#FBF5EA]">Listening… Speak naturally</p>
              <p className="text-xs text-[#C4B8A4] mt-1">
                Try &ldquo;spent 486 on swiggy dinner yesterday by UPI&rdquo;
              </p>
            </div>

            <div className="w-full min-h-[64px] rounded-2xl bg-[#5A1A2E]/30 border border-[#D4AF6A]/25 px-4 py-3 flex items-center justify-center">
              <p className="text-sm text-[#FBF5EA]">
                {partialText ? `“${partialText}”` : 'Waiting for your voice…'}
              </p>
            </div>

            <div className="grid grid-cols-2 gap-3 w-full pt-1">
              <button
                type="button"
                onClick={() => {
                  stopRecognition();
                  onClose();
                }}
                className="min-h-[44px] px-4 py-2.5 rounded-2xl border border-[#D4AF6A]/35 bg-[#5A1A2E]/30 hover:bg-[#5A1A2E]/55 text-sm font-semibold text-[#FBF5EA] transition-colors"
              >
                Cancel
              </button>
              {partialText.trim() ? (
                <button
                  type="button"
                  onClick={() => {
                    stopRecognition();
                    void processTranscript(partialText.trim());
                  }}
                  className="min-h-[44px] px-4 py-2.5 rounded-2xl bg-gradient-to-b from-[#F3DBA0] via-[#D4AF6A] to-[#8A6420] text-[#2A0F1A] text-sm font-bold"
                >
                  Done speaking
                </button>
              ) : onSwitchToManual ? (
                <button
                  type="button"
                  onClick={() => {
                    stopRecognition();
                    onClose();
                    onSwitchToManual();
                  }}
                  className="min-h-[44px] px-4 py-2.5 rounded-2xl border border-[#D4AF6A]/35 bg-[#2A0F1A]/80 hover:bg-[#5A1A2E]/45 text-sm font-semibold text-[#F3DBA0] flex items-center justify-center gap-1.5 transition-colors"
                >
                  <Keyboard className="w-4 h-4" />
                  <span>Type manually</span>
                </button>
              ) : (
                <button
                  type="button"
                  onClick={() => {
                    stopRecognition();
                    onClose();
                  }}
                  className="min-h-[44px] px-4 py-2.5 rounded-2xl border border-[#D4AF6A]/35 bg-[#2A0F1A]/80 text-sm font-semibold text-[#F3DBA0]"
                >
                  Close
                </button>
              )}
            </div>
          </div>
        )}

        {/* PARSING STAGE */}
        {stage === 'parsing' && (
          <div className="py-10 flex flex-col items-center text-center space-y-3">
            <div className="w-10 h-10 rounded-full border-2 border-[#D4AF6A] border-t-transparent animate-spin" />
            <p className="text-sm text-[#FBF5EA]">
              Parsing &ldquo;{rawTranscript}&rdquo;…
            </p>
          </div>
        )}

        {/* ERROR STAGE */}
        {stage === 'error' && errorInfo && (
          <div className="space-y-5 text-center py-2">
            <div className="w-14 h-14 mx-auto rounded-full bg-rose-500/15 border border-rose-400/35 flex items-center justify-center text-rose-300">
              {errorInfo.kind === 'permission-denied' ? (
                <MicOff className="w-6 h-6" />
              ) : (
                <AlertTriangle className="w-6 h-6" />
              )}
            </div>
            <p className="text-sm text-[#FBF5EA] leading-relaxed">{errorInfo.message}</p>
            <div className="grid grid-cols-2 gap-3">
              {onSwitchToManual && (
                <button
                  type="button"
                  onClick={() => {
                    onClose();
                    onSwitchToManual();
                  }}
                  className="min-h-[44px] px-4 py-2.5 rounded-2xl border border-[#D4AF6A]/35 bg-[#5A1A2E]/35 text-sm font-semibold text-[#FBF5EA]"
                >
                  Enter Manually
                </button>
              )}
              {errorInfo.kind !== 'unsupported' && (
                <button
                  type="button"
                  onClick={startListening}
                  className={`min-h-[44px] px-4 py-2.5 rounded-2xl bg-gradient-to-b from-[#F3DBA0] via-[#D4AF6A] to-[#8A6420] text-[#2A0F1A] text-sm font-bold ${
                    onSwitchToManual ? '' : 'col-span-2'
                  }`}
                >
                  Try again
                </button>
              )}
            </div>
          </div>
        )}

        {/* CONFIRMATION CARD STAGE */}
        {stage === 'confirmation' && (
          <div className="space-y-4">
            {rawTranscript && (
              <p className="text-xs text-[#C4B8A4]">
                Heard: &ldquo;{rawTranscript}&rdquo;
              </p>
            )}

            {(missingType || missingAmount) && (
              <div className="p-3 rounded-2xl bg-amber-500/15 border border-amber-400/45 text-amber-200 text-xs font-medium">
                {missingType && missingAmount
                  ? 'Please select Expense or Income and enter the amount below.'
                  : missingType
                    ? 'Could not determine Expense or Income from speech. Tap one below.'
                    : 'Amount was not detected. Please enter the amount in INR.'}
              </div>
            )}

            <div className="rounded-2xl bg-[#5A1A2E]/25 border border-[#D4AF6A]/30 p-4 space-y-4">
              {/* Type */}
              <div className="space-y-1.5">
                <span className="text-[11px] font-semibold uppercase tracking-wider text-[#C4B8A4]">
                  Type
                </span>
                <div className="grid grid-cols-2 gap-2">
                  {(['expense', 'income'] as const).map((t) => {
                    const active = txType === t;
                    return (
                      <button
                        key={t}
                        type="button"
                        onClick={() => {
                          setTxType(t);
                          const match = categories.filter((c) => c.type === t);
                          if (!match.some((c) => c.id === categoryId)) {
                            setCategoryId(match[0]?.id || '');
                          }
                        }}
                        className={`min-h-[42px] rounded-xl border text-xs font-bold transition-all ${
                          active
                            ? t === 'income'
                              ? 'bg-emerald-500/20 border-emerald-400 text-emerald-300'
                              : 'bg-rose-500/20 border-rose-400 text-rose-300'
                            : 'border-[#D4AF6A]/25 text-[#FBF5EA] hover:bg-[#5A1A2E]/40'
                        }`}
                      >
                        {t === 'income' ? 'Income' : 'Expense'}
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Amount (INR grouped, tappable to edit) */}
              <div className="pt-3 border-t border-[#D4AF6A]/15 space-y-2">
                <button
                  type="button"
                  onClick={() => setEditingAmount((v) => !v)}
                  className="w-full flex items-center justify-between text-left"
                >
                  <span className="text-xs font-semibold text-[#C4B8A4]">Amount (INR)</span>
                  <span className="flex items-center gap-1.5 text-lg font-extrabold text-[#F3DBA0]">
                    {missingAmount ? 'Tap to enter' : formatInrAmount(parsedAmountNum)}
                    <Edit3 className="w-3.5 h-3.5 text-[#D4AF6A]" />
                  </span>
                </button>
                {editingAmount && (
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    value={amountInput}
                    onChange={(e) => setAmountInput(e.target.value)}
                    placeholder="Enter amount (e.g. 486)"
                    className="w-full px-3.5 py-2.5 rounded-xl bg-[#1A0810]/80 border border-[#D4AF6A]/45 text-sm font-bold text-[#FBF5EA] focus:outline-none focus:ring-2 focus:ring-[#D4AF6A]"
                  />
                )}
              </div>

              {/* Category */}
              <div className="pt-3 border-t border-[#D4AF6A]/15 space-y-2">
                <button
                  type="button"
                  onClick={() => setEditingCategory((v) => !v)}
                  className="w-full flex items-center justify-between text-left"
                >
                  <span className="text-xs font-semibold text-[#C4B8A4]">Category</span>
                  <span className="flex items-center gap-1.5 text-sm font-semibold text-[#FBF5EA]">
                    {selectedCategory?.name || 'Select category'}
                    <Edit3 className="w-3.5 h-3.5 text-[#D4AF6A]" />
                  </span>
                </button>
                {editingCategory && (
                  <div className="flex flex-wrap gap-2 pt-1">
                    {filteredCategories.map((cat) => {
                      const active = selectedCategory?.id === cat.id;
                      return (
                        <button
                          key={cat.id}
                          type="button"
                          onClick={() => {
                            setCategoryId(cat.id);
                            if (!txType) setTxType(cat.type);
                            setEditingCategory(false);
                          }}
                          className={`px-3 py-1.5 rounded-xl text-xs font-semibold border transition-colors ${
                            active
                              ? 'bg-[#D4AF6A] text-[#2A0F1A] border-[#D4AF6A]'
                              : 'border-[#D4AF6A]/30 text-[#FBF5EA] hover:bg-[#5A1A2E]/45'
                          }`}
                        >
                          {cat.name}
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>

              {/* Date */}
              <div className="pt-3 border-t border-[#D4AF6A]/15 space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold text-[#C4B8A4]">Date</span>
                  <span className="text-xs font-semibold text-[#FBF5EA]">
                    {date === todayStr ? `Today (${date})` : date === yesterdayStr ? `Yesterday (${date})` : date}
                  </span>
                </div>
                <div className="grid grid-cols-3 gap-2">
                  <button
                    type="button"
                    onClick={() => setDate(todayStr)}
                    className={`py-1.5 rounded-xl text-xs font-bold border ${
                      date === todayStr
                        ? 'bg-[#D4AF6A] text-[#2A0F1A] border-[#D4AF6A]'
                        : 'border-[#D4AF6A]/30 text-[#FBF5EA]'
                    }`}
                  >
                    Today
                  </button>
                  <button
                    type="button"
                    onClick={() => setDate(yesterdayStr)}
                    className={`py-1.5 rounded-xl text-xs font-bold border ${
                      date === yesterdayStr
                        ? 'bg-[#D4AF6A] text-[#2A0F1A] border-[#D4AF6A]'
                        : 'border-[#D4AF6A]/30 text-[#FBF5EA]'
                    }`}
                  >
                    Yesterday
                  </button>
                  <input
                    type="date"
                    value={date}
                    onChange={(e) => setDate(e.target.value)}
                    aria-label="Custom transaction date"
                    className="px-2 py-1.5 rounded-xl bg-[#1A0810]/70 border border-[#D4AF6A]/30 text-[11px] font-semibold text-[#FBF5EA]"
                  />
                </div>
              </div>

              {/* Payment Method */}
              <div className="pt-3 border-t border-[#D4AF6A]/15 space-y-2">
                <span className="text-xs font-semibold text-[#C4B8A4] block">Payment Method</span>
                <div className="flex flex-wrap gap-2">
                  {PAYMENT_OPTIONS.map((opt) => {
                    const active = paymentMethod === opt.value;
                    return (
                      <button
                        key={opt.value}
                        type="button"
                        onClick={() => setPaymentMethod(opt.value)}
                        className={`px-3 py-1.5 rounded-xl text-xs font-bold border transition-colors ${
                          active
                            ? 'bg-[#D4AF6A] text-[#2A0F1A] border-[#D4AF6A]'
                            : 'border-[#D4AF6A]/30 text-[#FBF5EA] hover:bg-[#5A1A2E]/45'
                        }`}
                      >
                        {opt.label}
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Note */}
              <div className="pt-3 border-t border-[#D4AF6A]/15 space-y-2">
                <button
                  type="button"
                  onClick={() => setEditingNote((v) => !v)}
                  className="w-full flex items-center justify-between text-left"
                >
                  <span className="text-xs font-semibold text-[#C4B8A4]">Note</span>
                  <span className="flex items-center gap-1.5 text-xs text-[#FBF5EA]">
                    {note || 'Add note'}
                    <Edit3 className="w-3.5 h-3.5 text-[#D4AF6A]" />
                  </span>
                </button>
                {editingNote && (
                  <input
                    type="text"
                    value={note}
                    onChange={(e) => setNote(e.target.value)}
                    placeholder="Add note..."
                    className="w-full px-3.5 py-2 rounded-xl bg-[#1A0810]/80 border border-[#D4AF6A]/45 text-xs text-[#FBF5EA] focus:outline-none focus:ring-2 focus:ring-[#D4AF6A]"
                  />
                )}
              </div>

              {usedSmartParser && (
                <p className="text-[11px] text-[#C4B8A4]">Smart parsed via Gemini</p>
              )}
            </div>

            {/* Actions */}
            <div className="grid grid-cols-2 gap-3 pt-1">
              <button
                type="button"
                onClick={startListening}
                className="min-h-[46px] px-4 py-2.5 rounded-2xl border border-[#D4AF6A]/35 bg-[#5A1A2E]/35 hover:bg-[#5A1A2E]/55 text-sm font-bold text-[#FBF5EA] flex items-center justify-center gap-2 transition-colors"
              >
                <RefreshCw className="w-4 h-4" />
                <span>Try again</span>
              </button>
              <button
                type="button"
                disabled={missingType || missingAmount || isSaving}
                onClick={handleSave}
                className="min-h-[46px] px-4 py-2.5 rounded-2xl bg-gradient-to-b from-[#F3DBA0] via-[#D4AF6A] to-[#8A6420] text-[#2A0F1A] text-sm font-extrabold flex items-center justify-center gap-2 shadow-lg shadow-[#D4AF6A]/25 disabled:opacity-45 transition-all"
              >
                <Check className="w-4 h-4 stroke-[2.5]" />
                <span>{isSaving ? 'Saving…' : 'Save'}</span>
              </button>
            </div>

            {onSwitchToManual && (
              <button
                type="button"
                onClick={() => {
                  onClose();
                  onSwitchToManual();
                }}
                className="w-full text-center text-xs text-[#C4B8A4] hover:text-[#FBF5EA] py-1"
              >
                Edit inside manual Add Transaction form instead
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
