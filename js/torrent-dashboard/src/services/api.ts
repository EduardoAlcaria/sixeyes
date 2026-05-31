import type { BrowseResult, CompletedTorrent, DiskInfo, Settings, SystemInfo, Torrent } from '../types'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'

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
  add: (magnet: string, downloadPath?: string) =>
    request<Torrent>('/torrents/add', {
      method: 'POST',
      body: JSON.stringify({ magnet, downloadPath: downloadPath ?? null }),
    }),
  addFile: async (file: File, downloadPath?: string): Promise<Torrent> => {
    const token = getToken()
    const form = new FormData()
    form.append('file', file)
    if (downloadPath) form.append('downloadPath', downloadPath)
    const res = await fetch(`${BASE_URL}/torrents/addFile`, {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: form, // no Content-Type — browser sets the multipart boundary
    })
    if (res.status === 401) {
      clearToken()
      window.location.reload()
      throw new Error('Session expired')
    }
    if (!res.ok) {
      const b = await res.json().catch(() => ({}))
      throw new Error((b as { message?: string }).message ?? `HTTP ${res.status}`)
    }
    return res.json() as Promise<Torrent>
  },
  getAll: () => request<Torrent[]>('/torrents/get'),
  getCompleted: () => request<CompletedTorrent[]>('/torrents/getCompleted'),
  pause: (id: number) => request<Torrent>(`/torrents/${id}/pause`, { method: 'PUT' }),
  stop: (id: number) => request<Torrent>(`/torrents/${id}/stop`, { method: 'PUT' }),
  resume: (id: number) => request<Torrent>(`/torrents/${id}/resume`, { method: 'PUT' }),
  remove: (id: number, deleteFiles = false) =>
    request<{ message: string }>(
      `/torrents/${id}/removeTorrent?deleteFiles=${deleteFiles}`,
      { method: 'DELETE' },
    ),
  install: (id: number) => request<Torrent>(`/torrents/${id}/install`, { method: 'POST' }),
  cancelInstall: (id: number) => request<Torrent>(`/torrents/${id}/install`, { method: 'DELETE' }),
}

export const systemApi = {
  getInfo: () => request<SystemInfo>('/system/info'),
  getDisks: () => request<DiskInfo[]>('/system/disks'),
  browse: (path?: string) =>
    request<BrowseResult>(`/system/browse${path ? `?path=${encodeURIComponent(path)}` : ''}`),
  mkdir: (parent: string, name: string) =>
    request<{ path: string }>('/system/mkdir', {
      method: 'POST',
      body: JSON.stringify({ parent, name }),
    }),
}

export const settingsApi = {
  get: () => request<Settings>('/settings'),
  update: (downloadPath: string) =>
    request<Settings>('/settings', {
      method: 'PUT',
      body: JSON.stringify({ downloadPath }),
    }),
}
