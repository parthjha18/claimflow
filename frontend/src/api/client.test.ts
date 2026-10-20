import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, request, setSessionExpiredHandler, storeTokens } from './client';

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
const tokens = (n: number) => ({ accessToken: `access-${n}`, refreshToken: `refresh-${n}` });

describe('api client', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    fetchMock.mockReset();
    localStorage.clear();
    storeTokens(tokens(1));
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    setSessionExpiredHandler(null);
  });

  it('sends the bearer token and parses JSON', async () => {
    fetchMock.mockResolvedValueOnce(json(200, { ok: true }));
    await expect(request('/api/v1/x')).resolves.toEqual({ ok: true });
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer access-1');
  });

  it('turns the backend error body into an ApiError', async () => {
    fetchMock.mockResolvedValueOnce(
      json(409, { code: 'CONCURRENT_MODIFICATION', message: 'changed', details: ['a'] }),
    );
    const err = await request('/api/v1/x').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err).toMatchObject({ status: 409, code: 'CONCURRENT_MODIFICATION', details: ['a'] });
  });

  it('refreshes once on 401 and retries with the new token', async () => {
    fetchMock
      .mockResolvedValueOnce(json(401, { code: 'UNAUTHENTICATED' }))
      .mockResolvedValueOnce(json(200, { ...tokens(2), userId: 'u', role: 'ADMIN' }))
      .mockResolvedValueOnce(json(200, { data: 1 }));
    await expect(request('/api/v1/x')).resolves.toEqual({ data: 1 });
    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/auth/refresh');
    expect(fetchMock.mock.calls[2][1].headers.Authorization).toBe('Bearer access-2');
    expect(localStorage.getItem('claimflow.refresh')).toBe('refresh-2');
  });

  it('shares ONE refresh between parallel 401s (a second use of the old token would revoke the session)', async () => {
    let refreshCalls = 0;
    fetchMock.mockImplementation(async (url: string, init: RequestInit) => {
      if (url === '/api/v1/auth/refresh') {
        refreshCalls++;
        return json(200, { ...tokens(2), userId: 'u', role: 'ADMIN' });
      }
      const auth = (init.headers as Record<string, string>).Authorization;
      return auth === 'Bearer access-2' ? json(200, { ok: true }) : json(401, { code: 'UNAUTHENTICATED' });
    });
    await Promise.all([request('/api/v1/a'), request('/api/v1/b'), request('/api/v1/c')]);
    expect(refreshCalls).toBe(1);
  });

  it('signs the user out when the refresh fails', async () => {
    const expired = vi.fn();
    setSessionExpiredHandler(expired);
    fetchMock
      .mockResolvedValueOnce(json(401, { code: 'UNAUTHENTICATED' }))
      .mockResolvedValueOnce(json(401, { code: 'INVALID_REFRESH_TOKEN' }));
    await expect(request('/api/v1/x')).rejects.toMatchObject({ code: 'SESSION_EXPIRED' });
    expect(expired).toHaveBeenCalledOnce();
    expect(localStorage.getItem('claimflow.refresh')).toBeNull();
  });

  it('does not try to refresh for anonymous calls such as login', async () => {
    fetchMock.mockResolvedValueOnce(json(401, { code: 'INVALID_CREDENTIALS', message: 'Invalid email or password' }));
    await expect(request('/api/v1/auth/login', { method: 'POST', body: {}, anonymous: true })).rejects.toMatchObject({
      code: 'INVALID_CREDENTIALS',
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('builds query strings, skipping empty values and repeating arrays', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {}));
    await request('/api/v1/reports/spend', { query: { page: 0, status: ['PAID', 'DRAFT'], from: '', to: undefined } });
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/reports/spend?page=0&status=PAID&status=DRAFT');
  });
});
