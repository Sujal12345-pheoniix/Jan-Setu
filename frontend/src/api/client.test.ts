import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ApiError, apiRequest, onSessionEvent, setAccessToken, getAccessToken } from './client';

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('api client', () => {
  beforeEach(() => setAccessToken('old-token'));

  it('unwraps the data envelope and sends the bearer token', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(200, { data: { ok: true }, meta: { requestId: 'r1' } }));
    vi.stubGlobal('fetch', fetchMock);
    await expect(apiRequest('/things')).resolves.toEqual({ ok: true });
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer old-token');
  });

  it('maps problem details to ApiError with field errors and request id', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(400, {
      status: 400, detail: 'One or more fields are invalid.', errorCode: 'VALIDATION_ERROR', requestId: 'req-9',
      fieldErrors: [{ field: 'email', message: 'must be a well-formed email address' }],
    })));
    const error = (await apiRequest('/x').catch((e: unknown) => e)) as ApiError;
    expect(error).toBeInstanceOf(ApiError);
    expect(error.errorCode).toBe('VALIDATION_ERROR');
    expect(error.fieldErrors[0].field).toBe('email');
    expect(error.requestId).toBe('req-9');
  });

  it('coordinates a single refresh for concurrent 401s and retries each request once', async () => {
    let refreshCalls = 0;
    const fetchMock = vi.fn(async (url: string, init: RequestInit) => {
      if (url.endsWith('/auth/refresh')) {
        refreshCalls++;
        expect((init.headers as Record<string, string>)['X-JanSetu-Client']).toBe('web');
        await new Promise((r) => setTimeout(r, 10));
        return json(200, { data: { accessToken: 'new-token' }, meta: {} });
      }
      const auth = (init.headers as Record<string, string>).Authorization;
      return auth === 'Bearer new-token' ? json(200, { data: url, meta: {} }) : json(401, { status: 401, errorCode: 'INVALID_TOKEN' });
    });
    vi.stubGlobal('fetch', fetchMock);
    const results = await Promise.all([apiRequest('/a'), apiRequest('/b'), apiRequest('/c')]);
    expect(results).toEqual(['/api/v1/a', '/api/v1/b', '/api/v1/c']);
    expect(refreshCalls).toBe(1);
    expect(getAccessToken()).toBe('new-token');
  });

  it('emits a session-expired event when the refresh fails', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) =>
      url.endsWith('/auth/refresh') ? json(401, { status: 401 }) : json(401, { status: 401, errorCode: 'INVALID_TOKEN' })));
    const listener = vi.fn();
    const off = onSessionEvent(listener);
    await expect(apiRequest('/secure')).rejects.toBeInstanceOf(ApiError);
    expect(listener).toHaveBeenCalledWith('expired');
    expect(getAccessToken()).toBeNull();
    off();
  });

  it('reports network failures as status 0', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    const error = (await apiRequest('/x').catch((e: unknown) => e)) as ApiError;
    expect(error.isNetwork).toBe(true);
  });

  it('never stores tokens in web storage', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(200, { data: { accessToken: 'tok' }, meta: {} })));
    await apiRequest('/auth/login', { method: 'POST', body: {} });
    expect(JSON.stringify({ ...localStorage })).not.toContain('tok');
    expect(JSON.stringify({ ...sessionStorage })).not.toContain('tok');
  });
});
