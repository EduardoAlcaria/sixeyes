# Plan B — Frontend Redesign (shadcn + Tailwind 4)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Commit/push policy (SixEyes):** ONE file change per commit, push after each commit, NO `Co-Authored-By` trailer. When a task touches several files, split into one commit per file in the order shown.

> **Depends on Plan A:** backend routes are under `/api/v1`. `services/api.ts` switches to a relative base. Run Plan A first (or at least Task 1).

**Goal:** Rebuild the SixEyes dashboard on shadcn/ui + Tailwind 4 with the tweakcn theme `cml5dyjea000004jr66ytcg0l`, a sidebar shell, dark/light toggle, mobile-responsive layout, live torrent progress, a dashboard overview with charts, and an improved add-torrent flow (magnet + `.torrent` upload + save-path picker).

**Architecture:** Keep the data layer (`services/api.ts`, `hooks/`, `types/`) and rebuild all UI. shadcn primitives (new-york) styled by the tweakcn theme tokens. Sidebar shell with routed pages: Dashboard, Torrents, Completed, Settings, Login. Polling hooks already drive live data.

**Tech Stack:** Vite 7 + React 18 + TypeScript, Tailwind 4 (`@tailwindcss/vite`), shadcn/ui, recharts (via shadcn chart), lucide-react, sonner, react-router-dom.

---

## File Structure

| File | Responsibility | Action |
|---|---|---|
| `package.json` | add tailwind4/shadcn/router/sonner deps | Modify |
| `vite.config.ts` | `@tailwindcss/vite` plugin + `@/` alias | Modify |
| `tsconfig.json`, `tsconfig.app.json` | `@/*` path mapping | Modify |
| `components.json` | shadcn config | Create (via CLI) |
| `src/index.css` | tweakcn theme tokens (Tailwind 4) | Modify (replace) |
| `index.html` | Bricolage Grotesque + JetBrains Mono fonts | Modify |
| `src/lib/utils.ts` | `cn()` helper | Create (via CLI) |
| `src/components/ui/*` | shadcn primitives | Create (via CLI) |
| `src/services/api.ts` | relative `/api/v1` base + `addFile` | Modify |
| `src/types/index.ts` | unchanged / minor | Verify |
| `src/hooks/useTorrents.ts` | add `addTorrentFile`, optimistic actions | Modify |
| `src/providers/ThemeProvider.tsx` | dark/light context | Create |
| `src/components/layout/AppShell.tsx` | sidebar + topbar + theme toggle | Create |
| `src/pages/{Dashboard,Torrents,Completed,Settings,Login}.tsx` | pages | Create |
| `src/components/torrents/*` | TorrentTable, TorrentRow, AddTorrentDialog | Create |
| `src/components/dashboard/*` | StatCards, NetworkChart, StorageChart | Create |
| `src/App.tsx` | router + auth gate + shell | Modify (replace) |
| `src/main.tsx` | mount ThemeProvider + Toaster | Modify |
| old `src/components/*.tsx` (App.css etc.) | remove replaced files | Delete |

---

## Task 1: Tailwind 4 + shadcn scaffolding

**Files:**
- Modify: `package.json`, `vite.config.ts`, `tsconfig.json`, `tsconfig.app.json`
- Create: `components.json`, `src/lib/utils.ts`

- [ ] **Step 1: Install Tailwind 4 + tooling**

Run from `js/torrent-dashboard`:
```bash
npm install tailwindcss @tailwindcss/vite
npm install -D @types/node
npm install class-variance-authority clsx tailwind-merge lucide-react sonner react-router-dom
npm uninstall autoprefixer postcss   # not needed with @tailwindcss/vite
```

- [ ] **Step 2: Configure Vite plugin + alias**

Replace `vite.config.ts`:
```ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: { port: 5173 },
})
```

- [ ] **Step 3: Add path alias to tsconfig**

