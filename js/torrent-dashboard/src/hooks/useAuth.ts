import { useCallback, useEffect, useState } from 'react'
import { PendingApprovalError, authApi, setUnauthorizedHandler } from '../services/api'

export function useAuth() {
  // null while the server is still being asked - the session cookie is httpOnly, so the
  // client cannot answer this itself and must not flash the login page in the meantime.
  const [authenticated, setAuthenticated] = useState<boolean | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [pendingApproval, setPendingApproval] = useState<string | null>(null)

  useEffect(() => {
    authApi.me().then(() => setAuthenticated(true)).catch(() => setAuthenticated(false))
  }, [])

  // Any 401 from anywhere in the app means the session died server-side - expired,
  // logged out elsewhere, or revoked with the device.
  useEffect(() => {
    setUnauthorizedHandler(() => setAuthenticated(false))
    return () => setUnauthorizedHandler(null)
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    setLoading(true)
    setError(null)
    setPendingApproval(null)
    try {
      await authApi.login(username, password)
      setAuthenticated(true)
    } catch (e) {
      if (e instanceof PendingApprovalError) {
        setPendingApproval(e.message)
      } else {
        setError(e instanceof Error ? e.message : 'Sign in failed')
      }
    } finally {
      setLoading(false)
    }
  }, [])

  const logout = useCallback(async () => {
    // Revoke server-side first; clearing local state alone would leave the session live.
    await authApi.logout().catch(() => undefined)
    setAuthenticated(false)
  }, [])

  return { authenticated, loading, error, pendingApproval, login, logout }
}
