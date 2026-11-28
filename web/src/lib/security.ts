/**
 * Sanitizes OAuth redirect targets so open-redirect payloads like //evil.com,
 * /\evil.com, or external https:// URLs fall back cleanly to '/'.
 */
export function sanitizeRedirectPath(rawNext: string | null | undefined): string {
  if (!rawNext || typeof rawNext !== 'string') return '/';
  const trimmed = rawNext.trim();
  if (/^\/(?!\/|\\)/.test(trimmed)) {
    return trimmed;
  }
  return '/';
}
