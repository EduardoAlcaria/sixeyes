import { useTorrents } from '@/hooks/useTorrents'
import { TorrentTable } from '@/components/torrents/TorrentTable'
import { AddTorrentDialog } from '@/components/torrents/AddTorrentDialog'

export function TorrentsPage() {
  const {
    torrents,
    addTorrent,
    addTorrentFile,
    pauseTorrent,
    resumeTorrent,
    removeTorrent,
    loading,
  } = useTorrents()

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-2xl font-bold tracking-tight">
          Torrents <span className="text-muted-foreground font-normal">({torrents.length})</span>
        </h1>
        <AddTorrentDialog onAddMagnet={addTorrent} onAddFile={addTorrentFile} loading={loading} />
      </div>
      <TorrentTable
        torrents={torrents}
        onPause={pauseTorrent}
        onResume={resumeTorrent}
        onRemove={removeTorrent}
      />
    </div>
  )
}