In `tsconfig.json` add under `compilerOptions` (create the block if the file only has references):
```json
{
  "compilerOptions": {
    "baseUrl": ".",
    "paths": { "@/*": ["./src/*"] }
  }
}
```
Add the same `baseUrl` + `paths` to `tsconfig.app.json` `compilerOptions`.

- [ ] **Step 4: Init shadcn (non-interactive) + cn helper**

Run: `npx shadcn@latest init -d`
Expected: creates `components.json` and `src/lib/utils.ts` (with `cn()`), style new-york. If it rewrites `index.css`, that's fine — Task 2 overwrites it with the theme.

- [ ] **Step 5: Verify typecheck/build wiring**

Run: `npx tsc -b --noEmit`
Expected: no path-alias errors (`@/lib/utils` resolves).

- [ ] **Step 6: Commit**

```bash
git add package.json && git commit -m "build(frontend): add Tailwind 4 + shadcn + router deps" && git push
git add package-lock.json && git commit -m "build(frontend): lock Tailwind 4 + shadcn deps" && git push
git add vite.config.ts && git commit -m "build(frontend): @tailwindcss/vite plugin + @/ alias" && git push
git add tsconfig.json && git commit -m "build(frontend): map @/* path alias" && git push
git add tsconfig.app.json && git commit -m "build(frontend): map @/* alias for app tsconfig" && git push
git add components.json && git commit -m "build(frontend): shadcn config (new-york)" && git push
git add src/lib/utils.ts && git commit -m "feat(frontend): add cn() class helper" && git push
```

---

## Task 2: Apply the tweakcn theme + fonts

**Files:**
- Modify: `src/index.css` (full replace with theme)
- Modify: `index.html` (fonts)

- [ ] **Step 1: Install the theme via shadcn registry**

Run: `npx shadcn@latest add https://tweakcn.com/r/themes/cml5dyjea000004jr66ytcg0l`
This writes the theme tokens into `src/index.css`. If the CLI cannot reach the registry, paste the theme CSS manually (the full `@import "tailwindcss"; @custom-variant dark ...; :root{...} .dark{...} @theme inline{...} @layer base{...}` block from the spec) into `src/index.css`, replacing its contents.

- [ ] **Step 2: Confirm theme essentials present**

Open `src/index.css`; verify it contains:
- `@import "tailwindcss";`
- `--primary: oklch(0.2236 0.1469 265.8205);` (light) and the `.dark` block
- `--font-sans: Bricolage Grotesque, ...;` `--font-mono: JetBrains Mono, ...;`
- `--radius: 1.25rem;`
- `@theme inline { ... --color-primary: var(--primary); ... }`
- `@layer base { body { @apply bg-background text-foreground; } }`

- [ ] **Step 3: Load fonts in index.html**

Replace the font `<link>` in `index.html` `<head>`:
```html
<link rel="preconnect" href="https://fonts.googleapis.com" />
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
<link href="https://fonts.googleapis.com/css2?family=Bricolage+Grotesque:opsz,wght@12..96,400;12..96,600;12..96,700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet" />
```

- [ ] **Step 4: Verify build compiles the theme**

Run: `npm run build`
Expected: build succeeds, `dist/` produced. (UI not wired yet — only checking CSS/Tailwind 4 compiles.)

- [ ] **Step 5: Commit**

```bash
git add src/index.css && git commit -m "feat(theme): apply tweakcn cml5dyjea theme (Tailwind 4 tokens)" && git push
git add index.html && git commit -m "feat(theme): load Bricolage Grotesque + JetBrains Mono" && git push
```

---

## Task 3: Add shadcn components

**Files:**
- Create: `src/components/ui/*` (via CLI)

- [ ] **Step 1: Add the primitives the redesign uses**

Run:
```bash
npx shadcn@latest add button card dialog input label table tabs badge progress \
  sonner chart sidebar sheet dropdown-menu tooltip skeleton separator \
  select form scroll-area avatar
```
Expected: files created under `src/components/ui/`.

