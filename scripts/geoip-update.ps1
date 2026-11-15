<#
.SYNOPSIS
  Downloads DB-IP's free "IP to Country Lite" database for the peer map.

  Peer IPs are looked up locally against this file; nothing is sent to a web service.
  DB-IP publishes a new edition monthly - rerun this to refresh. Licensed CC BY 4.0
  (https://db-ip.com); the dashboard credits it on the map.
#>
param(
  [string]$StateDir = $(if ($env:STATE_DIR) { $env:STATE_DIR } else { Join-Path $PSScriptRoot '..\python\state' })
)

$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $StateDir | Out-Null
$target = Join-Path $StateDir 'dbip-country-lite.mmdb'
$gz = "$target.gz"

# The current month's file appears a few days in; fall back to last month's.
$months = @((Get-Date), (Get-Date).AddMonths(-1)) | ForEach-Object { $_.ToString('yyyy-MM') }
$ok = $false
foreach ($m in $months) {
  $url = "https://download.db-ip.com/free/dbip-country-lite-$m.mmdb.gz"
  try {
    Invoke-WebRequest -Uri $url -OutFile $gz -UseBasicParsing
    $ok = $true
    Write-Host "Downloaded $url"
    break
  } catch {
    Write-Host "Not available: $url"
  }
}
if (-not $ok) { throw 'Could not download the DB-IP country database.' }

$in = [IO.File]::OpenRead($gz)
$out = [IO.File]::Create("$target.tmp")
try {
  $unzip = New-Object IO.Compression.GZipStream($in, [IO.Compression.CompressionMode]::Decompress)
  $unzip.CopyTo($out)
} finally {
  $out.Dispose(); $in.Dispose()
}
# Replace atomically so the agent never opens a half-written file.
Move-Item -Force "$target.tmp" $target
Remove-Item $gz
Write-Host "GeoIP database ready: $target"
