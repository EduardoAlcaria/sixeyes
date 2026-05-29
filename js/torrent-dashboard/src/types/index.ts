export interface Torrent {
  id: number
  title: string | null
  size: string
  progress: number
  downloadSpeed: string
  uploadSpeed: string
  peers: number
  eta: string | null
  status: TorrentStatus
  createdAt: string | null
  updatedAt: string | null
}

export type TorrentStatus = 'Downloading' | 'Seeding' | 'Paused' | 'Stopped' | 'Error'

export interface CompletedTorrent {
  id: number
  title: string | null
  size: string
  completedAt: string
}

export interface SystemInfo {
  storage: { total: number; used: number; available: number }
  network: { downloadSpeed: number; uploadSpeed: number }
}

export interface NetworkDataPoint {
  time: string
  download: number
  upload: number
}

export interface DiskInfo {
  path: string
  device: string
  total: number
  used: number
  available: number
}

export interface Settings {
  downloadPath: string
}
