import type { CompletedTorrent, DiskInfo, Settings, SystemInfo, Torrent } from '../types'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:9090'

function getToken(): string | null {
  return localStorage.getItem('token')
}

function isTokenExpired(token: string): boolean {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return payload.exp * 1000 < Date.now()
  } catch {
    return true
  }
}

export function isAuthenticated(): boolean {
  const token = getToken()
  return token !== null && !isTokenExpired(token)
}

export function clearToken(): void {
  localStorage.removeItem('token')
}

export function storeToken(token: string): void {
  localStorage.setItem('token', token)
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = getToken()
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (token) headers['Authorization'] = `Bearer ${token}`

  const res = await fetch(`${BASE_URL}${path}`, { headers, ...init })

  if (res.status === 401) {
    const hadToken = !!getToken()
    clearToken()
    if (hadToken) window.location.reload()
    throw new Error('Session expired')
  }
  if (!res.ok) {
    const body = await res.json().catch(() => ({}))
    throw new Error((body as { message?: string }).message ?? `HTTP ${res.status}`)
  }
  return res.json() as Promise<T>
}

export const authApi = {
  login: (username: string, password: string) =>
    request<{ token: string }>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }),
}

export const torrentApi = {
  add: (magnet: string) =>
    request<Torrent>('/api/torrents/add', {
      method: 'POST',
      body: JSON.stringify({ magnet }),
    }),
  getAll: () => request<Torrent[]>('/api/torrents/get'),
  getCompleted: () => request<CompletedTorrent[]>('/api/torrents/getCompleted'),
  pause: (id: number) => request<Torrent>(`/api/torrents/${id}/pause`, { method: 'PUT' }),
  resume: (id: number) => request<Torrent>(`/api/torrents/${id}/resume`, { method: 'PUT' }),
  remove: (id: number) => request<{ message: string }>(`/api/torrents/${id}/removeTorrent`, { method: 'DELETE' }),
}

export const systemApi = {
  getInfo: () => request<SystemInfo>('/api/system/info'),
  getDisks: () => request<DiskInfo[]>('/api/system/disks'),
}

export const settingsApi = {
  get: () => request<Settings>('/api/settings'),
  update: (downloadPath: string) =>
    request<Settings>('/api/settings', {
      method: 'PUT',
      body: JSON.stringify({ downloadPath }),
    }),
}
