import { getAccessToken } from './auth';

const API_BASE = '/api';

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
    this.name = 'ApiError';
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getAccessToken();

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });

  if (!response.ok) {
    throw new ApiError(response.status, await response.text());
  }

  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

async function requestMultipart<T>(path: string, formData: FormData): Promise<T> {
  const token = getAccessToken();
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    body: formData,
    // DO NOT set Content-Type -- browser sets multipart/form-data with boundary automatically
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!response.ok) throw new ApiError(response.status, await response.text());
  return response.json() as Promise<T>;
}

export const apiClient = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body: unknown) => request<T>(path, {
    method: 'POST',
    body: JSON.stringify(body),
  }),
  put: <T>(path: string, body: unknown) => request<T>(path, {
    method: 'PUT',
    body: JSON.stringify(body),
  }),
  patch: <T>(path: string, body: unknown) => request<T>(path, {
    method: 'PATCH',
    body: JSON.stringify(body),
  }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
  upload: <T>(path: string, formData: FormData): Promise<T> => requestMultipart<T>(path, formData),
};
