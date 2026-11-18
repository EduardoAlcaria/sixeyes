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
  errorMessage: string | null
  savePath: string | null
  installPath: string | null
  autoInstall: boolean
  /** FitGirl repack from the catalog - the only kind that may be installed. */
  installable: boolean
  installStatus: InstallStatus
  installMessage: string | null
  createdAt: string | null
  updatedAt: string | null
  seeds: number | null
  connections: number | null
  availability: number | null
  totalDownloaded: number | null
  totalUploaded: number | null
  wasted: number | null
  ratio: number | null
  installedDir: string | null
  coverUrl: string | null
  /** 0-100 while verifying; null when the length of the work is unknown. */
  installProgress: number | null
  /** Time left reported by setup.exe while installing, "01:22:39". */
  installEta: string | null
}

export type TorrentStatus = 'Downloading' | 'Seeding' | 'Paused' | 'Stopped' | 'Error'

export type InstallStatus = 'NONE' | 'REQUESTED' | 'VERIFYING' | 'INSTALLING' | 'INSTALLED' | 'FAILED'

export interface CompletedTorrent {
  id: number
  title: string | null
  size: string
  completedAt: string
  savePath: string | null
  installPath: string | null
  installable: boolean
  installStatus: InstallStatus
  installMessage: string | null
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
  installPath: string | null
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
  originalSize: string | null
  postedAt: string | null
  genres: string | null
  optionalParts: string[]
  screenshots: string[]
  summary: string | null
  steamPrice: string | null
  hltbMain: number | null
  hltbRushed: number | null
  hltbCompletionist: number | null
  developers: string | null
  publishers: string | null
}

// --- Command deck ----------------------------------------------------------

export interface EngineFile {
  path: string
  size: number
  progress: number
  skipped: boolean
}

export interface EngineTracker {
  url: string
  tier: number
  working: boolean
  fails: number
  updating: boolean
  /** From the tracker's scrape; -1 when it did not say. */
  seeds: number
  leechers: number
  message: string
}

export interface EnginePeer {
  ip: string
  country: string | null
  client: string
  downSpeed: number
  upSpeed: number
  progress: number
  seed: boolean
}

export interface EngineDetails {
  metadata: boolean
  status?: string
  title?: string
  seeds?: number
  connections?: number
  availability?: number | null
  totalDownloaded?: number
  totalUploaded?: number
  wasted?: number
  activeSeconds?: number
  seedingSeconds?: number
  numPieces?: number
  pieceSize?: number
  pieces: number[]
  files: EngineFile[]
  fileCount?: number
  trackers: EngineTracker[]
  peerList: EnginePeer[]
  downloadRate?: number
  uploadRate?: number
  dhtNodes?: number
}

export interface HistoryEntry {
  id: number
  torrentId: number
  title: string | null
  size: string | null
  status: string
  addedAt: string | null
  completedAt: string | null
  removedAt: string | null
  maxSeeds: number | null
  speedSum: number | null
  speedSamples: number | null
  peakSpeed: number | null
  totalDownloaded: number | null
  totalUploaded: number | null
  installStatus: string | null
  installMessage: string | null
  installStartedAt: string | null
  installFinishedAt: string | null
  installExitCode: number | null
  verifiedFiles: number | null
  skippedFiles: number | null
  installedDir: string | null
}

export interface TorrentDetails {
  torrent: Torrent
  engine: EngineDetails | null
  catalog: CatalogGame | null
  history: HistoryEntry | null
}

export interface PeerMap {
  geoAvailable: boolean
  total: number
  unknown: number
  countries: Record<string, { peers: number; downSpeed: number; upSpeed: number }>
}

export interface Drive {
  drive: string
  path: string
  fs: string
  total: number
  used: number
  free: number
  readBps: number
  writeBps: number
  model?: string
  mediaType?: string
  bus?: string
  health?: string
  temperature?: number | null
  wear?: number | null
}

export interface Vitals {
  cpu: number | null
  cpuCount: number | null
  ramUsed: number | null
  ramTotal: number | null
  netRx: number
  netTx: number
  gpu: { name: string; usage: number; temperature: number; memUsedMb: number; memTotalMb: number } | null
  drives: Drive[]
  agentUptime: number
  systemUptime: number
  controllerUptime: number
  dhtNodes: number
}

export type NetRange = '24h' | '7d' | '30d'

export interface NetSeries {
  range: NetRange
  bucketMinutes: number
  points: { t: string; down: number; up: number }[]
  peakDown: number
  peakAt: string | null
  downBytes: number
  upBytes: number
}

export interface ActivityDay {
  date: string
  downBytes: number
  upBytes: number
}

export interface MetricsSummary {
  downloadedToday: number
  uploadedToday: number
  peakToday: number
  avgDailyDown: number
  diskFullDays: number | null
  globalRatio: number | null
  allTimeDownloaded: number
  allTimeUploaded: number
}

export interface AppEvent {
  id: number
  at: string
  type: string
  level: 'INFO' | 'OK' | 'WARN' | 'ERROR'
  message: string
  torrentId: number | null
}

export type GameSource = 'TORRENT' | 'STEAM' | 'LOCAL'

export interface Game {
  key: string
  name: string
  source: GameSource
  installDir: string | null
  sizeBytes: number | null
  coverUrl: string | null
  headerUrl: string | null
  genres: string | null
  hltbMain: number | null
  summary: string | null
  developers: string | null
  torrentId: number | null
  playtimeMinutes: number
  lastPlayed: string | null
  sessions: number
  avgSessionMinutes: number
  last7DaysHours: number[]
  installedAt: string | null
}

export interface GameLibrary {
  games: Game[]
  installRoot: string | null
  installFree: number | null
  installTotal: number | null
  sizesComputing: boolean
}

export interface Genre {
  name: string
  games: number
}

export interface CatalogPage {
  content: CatalogGame[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

/** A Steam library entry that is not installed on this PC. */
export interface OwnedGame {
  appId: string
  name: string
  playtimeMinutes: number
  lastPlayed: string | null
}

/** Minutes played on one day, from the sessions SixEyes watched. */
export interface PlayDay {
  date: string
  minutes: number
}

export interface Achievement {
  name: string
  description: string
  unlocked: boolean
  unlockedAt: string | null
  icon: string
}

/** From the public Steam profile of the account signed in on the PC. */
export interface Achievements {
  unlocked: number
  total: number
  note: string | null
  list: Achievement[]
}
