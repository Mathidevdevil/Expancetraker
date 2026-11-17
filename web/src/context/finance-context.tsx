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
  name: 'Alex Morgan',
  email: 'alex.morgan@spendwise.app',
  initials: 'AM',
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
  exportToCSV: (transactionsToExport?: Transaction[]) => void;
}

const FinanceContext = createContext<FinanceContextType | undefined>(undefined);

const LOCAL_STORAGE_KEY_PREFIX = 'spendwise_data_';

export function FinanceProvider({ children }: { children: React.ReactNode }) {
  const [currencyCode, setCurrencyCodeState] = useState<CurrencyCode>('INR');
  const [categories, setCategories] = useState<Category[]>(DEFAULT_CATEGORIES);
  const [transactions, setTransactions] = useState<Transaction[]>(INITIAL_TRANSACTIONS);
  const [budgets, setBudgets] = useState<Budget[]>(INITIAL_BUDGETS);
  const [trips, setTrips] = useState<Trip[]>(INITIAL_TRIPS);
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

    if (!isConfigured) {
      // Load from localStorage if present, else fallback to mock seeds
      try {
        const storedCats = localStorage.getItem(`${LOCAL_STORAGE_KEY_PREFIX}categories`);
        const storedTxs = localStorage.getItem(`${LOCAL_STORAGE_KEY_PREFIX}transactions`);
        const storedBudgets = localStorage.getItem(`${LOCAL_STORAGE_KEY_PREFIX}budgets`);
        const storedTrips = localStorage.getItem(`${LOCAL_STORAGE_KEY_PREFIX}trips`);

        if (storedCats) setCategories(JSON.parse(storedCats));
        if (storedTxs) setTransactions(JSON.parse(storedTxs));
        if (storedBudgets) setBudgets(JSON.parse(storedBudgets));
        if (storedTrips) setTrips(JSON.parse(storedTrips));
      } catch (e) {
        console.error('Error reading localStorage data', e);
      }
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
          // Fallback demo user
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
      const [catsRes, tripsRes, txsRes, budgetsRes] = await Promise.all([
        supabase.from('categories').select('*').order('created_at', { ascending: true }),
        supabase.from('trips').select('*').eq('user_id', userId).order('start_date', { ascending: false }),
        supabase.from('transactions').select('*').eq('user_id', userId).order('date', { ascending: false }),
        supabase.from('budgets').select('*').eq('user_id', userId),
      ]);

      if (catsRes.data && catsRes.data.length > 0) setCategories(catsRes.data);
      if (tripsRes.data) setTrips(tripsRes.data);
      if (txsRes.data) setTransactions(txsRes.data);
      if (budgetsRes.data) setBudgets(budgetsRes.data);
    } catch (e) {
      console.error('Failed to load remote Supabase data', e);
    } finally {
      setIsLoading(false);
    }
  }, [supabase]);

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
          if (payload.eventType === 'INSERT') {
            setCategories((prev) => [...prev.filter((c) => c.id !== payload.new.id), payload.new as Category]);
          } else if (payload.eventType === 'UPDATE') {
            setCategories((prev) => prev.map((c) => (c.id === payload.new.id ? (payload.new as Category) : c)));
          } else if (payload.eventType === 'DELETE') {
            setCategories((prev) => prev.filter((c) => c.id !== payload.old.id));
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

  // 4. Local Persistence for Offline / Demo Mode
  useEffect(() => {
    if (!isSupabaseLive) {
      try {
        localStorage.setItem(`${LOCAL_STORAGE_KEY_PREFIX}categories`, JSON.stringify(categories));
        localStorage.setItem(`${LOCAL_STORAGE_KEY_PREFIX}transactions`, JSON.stringify(transactions));
        localStorage.setItem(`${LOCAL_STORAGE_KEY_PREFIX}budgets`, JSON.stringify(budgets));
        localStorage.setItem(`${LOCAL_STORAGE_KEY_PREFIX}trips`, JSON.stringify(trips));
      } catch (e) {
        console.error('Failed to sync to localStorage', e);
      }
    }
  }, [categories, transactions, budgets, trips, isSupabaseLive]);

  const setCurrencyCode = (code: CurrencyCode) => {
    setCurrencyCodeState(code);
    localStorage.setItem('spendwise_currency', code);
  };

  // 5. Transaction Actions (Optimistic)
  const addTransaction = async (txData: Omit<Transaction, 'id' | 'created_at' | 'user_id'>): Promise<Transaction> => {
    const tempId = `tx-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`;
    const newTx: Transaction = {
      ...txData,
      id: tempId,
      user_id: user?.id || 'demo-user',
      created_at: new Date().toISOString(),
    };

    // Optimistic Update
    setTransactions((prev) => [newTx, ...prev]);

    if (isSupabaseLive && user?.id) {
      try {
        const { data, error } = await supabase
          .from('transactions')
          .insert({
            user_id: user.id,
            category_id: txData.category_id,
            trip_id: txData.trip_id || null,
            type: txData.type,
            amount: txData.amount,
            date: txData.date,
            note: txData.note,
            payment_method: txData.payment_method,
          })
          .select()
          .single();

        if (error) throw error;
        if (data) {
          setTransactions((prev) => prev.map((t) => (t.id === tempId ? (data as Transaction) : t)));
          return data as Transaction;
        }
      } catch (err) {
        console.error('Error inserting transaction in Supabase', err);
      }
    }
    return newTx;
  };

  const updateTransaction = async (id: string, partial: Partial<Transaction>) => {
    setTransactions((prev) => prev.map((t) => (t.id === id ? { ...t, ...partial, updated_at: new Date().toISOString() } : t)));

    if (isSupabaseLive && user?.id) {
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

    if (isSupabaseLive && user?.id) {
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
    const tempId = `cat-${Date.now()}`;
    const newCat: Category = {
      ...catData,
      id: tempId,
      user_id: user?.id || null,
      is_default: false,
      created_at: new Date().toISOString(),
    };

    setCategories((prev) => [...prev, newCat]);

    if (isSupabaseLive && user?.id) {
      try {
        const { data, error } = await supabase
          .from('categories')
          .insert({
            user_id: user.id,
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
          setCategories((prev) => prev.map((c) => (c.id === tempId ? (data as Category) : c)));
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

    if (isSupabaseLive && user?.id) {
      try {
        const { error } = await supabase.from('budgets').upsert(
          {
            user_id: user.id,
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

    if (isSupabaseLive && user?.id) {
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
    const tempId = `trip-${Date.now()}`;
    const newTrip: Trip = {
      ...tripData,
      id: tempId,
      user_id: user?.id || 'demo-user',
      created_at: new Date().toISOString(),
    };

    setTrips((prev) => [newTrip, ...prev]);

    if (isSupabaseLive && user?.id) {
      try {
        const { data, error } = await supabase
          .from('trips')
          .insert({
            user_id: user.id,
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

    if (isSupabaseLive && user?.id) {
      try {
        const { error } = await supabase.from('trips').update(partial).eq('id', id);
        if (error) throw error;
      } catch (err) {
        console.error('Error updating trip', err);
      }
    }
  };

  const deleteTrip = async (id: string) => {
    setTrips((prev) => prev.filter((t) => t.id !== id));

    if (isSupabaseLive && user?.id) {
      try {
        const { error } = await supabase.from('trips').delete().eq('id', id);
        if (error) throw error;
      } catch (err) {
        console.error('Error deleting trip', err);
      }
    }
  };

  // 9. CSV Export
  const exportToCSV = (transactionsToExport = transactions) => {
    const categoryMap = new Map(categories.map((c) => [c.id, c.name]));
    const tripMap = new Map(trips.map((t) => [t.id, t.destination]));

    const headers = ['ID', 'Date', 'Type', 'Category', 'Amount', 'Payment Method', 'Trip', 'Notes'];
    const rows = transactionsToExport.map((t) => [
      t.id,
      t.date,
      t.type.toUpperCase(),
      t.category_id ? categoryMap.get(t.category_id) || 'Uncategorized' : 'None',
      t.amount,
      t.payment_method.toUpperCase(),
      t.trip_id ? `"${tripMap.get(t.trip_id) || 'Trip'}"` : '',
      `"${(t.note || '').replace(/"/g, '""')}"`,
    ]);

    const csvContent = [headers.join(','), ...rows.map((r) => r.join(','))].join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', `Spendwise_Export_${new Date().toISOString().split('T')[0]}.csv`);
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
    try {
      localStorage.setItem('spendwise_user_profile', JSON.stringify(profile));
      localStorage.setItem('spendwise_is_logged_in', 'true');
    } catch (_) {}
  }, []);

  const logout = useCallback(async () => {
    setIsLoggedIn(false);
    setUser(null);
    try {
      localStorage.removeItem('spendwise_is_logged_in');
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
