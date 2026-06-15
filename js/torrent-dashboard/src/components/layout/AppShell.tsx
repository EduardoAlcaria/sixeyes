import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import {
  Activity,
  CheckCircle2,
  LayoutDashboard,
  Library,
  ListTree,
  Menu,
  Moon,
  Settings,
  Sun,
  LogOut,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetTrigger, SheetTitle } from '@/components/ui/sheet'
import { useTheme } from '@/providers/ThemeProvider'

const nav = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/torrents', label: 'Torrents', icon: ListTree, end: false },
  { to: '/catalog', label: 'Catalog', icon: Library, end: false },
  { to: '/completed', label: 'Completed', icon: CheckCircle2, end: false },
  { to: '/settings', label: 'Settings', icon: Settings, end: false },
]

function NavLinks({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <>
      {nav.map(({ to, label, icon: Icon, end }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
          onClick={onNavigate}
          className={({ isActive }) =>
            `flex items-center gap-2 rounded-lg px-3 py-2 text-sm transition-colors ${
              isActive
                ? 'bg-sidebar-accent text-sidebar-accent-foreground'
                : 'text-sidebar-foreground/80 hover:bg-sidebar-accent/50'
            }`
          }
        >
          <Icon className="size-4" /> {label}
        </NavLink>
      ))}
    </>
  )
}

function Brand() {
  return (
    <div className="flex items-center gap-2 px-2 py-3">
      <Activity className="size-5 text-primary" />
      <span className="font-bold tracking-tight">SixEyes</span>
    </div>
  )
}

export function AppShell({ onLogout }: { onLogout: () => void }) {
  const { theme, toggle } = useTheme()
  const [mobileOpen, setMobileOpen] = useState(false)

  return (
    <div className="min-h-screen flex bg-background text-foreground">
      {/* desktop sidebar */}
      <aside className="hidden md:flex w-60 shrink-0 flex-col gap-1 border-r bg-sidebar p-3">
        <Brand />
        <NavLinks />
      </aside>

      <div className="flex-1 flex flex-col min-w-0">
        <header className="flex items-center justify-between border-b px-4 md:px-6 h-14 shrink-0">
          <div className="flex items-center gap-2">
            {/* mobile nav trigger */}
            <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
              <SheetTrigger
                render={
                  <Button variant="ghost" size="icon" className="md:hidden" aria-label="Open menu">
                    <Menu className="size-4" />
                  </Button>
                }
              />
              <SheetContent side="left" className="w-64 bg-sidebar p-3">
                <SheetTitle className="sr-only">Navigation</SheetTitle>
                <Brand />
                <nav className="flex flex-col gap-1">
                  <NavLinks onNavigate={() => setMobileOpen(false)} />
                </nav>
              </SheetContent>
            </Sheet>
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <span className="size-2 rounded-full bg-chart-4 animate-pulse" /> Connected
            </div>
          </div>
          <div className="flex items-center gap-1">
            <Button variant="ghost" size="icon" onClick={toggle} aria-label="Toggle theme">
              {theme === 'dark' ? <Sun className="size-4" /> : <Moon className="size-4" />}
            </Button>
            <Button variant="ghost" size="icon" onClick={onLogout} aria-label="Sign out">
              <LogOut className="size-4" />
            </Button>
          </div>
        </header>
        <main className="flex-1 overflow-auto p-4 md:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
