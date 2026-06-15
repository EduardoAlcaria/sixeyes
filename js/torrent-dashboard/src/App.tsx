import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from '@/components/layout/AppShell'
import { useAuth } from '@/hooks/useAuth'
import { LoginPage } from '@/pages/Login'
import { DashboardPage } from '@/pages/Dashboard'
import { TorrentsPage } from '@/pages/Torrents'
import { CompletedPage } from '@/pages/Completed'
import { SettingsPage } from '@/pages/Settings'
import { CatalogPage } from '@/pages/Catalog'

export default function App() {
  const { authenticated, loading, error, login, logout } = useAuth()

  if (!authenticated) {
    return <LoginPage onLogin={login} loading={loading} error={error} />
  }

  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppShell onLogout={logout} />}>
          <Route index element={<DashboardPage />} />
          <Route path="torrents" element={<TorrentsPage />} />
          <Route path="catalog" element={<CatalogPage />} />
          <Route path="completed" element={<CompletedPage />} />
          <Route path="settings" element={<SettingsPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
