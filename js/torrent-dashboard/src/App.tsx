import { useState } from 'react'
import { AddTorrentForm } from './components/AddTorrentForm'
import { CompletedList } from './components/CompletedList'
import { ErrorBanner } from './components/ErrorBanner'
import { LoadingOverlay } from './components/LoadingOverlay'
import { LoginPage } from './components/LoginPage'
import { NetworkChart } from './components/NetworkChart'
import { SettingsPage } from './components/SettingsPage'
import { StatsBar } from './components/StatsBar'
import { StorageChart } from './components/StorageChart'
import { TorrentCard } from './components/TorrentCard'
import { useAuth } from './hooks/useAuth'
import { useSystemMonitoring } from './hooks/useSystemMonitoring'
import { useTorrents } from './hooks/useTorrents'

type Page = 'dashboard' | 'settings'

function NavButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      className={`text-xs font-medium px-3 py-1.5 rounded-lg transition-colors ${
        active ? 'bg-slate-800 text-slate-100' : 'text-slate-500 hover:text-slate-300'
      }`}
    >
      {children}
    </button>
  )
}

export default function App() {
  const { authenticated, loading: authLoading, error: authError, login, logout } = useAuth()
  const [page, setPage] = useState<Page>('dashboard')

  const { torrents, completed, loading, error, addTorrent, pauseTorrent, resumeTorrent, removeTorrent } = useTorrents()
  const { system, history } = useSystemMonitoring()

  if (!authenticated) {
    return <LoginPage onLogin={login} loading={authLoading} error={authError} />
  }

  return (
    <div className="min-h-screen bg-slate-950 text-slate-200">
      {loading && <LoadingOverlay message="Adding torrent…" />}

      <header className="border-b border-slate-800 px-6 py-4">
        <div className="max-w-screen-2xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-4">
            <div className="flex items-center gap-3">
              <div className="w-8 h-8 rounded-lg bg-indigo-600 flex items-center justify-center">
                <svg className="w-4 h-4 text-white" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 14.5v-9l6 4.5-6 4.5z" />
                </svg>
              </div>
              <div>
                <h1 className="text-base font-semibold text-slate-100 leading-tight">SixEyes</h1>
                <p className="text-xs text-slate-500 leading-tight">Torrent Manager</p>
              </div>
            </div>

            <nav className="flex items-center gap-1 ml-2">
              <NavButton active={page === 'dashboard'} onClick={() => setPage('dashboard')}>Dashboard</NavButton>
              <NavButton active={page === 'settings'} onClick={() => setPage('settings')}>Settings</NavButton>
            </nav>
          </div>

          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
              <span className="text-xs text-slate-500">Connected</span>
            </div>
            <button
              onClick={logout}
              className="text-xs text-slate-500 hover:text-slate-300 transition-colors px-2 py-1 rounded-lg hover:bg-slate-800"
            >
              Sign out
            </button>
          </div>
        </div>
      </header>

      {page === 'settings' ? (
        <SettingsPage />
      ) : (
        <main className="max-w-screen-2xl mx-auto px-6 py-6 space-y-6">
          {error && <ErrorBanner message={error} />}

          <StatsBar torrents={torrents} />

          <div className="flex flex-col xl:flex-row gap-6">
            <div className="flex-1 min-w-0 space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-sm font-semibold text-slate-400 uppercase tracking-wide">
                  Torrents <span className="ml-1 text-slate-600 font-normal normal-case">({torrents.length})</span>
                </h2>
              </div>

              {torrents.length === 0 && !loading && (
                <div className="text-center py-12 text-slate-600 text-sm">
                  No torrents. Add one to get started.
                </div>
              )}

              <div className="grid grid-cols-1 md:grid-cols-2 2xl:grid-cols-3 gap-3">
                {torrents.map(t => (
                  <TorrentCard
                    key={t.id}
                    torrent={t}
                    onPause={pauseTorrent}
                    onResume={resumeTorrent}
                    onRemove={removeTorrent}
                  />
                ))}
                <AddTorrentForm onAdd={addTorrent} loading={loading} />
              </div>
            </div>

            <div className="xl:w-80 2xl:w-96 flex-shrink-0 space-y-4">
              <NetworkChart
                history={history}
                downloadSpeed={system.network.downloadSpeed}
                uploadSpeed={system.network.uploadSpeed}
              />
              <StorageChart
                total={system.storage.total}
                used={system.storage.used}
                available={system.storage.available}
              />
              <CompletedList items={completed} />
            </div>
          </div>
        </main>
      )}
    </div>
  )
}
