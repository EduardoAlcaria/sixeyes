import { Gamepad2 } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import type { CatalogGame } from '@/types'

interface Props {
  game: CatalogGame
  onClick: () => void
}

export function GameCard({ game, onClick }: Props) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="w-full text-left group focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary rounded-xl"
    >
      <Card className="overflow-hidden transition-all group-hover:ring-2 group-hover:ring-primary/50 group-hover:shadow-md">
        <div className="aspect-[3/4] relative overflow-hidden bg-muted">
          {game.imageUrl ? (
            <img
              src={game.imageUrl}
              alt={game.title}
              className="object-cover w-full h-full transition-transform group-hover:scale-105"
              loading="lazy"
            />
          ) : (
            <div className="flex items-center justify-center w-full h-full text-muted-foreground">
              <Gamepad2 className="size-10 opacity-30" />
            </div>
          )}
        </div>
        <CardContent className="p-2.5">
          <p className="text-xs font-medium line-clamp-2 leading-snug">{game.title}</p>
          {game.repackSize && (
            <p className="text-xs text-muted-foreground mt-0.5">{game.repackSize}</p>
          )}
        </CardContent>
      </Card>
    </button>
  )
}

export function GameCardSkeleton() {
  return (
    <Card className="overflow-hidden">
      <Skeleton className="aspect-[3/4] w-full rounded-none" />
      <CardContent className="p-2.5 space-y-1.5">
        <Skeleton className="h-3 w-full" />
        <Skeleton className="h-3 w-2/3" />
      </CardContent>
    </Card>
  )
}