- [ ] **Step 2: Typecheck**

Run: `npx tsc -b --noEmit`
Expected: no errors (recharts already installed for `chart`).

- [ ] **Step 3: Commit (group the generated ui dir as one logical unit)**

> Exception to one-file rule: shadcn generates many `ui/*` files at once; commit the `ui` directory in a single commit since they are one generated unit.
```bash
git add src/components/ui && git commit -m "feat(ui): add shadcn primitives" && git push
```

---

## Task 4: Point the API client at `/api/v1` and add file upload

**Files:**
- Modify: `src/services/api.ts`
- Test: `src/services/api.test.ts` (new) + add `vitest`

- [ ] **Step 1: Add a test runner**

Run: `npm install -D vitest`
Add to `package.json` scripts: `"test": "vitest run"`.

- [ ] **Step 2: Write a failing test for the relative base + paths**

Create `src/services/api.test.ts`:
```ts
import { afterEach, describe, expect, it, vi } from 'vitest'
import { torrentApi, authApi } from './api'

function mockFetch(status = 200, body: unknown = {}) {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  })
}

afterEach(() => vi.restoreAllMocks())

describe('api base + paths', () => {
  it('calls torrents under /api/v1', async () => {
    const f = mockFetch(200, [])
    vi.stubGlobal('fetch', f)
    await torrentApi.getAll()
    expect(f).toHaveBeenCalledWith('/api/v1/torrents/get', expect.anything())
  })

  it('logs in under /api/v1/auth/login', async () => {
    const f = mockFetch(200, { token: 't' })
    vi.stubGlobal('fetch', f)
    await authApi.login('a', 'b')
    expect(f).toHaveBeenCalledWith('/api/v1/auth/login', expect.objectContaining({ method: 'POST' }))
  })
})
```

- [ ] **Step 3: Run it (must fail)**

Run: `npx vitest run src/services/api.test.ts`
Expected: FAIL (current base is `http://localhost:9090`, paths `/api/torrents/...`).

- [ ] **Step 4: Update `src/services/api.ts`**

- Change base:
```ts
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'
```
- Update endpoint paths to drop the redundant `/api` segment and `/auth` (context-path is `/api/v1`):
```ts
export const authApi = {
  login: (username: string, password: string) =>
    request<{ token: string }>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }),
}

export const torrentApi = {
  add: (magnet: string) =>
    request<Torrent>('/torrents/add', { method: 'POST', body: JSON.stringify({ magnet }) }),
  addFile: async (file: File): Promise<Torrent> => {
    const token = localStorage.getItem('token')
    const form = new FormData()
    form.append('file', file)
    const res = await fetch(`${BASE_URL}/torrents/addFile`, {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: form, // no Content-Type — browser sets multipart boundary
    })
    if (!res.ok) {
      const b = await res.json().catch(() => ({}))
      throw new Error((b as { message?: string }).message ?? `HTTP ${res.status}`)
    }
    return res.json()
  },
  getAll: () => request<Torrent[]>('/torrents/get'),
  getCompleted: () => request<CompletedTorrent[]>('/torrents/getCompleted'),
  pause: (id: number) => request<Torrent>(`/torrents/${id}/pause`, { method: 'PUT' }),
  resume: (id: number) => request<Torrent>(`/torrents/${id}/resume`, { method: 'PUT' }),
  remove: (id: number) => request<{ message: string }>(`/torrents/${id}/removeTorrent`, { method: 'DELETE' }),
}

export const systemApi = {
  getInfo: () => request<SystemInfo>('/system/info'),
  getDisks: () => request<DiskInfo[]>('/system/disks'),
}

export const settingsApi = {
  get: () => request<Settings>('/settings'),
  update: (downloadPath: string) =>
    request<Settings>('/settings', { method: 'PUT', body: JSON.stringify({ downloadPath }) }),
}
```
Keep the `request`, token, and 401 logic as-is.

- [ ] **Step 5: Run the test (must pass)**

