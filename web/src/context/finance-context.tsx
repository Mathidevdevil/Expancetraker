'use client';

import React, { createContext, useContext, useEffect, useState, useMemo, useCallback } from 'react';
import { Category, Transaction, Budget, Trip, CurrencyCode, CURRENCIES, CurrencyConfig } from '@/types/database';
import { DEFAULT_CATEGORIES, INITIAL_TRANSACTIONS, INITIAL_BUDGETS, INITIAL_TRIPS } from '@/lib/supabase/mock-data';
import { createClient, isSupabaseConfigured } from '@/lib/supabase/client';

export interface UserProfile {
  name: string;
  email: string;
  initials: string;
  bio?: string;
}

const DEFAULT_USER_PROFILE: UserProfile = {
  name: 'Spendwise User',
  email: '',
  initials: 'SU',
  bio: 'Personal Finance & Travel Budgeter',
};

interface FinanceContextType {
  transactions: Transaction[];
  categories: Category[];
  budgets: Budget[];
  trips: Trip[];
  currency: CurrencyConfig;
  setCurrencyCode: (code: CurrencyCode) => void;
  userProfile: UserProfile;
  updateUserProfile: (profile: Partial<UserProfile>) => Promise<void>;
  user: any | null;
  isLoggedIn: boolean;
  login: (email: string, name?: string) => void;
  logout: () => Promise<void>;
  isSupabaseLive: boolean;
  isRealtimeConnected: boolean;
  isLoading: boolean;
  // Transactions
  addTransaction: (tx: Omit<Transaction, 'id' | 'created_at' | 'user_id'>) => Promise<Transaction>;
  updateTransaction: (id: string, tx: Partial<Transaction>) => Promise<void>;
  deleteTransaction: (id: string) => Promise<void>;
  // Categories
  addCategory: (cat: Omit<Category, 'id' | 'created_at' | 'user_id' | 'is_default'>) => Promise<Category>;
  updateCategory: (id: string, cat: Partial<Category>) => Promise<void>;
  deleteCategory: (id: string) => Promise<void>;
  // Budgets
  setCategoryBudget: (categoryId: string, month: number, year: number, amount: number) => Promise<void>;
  deleteBudget: (id: string) => Promise<void>;
  // Trips
  addTrip: (trip: Omit<Trip, 'id' | 'created_at' | 'user_id'>) => Promise<Trip>;
  updateTrip: (id: string, trip: Partial<Trip>) => Promise<void>;
  deleteTrip: (id: string) => Promise<void>;
  // Export
  exportToCSV: (transactionsToExport?: Transaction[], periodTitle?: string) => void;
  exportToExcel: (transactionsToExport?: Transaction[], periodTitle?: string) => void;
}

const FinanceContext = createContext<FinanceContextType | undefined>(undefined);

const LOCAL_STORAGE_KEY_PREFIX = 'spendwise_data_';

const CANONICAL_CATEGORY_SLUGS: Record<string, string> = {
  'food & dining': 'cat-food',
  'transportation': 'cat-trans',
  'shopping': 'cat-shop',
  'entertainment': 'cat-ent',
  'bills & utilities': 'cat-bills',
  'healthcare': 'cat-health',
  'travel': 'cat-travel',
  'groceries': 'cat-groc',
  'education': 'cat-edu',
  'other expense': 'cat-other-exp',
  'salary': 'cat-sal',
  'investment': 'cat-inv',
  'freelance': 'cat-free',
  'gift / bonus': 'cat-gift',
  'other income': 'cat-other-inc',
};

function deduplicateCategories(list: Category[], allowedUserIds?: string[]): Category[] {
  const map = new Map<string, Category>();
  for (const cat of list) {
    if (allowedUserIds && cat.user_id && !allowedUserIds.includes(cat.user_id)) {
      continue;
    }
    const key = `${cat.name.trim().toLowerCase()}|${cat.type}`;
    const existing = map.get(key);
    if (!existing) {
      map.set(key, cat);
    } else {
      const existingCanonical = existing.id.startsWith('cat-') && existing.user_id === null;
      const candidateCanonical = cat.id.startsWith('cat-') && cat.user_id === null;
      if (!existingCanonical && candidateCanonical) {
        map.set(key, cat);
      } else if (existing.is_default && !existing.id.startsWith('cat-') && !cat.is_default) {
        map.set(key, cat);
      }
    }
  }
  return Array.from(map.values());
}

