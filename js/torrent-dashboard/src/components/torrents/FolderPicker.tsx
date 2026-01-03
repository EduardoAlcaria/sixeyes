import { useEffect, useState } from 'react'
import { ChevronUp, Folder, FolderPlus, RefreshCw } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { ScrollArea } from '@/components/ui/scroll-area'
import { systemApi } from '@/services/api'
import type { BrowseResult } from '@/types'

// /host/C/Movies -> C:\Movies ; /app/downloads -> friendly default label
export function friendlyPath(p: string | null): string {
  if (!p) return 'Default downloads'
  if (p === '/app/downloads' || p.endsWith('/app/downloads')) return 'Default downloads'
  return p.replace(/^\/host\/([A-Za-z])/, '$1:').replace(/\//g, '\\')
}

export function FolderPicker({
  value,
  onChange,
}: {
  value: string | null
  onChange: (p: string | null) => void
}) {
  const [data, setData] = useState<BrowseResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [newName, setNewName] = useState('')

  const load = async (path?: string) => {
    setLoading(true)
    try {
      const res = await systemApi.browse(path)
      setData(res)
      onChange(res.path ? res.path : null)
    } catch (e) {
      toast.error((e as Error).message)
    } finally {
      setLoading(false)
    }
  }

  // initial load (roots, or wherever value points)
  useEffect(() => {
    load(value ?? undefined)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const goUp = () => {
    if (!data) return
    if (data.parent) load(data.parent)
    else load(undefined) // drive root -> back to the drive list
  }

  const createFolder = async () => {
    if (!data?.path || !newName.trim()) return
    try {
      const res = await systemApi.mkdir(data.path, newName.trim())
      setNewName('')
      await load(res.path)
      toast.success('Folder created')
    } catch (e) {
      toast.error((e as Error).message)
    }
  }

  return (
    <div className="rounded-xl border">
      <div className="flex items-center gap-1 border-b px-2 py-1.5">
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="size-7"
          disabled={!data || data.path === ''}
          onClick={goUp}
          aria-label="Up one level"
        >
          <ChevronUp className="size-4" />
        </Button>
        <span className="flex-1 truncate text-sm font-medium">{friendlyPath(data?.path ?? null)}</span>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="size-7"
          onClick={() => load(data?.path || undefined)}
          aria-label="Refresh"
        >
          <RefreshCw className={`size-4 ${loading ? 'animate-spin' : ''}`} />
        </Button>
      </div>

      <ScrollArea className="h-40">
        <div className="p-1">
          {data && data.entries.length === 0 && (
            <p className="px-2 py-8 text-center text-xs text-muted-foreground">No sub-folders here</p>
          )}
          {data?.entries.map(e => (
            <button
              key={e.path}
              type="button"
              onClick={() => load(e.path)}
              className="flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-left text-sm hover:bg-muted"
            >
              <Folder className="size-4 shrink-0 text-primary" />
              <span className="truncate">{e.name}</span>
            </button>
          ))}
        </div>
      </ScrollArea>

      <div className="flex items-center gap-2 border-t px-2 py-1.5">
        <Input
          value={newName}
          onChange={e => setNewName(e.target.value)}
          placeholder="New folder name"
          className="h-8"
          disabled={!data?.path}
        />
        <Button
          type="button"
          variant="outline"
          size="icon"
          className="size-8 shrink-0"
          onClick={createFolder}
          disabled={!data?.path || !newName.trim()}
          aria-label="Create folder"
        >
          <FolderPlus className="size-4" />
        </Button>
      </div>
    </div>
  )
}