Run: `npx vitest run src/services/api.test.ts`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add package.json && git commit -m "build(frontend): add vitest test runner" && git push
git add package-lock.json && git commit -m "build(frontend): lock vitest" && git push
git add src/services/api.ts && git commit -m "feat(api-client): relative /api/v1 base + addFile upload" && git push
git add src/services/api.test.ts && git commit -m "test(api-client): assert /api/v1 base and paths" && git push
```

---

## Task 5: Hooks — file add + optimistic pause/resume/delete

**Files:**
- Modify: `src/hooks/useTorrents.ts`

- [ ] **Step 1: Add `addTorrentFile` and make actions optimistic**

In `useTorrents.ts`:
- Add a file adder mirroring `addTorrent`:
```ts
const addTorrentFile = useCallback(async (file: File) => {
  setLoading(true)
  try {
    await torrentApi.addFile(file)
    await fetchTorrents()
    setError(null)
  } catch (e) {
    showError((e as Error).message)
  } finally {
    setLoading(false)
  }
}, [fetchTorrents, showError])
```
- Make `removeTorrent` optimistic with rollback:
```ts
const removeTorrent = useCallback(async (id: number) => {
  const prev = torrents
  setTorrents(p => p.filter(t => t.id !== id))
  try {
    await torrentApi.remove(id)
  } catch (e) {
    setTorrents(prev)              // rollback
    showError((e as Error).message)
  }
}, [torrents, showError])
```
- Return `addTorrentFile` in the hook's return object.

- [ ] **Step 2: Typecheck**

Run: `npx tsc -b --noEmit`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add src/hooks/useTorrents.ts && git commit -m "feat(torrents): file add + optimistic remove with rollback" && git push
```

---

## Task 6: Theme provider + Toaster mount

**Files:**
- Create: `src/providers/ThemeProvider.tsx`
- Modify: `src/main.tsx`

- [ ] **Step 1: Create the provider**

`src/providers/ThemeProvider.tsx`:
```tsx
import { createContext, useContext, useEffect, useState } from 'react'

type Theme = 'light' | 'dark'
const ThemeCtx = createContext<{ theme: Theme; toggle: () => void }>({
  theme: 'dark',
  toggle: () => {},
})

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [theme, setTheme] = useState<Theme>(
    () => (localStorage.getItem('theme') as Theme) ?? 'dark'
  )
  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark')
    localStorage.setItem('theme', theme)
  }, [theme])
  return (
    <ThemeCtx.Provider value={{ theme, toggle: () => setTheme(t => (t === 'dark' ? 'light' : 'dark')) }}>
      {children}
    </ThemeCtx.Provider>
  )
}

export const useTheme = () => useContext(ThemeCtx)
```

- [ ] **Step 2: Mount provider + Toaster in main.tsx**

```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App'
import { ThemeProvider } from './providers/ThemeProvider'
import { Toaster } from '@/components/ui/sonner'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider>
      <App />
      <Toaster richColors position="top-right" />
    </ThemeProvider>
  </StrictMode>,
)
```

- [ ] **Step 3: Typecheck**

Run: `npx tsc -b --noEmit`
Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add src/providers/ThemeProvider.tsx && git commit -m "feat(ui): dark/light theme provider" && git push
git add src/main.tsx && git commit -m "feat(ui): mount ThemeProvider and Toaster" && git push
```

---

## Task 7: App shell (sidebar + topbar + routing + auth gate)

**Files:**
- Create: `src/components/layout/AppShell.tsx`
- Modify: `src/App.tsx`

- [ ] **Step 1: Build the shell**

`src/components/layout/AppShell.tsx` — use shadcn `Sidebar` (with `Sheet` collapse on mobile, provided by the sidebar component), nav links (Dashboard, Torrents, Completed, Settings) via `react-router-dom` `NavLink`, a topbar with app title, a "Connected" indicator, a theme toggle button (`useTheme().toggle`, lucide `Moon`/`Sun`), and a logout button (prop). Wrap `children`/`<Outlet />`.

```tsx
import { NavLink, Outlet } from 'react-router-dom'
import { Activity, CheckCircle2, LayoutDashboard, ListTree, Moon, Settings, Sun, LogOut } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useTheme } from '@/providers/ThemeProvider'

