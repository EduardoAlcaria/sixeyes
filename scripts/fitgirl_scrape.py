"""Scrape the full FitGirl A-Z repack catalog.

The A-Z page renders its list with the WordPress "List Category Posts" (LCP)
plugin, which paginates via ?lcp_page0=N#lcp_instance_0. One page shows only a
slice of the catalog, so we walk lcp_page0 from 1 upward until a page returns no
game entries, collecting every (title, url) along the way.

Usage:
    python scripts/fitgirl_scrape.py                 # full crawl -> fitgirl_games.json
    python scripts/fitgirl_scrape.py --dump-only     # just save page 1 HTML
    python scripts/fitgirl_scrape.py --max-pages 5   # cap pages (debug)
    python scripts/fitgirl_scrape.py --out games.json --delay 1.5
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import time

import requests

BASE = "https://fitgirl-repacks.site/all-my-repacks-a-z/"
OUT = "fitgirl_games.json"

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    ),
    "Accept": (
        "text/html,application/xhtml+xml,application/xml;q=0.9,"
        "image/avif,image/webp,*/*;q=0.8"
    ),
    "Accept-Language": "en-US,en;q=0.9",
    "Connection": "keep-alive",
    "Upgrade-Insecure-Requests": "1",
}

# The LCP list container on this page. Game links live inside it as <li><a>.
_LCP_RE = re.compile(
    r'<ul class="lcp_catlist"[^>]*id="lcp_instance_0"[^>]*>(.*?)</ul>',
    re.S,
)
_LINK_RE = re.compile(
    r'<li[^>]*>\s*<a\s+href="(https://fitgirl-repacks\.site/[^"]+)"[^>]*>(.*?)</a>',
    re.S,
)
_TAG_RE = re.compile(r"<[^>]+>")


def _clean(text: str) -> str:
    text = _TAG_RE.sub("", text)
    text = (
        text.replace("&amp;", "&")
        .replace("&#8217;", "’")
        .replace("&#8211;", "–")
        .replace("&#038;", "&")
        .replace("&nbsp;", " ")
    )
    return text.strip()


def fetch(session: requests.Session, page: int) -> str:
    url = f"{BASE}?lcp_page0={page}#lcp_instance_0"
    resp = session.get(url, timeout=30)
    resp.raise_for_status()
    return resp.text


def extract(html: str) -> list[dict]:
    """Pull (title, url) game entries from the LCP list block on one page."""
    block = _LCP_RE.search(html)
    if not block:
        return []
    games = []
    for href, raw_title in _LINK_RE.findall(block.group(1)):
        title = _clean(raw_title)
        if title:
            games.append({"title": title, "url": href})
    return games


def crawl(out: str, max_pages: int, delay: float) -> int:
    seen: set[str] = set()
    games: list[dict] = []
    with requests.Session() as s:
        s.headers.update(HEADERS)
        page = 1
        while page <= max_pages:
            try:
                html = fetch(s, page)
            except requests.RequestException as e:
                print(f"page {page}: request failed: {e}", file=sys.stderr)
                break

            batch = extract(html)
            if not batch:
                print(f"page {page}: 0 entries — end of catalog.")
                break

            new = 0
            for g in batch:
                if g["url"] not in seen:
                    seen.add(g["url"])
                    games.append(g)
                    new += 1
            print(f"page {page}: {len(batch)} entries ({new} new) — total {len(games)}")

            # All-duplicate page = LCP looped back to the start; stop.
            if new == 0:
                print(f"page {page}: all duplicates — stopping.")
                break

            page += 1
            time.sleep(delay)

    with open(out, "w", encoding="utf-8") as f:
        json.dump(games, f, ensure_ascii=False, indent=2)
    print(f"\nwrote {len(games)} games -> {out}")
    return 0


def dump_only() -> int:
    with requests.Session() as s:
        s.headers.update(HEADERS)
        html = fetch(s, 1)
    with open("fitgirl_az.html", "w", encoding="utf-8") as f:
        f.write(html)
    print(f"wrote fitgirl_az.html ({len(html)} chars); page-1 entries: {len(extract(html))}")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=OUT)
    ap.add_argument("--max-pages", type=int, default=500)
    ap.add_argument("--delay", type=float, default=1.0)
    ap.add_argument("--dump-only", action="store_true")
    args = ap.parse_args()
    if args.dump_only:
        return dump_only()
    return crawl(args.out, args.max_pages, args.delay)


if __name__ == "__main__":
    raise SystemExit(main())