export function FinanceProvider({ children }: { children: React.ReactNode }) {
  const [currencyCode, setCurrencyCodeState] = useState<CurrencyCode>('INR');
  const [categories, setCategories] = useState<Category[]>(DEFAULT_CATEGORIES);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [budgets, setBudgets] = useState<Budget[]>([]);
  const [trips, setTrips] = useState<Trip[]>([]);
  const [userProfile, setUserProfile] = useState<UserProfile>(DEFAULT_USER_PROFILE);
  const [user, setUser] = useState<any | null>(null);
  const [isLoggedIn, setIsLoggedIn] = useState<boolean>(false);
  const [isSupabaseLive, setIsSupabaseLive] = useState<boolean>(false);
  const [isRealtimeConnected, setIsRealtimeConnected] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  const supabase = useMemo(() => createClient(), []);

  // 1. Load Initial State & Auth
  useEffect(() => {
    const isConfigured = isSupabaseConfigured();
    setIsSupabaseLive(isConfigured);

    // Load stored currency
    const savedCurrency = localStorage.getItem('spendwise_currency') as CurrencyCode | null;
    if (savedCurrency && CURRENCIES[savedCurrency]) {
      setCurrencyCodeState(savedCurrency);
    }

    // Load stored profile & login state
    const savedLogin = localStorage.getItem('spendwise_is_logged_in');
    if (savedLogin === 'true') {
      setIsLoggedIn(true);
    }

    try {
      const storedProfile = localStorage.getItem('spendwise_user_profile');
      if (storedProfile) {
        setUserProfile(JSON.parse(storedProfile));
      }
    } catch (e) {
      console.error('Error reading localStorage profile', e);
    }

    // Always load from local cache first so user data is immediately available
    try {
      const storedCats = localStorage.getItem(`${LOCAL_STORAGE_KEY_PREFIX}categories`);
      const storedTxs = localStorage.getItem(`${LOCAL_STORAGE_KEY_PREFIX}transactions`);
      const storedBudgets = localStorage.getItem(`${LOCAL_STORAGE_KEY_PREFIX}budgets`);
      const storedTrips = localStorage.getItem(`${LOCAL_STORAGE_KEY_PREFIX}trips`);

      if (storedCats) {
        const parsedCats = JSON.parse(storedCats) as Category[];
        const currentIds = userProfile?.email && userProfile.email !== 'user@spendwise.app' ? [userProfile.email] : undefined;
        setCategories(deduplicateCategories([...DEFAULT_CATEGORIES, ...parsedCats], currentIds));
      }
      if (storedTxs) setTransactions(JSON.parse(storedTxs));
      if (storedBudgets) setBudgets(JSON.parse(storedBudgets));
      if (storedTrips) setTrips(JSON.parse(storedTrips));
    } catch (e) {
      console.error('Error reading localStorage data', e);
    }

    if (!isConfigured) {
      setIsLoading(false);
      return;
    }

    // Live Supabase flow
    async function initSupabase() {
      try {
        const { data: { session } } = await supabase.auth.getSession();
        if (session?.user) {
          setUser(session.user);
          setIsLoggedIn(true);
          try {
            localStorage.setItem('spendwise_is_logged_in', 'true');
          } catch (_) {}
          const metaName = session.user.user_metadata?.full_name || session.user.user_metadata?.name;
          const email = session.user.email || 'user@spendwise.app';
          setUserProfile((prev) => {
            const name = metaName || prev.name;
            const initials = name.split(' ').filter(Boolean).slice(0, 2).map((s: string) => s[0].toUpperCase()).join('') || 'SW';
            const updated = { ...prev, name, email, initials };
            try {
              localStorage.setItem('spendwise_user_profile', JSON.stringify(updated));
            } catch (_) {}
            return updated;
          });
          await fetchRemoteData(session.user.id);
        } else {
          // If no active auth session, check stored user profile email
          const stored = localStorage.getItem('spendwise_user_profile');
          if (stored) {
            try {
              const p = JSON.parse(stored);
              if (p?.email) {
                await fetchRemoteData(p.email);
              }
            } catch (_) {}
          }
          setIsLoading(false);
        }
      } catch (err) {
        console.warn('Supabase fetch failed, falling back to local cache', err);
        setIsLoading(false);
      }
    }

    initSupabase();

    const { data: { subscription: authSub } } = supabase.auth.onAuthStateChange(async (_event, session) => {
      setUser(session?.user || null);
      if (session?.user) {
        setIsLoggedIn(true);
        try {
          localStorage.setItem('spendwise_is_logged_in', 'true');
        } catch (_) {}
        const metaName = session.user.user_metadata?.full_name || session.user.user_metadata?.name;
        const email = session.user.email || 'user@spendwise.app';
        setUserProfile((prev) => {
          const name = metaName || prev.name;
          const initials = name.split(' ').filter(Boolean).slice(0, 2).map((s: string) => s[0].toUpperCase()).join('') || 'SW';
          const updated = { ...prev, name, email, initials };
          try {
            localStorage.setItem('spendwise_user_profile', JSON.stringify(updated));
          } catch (_) {}
          return updated;
        });
        await fetchRemoteData(session.user.id);
      }
    });

    return () => {
      authSub.unsubscribe();
    };
  }, [supabase]);

  // 2. Fetch Data from Supabase
  const fetchRemoteData = useCallback(async (userId: string) => {
    setIsLoading(true);
    try {
      const email = userProfile?.email;
      const authEmail = user?.email;
      const ids = Array.from(new Set([userId, user?.id, email, authEmail].filter((id): id is string => Boolean(id) && id !== 'user@spendwise.app')));
      const queryIds = ids.length > 0 ? ids : [userId];

      const catFilter = ['user_id.is.null', ...queryIds.map((id) => `user_id.eq.${id}`)].join(',');
      const [catsRes, tripsRes, txsRes, budgetsRes] = await Promise.all([
        supabase.from('categories').select('*').or(catFilter).order('created_at', { ascending: true }),
        supabase.from('trips').select('*').in('user_id', queryIds).order('start_date', { ascending: false }),
        supabase.from('transactions').select('*').in('user_id', queryIds).order('date', { ascending: false }),
        supabase.from('budgets').select('*').in('user_id', queryIds),
      ]);

      if (catsRes.data) {
        const cleaned = deduplicateCategories([...DEFAULT_CATEGORIES, ...(catsRes.data as Category[])], queryIds);
        setCategories(cleaned);
      }
      if (tripsRes.data) setTrips(tripsRes.data);
      if (txsRes.data) setTransactions(txsRes.data);
      if (budgetsRes.data) setBudgets(budgetsRes.data);
    } catch (e) {
      console.error('Failed to load remote Supabase data', e);
    } finally {
      setIsLoading(false);
    }
  }, [supabase, user?.id, user?.email, userProfile?.email]);

  // 3. Realtime Subscription Channel
  useEffect(() => {
    if (!isSupabaseLive || !user?.id) return;

    const channel = supabase
      .channel('spendwise_realtime_changes')
      .on(
        'postgres_changes',
        { event: '*', schema: 'public', table: 'transactions', filter: `user_id=eq.${user.id}` },
        (payload) => {
          if (payload.eventType === 'INSERT') {
            setTransactions((prev) => [payload.new as Transaction, ...prev.filter((t) => t.id !== payload.new.id)]);
          } else if (payload.eventType === 'UPDATE') {
            setTransactions((prev) => prev.map((t) => (t.id === payload.new.id ? (payload.new as Transaction) : t)));
          } else if (payload.eventType === 'DELETE') {
            setTransactions((prev) => prev.filter((t) => t.id !== payload.old.id));
          }
        }
      )
      .on(
        'postgres_changes',
        { event: '*', schema: 'public', table: 'categories' },
        (payload) => {
          const row = (payload.eventType === 'DELETE' ? payload.old : payload.new) as Category;
          if (row?.user_id && row.user_id !== user.id && row.user_id !== user.email) {
            return;
          }
          if (row?.is_default && !row.id?.startsWith('cat-')) {
            return;
          }
          if (payload.eventType === 'INSERT') {
            setCategories((prev) => deduplicateCategories([...prev.filter((c) => c.id !== row.id), row]));
          } else if (payload.eventType === 'UPDATE') {
            setCategories((prev) => deduplicateCategories(prev.map((c) => (c.id === row.id ? row : c))));
          } else if (payload.eventType === 'DELETE') {
            setCategories((prev) => prev.filter((c) => c.id !== row.id));
          }
        }
      )
      .on(
        'postgres_changes',
        { event: '*', schema: 'public', table: 'budgets', filter: `user_id=eq.${user.id}` },
        (payload) => {
          if (payload.eventType === 'INSERT') {
            setBudgets((prev) => [...prev.filter((b) => b.id !== payload.new.id), payload.new as Budget]);
          } else if (payload.eventType === 'UPDATE') {
            setBudgets((prev) => prev.map((b) => (b.id === payload.new.id ? (payload.new as Budget) : b)));
          } else if (payload.eventType === 'DELETE') {
            setBudgets((prev) => prev.filter((b) => b.id !== payload.old.id));
          }
        }
      )
      .on(
        'postgres_changes',
        { event: '*', schema: 'public', table: 'trips', filter: `user_id=eq.${user.id}` },
        (payload) => {
          if (payload.eventType === 'INSERT') {
            setTrips((prev) => [...prev.filter((tr) => tr.id !== payload.new.id), payload.new as Trip]);
          } else if (payload.eventType === 'UPDATE') {
            setTrips((prev) => prev.map((tr) => (tr.id === payload.new.id ? (payload.new as Trip) : tr)));
          } else if (payload.eventType === 'DELETE') {
            setTrips((prev) => prev.filter((tr) => tr.id !== payload.old.id));
          }
        }
      )
      .subscribe((status) => {
        setIsRealtimeConnected(status === 'SUBSCRIBED');
      });

    return () => {
      supabase.removeChannel(channel);
    };
  }, [supabase, isSupabaseLive, user]);

  // 4. Local Persistence for Offline & Immediate Cache (always active)
  useEffect(() => {
    try {
      localStorage.setItem(`${LOCAL_STORAGE_KEY_PREFIX}categories`, JSON.stringify(categories));
      localStorage.setItem(`${LOCAL_STORAGE_KEY_PREFIX}transactions`, JSON.stringify(transactions));
      localStorage.setItem(`${LOCAL_STORAGE_KEY_PREFIX}budgets`, JSON.stringify(budgets));
      localStorage.setItem(`${LOCAL_STORAGE_KEY_PREFIX}trips`, JSON.stringify(trips));
    } catch (e) {
      console.error('Failed to sync to localStorage', e);
    }
  }, [categories, transactions, budgets, trips]);

  const setCurrencyCode = (code: CurrencyCode) => {
    setCurrencyCodeState(code);
    localStorage.setItem('spendwise_currency', code);
  };

  // 5. Transaction Actions (Optimistic)
  const resolveValidCategoryId = async (rawCategoryId: string | null | undefined, targetUserId: string): Promise<string | null> => {
    if (!rawCategoryId) return null;
    if (rawCategoryId.startsWith('cat-') && DEFAULT_CATEGORIES.some((d) => d.id === rawCategoryId)) {
      return rawCategoryId;
    }
    const localCat = categories.find((c) => c.id === rawCategoryId);
    if (!localCat) return rawCategoryId;
    const canonical = CANONICAL_CATEGORY_SLUGS[localCat.name.trim().toLowerCase()];
    if (canonical && localCat.is_default) return canonical;

    // Ensure custom local category exists in Supabase before linking a transaction to avoid FK failure
    if (isSupabaseLive && targetUserId && targetUserId !== 'demo-user') {
      try {
        const { data: existing } = await supabase
          .from('categories')
          .select('id')
          .eq('id', localCat.id)
          .maybeSingle();
        if (!existing) {
          const { data: inserted } = await supabase
            .from('categories')
            .upsert({
              id: localCat.id,
              user_id: localCat.user_id || targetUserId,
              name: localCat.name,
              type: localCat.type,
              icon: localCat.icon,
              color: localCat.color,
              is_default: false,
            })
            .select()
            .single();
          if (inserted?.id) return inserted.id;
        }
      } catch (e) {
        console.warn('Could not sync custom category before transaction insert:', e);
      }
    }
    return rawCategoryId;
  };

  const addTransaction = async (txData: Omit<Transaction, 'id' | 'created_at' | 'user_id'>): Promise<Transaction> => {
    const targetUserId = user?.id || (userProfile?.email && userProfile.email !== '' ? userProfile.email : 'user@spendwise.app');
    const resolvedCategoryId = await resolveValidCategoryId(txData.category_id, targetUserId);
    const tempId = `tx-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`;
    const newTx: Transaction = {
      ...txData,
      category_id: resolvedCategoryId,
      id: tempId,
      user_id: targetUserId,
      created_at: new Date().toISOString(),
    };

    // Optimistic Update
    setTransactions((prev) => [newTx, ...prev]);

    if (isSupabaseLive && targetUserId !== 'demo-user') {
      try {
        const { data, error } = await supabase
          .from('transactions')
          .insert({
            user_id: targetUserId,
            category_id: resolvedCategoryId || null,
            trip_id: txData.trip_id || null,
            type: txData.type,
            amount: txData.amount,
            date: txData.date,
            note: txData.note || null,
            payment_method: txData.payment_method,
          })
          .select()
          .single();

        if (error) {
          console.error('Supabase insert transaction error:', error.message);
        } else if (data) {
          setTransactions((prev) => prev.map((t) => (t.id === tempId ? (data as Transaction) : t)));
          return data as Transaction;
        }
      } catch (err: any) {
        console.warn('Network/Supabase error on addTransaction (saved locally):', err?.message || err);
      }
    }
    return newTx;
  };

  const updateTransaction = async (id: string, partial: Partial<Transaction>) => {
    setTransactions((prev) => prev.map((t) => (t.id === id ? { ...t, ...partial, updated_at: new Date().toISOString() } : t)));

    if (isSupabaseLive) {
      try {
        const { error } = await supabase.from('transactions').update(partial).eq('id', id);
        if (error) throw error;
      } catch (err) {
        console.error('Error updating transaction in Supabase', err);
      }
    }
  };

  const deleteTransaction = async (id: string) => {
    setTransactions((prev) => prev.filter((t) => t.id !== id));

    if (isSupabaseLive) {
      try {
        const { error } = await supabase.from('transactions').delete().eq('id', id);
        if (error) throw error;
      } catch (err) {
        console.error('Error deleting transaction from Supabase', err);
      }
    }
  };

  // 6. Category Actions
  const addCategory = async (catData: Omit<Category, 'id' | 'created_at' | 'user_id' | 'is_default'>): Promise<Category> => {
    const targetUserId = user?.id || (userProfile?.email && userProfile.email !== '' ? userProfile.email : null);
    const tempId = `cat-custom-${Date.now()}`;
    const newCat: Category = {
      ...catData,
      id: tempId,
      user_id: targetUserId,
      is_default: false,
      created_at: new Date().toISOString(),
    };

    setCategories((prev) => deduplicateCategories([...prev, newCat]));

    if (isSupabaseLive && targetUserId && targetUserId !== 'demo-user') {
      try {
        const { data, error } = await supabase
          .from('categories')
          .insert({
            user_id: targetUserId,
            name: catData.name,
            type: catData.type,
            icon: catData.icon,
            color: catData.color,
            is_default: false,
          })
          .select()
          .single();

        if (error) throw error;
        if (data) {
          setCategories((prev) => deduplicateCategories(prev.map((c) => (c.id === tempId ? (data as Category) : c))));
          return data as Category;
        }
      } catch (err) {
        console.error('Error inserting category in Supabase', err);
      }
    }
    return newCat;
  };

  const updateCategory = async (id: string, partial: Partial<Category>) => {
    setCategories((prev) => prev.map((c) => (c.id === id ? { ...c, ...partial } : c)));

    if (isSupabaseLive && user?.id) {
      try {
        const { error } = await supabase.from('categories').update(partial).eq('id', id);
        if (error) throw error;
      } catch (err) {
        console.error('Error updating category', err);
      }
    }
  };

  const deleteCategory = async (id: string) => {
    setCategories((prev) => prev.filter((c) => c.id !== id));

    if (isSupabaseLive && user?.id) {
      try {
        const { error } = await supabase.from('categories').delete().eq('id', id);
        if (error) throw error;
      } catch (err) {
        console.error('Error deleting category', err);
      }
    }
  };

  // 7. Budget Actions
  const setCategoryBudget = async (categoryId: string, month: number, year: number, amount: number) => {
    const existingIndex = budgets.findIndex(
      (b) => b.category_id === categoryId && b.month === month && b.year === year
    );

    if (existingIndex >= 0) {
      const updated = [...budgets];
      updated[existingIndex] = { ...updated[existingIndex], amount };
      setBudgets(updated);
    } else {
      const newBudget: Budget = {
        id: `budget-${Date.now()}`,
        user_id: user?.id || 'demo-user',
        category_id: categoryId,
        month,
        year,
        amount,
        created_at: new Date().toISOString(),
      };
      setBudgets((prev) => [...prev, newBudget]);
    }

    if (isSupabaseLive) {
      try {
        const targetUserId = user?.id || userProfile?.email || 'user@spendwise.app';
        const { error } = await supabase.from('budgets').upsert(
          {
            user_id: targetUserId,
            category_id: categoryId,
            month,
            year,
            amount,
          },
          { onConflict: 'user_id,category_id,month,year' }
        );
        if (error) throw error;
      } catch (err) {
        console.error('Error upserting budget in Supabase', err);
      }
    }
  };

  const deleteBudget = async (id: string) => {
    setBudgets((prev) => prev.filter((b) => b.id !== id));

    if (isSupabaseLive) {
      try {
        const { error } = await supabase.from('budgets').delete().eq('id', id);
        if (error) throw error;
      } catch (err) {
        console.error('Error deleting budget', err);
      }
    }
  };

  // 8. Trip Actions
  const addTrip = async (tripData: Omit<Trip, 'id' | 'created_at' | 'user_id'>): Promise<Trip> => {
    const targetUserId = user?.id || userProfile?.email || 'user@spendwise.app';
    const tempId = `trip-${Date.now()}`;
    const newTrip: Trip = {
      ...tripData,
      id: tempId,
      user_id: targetUserId,
      created_at: new Date().toISOString(),
    };

    setTrips((prev) => [newTrip, ...prev]);

    if (isSupabaseLive) {
      try {
        const { data, error } = await supabase
          .from('trips')
          .insert({
            user_id: targetUserId,
            destination: tripData.destination,
            start_date: tripData.start_date,
            end_date: tripData.end_date,
            budget: tripData.budget,
            notes: tripData.notes,
          })
          .select()
          .single();

        if (error) throw error;
        if (data) {
          setTrips((prev) => prev.map((t) => (t.id === tempId ? (data as Trip) : t)));
          return data as Trip;
        }
      } catch (err) {
        console.error('Error inserting trip in Supabase', err);
      }
    }
    return newTrip;
  };

  const updateTrip = async (id: string, partial: Partial<Trip>) => {
    setTrips((prev) => prev.map((t) => (t.id === id ? { ...t, ...partial } : t)));

    if (isSupabaseLive) {
      try {
        const { error } = await supabase.from('trips').update(partial).eq('id', id);
        if (error) throw error;
      } catch (err) {
        console.error('Error updating trip in Supabase', err);
      }
    }
  };

  const deleteTrip = async (id: string) => {
    setTrips((prev) => prev.filter((t) => t.id !== id));

    if (isSupabaseLive) {
      try {
        const { error } = await supabase.from('trips').delete().eq('id', id);
        if (error) throw error;
      } catch (err) {
        console.error('Error deleting trip from Supabase', err);
      }
    }
  };

  // 9. CSV & Excel Export
  const exportToCSV = (transactionsToExport = transactions, periodTitle = 'Financial_Report') => {
    const categoryMap = new Map(categories.map((c) => [c.id, c.name]));
    const tripMap = new Map(trips.map((t) => [t.id, t.destination]));

    let totalIncome = 0;
    let totalExpense = 0;

    const headers = ['Date', 'Type', 'Category', 'Trip', 'Amount', 'Currency', 'Payment Method', 'Notes'];
    const rows = transactionsToExport.map((t) => {
      if (t.type === 'income') totalIncome += t.amount;
      else totalExpense += t.amount;

      const cat = (t.category_id ? categoryMap.get(t.category_id) || 'General' : 'General').replace(/"/g, '""');
      const trip = (t.trip_id ? tripMap.get(t.trip_id) || '-' : '-').replace(/"/g, '""');
      const note = (t.note || '').replace(/"/g, '""');

      return [
        t.date,
        t.type === 'income' ? 'Income' : 'Expense',
        `"${cat}"`,
        `"${trip}"`,
        t.amount.toFixed(2),
        `"${currency.symbol}"`,
        t.payment_method.toUpperCase(),
        `"${note}"`,
      ].join(',');
    });

    const net = totalIncome - totalExpense;
    const summaryRows = [
      '',
      '--- Financial Summary ---',
      `Report Period,"${periodTitle}"`,
      `Total Inflow,${totalIncome.toFixed(2)}`,
      `Total Outflow,${totalExpense.toFixed(2)}`,
      `Net Balance,${net.toFixed(2)}`,
      `Total Transactions,${transactionsToExport.length}`,
    ];

    // Prepend UTF-8 BOM \uFEFF so Excel opens UTF-8 symbols and currency cleanly
    const csvContent = '\uFEFF' + [headers.join(','), ...rows, ...summaryRows].join('\r\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', `Spendwise_${periodTitle.replace(/\s+/g, '_')}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  // 10. Excel (.xls) Export
  const exportToExcel = (transactionsToExport = transactions, periodTitle = 'Financial Statement') => {
    const categoryMap = new Map(categories.map((c) => [c.id, c.name]));
    const tripMap = new Map(trips.map((t) => [t.id, t.destination]));

    let totalIncome = 0;
    let totalExpense = 0;
    const catSpendMap = new Map<string, number>();

    transactionsToExport.forEach((t) => {
      const cat = t.category_id ? categoryMap.get(t.category_id) || 'Uncategorized' : 'None';
      if (t.type === 'income') {
        totalIncome += t.amount;
      } else {
        totalExpense += t.amount;
        catSpendMap.set(cat, (catSpendMap.get(cat) || 0) + t.amount);
      }
    });

    const netSavings = totalIncome - totalExpense;
    const savingsRate = totalIncome > 0 ? Math.round((netSavings / totalIncome) * 100) : 0;
    const generatedAt = new Date().toISOString().replace('T', ' ').substring(0, 16);

    const escapeXml = (s: string) => (s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

    let categoryRows = '';
    Array.from(catSpendMap.entries()).sort((a, b) => b[1] - a[1]).forEach(([name, spent]) => {
      const pct = totalExpense > 0 ? Math.round((spent / totalExpense) * 100) : 0;
      categoryRows += `   <Row>
    <Cell ss:StyleID="Bold"><Data ss:Type="String">${escapeXml(name)}</Data></Cell>
    <Cell ss:StyleID="CenterCell"><Data ss:Type="String">Expense</Data></Cell>
    <Cell ss:StyleID="Expense"><Data ss:Type="Number">${spent}</Data></Cell>
    <Cell ss:StyleID="NumberCell"><Data ss:Type="String">${pct}%</Data></Cell>
   </Row>\n`;
    });

    let transactionRows = '';
    transactionsToExport.forEach((t) => {
      const cat = escapeXml(t.category_id ? categoryMap.get(t.category_id) || 'General' : 'General');
      const trip = escapeXml(t.trip_id ? tripMap.get(t.trip_id) || '-' : '-');
      const note = escapeXml(t.note || '');
      const typeStyle = t.type === 'income' ? 'Income' : 'Expense';
      transactionRows += `   <Row>
    <Cell ss:StyleID="CenterCell"><Data ss:Type="String">${t.date}</Data></Cell>
    <Cell ss:StyleID="CenterCell"><Data ss:Type="String">${t.type.toUpperCase()}</Data></Cell>
    <Cell><Data ss:Type="String">${cat}</Data></Cell>
    <Cell><Data ss:Type="String">${trip}</Data></Cell>
    <Cell ss:StyleID="${typeStyle}"><Data ss:Type="Number">${t.amount}</Data></Cell>
    <Cell ss:StyleID="CenterCell"><Data ss:Type="String">${t.payment_method.toUpperCase()}</Data></Cell>
    <Cell><Data ss:Type="String">${note}</Data></Cell>
   </Row>\n`;
    });

    const xml = `<?xml version="1.0" encoding="UTF-8"?>
<?mso-application progid="Excel.Sheet"?>
<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"
 xmlns:o="urn:schemas-microsoft-com:office:office"
 xmlns:x="urn:schemas-microsoft-com:office:excel"
 xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
 xmlns:html="http://www.w3.org/TR/REC-html40">
 <DocumentProperties xmlns="urn:schemas-microsoft-com:office:office">
  <Author>Spendwise</Author>
  <Created>${generatedAt}</Created>
 </DocumentProperties>
 <Styles>
  <Style ss:ID="Default" ss:Name="Normal">
   <Alignment ss:Vertical="Center"/>
   <Font ss:FontName="Segoe UI" ss:Size="10" ss:Color="#1E293B"/>
  </Style>
  <Style ss:ID="Title">
   <Font ss:FontName="Segoe UI" ss:Size="16" ss:Bold="1" ss:Color="#4F46E5"/>
  </Style>
  <Style ss:ID="Header">
   <Font ss:FontName="Segoe UI" ss:Size="11" ss:Bold="1" ss:Color="#FFFFFF"/>
   <Interior ss:Color="#4F46E5" ss:Pattern="Solid"/>
   <Alignment ss:Horizontal="Center"/>
  </Style>
  <Style ss:ID="SectionHeader">
   <Font ss:FontName="Segoe UI" ss:Size="12" ss:Bold="1" ss:Color="#1E293B"/>
   <Interior ss:Color="#EEF2FF" ss:Pattern="Solid"/>
  </Style>
  <Style ss:ID="Bold">
   <Font ss:FontName="Segoe UI" ss:Size="10" ss:Bold="1"/>
  </Style>
  <Style ss:ID="Income">
   <Font ss:FontName="Segoe UI" ss:Size="10" ss:Bold="1" ss:Color="#10B981"/>
   <Alignment ss:Horizontal="Right"/>
  </Style>
  <Style ss:ID="Expense">
   <Font ss:FontName="Segoe UI" ss:Size="10" ss:Bold="1" ss:Color="#EF4444"/>
   <Alignment ss:Horizontal="Right"/>
  </Style>
  <Style ss:ID="NumberCell"><Alignment ss:Horizontal="Right"/></Style>
  <Style ss:ID="CenterCell"><Alignment ss:Horizontal="Center"/></Style>
 </Styles>
 <Worksheet ss:Name="Financial Summary">
  <Table ss:DefaultRowHeight="20">
   <Column ss:Width="160"/>
   <Column ss:Width="140"/>
   <Column ss:Width="120"/>
   <Row ss:Height="28"><Cell ss:StyleID="Title"><Data ss:Type="String">Spendwise Financial Statement</Data></Cell></Row>
   <Row><Cell><Data ss:Type="String">Period: ${escapeXml(periodTitle)} | Generated: ${generatedAt}</Data></Cell></Row>
   <Row><Cell><Data ss:Type="String"></Data></Cell></Row>
   <Row ss:Height="22">
    <Cell ss:StyleID="SectionHeader"><Data ss:Type="String">Executive Summary</Data></Cell>
    <Cell ss:StyleID="SectionHeader"><Data ss:Type="String">Amount (${currency.symbol})</Data></Cell>
    <Cell ss:StyleID="SectionHeader"><Data ss:Type="String">Status</Data></Cell>
   </Row>
   <Row>
    <Cell ss:StyleID="Bold"><Data ss:Type="String">Total Inflow (Income)</Data></Cell>
    <Cell ss:StyleID="Income"><Data ss:Type="Number">${totalIncome}</Data></Cell>
    <Cell ss:StyleID="CenterCell"><Data ss:Type="String">Credit</Data></Cell>
   </Row>
   <Row>
    <Cell ss:StyleID="Bold"><Data ss:Type="String">Total Outflow (Expenses)</Data></Cell>
    <Cell ss:StyleID="Expense"><Data ss:Type="Number">${totalExpense}</Data></Cell>
    <Cell ss:StyleID="CenterCell"><Data ss:Type="String">Debit</Data></Cell>
   </Row>
   <Row>
    <Cell ss:StyleID="Bold"><Data ss:Type="String">Net Savings Balance</Data></Cell>
    <Cell ss:StyleID="${netSavings >= 0 ? 'Income' : 'Expense'}"><Data ss:Type="Number">${netSavings}</Data></Cell>
    <Cell ss:StyleID="CenterCell"><Data ss:Type="String">${netSavings >= 0 ? 'Surplus' : 'Deficit'}</Data></Cell>
   </Row>
   <Row>
    <Cell ss:StyleID="Bold"><Data ss:Type="String">Savings Rate</Data></Cell>
    <Cell ss:StyleID="NumberCell"><Data ss:Type="String">${savingsRate}%</Data></Cell>
    <Cell ss:StyleID="CenterCell"><Data ss:Type="String">${savingsRate >= 20 ? 'Healthy' : 'Needs Attention'}</Data></Cell>
   </Row>
   <Row><Cell><Data ss:Type="String"></Data></Cell></Row>
   <Row ss:Height="22">
    <Cell ss:StyleID="SectionHeader"><Data ss:Type="String">Category Breakdown</Data></Cell>
    <Cell ss:StyleID="SectionHeader"><Data ss:Type="String">Type</Data></Cell>
    <Cell ss:StyleID="SectionHeader"><Data ss:Type="String">Spent (${currency.symbol})</Data></Cell>
    <Cell ss:StyleID="SectionHeader"><Data ss:Type="String">Share (%)</Data></Cell>
   </Row>
${categoryRows}
  </Table>
 </Worksheet>
 <Worksheet ss:Name="Transactions Ledger">
  <Table ss:DefaultRowHeight="20">
   <Column ss:Width="90"/>
   <Column ss:Width="80"/>
   <Column ss:Width="140"/>
   <Column ss:Width="120"/>
   <Column ss:Width="100"/>
   <Column ss:Width="110"/>
   <Column ss:Width="220"/>
   <Row ss:Height="24">
    <Cell ss:StyleID="Header"><Data ss:Type="String">Date</Data></Cell>
    <Cell ss:StyleID="Header"><Data ss:Type="String">Type</Data></Cell>
    <Cell ss:StyleID="Header"><Data ss:Type="String">Category</Data></Cell>
    <Cell ss:StyleID="Header"><Data ss:Type="String">Trip / Event</Data></Cell>
    <Cell ss:StyleID="Header"><Data ss:Type="String">Amount (${currency.symbol})</Data></Cell>
    <Cell ss:StyleID="Header"><Data ss:Type="String">Payment Method</Data></Cell>
    <Cell ss:StyleID="Header"><Data ss:Type="String">Notes</Data></Cell>
   </Row>
${transactionRows}
  </Table>
 </Worksheet>
</Workbook>`;

    const blob = new Blob([xml], { type: 'application/vnd.ms-excel;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', `Spendwise_${periodTitle.replace(/\s+/g, '_')}.xls`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const updateUserProfile = useCallback(async (updates: Partial<UserProfile>) => {
    setUserProfile((prev) => {
      const name = (updates.name !== undefined ? updates.name : prev.name).trim() || 'Alex Morgan';
      const email = (updates.email !== undefined ? updates.email : prev.email).trim() || 'alex.morgan@spendwise.app';
      const initials = updates.initials?.trim() || name.split(' ').filter(Boolean).slice(0, 2).map((s: string) => s[0].toUpperCase()).join('') || 'SW';
      const bio = updates.bio !== undefined ? updates.bio : prev.bio;
      const updated: UserProfile = { name, email, initials, bio };
      try {
        localStorage.setItem('spendwise_user_profile', JSON.stringify(updated));
      } catch (err) {
        console.error('Failed to save profile to localStorage', err);
      }
      return updated;
    });

    if (isSupabaseLive && user?.id) {
      try {
        await supabase.auth.updateUser({
          data: {
            full_name: updates.name,
            bio: updates.bio,
          },
        });
      } catch (err) {
        console.warn('Could not sync profile to Supabase user metadata', err);
      }
    }
  }, [isSupabaseLive, user, supabase]);

  // Enrich transactions with category and trip objects
  const enrichedTransactions = useMemo(() => {
    const categoryMap = new Map(categories.map((c) => [c.id, c]));
    const tripMap = new Map(trips.map((t) => [t.id, t]));

    return transactions.map((t) => ({
      ...t,
      category: t.category_id ? categoryMap.get(t.category_id) || null : null,
      trip: t.trip_id ? tripMap.get(t.trip_id) || null : null,
    }));
  }, [transactions, categories, trips]);

  const login = useCallback((email: string, name?: string) => {
    const cleanName = name || email.split('@')[0].replace('.', ' ')
      .split(' ')
      .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
      .join(' ') || 'User';
    const initials = cleanName
      .split(' ')
      .slice(0, 2)
      .map((s) => s[0].toUpperCase())
      .join('') || 'SW';
    const profile = { name: cleanName, email, initials, bio: 'Personal Finance & Travel Budgeter' };
    setUserProfile(profile);
    setIsLoggedIn(true);
    // On first login, keep transactions, budgets, and trips empty if not in storage
    const hasStoredTxs = localStorage.getItem(`${LOCAL_STORAGE_KEY_PREFIX}transactions`);
    if (!hasStoredTxs) {
      setTransactions([]);
      setBudgets([]);
      setTrips([]);
    }
    try {
      localStorage.setItem('spendwise_user_profile', JSON.stringify(profile));
      localStorage.setItem('spendwise_is_logged_in', 'true');
    } catch (_) {}

    if (isSupabaseConfigured()) {
      fetchRemoteData(email);
    }
  }, [fetchRemoteData]);

  const logout = useCallback(async () => {
    setIsLoggedIn(false);
    setUser(null);
    setUserProfile(DEFAULT_USER_PROFILE);
    setTransactions([]);
    setBudgets([]);
    setTrips([]);
    setCategories(DEFAULT_CATEGORIES);
    try {
      localStorage.removeItem('spendwise_is_logged_in');
      localStorage.removeItem('spendwise_user_profile');
      localStorage.removeItem(`${LOCAL_STORAGE_KEY_PREFIX}transactions`);
      localStorage.removeItem(`${LOCAL_STORAGE_KEY_PREFIX}budgets`);
      localStorage.removeItem(`${LOCAL_STORAGE_KEY_PREFIX}trips`);
      localStorage.removeItem(`${LOCAL_STORAGE_KEY_PREFIX}categories`);
      if (isSupabaseConfigured()) {
        await supabase.auth.signOut();
      }
    } catch (_) {}
  }, [supabase]);

  const currency = CURRENCIES[currencyCode] || CURRENCIES.INR;

  return (
    <FinanceContext.Provider
      value={{
        transactions: enrichedTransactions,
        categories,
        budgets,
        trips,
        currency,
        setCurrencyCode,
        userProfile,
        updateUserProfile,
        user,
        isLoggedIn,
        login,
        logout,
        isSupabaseLive,
        isRealtimeConnected,
        isLoading,
        addTransaction,
        updateTransaction,
        deleteTransaction,
        addCategory,
        updateCategory,
        deleteCategory,
        setCategoryBudget,
        deleteBudget,
        addTrip,
        updateTrip,
        deleteTrip,
        exportToCSV,
        exportToExcel,
      }}
    >
      {children}
    </FinanceContext.Provider>
  );
}

export function useFinance() {
  const context = useContext(FinanceContext);
  if (!context) {
    throw new Error('useFinance must be used within a FinanceProvider');
  }
  return context;
}