const nav = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/torrents', label: 'Torrents', icon: ListTree },
  { to: '/completed', label: 'Completed', icon: CheckCircle2 },
  { to: '/settings', label: 'Settings', icon: Settings },
]

export function AppShell({ onLogout }: { onLogout: () => void }) {
  const { theme, toggle } = useTheme()
  return (
    <div className="min-h-screen flex">
      <aside className="hidden md:flex w-60 flex-col gap-1 border-r bg-sidebar text-sidebar-foreground p-3">
        <div className="flex items-center gap-2 px-2 py-3">
          <Activity className="size-5 text-primary" />
          <span className="font-bold tracking-tight">SixEyes</span>
        </div>
        {nav.map(({ to, label, icon: Icon, end }) => (
          <NavLink key={to} to={to} end={end}
            className={({ isActive }) =>
              `flex items-center gap-2 rounded-lg px-3 py-2 text-sm transition-colors ${
                isActive ? 'bg-sidebar-accent text-sidebar-accent-foreground' : 'hover:bg-sidebar-accent/50'
              }`}>
            <Icon className="size-4" /> {label}
          </NavLink>
        ))}
      </aside>
      <div className="flex-1 flex flex-col min-w-0">
        <header className="flex items-center justify-between border-b px-4 md:px-6 h-14">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <span className="size-2 rounded-full bg-chart-4 animate-pulse" /> Connected
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
        <main className="flex-1 overflow-auto p-4 md:p-6"><Outlet /></main>
      </div>
    </div>
  )
}
```
> A mobile nav `Sheet` trigger can be added in the header; keep this commit focused on the desktop shell + outlet, add the mobile drawer in Task 11 (responsive pass).

- [ ] **Step 2: Rewrite `src/App.tsx` as router + auth gate**

```tsx
import { BrowserRouter, Route, Routes, Navigate } from 'react-router-dom'
import { AppShell } from '@/components/layout/AppShell'
import { useAuth } from '@/hooks/useAuth'
import { LoginPage } from '@/pages/Login'
import { DashboardPage } from '@/pages/Dashboard'
import { TorrentsPage } from '@/pages/Torrents'
import { CompletedPage } from '@/pages/Completed'
import { SettingsPage } from '@/pages/Settings'

