# SixEyes - internet speed test
# Measures the host's real download/upload line in a throwaway container,
# then prints the BT-relevant context. No host install needed (uses Docker).
#
# Usage:  ./scripts/speedtest.ps1

Write-Host "Running internet speed test (this can take ~30s)..." -ForegroundColor Cyan

docker run --rm python:3.12-alpine sh -c "pip install -q speedtest-cli 2>/dev/null && speedtest-cli --simple"

if ($LASTEXITCODE -ne 0) {
    Write-Host "speedtest-cli failed (server list issue?). Trying librespeed fallback..." -ForegroundColor Yellow
    docker run --rm ghcr.io/librespeed/speedtest-cli:latest --json 2>$null
}

Write-Host "`nReference:" -ForegroundColor DarkGray
Write-Host "  Mbit/s / 8 = MB/s.  A torrent maxing your line should hit ~(download Mbit/s ÷ 8) MB/s." -ForegroundColor DarkGray
Write-Host "  If this line is fast but torrents are slow, the bottleneck is the swarm/peers, not your ISP." -ForegroundColor DarkGray
