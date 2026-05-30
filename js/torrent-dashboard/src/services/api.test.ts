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

  it('uploads a .torrent file to /api/v1/torrents/addFile as multipart', async () => {
    const f = mockFetch(200, { id: 1 })
    vi.stubGlobal('fetch', f)
    await torrentApi.addFile(new File(['x'], 'a.torrent'))
    const [url, init] = f.mock.calls[0]
    expect(url).toBe('/api/v1/torrents/addFile')
    expect(init.method).toBe('POST')
    expect(init.body).toBeInstanceOf(FormData)
  })
})