export default function App() {
  const { authenticated, loading, error, login, logout } = useAuth()
  if (!authenticated) return <LoginPage onLogin={login} loading={loading} error={error} />
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppShell onLogout={logout} />}>
          <Route index element={<DashboardPage />} />
          <Route path="torrents" element={<TorrentsPage />} />
          <Route path="completed" element={<CompletedPage />} />
          <Route path="settings" element={<SettingsPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
```
> SPA routing relies on nginx `try_files … /index.html` (Plan A Task 5) — already handled.

- [ ] **Step 3: Typecheck (pages will be missing — create stubs to satisfy, or do after Task 8-10)**

> Implementation order note: create the page files (Tasks 8-10) before this typechecks. If executing strictly in order, add temporary one-line stub exports for the four pages now, replaced in later tasks. Recommended: implement Tasks 8-10 then return to verify Task 7.

- [ ] **Step 4: Commit**

```bash
git add src/components/layout/AppShell.tsx && git commit -m "feat(ui): sidebar + topbar app shell with theme toggle" && git push
git add src/App.tsx && git commit -m "feat(ui): router + auth gate replacing single-page app" && git push
```

---

## Task 8: Login page

**Files:**
- Create: `src/pages/Login.tsx`
- Delete: `src/components/LoginPage.tsx` (replaced)

- [ ] **Step 1: Build the page**

`src/pages/Login.tsx` — centered `Card` with `Input` username/password, `Button` submit, error text from `error` prop, disabled while `loading`. Signature: `{ onLogin: (u: string, p: string) => void; loading: boolean; error: string | null }`. Uses shadcn `Card`, `Input`, `Label`, `Button`. App title + `Activity` icon header.

- [ ] **Step 2: Remove old component**

```bash
git rm src/components/LoginPage.tsx
```

- [ ] **Step 3: Typecheck**

Run: `npx tsc -b --noEmit` (Login should compile; other pages may still be pending).

- [ ] **Step 4: Commit**

```bash
git add src/pages/Login.tsx && git commit -m "feat(login): shadcn login card" && git push
git rm src/components/LoginPage.tsx && git commit -m "chore(login): remove old LoginPage" && git push
```

---

## Task 9: Dashboard page (stat cards + charts)

**Files:**
- Create: `src/components/dashboard/StatCards.tsx`
- Create: `src/components/dashboard/NetworkChart.tsx`
- Create: `src/components/dashboard/StorageChart.tsx`
- Create: `src/pages/Dashboard.tsx`
- Delete: old `src/components/{StatsBar,NetworkChart,StorageChart}.tsx`

- [ ] **Step 1: StatCards**

`src/components/dashboard/StatCards.tsx` — takes `torrents: Torrent[]` and `system: SystemInfo`; renders a responsive grid of shadcn `Card`s: Active (status Downloading), Seeding, Completed count, total ↓ + ↑ speed (`system.network`). Use `Badge`/icons.

- [ ] **Step 2: NetworkChart (shadcn chart + recharts)**

`src/components/dashboard/NetworkChart.tsx` — props `{ history: NetworkDataPoint[] }`. Use shadcn `ChartContainer` + recharts `AreaChart` with two series (download=`chart-1`, upload=`chart-2`), `time` on X. Colors from theme via `var(--chart-1)` / `ChartConfig`.

- [ ] **Step 3: StorageChart**

`src/components/dashboard/StorageChart.tsx` — props `{ system: SystemInfo }`. Donut/radial or simple `Progress` of used/total with labels (GB). Keep it small.

- [ ] **Step 4: Dashboard page wires hooks**

`src/pages/Dashboard.tsx`:
```tsx
import { useTorrents } from '@/hooks/useTorrents'
import { useSystemMonitoring } from '@/hooks/useSystemMonitoring'
import { StatCards } from '@/components/dashboard/StatCards'
import { NetworkChart } from '@/components/dashboard/NetworkChart'
import { StorageChart } from '@/components/dashboard/StorageChart'

export function DashboardPage() {
  const { torrents } = useTorrents()
  const { system, history } = useSystemMonitoring()
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold tracking-tight">Dashboard</h1>
      <StatCards torrents={torrents} system={system} />
      <div className="grid gap-6 lg:grid-cols-2">
        <NetworkChart history={history} />
        <StorageChart system={system} />
      </div>
    </div>
  )
}
```

- [ ] **Step 5: Remove old components**

```bash
git rm src/components/StatsBar.tsx src/components/NetworkChart.tsx src/components/StorageChart.tsx
```

- [ ] **Step 6: Typecheck**

Run: `npx tsc -b --noEmit`
Expected: Dashboard + children compile.

- [ ] **Step 7: Commit**

```bash
git add src/components/dashboard/StatCards.tsx && git commit -m "feat(dashboard): stat cards" && git push
git add src/components/dashboard/NetworkChart.tsx && git commit -m "feat(dashboard): network speed chart" && git push
git add src/components/dashboard/StorageChart.tsx && git commit -m "feat(dashboard): storage usage chart" && git push
git add src/pages/Dashboard.tsx && git commit -m "feat(dashboard): overview page" && git push
git rm src/components/StatsBar.tsx && git commit -m "chore: remove old StatsBar" && git push
git rm src/components/NetworkChart.tsx && git commit -m "chore: remove old NetworkChart" && git push
git rm src/components/StorageChart.tsx && git commit -m "chore: remove old StorageChart" && git push
```

---

## Task 10: Torrents page (table + live progress + add dialog), Completed, Settings

**Files:**
- Create: `src/components/torrents/TorrentTable.tsx`
- Create: `src/components/torrents/AddTorrentDialog.tsx`
- Create: `src/pages/Torrents.tsx`
- Create: `src/pages/Completed.tsx`
- Create: `src/pages/Settings.tsx`
- Delete: old `src/components/{TorrentCard,AddTorrentForm,CompletedList,SettingsPage,ErrorBanner,LoadingOverlay}.tsx`

- [ ] **Step 1: TorrentTable**

`src/components/torrents/TorrentTable.tsx` — props `{ torrents, onPause, onResume, onRemove }`. Desktop: shadcn `Table` (Title, Size, Progress `Progress` bar + %, ↓/↑ speed, peers, ETA, status `Badge`, actions menu via `DropdownMenu`). Mobile (`md:hidden`): map to `Card` list with the same fields. Status→Badge variant map (Downloading=default, Seeding=chart-4, Paused=secondary, Error=destructive).

- [ ] **Step 2: AddTorrentDialog**

`src/components/torrents/AddTorrentDialog.tsx` — props `{ onAddMagnet, onAddFile, loading, disks }`. shadcn `Dialog` triggered by a `Button` ("Add torrent"). `Tabs`: "Magnet" (Input + validate `magnet:` prefix) and "File" (drag-drop zone + `<input type=file accept=".torrent">`). Optional save-path `Select` from `disks` (DiskInfo[]). On submit calls the right handler, toasts via `sonner` on success/error, closes on success.

- [ ] **Step 3: Torrents page**

`src/pages/Torrents.tsx`:
```tsx
import { useTorrents } from '@/hooks/useTorrents'
import { useSettings } from '@/hooks/useSettings'
import { TorrentTable } from '@/components/torrents/TorrentTable'
import { AddTorrentDialog } from '@/components/torrents/AddTorrentDialog'

