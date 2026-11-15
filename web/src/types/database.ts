export type TransactionType = 'expense' | 'income';

export type PaymentMethod = 'cash' | 'upi' | 'card' | 'bank_transfer' | 'net_banking';

export interface Category {
  id: string;
  user_id: string | null;
  name: string;
  type: TransactionType;
  icon: string;
  color: string;
  is_default: boolean;
  created_at: string;
  updated_at?: string;
}

export interface Trip {
  id: string;
  user_id: string;
  destination: string;
  start_date: string;
  end_date: string;
  budget: number;
  notes?: string | null;
  created_at: string;
  updated_at?: string;
}

export interface Transaction {
  id: string;
  user_id: string;
  category_id: string | null;
  trip_id?: string | null;
  type: TransactionType;
  amount: number;
  date: string; // YYYY-MM-DD
  note?: string | null;
  payment_method: PaymentMethod;
  created_at: string;
  updated_at?: string;
  // Joined relation fields for UI
  category?: Category | null;
  trip?: Trip | null;
}

export interface Budget {
  id: string;
  user_id: string;
  category_id: string;
  month: number; // 1-12
  year: number;
  amount: number;
  created_at: string;
  updated_at?: string;
  // Joined relation fields
  category?: Category | null;
}

export type CurrencyCode = 'INR' | 'USD' | 'EUR' | 'GBP';

export interface CurrencyConfig {
  code: CurrencyCode;
  symbol: string;
  name: string;
}

export const CURRENCIES: Record<CurrencyCode, CurrencyConfig> = {
  INR: { code: 'INR', symbol: '₹', name: 'Indian Rupee' },
  USD: { code: 'USD', symbol: '$', name: 'US Dollar' },
  EUR: { code: 'EUR', symbol: '€', name: 'Euro' },
  GBP: { code: 'GBP', symbol: '£', name: 'British Pound' },
};
