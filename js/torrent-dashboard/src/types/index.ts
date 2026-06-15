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
  installStatus: InstallStatus
  createdAt: string | null
  updatedAt: string | null
}

export type TorrentStatus = 'Downloading' | 'Seeding' | 'Paused' | 'Stopped' | 'Error'

export type InstallStatus = 'NONE' | 'REQUESTED' | 'INSTALLING' | 'INSTALLED' | 'FAILED'

export interface CompletedTorrent {
  id: number
  title: string | null
  size: string
  completedAt: string
  installStatus: InstallStatus
}

export interface SystemInfo {
  storage: { total: number; used: number; available: number; device?: string }
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

export interface FsEntry {
  name: string
  path: string
}

export interface BrowseResult {
  path: string
  parent: string | null
  entries: FsEntry[]
}

export interface CatalogGame {
  id: number
  title: string
  url: string
  imageUrl: string | null
  magnet: string | null
  repackSize: string | null
}

export interface CatalogPage {
  content: CatalogGame[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}
