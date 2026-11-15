import { afterEach, describe, expect, it, vi } from 'vitest'
import { PendingApprovalError, torrentApi, authApi } from './api'

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

  it('logs in under /api/v1/auth/login, sending the device id', async () => {
    const f = mockFetch(200, { username: 'admin' })
    vi.stubGlobal('fetch', f)
    await authApi.login('a', 'b')
    expect(f).toHaveBeenCalledWith('/api/v1/auth/login', expect.objectContaining({
      method: 'POST',
      credentials: 'same-origin',
      headers: expect.objectContaining({ 'X-SixEyes-Device-Id': expect.any(String) }),
    }))
  })

  it('surfaces an unapproved device distinctly from bad credentials', async () => {
    vi.stubGlobal('fetch', mockFetch(403, { code: 'PENDING_APPROVAL', message: 'waiting' }))
    await expect(authApi.login('a', 'b')).rejects.toBeInstanceOf(PendingApprovalError)
  })

  it('sends skipped optional parts and the install plan with the download', async () => {
    const f = mockFetch(200, { id: 1 })
    vi.stubGlobal('fetch', f)
    await torrentApi.add('magnet:?xt=urn:btih:ABC', {
      downloadPath: 'D:\\Downloads',
      installPath: 'D:\\Games',
      autoInstall: true,
      skipParts: ['fg-optional-bonus-content'],
    })
    const [url, init] = f.mock.calls[0]
    expect(url).toBe('/api/v1/torrents/add')
    expect(JSON.parse(init.body)).toEqual({
      magnet: 'magnet:?xt=urn:btih:ABC',
      downloadPath: 'D:\\Downloads',
      installPath: 'D:\\Games',
      autoInstall: true,
      skipParts: ['fg-optional-bonus-content'],
    })
  })

  it('defaults to downloading everything, no auto-install', async () => {
    const f = mockFetch(200, { id: 1 })
    vi.stubGlobal('fetch', f)
    await torrentApi.add('magnet:?xt=urn:btih:ABC')
    const body = JSON.parse(f.mock.calls[0][1].body)
    expect(body.skipParts).toEqual([])
    expect(body.autoInstall).toBe(false)
  })

  it('never attaches an Authorization header - the session is a cookie', async () => {
    const f = mockFetch(200, [])
    vi.stubGlobal('fetch', f)
    await torrentApi.getAll()
    const [, init] = f.mock.calls[0]
    expect(init.headers).not.toHaveProperty('Authorization')
    expect(init.credentials).toBe('same-origin')
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
