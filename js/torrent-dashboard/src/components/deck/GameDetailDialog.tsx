import { Play } from 'lucide-react'
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { bytes, minutes, when } from '@/lib/format'
import type { Game } from '@/types'
import { Bar, Cover, Fact, Panel, SourceBadge, steamArt } from './primitives'

const DAYS = ['D', 'S', 'T', 'Q', 'Q', 'S', 'S']

export function GameDetailDialog({ game, onClose, onLaunch, launching }: {
  game: Game | null
  onClose: () => void
  onLaunch: (g: Game) => void
  launching: boolean
}) {
  if (!game) return null
  const hours = game.playtimeMinutes / 60
  const beat = game.hltbMain ? Math.min(100, (hours / game.hltbMain) * 100) : null
  const max = Math.max(1, ...game.last7DaysHours)
  const today = new Date().getDay()

  return (
    <Dialog open onOpenChange={open => { if (!open) onClose() }}>
      <DialogContent className="max-h-[90vh] overflow-y-auto p-5 sm:max-w-3xl">
        <div className="flex flex-col gap-4">
          <div className="flex flex-wrap gap-4">
            <Cover className="aspect-[3/4] w-28 shrink-0 rounded-[calc(var(--radius)*0.6)]" seed={game.name}
              sources={game.source === 'STEAM' ? steamArt(game.key.slice(6)) : [game.coverUrl]} />
            <div className="flex min-w-[220px] flex-1 flex-col gap-2.5">
              <DialogTitle className="text-xl font-extrabold"><SourceBadge source={game.source} />{game.name}</DialogTitle>
              <div className="flex flex-wrap gap-1.5">
                {(game.genres ?? '').split(',').map(g => g.trim()).filter(Boolean).map(g => (
                  <span key={g} className="rounded-full border bg-panel2 px-2.5 py-0.5 text-[0.7rem] text-muted-foreground">{g}</span>
                ))}
                {game.developers && <span className="rounded-full border bg-panel2 px-2.5 py-0.5 text-[0.7rem] text-muted-foreground">{game.developers}</span>}
              </div>
              {game.summary && <p className="line-clamp-4 max-w-prose text-[0.84rem] text-muted-foreground">{game.summary}</p>}
              <div>
                <button type="button" disabled={launching} onClick={() => onLaunch(game)}
                  className="inline-flex items-center gap-2 rounded-xl bg-ok px-5 py-2.5 text-sm font-extrabold text-[#06231a] disabled:opacity-60">
                  <Play className="size-4" /> {launching ? 'Abrindo…' : 'Jogar'}
                </button>
              </div>
            </div>
          </div>

          <Panel title="Jogo">
            <div className="grid grid-cols-2 gap-2.5 md:grid-cols-4">
              <Fact label="Tempo jogado" value={minutes(game.playtimeMinutes)} />
              <Fact label="Pra zerar (HLTB)" value={game.hltbMain ? `${Math.round(game.hltbMain)}h` : '—'} />
              <Fact label="Sessões" value={game.sessions || '—'} />
              <Fact label="Sessão média" value={game.avgSessionMinutes ? minutes(game.avgSessionMinutes) : '—'} />
              <Fact label="Última vez" value={when(game.lastPlayed)} />
              <Fact label="Espaço" value={bytes(game.sizeBytes)} />
              <Fact label="Fonte" value={game.source === 'TORRENT' ? 'FitGirl' : game.source === 'STEAM' ? 'Steam' : 'Pasta local'} />
              <Fact label="Pasta" value={<span className="text-xs">{game.installDir ?? '—'}</span>} />
            </div>
            {beat != null && (
              <div className="mt-3">
                <div className="mb-1 flex justify-between text-[0.72rem] text-muted-foreground">
                  <span>Progresso estimado pelo HLTB</span><b className="font-mono text-foreground">{Math.round(beat)}%</b>
                </div>
                <Bar value={beat} tone={beat >= 100 ? 'ok' : 'flow'} />
              </div>
            )}
          </Panel>

          <Panel title="Horas jogadas · 7 dias" note="sessões que o SixEyes viu">
            <div className="flex h-32 items-end gap-2">
              {game.last7DaysHours.map((h, i) => (
                <div key={i} className="flex flex-1 flex-col items-center gap-1">
                  <span className="font-mono text-[0.62rem] text-faint">{h ? `${h}h` : ''}</span>
                  <div className="w-full rounded-t-sm" style={{ height: `${(h / max) * 90}px`, minHeight: 2,
                    background: i === 6 ? 'var(--cyan)' : 'var(--violet)' }} />
                  <span className="text-[0.62rem] text-faint">{DAYS[(today - 6 + i + 7) % 7]}</span>
                </div>
              ))}
            </div>
          </Panel>
        </div>
      </DialogContent>
    </Dialog>
  )
}
