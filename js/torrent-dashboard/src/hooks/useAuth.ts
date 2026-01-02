import { useState } from 'react'
import { authApi, clearToken, isAuthenticated, storeToken } from '../services/api'

export function useAuth() {
  const [authenticated, setAuthenticated] = useState(isAuthenticated)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function login(username: string, password: string) {
    setLoading(true)
    setError(null)
    try {
      const { token } = await authApi.login(username, password)
      storeToken(token)
      setAuthenticated(true)
    } catch (e) {
      setError('Invalid username or password')
    } finally {
      setLoading(false)
    }
  }

  function logout() {
    clearToken()
    setAuthenticated(false)
  }

  return { authenticated, loading, error, login, logout }
}
