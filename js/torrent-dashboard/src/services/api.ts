import type {
  Achievements, ActivityDay, AppEvent, BrowseResult, CatalogGame, CatalogPage, CompletedTorrent, DiskInfo,
  GameLibrary, Genre, HistoryEntry, MetricsSummary, NetRange, NetSeries, OwnedGame, PeerMap, PlayDay, Settings,
  SystemInfo, Torrent, TorrentDetails, Vitals,
} from '../types'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'

const DEVICE_KEY = 'sixeyes.deviceId'

export interface Me {
  username: string
}

export interface Device {
  id: number
  label: string
  status: 'PENDING' | 'TRUSTED'
  createdAt: string
  lastSeenAt: string | null
  /** This browser - removing it signs you out here. */
  current: boolean
}

/** Thrown when the password was right but this browser has not been approved yet. */
export class PendingApprovalError extends Error {}

export interface AddOptions {
  downloadPath?: string
  installPath?: string
  autoInstall?: boolean
  /** FitGirl fg-optional-* part names to skip entirely. */
  skipParts?: string[]
}

/**
 * A random 256-bit identifier for this browser, generated once and kept locally. It is
 * the second factor at login: the password alone is not enough on a device you have not
 * approved. Not a MAC address - those never survive routing to the server.
 */
function deviceId(): string {
  let id = localStorage.getItem(DEVICE_KEY)
  if (!id) {
    const bytes = crypto.getRandomValues(new Uint8Array(32))
    id = btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
    localStorage.setItem(DEVICE_KEY, id)
  }
  return id
}

// The session cookie is httpOnly, so nothing here can read it. Whether we are signed in
// is a question only the server can answer - see authApi.me().
let onUnauthorized: (() => void) | null = null

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  // The device id rides along so the server can tell which listed device is this one.
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-SixEyes-Device-Id': deviceId(),
  }

  const res = await fetch(`${BASE_URL}${path}`, { credentials: 'same-origin', headers, ...init })

  if (res.status === 401) {
    onUnauthorized?.()
    throw new Error('Not authenticated')
  }
  if (!res.ok) {
    const body = await res.json().catch(() => ({}))
    throw new Error((body as { message?: string }).message ?? `HTTP ${res.status}`)
  }
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}

export const authApi = {
  login: async (username: string, password: string): Promise<Me> => {
    const res = await fetch(`${BASE_URL}/auth/login`, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', 'X-SixEyes-Device-Id': deviceId() },
      body: JSON.stringify({ username, password }),
    })
    if (res.ok) return res.json() as Promise<Me>

    const body = (await res.json().catch(() => ({}))) as { code?: string; message?: string }
    if (res.status === 403 && body.code === 'PENDING_APPROVAL') {
      throw new PendingApprovalError(body.message ?? 'This device is awaiting approval.')
    }
    if (res.status === 401) throw new Error('Invalid username or password')
    throw new Error(body.message ?? `HTTP ${res.status}`)
  },
  me: () => request<Me>('/auth/me'),
  logout: () => request<void>('/auth/logout', { method: 'POST' }),
  logoutAll: () => request<{ revoked: number }>('/auth/logout-all', { method: 'POST' }),
  devices: () => request<Device[]>('/auth/devices'),
  approveDevice: (id: number) => request<Device>(`/auth/devices/${id}/approve`, { method: 'POST' }),
  removeDevice: (id: number) => request<{ revokedSessions: number }>(`/auth/devices/${id}`, { method: 'DELETE' }),
}

export const torrentApi = {
  add: (magnet: string, opts: AddOptions = {}) =>
    request<Torrent>('/torrents/add', {
      method: 'POST',
      body: JSON.stringify({
        magnet,
        downloadPath: opts.downloadPath ?? null,
        installPath: opts.installPath ?? null,
        autoInstall: opts.autoInstall ?? false,
        skipParts: opts.skipParts ?? [],
      }),
    }),
  addFile: async (file: File, downloadPath?: string): Promise<Torrent> => {
    const form = new FormData()
    form.append('file', file)
    if (downloadPath) form.append('downloadPath', downloadPath)
    const res = await fetch(`${BASE_URL}/torrents/addFile`, {
      method: 'POST',
      credentials: 'same-origin',
      body: form, // no Content-Type - browser sets the multipart boundary
    })
    if (res.status === 401) {
      onUnauthorized?.()
      throw new Error('Not authenticated')
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
  details: (id: number) => request<TorrentDetails>(`/torrents/${id}/details`),
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

export const layoutApi = {
  get: () => request<Record<string, unknown>>('/settings/layout'),
  save: (layout: unknown) =>
    request<void>('/settings/layout', { method: 'PUT', body: JSON.stringify(layout) }),
}

export const settingsApi = {
  get: () => request<Settings>('/settings'),
  update: (s: Settings) =>
    request<Settings>('/settings', {
      method: 'PUT',
      body: JSON.stringify(s),
    }),
}

export const insightApi = {
  network: (range: NetRange) => request<NetSeries>(`/metrics/network?range=${range}`),
  activity: (days = 182) => request<ActivityDay[]>(`/metrics/activity?days=${days}`),
  summary: () => request<MetricsSummary>('/metrics/summary'),
  events: (limit = 50) => request<AppEvent[]>(`/events?limit=${limit}`),
  history: () => request<HistoryEntry[]>('/history'),
  peers: (torrentId?: number) => request<PeerMap>(`/peers${torrentId ? `?torrentId=${torrentId}` : ''}`),
  vitals: () => request<Vitals>('/system/vitals'),
  games: () => request<GameLibrary>('/games'),
  genres: () => request<Genre[]>('/games/genres'),
  steamOwned: () => request<OwnedGame[]>('/games/steam/owned'),
  steamInstall: (appId: string) =>
    request<{ status: string }>('/games/steam/install', { method: 'POST', body: JSON.stringify({ appId }) }),
  achievements: (appId: string) => request<Achievements>(`/games/steam/${appId}/achievements`),
  playtime: (days = 364, key?: string) =>
    request<PlayDay[]>(`/games/playtime?days=${days}${key ? `&key=${encodeURIComponent(key)}` : ''}`),
  launch: (key: string) =>
    request<{ status: string }>('/games/launch', { method: 'POST', body: JSON.stringify({ key }) }),
}

export const catalogApi = {
  search: (q: string, page = 0, size = 24) =>
    request<CatalogPage>(`/catalog/search?q=${encodeURIComponent(q)}&page=${page}&size=${size}`),
  game: (url: string) =>
    request<CatalogGame>(`/catalog/game?url=${encodeURIComponent(url)}`),
  refresh: () =>
    request<{ status: string }>('/catalog/refresh', { method: 'POST' }),
}
