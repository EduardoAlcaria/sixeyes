<#
.SYNOPSIS
  Starts SixEyes natively: the Python torrent agent and the Spring Boot controller.

  Replaces `docker compose up`. No Postgres (H2 file), no nginx (Spring serves the SPA),
  no containers. Remote access still goes through the Cloudflare tunnel, which runs as its
  own Windows service - see docs/runbook-native.md.

.EXAMPLE
  ./scripts/start.ps1
  ./scripts/start.ps1 -Lan          # also listen on the LAN, not just loopback
#>
[CmdletBinding()]
param(
  [string]$EnvFile,
  [switch]$Lan
)

$ErrorActionPreference = 'Stop'
# $PSScriptRoot can be empty inside param() defaults on Windows PowerShell 5.1
if (-not $PSScriptRoot) { $PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path }
if (-not $EnvFile) { $EnvFile = Join-Path $PSScriptRoot '..\.env' }

$root       = Resolve-Path (Join-Path $PSScriptRoot '..')
$controller = Join-Path $root 'java\controller'
$agentPy    = Join-Path $root 'python\.venv\Scripts\python.exe'

function Import-DotEnv([string]$Path) {
  if (-not (Test-Path $Path)) {
    Write-Warning ".env not found at $Path - using defaults."
    return
  }
  Get-Content $Path | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
      $key, $value = $line -split '=', 2
      if ($key -and $value) {
        # A value already in the real environment wins over the file.
        if (-not [Environment]::GetEnvironmentVariable($key.Trim())) {
          Set-Item -Path "Env:\$($key.Trim())" -Value $value.Trim().Trim('"').Trim("'")
        }
      }
    }
  }
}

Import-DotEnv $EnvFile

if (-not (Test-Path $agentPy)) { throw "Python venv missing. Run ./scripts/build.ps1 first." }
$jar = Get-ChildItem (Join-Path $controller 'target\sixeyes-*.jar') -ErrorAction SilentlyContinue |
       Where-Object { $_.Name -notlike '*-plain.jar' } | Select-Object -First 1
if (-not $jar) { throw "Controller jar missing. Run ./scripts/build.ps1 first." }

# The agent exposes filesystem browse and mkdir on loopback, where every local process can
# reach it. A fresh random key each run means the secret never sits on disk.
# RNGCryptoServiceProvider, not RandomNumberGenerator::Fill - Windows PowerShell 5.1 runs
# on .NET Framework, which does not have Fill.
$bytes = New-Object byte[] 32
$rng = New-Object System.Security.Cryptography.RNGCryptoServiceProvider
try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
$env:AGENT_KEY = [Convert]::ToBase64String($bytes)

if (-not $env:SPRING_PROFILES_ACTIVE) { $env:SPRING_PROFILES_ACTIVE = 'local' }
if (-not $env:PYTHON_SERVICE_URL)     { $env:PYTHON_SERVICE_URL = 'http://127.0.0.1:9999' }
if (-not $env:STATE_DIR)              { $env:STATE_DIR = (Join-Path $root 'python\state') }
if (-not $env:DOWNLOAD_PATH)          { $env:DOWNLOAD_PATH = (Join-Path $root 'downloads') }
# Same folder for both processes, so the Settings default is one the agent may write to.
if (-not $env:DEFAULT_DOWNLOAD_PATH)  { $env:DEFAULT_DOWNLOAD_PATH = $env:DOWNLOAD_PATH }
if ($Lan)                             { $env:SERVER_ADDRESS = '0.0.0.0' }
# FitGirl setups need admin; use the elevated task when register-installer.ps1 created it.
if (-not $env:INSTALL_ELEVATED_TASK -and (Get-ScheduledTask -TaskName 'SixEyes Installer' -ErrorAction SilentlyContinue)) {
  $env:INSTALL_ELEVATED_TASK = 'SixEyes Installer'
  $env:INSTALL_JOB_FILE = Join-Path $controller 'data\install-job.json'
}

New-Item -ItemType Directory -Force -Path $env:STATE_DIR, $env:DOWNLOAD_PATH | Out-Null

Write-Host 'Starting Python agent on 127.0.0.1:9999' -ForegroundColor Cyan
$agent = Start-Process -FilePath $agentPy `
  -ArgumentList '-m', 'uvicorn', 'wsgi:app', '--host', '127.0.0.1', '--port', '9999' `
  -WorkingDirectory (Join-Path $root 'python') -PassThru -NoNewWindow

Write-Host "Starting controller on $(if ($Lan) { '0.0.0.0' } else { '127.0.0.1' }):9123" -ForegroundColor Cyan
$api = Start-Process -FilePath 'java' -ArgumentList '-jar', $jar.FullName `
  -WorkingDirectory $controller -PassThru -NoNewWindow

Write-Host "`nDashboard: http://127.0.0.1:9123/   (Ctrl+C to stop both)" -ForegroundColor Green

try {
  while (-not $agent.HasExited -and -not $api.HasExited) { Start-Sleep -Seconds 1 }
  Write-Warning 'One process exited - shutting the other down.'
} finally {
  foreach ($p in @($agent, $api)) {
    if ($p -and -not $p.HasExited) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue }
  }
}
