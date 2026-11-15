import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { DeckShell } from '@/components/layout/DeckShell'
import { useAuth } from '@/hooks/useAuth'
import { LoginPage } from '@/pages/Login'
import { OverviewPage } from '@/pages/Overview'
import { LibraryPage } from '@/pages/Library'
import { CatalogPage } from '@/pages/Catalog'
import { ShelfPage } from '@/pages/Shelf'
import { PeerMapPage } from '@/pages/PeerMap'
import { HistoryPage } from '@/pages/History'
import { SettingsPage } from '@/pages/Settings'

export default function App() {
  const { authenticated, loading, error, pendingApproval, login, logout } = useAuth()

  // Undecided: the session check is still in flight. Rendering the login page here would
  // flash it on every refresh for an already-signed-in user.
  if (authenticated === null) {
    return <div className="min-h-screen bg-background" />
  }

  if (!authenticated) {
    return (
      <LoginPage
        onLogin={login}
        loading={loading}
        error={error}
        pendingApproval={pendingApproval}
      />
    )
  }

  return (
    <BrowserRouter>
      <Routes>
        <Route element={<DeckShell onLogout={logout} />}>
          <Route index element={<OverviewPage />} />
          <Route path="library" element={<LibraryPage />} />
          <Route path="catalog" element={<CatalogPage />} />
          <Route path="shelf" element={<ShelfPage />} />
          <Route path="map" element={<PeerMapPage />} />
          <Route path="history" element={<HistoryPage />} />
          <Route path="settings" element={<SettingsPage />} />
          {/* Old bookmarks from before the redesign. */}
          <Route path="torrents" element={<Navigate to="/library" replace />} />
          <Route path="completed" element={<Navigate to="/library" replace />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
