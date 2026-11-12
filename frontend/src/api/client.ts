import type { Envelope, ProblemDetail, TokenResponse } from '@/types/api';

/**
 * Central HTTP client.
 *
 * Token strategy (matches ADR-005):
 *  - the short-lived access token lives only in memory (never localStorage/sessionStorage);
 *  - the refresh token is an HttpOnly SameSite=Strict cookie the browser sends to /api/v1/auth only;
 *  - refresh/logout calls carry the X-JanSetu-Client header (CSRF defence required by the backend);
 *  - concurrent 401s share ONE in-flight refresh, then each request is retried once.
 */

export const API_BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '/api/v1';
const CLIENT_HEADER = { 'X-JanSetu-Client': 'web' };

export class ApiError extends Error {
  readonly status: number;
  readonly errorCode?: string;
  readonly fieldErrors: { field: string; message: string }[];
  readonly requestId?: string;

  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? 'Request failed');
    this.name = 'ApiError';
    this.status = problem.status;
    this.errorCode = problem.errorCode;
    this.fieldErrors = problem.fieldErrors ?? [];
    this.requestId = problem.requestId;
  }

  get isNetwork(): boolean {
    return this.status === 0;
  }
}

let accessToken: string | null = null;
let refreshInFlight: Promise<string | null> | null = null;
const sessionListeners = new Set<(event: 'expired' | 'refreshed') => void>();

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function getAccessToken(): string | null {
  return accessToken;
}

export function onSessionEvent(listener: (event: 'expired' | 'refreshed') => void): () => void {
  sessionListeners.add(listener);
  return () => sessionListeners.delete(listener);
}

/** Exchanges the refresh cookie for a new access token. Concurrent callers share one request. */
export function refreshAccessToken(): Promise<string | null> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const response = await fetch(`${API_BASE}/auth/refresh`, {
          method: 'POST',
          credentials: 'same-origin',
          headers: CLIENT_HEADER,
        });
        if (!response.ok) {
          setAccessToken(null);
          return null;
        }
        const body = (await response.json()) as Envelope<TokenResponse>;
        setAccessToken(body.data.accessToken);
        sessionListeners.forEach((l) => l('refreshed'));
        return body.data.accessToken;
      } catch {
        return null;
      } finally {
        refreshInFlight = null;
      }
    })();
  }
  return refreshInFlight;
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined | null>;
  signal?: AbortSignal;
  headers?: Record<string, string>;
  /** Skip the automatic refresh-and-retry (used by auth endpoints). */
  noRetry?: boolean;
}

export function buildUrl(path: string, query?: RequestOptions['query']): string {
  const params = new URLSearchParams();
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') params.append(key, String(value));
  });
  const qs = params.toString();
  return `${API_BASE}${path}${qs ? `?${qs}` : ''}`;
}

async function toError(response: Response): Promise<ApiError> {
  try {
    const problem = (await response.json()) as ProblemDetail;
    return new ApiError({ ...problem, status: response.status });
  } catch {
    return new ApiError({ status: response.status, detail: response.statusText || 'Request failed' });
  }
}

async function send(path: string, options: RequestOptions, token: string | null): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers };
  if (options.body !== undefined && !(options.body instanceof FormData)) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;
  try {
    return await fetch(buildUrl(path, options.query), {
      method: options.method ?? 'GET',
      headers,
      body:
        options.body === undefined ? undefined : options.body instanceof FormData ? options.body : JSON.stringify(options.body),
      credentials: 'same-origin',
      signal: options.signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw new ApiError({ status: 0, detail: 'Network error. Check your connection and try again.' });
  }
}

/** Raw response with authentication and one refresh-retry on 401. */
export async function authorizedFetch(path: string, options: RequestOptions = {}): Promise<Response> {
  let response = await send(path, options, accessToken);
  if (response.status === 401 && !options.noRetry) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      response = await send(path, options, refreshed);
    } else {
      sessionListeners.forEach((l) => l('expired'));
    }
  }
  return response;
}

/** JSON request returning the unwrapped `data` of the standard envelope. */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await authorizedFetch(path, options);
  if (!response.ok) throw await toError(response);
  if (response.status === 204) return undefined as T;
  const body = (await response.json()) as Envelope<T>;
  return body.data;
}

/** Authenticated download as a Blob (documents, CSV exports). */
export async function apiBlob(path: string, query?: RequestOptions['query']): Promise<{ blob: Blob; fileName?: string }> {
  const response = await authorizedFetch(path, { query, headers: { Accept: '*/*' } });
  if (!response.ok) throw await toError(response);
  const disposition = response.headers.get('Content-Disposition') ?? '';
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition);
  return { blob: await response.blob(), fileName: match ? decodeURIComponent(match[1]) : undefined };
}

/**
 * Multipart upload with progress (XMLHttpRequest exposes upload progress; fetch does not).
 * Ensures a fresh token first and retries once after refreshing on 401.
 */
export function uploadFile<T>(
  path: string,
  form: FormData,
  onProgress?: (percent: number) => void,
  signal?: AbortSignal,
): Promise<T> {
  const attempt = (token: string | null) =>
    new Promise<{ status: number; body: string }>((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.open('POST', buildUrl(path));
      if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`);
      xhr.setRequestHeader('Accept', 'application/json');
      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable && onProgress) onProgress(Math.round((e.loaded / e.total) * 100));
      };
      xhr.onload = () => resolve({ status: xhr.status, body: xhr.responseText });
      xhr.onerror = () => reject(new ApiError({ status: 0, detail: 'Upload failed. Check your connection and retry.' }));
      xhr.onabort = () => reject(new DOMException('Upload cancelled', 'AbortError'));
      signal?.addEventListener('abort', () => xhr.abort());
      xhr.send(form);
    });

  return (async () => {
    let result = await attempt(accessToken);
    if (result.status === 401) {
      const refreshed = await refreshAccessToken();
      if (!refreshed) {
        sessionListeners.forEach((l) => l('expired'));
      } else {
        result = await attempt(refreshed);
      }
    }
    const parsed = result.body ? JSON.parse(result.body) : {};
    if (result.status < 200 || result.status >= 300) {
      throw new ApiError({ ...(parsed as ProblemDetail), status: result.status });
    }
    return (parsed as Envelope<T>).data;
  })();
}

/** Login does not go through the refresh-retry path. */
export async function login(email: string, password: string): Promise<TokenResponse> {
  const token = await apiRequest<TokenResponse>('/auth/login', {
    method: 'POST',
    body: { email, password },
    noRetry: true,
  });
  setAccessToken(token.accessToken);
  return token;
}

export async function logout(): Promise<void> {
  try {
    await fetch(`${API_BASE}/auth/logout`, { method: 'POST', credentials: 'same-origin', headers: CLIENT_HEADER });
  } finally {
    setAccessToken(null);
  }
}
