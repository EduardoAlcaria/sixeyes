# SixEyes Game Catalog - Design Spec
_Date: 2026-06-15_

## Goal

Add a browsable FitGirl repack catalog to SixEyes with search, cover images, and one-click download with folder picker. Tunnel must be running after implementation.

## Approach: Hybrid Aggregation (C)

A-Z crawl populates `catalog_game` with title+url only. Images and magnets are fetched on first user click and cached. Title search is instant SQL ILIKE. FitGirl is only hit when the user actually opens a game card.

---

## Backend

### New Dependency

Add Jsoup 1.17.2 to `java/controller/pom.xml` for HTML parsing.

### New DB Table - `catalog_game`

```sql
id          BIGSERIAL PRIMARY KEY
title       VARCHAR NOT NULL
url         VARCHAR UNIQUE NOT NULL
image_url   VARCHAR          -- null until first click
magnet      VARCHAR          -- null until first click
repack_size VARCHAR          -- null until first click
scraped_at  TIMESTAMP NOT NULL DEFAULT now()
```

Spring `ddl-auto=update` creates it automatically. JPA entity + repo follow the existing pattern (see `Torrent.java`, `TorrentRepository.java`).

### New Endpoints - `/api/v1/catalog` (all JWT-protected)

| Method | Path | Behaviour |
|--------|------|-----------|
| `GET` | `/catalog/search?q=&page=0&size=24` | `ILIKE '%q%'` on title, `Page<CatalogGameResponse>` |
| `GET` | `/catalog/game?url=` | Returns full game row; if image/magnet null, fetches the FitGirl game page with Jsoup, extracts `og:image` + first `a[href^=magnet:]`, upserts, returns enriched row |
| `POST` | `/catalog/refresh` | Starts async A-Z crawl on a background thread (returns 202 immediately); upserts `(title, url)` rows; does NOT overwrite existing image/magnet |

### Scraper Details

- Target: `https://fitgirl-repacks.site/all-my-repacks-a-z/?lcp_page0={n}#lcp_instance_0`
- Stop condition: page returns zero entries OR all entries are duplicates
- User-Agent: same string as `scripts/fitgirl_scrape.py`
- Delay between A-Z pages: 1 s (configurable via `catalog.scrape.delay-ms` property)
- Game detail fetch: parse `<meta property="og:image">` for image, first `a[href^="magnet:"]` for magnet, decode HTML entities (`&#038;` → `&`)

### New Java Files

```
service/CatalogScraperService.java   - A-Z crawl + game-detail fetch (Jsoup)
service/CatalogService.java          - search, getDetails, triggerRefresh
controller/CatalogController.java    - 3 endpoints above
model/CatalogGame.java               - JPA entity
repo/CatalogGameRepository.java      - JpaRepository + ILIKE query
dto/response/CatalogGameResponse.java
```

---

## Frontend

### New Route

`/catalog` added to `App.tsx`. Nav link added to `AppShell.tsx` nav array between Torrents and Completed.

### New Files

```
src/pages/Catalog.tsx                   - page: search bar + grid + refresh button
src/components/catalog/GameCard.tsx     - cover image, title, click handler
src/components/catalog/GameDownloadDialog.tsx - detail dialog with FolderPicker + Download
src/hooks/useCatalog.ts                 - search query, detail fetch, refresh trigger
```

`src/services/api.ts` gets a new `catalogApi` block:
```ts
catalogApi = {
  search: (q: string, page = 0, size = 24) => request<CatalogPage>(...),
  game:   (url: string)                    => request<CatalogGame>(...),
  refresh: ()                              => request<void>('/catalog/refresh', { method: 'POST' }),
}
```

`src/types/index.ts` gets `CatalogGame` and `CatalogPage` interfaces.

### UX Flow

1. User opens `/catalog` - sees search bar + empty state ("Search for a game or refresh the catalog")
2. User types → debounced search → card grid renders (skeleton cards while loading)
3. User clicks card → `GameDownloadDialog` opens
   - If image/magnet not cached: calls `GET /catalog/game?url=` (shows spinner)
   - Once loaded: shows cover, title, `FolderPicker` (existing component, unchanged)
   - "Download" → `torrentApi.add(magnet, path)` → toast success → navigate to `/torrents`
4. "Refresh catalog" button → `POST /catalog/refresh` → toast "Crawl started"

### Components / UI

Reuse all existing primitives: `Button`, `Card`, `Input`, `Dialog`, `Skeleton`, `Badge`, `Separator`, `toast` (Sonner), `FolderPicker`. No new UI library additions.

---

## Tunnel

No code changes. `cloudflared` service already configured in `docker-compose.yml`. Final task: start Docker Desktop, run `docker compose up -d`, verify all 5 containers healthy.

---

## Out of Scope

- IGDB metadata enrichment (deferred, phase 2)
- Background magnet pre-fetch for all games
- Catalog auto-refresh on schedule
