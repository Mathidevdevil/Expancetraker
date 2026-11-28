import { NextResponse, type NextRequest } from 'next/server';
import { createServerClient } from '@supabase/ssr';

export async function middleware(request: NextRequest) {
  let response = NextResponse.next({ request });

  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  if (!url || !anonKey || url.includes('your-project-ref') || url.includes('placeholder')) {
    return response;
  }

  const isProd = process.env.NODE_ENV === 'production';

  const supabase = createServerClient(url, anonKey, {
    cookies: {
      getAll() {
        return request.cookies.getAll();
      },
      setAll(cookiesToSet: { name: string; value: string; options?: Record<string, unknown> }[]) {
        cookiesToSet.forEach(({ name, value }) => {
          request.cookies.set(name, value);
        });
        response = NextResponse.next({ request });
        cookiesToSet.forEach(({ name, value, options }) => {
          response.cookies.set(name, value, {
            ...options,
            sameSite: 'lax',
            secure: isProd,
            path: '/',
          });
        });
      },
    },
  });

  // Refresh Supabase session token cleanly without forcing redirects or breaking Demo Mode
  try {
    const {
      data: { user },
    } = await supabase.auth.getUser();

    if (user) {
      response.cookies.set('spendwise_ssr_auth', '1', {
        httpOnly: true,
        secure: isProd,
        sameSite: 'lax',
        path: '/',
        maxAge: 60 * 60 * 24 * 7,
      });
    } else if (request.cookies.has('spendwise_ssr_auth')) {
      response.cookies.delete('spendwise_ssr_auth');
    }
  } catch {
    // Preserve existing offline / client-side session cleanly
  }

  return response;
}

export const config = {
  matcher: [
    '/((?!_next/static|_next/image|favicon.ico|icon.png|spendwise-icon.svg|.*\\.(?:svg|png|jpg|jpeg|gif|webp|otf|woff2)$).*)',
  ],
};
