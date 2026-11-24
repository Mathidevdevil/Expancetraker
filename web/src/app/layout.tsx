import type { Metadata, Viewport } from 'next';
import { Plus_Jakarta_Sans } from 'next/font/google';
import localFont from 'next/font/local';
import './globals.css';
import { ThemeProvider } from '@/components/theme-provider';
import { FinanceProvider } from '@/context/finance-context';
import { AppShell } from '@/components/navigation';

const plusJakartaSans = Plus_Jakarta_Sans({
  subsets: ['latin'],
  weight: ['400', '500', '600', '700', '800'],
  variable: '--font-plus-jakarta-sans',
  display: 'swap',
});

const sfPro = localFont({
  src: [
    {
      path: '../fonts/sf_pro_regular.otf',
      weight: '400',
      style: 'normal',
    },
    {
      path: '../fonts/sf_pro_medium.otf',
      weight: '500',
      style: 'normal',
    },
    {
      path: '../fonts/sf_pro_semibold.otf',
      weight: '600',
      style: 'normal',
    },
    {
      path: '../fonts/sf_pro_bold.otf',
      weight: '700',
      style: 'normal',
    },
    {
      path: '../fonts/sf_pro_heavy.otf',
      weight: '800',
      style: 'normal',
    },
  ],
  variable: '--font-sf-pro',
  display: 'swap',
});

export const metadata: Metadata = {
  title: 'Spendwise — Personal Finance & Travel Tracker',
  description: 'Manage income, expenses, budgets, and travel plans across web and Android.',
};

export const viewport: Viewport = {
  themeColor: '#0D0F1F',
  width: 'device-width',
  initialScale: 1,
  maximumScale: 1,
  userScalable: false,
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning className={`${plusJakartaSans.variable} ${sfPro.variable}`}>
      <body className={`${plusJakartaSans.className} antialiased font-sans selection:bg-[#E7B94F]/30 selection:text-[#E7B94F]`}>
        <ThemeProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange>
          <FinanceProvider>
            <AppShell>{children}</AppShell>
          </FinanceProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
