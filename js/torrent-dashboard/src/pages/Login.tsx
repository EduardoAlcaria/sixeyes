import { useEffect, useState } from 'react'
import { Activity, Eye, EyeOff } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

interface Props {
  onLogin: (username: string, password: string) => void
  loading: boolean
  error: string | null
  /** Set when the credentials were correct but this browser is not approved yet. */
  pendingApproval?: string | null
}

export function LoginPage({ onLogin, loading, error, pendingApproval }: Props) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)

  // Don't leave a rejected password sitting in the DOM.
  useEffect(() => {
    if (error) setPassword('')
  }, [error])

  function submit(e: React.FormEvent) {
    e.preventDefault()
    if (loading) return
    onLogin(username.trim(), password)
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-background p-4">
      <Card className="w-full max-w-sm">
        <CardHeader className="text-center">
          <div className="flex justify-center mb-2">
            <span className="flex size-11 items-center justify-center rounded-2xl bg-primary/10">
              <Activity className="size-6 text-primary" />
            </span>
          </div>
          <CardTitle className="text-2xl">SixEyes</CardTitle>
          <CardDescription>Sign in to your torrent dashboard</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={submit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="username">Username</Label>
              <Input
                id="username"
                value={username}
                onChange={e => setUsername(e.target.value)}
                autoComplete="username"
                autoCapitalize="none"
                autoCorrect="off"
                spellCheck={false}
                maxLength={64}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">Password</Label>
              <div className="relative">
                <Input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  autoComplete="current-password"
                  maxLength={72}
                  required
                  className="pr-9"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(s => !s)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                >
                  {showPassword ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                </button>
              </div>
            </div>
            {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
            {pendingApproval && (
              <div className="rounded-lg border border-amber-500/40 bg-amber-500/10 p-3">
                <p className="text-sm font-medium">Device awaiting approval</p>
                <p className="mt-1 text-sm text-muted-foreground">{pendingApproval}</p>
              </div>
            )}
            <Button type="submit" className="w-full" disabled={loading}>
              {loading ? 'Signing in…' : 'Sign in'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