export function TorrentsPage() {
  const { torrents, addTorrent, addTorrentFile, pauseTorrent, resumeTorrent, removeTorrent, loading } = useTorrents()
  const { disks } = useSettings()
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">Torrents <span className="text-muted-foreground font-normal">({torrents.length})</span></h1>
        <AddTorrentDialog onAddMagnet={addTorrent} onAddFile={addTorrentFile} loading={loading} disks={disks} />
      </div>
      <TorrentTable torrents={torrents} onPause={pauseTorrent} onResume={resumeTorrent} onRemove={removeTorrent} />
    </div>
  )
}
```

- [ ] **Step 4: Completed page**

`src/pages/Completed.tsx` — `useTorrents().completed` in a shadcn `Table` (Title, Size, Completed at). Empty state text.

- [ ] **Step 5: Settings page**

`src/pages/Settings.tsx` — use `useSettings()` (`settings, disks, saving, saved, save`). shadcn `Card` + `Form`/`Input` for download path, `Select` to pick a disk path, `Button` save, toast on `saved`.

- [ ] **Step 6: Remove old components**

```bash
git rm src/components/TorrentCard.tsx src/components/AddTorrentForm.tsx \
       src/components/CompletedList.tsx src/components/SettingsPage.tsx \
       src/components/ErrorBanner.tsx src/components/LoadingOverlay.tsx
