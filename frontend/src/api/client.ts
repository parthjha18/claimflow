import type { TokenResponse } from './types';

const REFRESH_KEY = 'claimflow.refresh';

/** Error body returned by the backend for every non-2xx response. */
export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public details: string[] = [],
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

// The access token lives only in memory (not readable after a page reload, and not in localStorage where any
// injected script could lift it). The longer-lived refresh token is kept in localStorage so a reload can sign
// the user back in.
let accessToken: string | null = null;
let onSessionExpired: (() => void) | null = null;
let refreshing: Promise<TokenResponse> | null = null;

export function setSessionExpiredHandler(handler: (() => void) | null) {
  onSessionExpired = handler;
}

export function storeTokens(tokens: Pick<TokenResponse, 'accessToken' | 'refreshToken'> | null) {
  accessToken = tokens?.accessToken ?? null;
  try {
    if (tokens) localStorage.setItem(REFRESH_KEY, tokens.refreshToken);
    else localStorage.removeItem(REFRESH_KEY);
  } catch {
    /* storage unavailable (private mode): the session just will not survive a reload */
  }
}

export function getRefreshToken(): string | null {
  try {
    return localStorage.getItem(REFRESH_KEY);
  } catch {
    return null;
  }
}

export function hasStoredSession(): boolean {
  try {
    return !!localStorage.getItem(REFRESH_KEY);
  } catch {
    return false;
  }
}

/**
 * Trades the stored refresh token for a new token pair.
 *
 * Refresh tokens rotate and the server treats re-use of an old one as theft (it revokes the whole chain), so
 * two overlapping refreshes would log the user out. We therefore (1) share one in-flight request between all
 * callers in this tab (also covers React StrictMode's double effects) and (2) use the Web Locks API so other
 * tabs queue behind us and then pick up the token we just stored.
 */
export function refreshSession(): Promise<TokenResponse> {
  if (!refreshing) {
    refreshing = withCrossTabLock(doRefresh).finally(() => {
      refreshing = null;
    });
  }
  return refreshing;
}

async function withCrossTabLock<T>(fn: () => Promise<T>): Promise<T> {
  const locks = typeof navigator !== 'undefined' ? navigator.locks : undefined;
  return locks ? locks.request('claimflow-refresh', fn) : fn();
}

async function doRefresh(): Promise<TokenResponse> {
  let token: string | null = null;
  try {
    token = localStorage.getItem(REFRESH_KEY); // read inside the lock: another tab may have rotated it
  } catch {
    token = null;
  }
  if (!token) throw expire();

  const res = await fetch('/api/v1/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: token }),
  });
  if (!res.ok) throw expire();
  const tokens = (await res.json()) as TokenResponse;
  storeTokens(tokens);
  return tokens;
}

function expire(): ApiError {
  storeTokens(null);
  onSessionExpired?.();
  return new ApiError(401, 'SESSION_EXPIRED', 'Your session has expired. Please sign in again.');
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  query?: object;
  body?: unknown;
  form?: FormData;
  idempotencyKey?: string;
  /** Do not attach a token or try to refresh (login, register). */
  anonymous?: boolean;
  as?: 'json' | 'blob';
}

function buildUrl(path: string, query?: RequestOptions['query']): string {
  if (!query) return path;
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query) as [string, unknown][]) {
    if (value === undefined || value === null || value === '') continue;
    if (Array.isArray(value)) value.forEach((v) => params.append(key, String(v)));
    else params.set(key, String(value));
  }
  const qs = params.toString();
  return qs ? `${path}?${qs}` : path;
}

function send(path: string, opts: RequestOptions): Promise<Response> {
  const headers: Record<string, string> = {};
  let body: BodyInit | undefined;
  if (opts.form) {
    body = opts.form; // the browser sets the multipart boundary itself
  } else if (opts.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(opts.body);
  }
  if (!opts.anonymous && accessToken) headers.Authorization = `Bearer ${accessToken}`;
  if (opts.idempotencyKey) headers['Idempotency-Key'] = opts.idempotencyKey;
  return fetch(buildUrl(path, opts.query), { method: opts.method ?? 'GET', headers, body });
}

async function toApiError(res: Response): Promise<ApiError> {
  try {
    const b = await res.json();
    return new ApiError(res.status, b.code ?? 'ERROR', b.message ?? res.statusText, b.details ?? []);
  } catch {
    return new ApiError(res.status, 'ERROR', res.statusText || 'Request failed');
  }
}

export async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  let res = await send(path, opts);
  if (res.status === 401 && !opts.anonymous) {
    await refreshSession(); // throws SESSION_EXPIRED (and signs the user out) if it cannot
    res = await send(path, opts);
  }
  if (!res.ok) throw await toApiError(res);
  if (res.status === 204) return undefined as T;
  return (opts.as === 'blob' ? await res.blob() : await res.json()) as T;
}

/** A fresh key per user action: retries of that action reuse it, a new click gets a new one. */
export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}