```

- [ ] **Step 7: Typecheck + build**

Run: `npx tsc -b --noEmit && npm run build`
Expected: clean typecheck, `dist/` produced.

- [ ] **Step 8: Commit (one file per commit)**

```bash
git add src/components/torrents/TorrentTable.tsx && git commit -m "feat(torrents): table + mobile cards with live progress" && git push
git add src/components/torrents/AddTorrentDialog.tsx && git commit -m "feat(torrents): add dialog (magnet + .torrent + save path)" && git push
git add src/pages/Torrents.tsx && git commit -m "feat(torrents): torrents page" && git push
git add src/pages/Completed.tsx && git commit -m "feat(completed): completed torrents page" && git push
git add src/pages/Settings.tsx && git commit -m "feat(settings): settings page" && git push
git rm src/components/TorrentCard.tsx && git commit -m "chore: remove old TorrentCard" && git push
git rm src/components/AddTorrentForm.tsx && git commit -m "chore: remove old AddTorrentForm" && git push
git rm src/components/CompletedList.tsx && git commit -m "chore: remove old CompletedList" && git push
git rm src/components/SettingsPage.tsx && git commit -m "chore: remove old SettingsPage" && git push
git rm src/components/ErrorBanner.tsx && git commit -m "chore: remove old ErrorBanner" && git push
git rm src/components/LoadingOverlay.tsx && git commit -m "chore: remove old LoadingOverlay" && git push
```

---

## Task 11: Responsive pass + mobile nav drawer

**Files:**
- Modify: `src/components/layout/AppShell.tsx`

- [ ] **Step 1: Add mobile nav**

Add a `Sheet` (shadcn) triggered by a hamburger `Button` shown only `md:hidden` in the header; its content lists the same nav links. Ensure `main` and tables scroll on small screens.

- [ ] **Step 2: Manual responsive check**

Run: `npm run dev`, open `http://localhost:5173`, toggle device toolbar (e.g. 390px). Verify: sidebar hidden on mobile, hamburger opens drawer, tables become card lists, theme toggle works, no horizontal overflow.

- [ ] **Step 3: Commit**

```bash
git add src/components/layout/AppShell.tsx && git commit -m "feat(ui): mobile nav drawer + responsive header" && git push
```

---

## Task 12: End-to-end verification

- [ ] **Step 1: Lint + typecheck + build + unit tests**

Run: `npm run lint ; npx tsc -b --noEmit && npm run build && npx vitest run`
Expected: build succeeds, tests pass.

- [ ] **Step 2: Run full stack and exercise the UI**

Run: `docker compose up -d --build` (from repo root), open `http://localhost:4000`.
Verify: login works; dashboard shows cards + charts; add via magnet works; add via `.torrent` file works; pause/resume/delete update live; completed list populates; settings save toasts; dark/light toggle persists across reload; mobile layout OK.

- [ ] **Step 3: Confirm no dead imports / old components referenced**

Run: `grep -rin "App.css\|components/TorrentCard\|components/StatsBar\|components/LoginPage" src`
Expected: no matches.

- [ ] **Step 4: Tunnel smoke test**

Open `https://sixeyes.alcaria.dev` — full app loads and functions through the tunnel.

---

## Self-Review Notes

- Spec §"Stack/Reuse/App shell/Pages/UX priorities/.torrent" → Tasks 1-12 cover each. Live progress (Task 10 + polling hooks), dashboard overview (Task 9), add-torrent flow (Task 10 dialog + Task 4/5 `addFile`), mobile + dark toggle (Tasks 6, 7, 11).
- Type consistency: `addTorrentFile(file: File)` (hook, Task 5) ↔ `torrentApi.addFile(file)` (Task 4) ↔ `AddTorrentDialog onAddFile` (Task 10). `useTorrents` returns `{torrents, completed, loading, error, addTorrent, addTorrentFile, pauseTorrent, resumeTorrent, removeTorrent}` — consumed by Torrents/Dashboard pages. `useSettings` returns `{settings, disks, loading, saving, error, saved, save}` — consumed by Settings + AddTorrentDialog (disks).
- Ordering caveat flagged in Task 7 Step 3: pages must exist before `App.tsx` typechecks; implement Tasks 8-10 then verify Task 7 (or add temporary stubs).
- Vitest added in Task 4; `npm run test` script defined there.
