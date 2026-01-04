<#
.SYNOPSIS
  SixEyes host auto-installer. Polls the API for install requests (triggered by
  the "Install" button in the dashboard) and runs each finished game's FitGirl /
  InnoSetup setup.exe silently on this Windows machine, reporting status back.

  Must run on the Windows HOST (the Docker agent is Linux and cannot run .exe).
  Run an elevated PowerShell so redist installers (VC++, DirectX) can elevate.

.EXAMPLE
  # See what it WOULD do, without installing anything:
  ./scripts/auto-install.ps1 -DryRun

  # Run for real, installing games under D:\Games:
  ./scripts/auto-install.ps1 -InstallRoot 'D:\Games'
#>
[CmdletBinding()]
param(
  [string]$ApiBase        = 'http://localhost:9090/api/v1',
  # Credentials are read from the repo .env (ADMIN_USERNAME / ADMIN_PASSWORD),
  # or from $env:SIXEYES_USERNAME / $env:SIXEYES_PASSWORD, or you're prompted.
  # Never hardcoded. -EnvFile overrides the .env location.
  [string]$EnvFile        = (Join-Path $PSScriptRoot '..\.env'),
  [string]$Username,
  [string]$Password,
  [string]$InstallRoot    = 'D:\Games',
  # Host folder that the agent mounts as /app/downloads (this repo's ./downloads):
  [string]$DownloadsHostDir = (Join-Path $PSScriptRoot '..\downloads' | Resolve-Path -ErrorAction SilentlyContinue),
  [int]$PollSeconds       = 15,
  [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

# --- load .env into the process environment ---------------------------------
function Import-DotEnv([string]$Path) {
  if (-not (Test-Path $Path)) {
    Write-Warning ".env not found at $Path — relying on existing env / prompt."
    return
  }
  Get-Content $Path | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
      $key, $value = $line -split '=', 2
      if ($key -and $value) {
        $key   = $key.Trim()
        $value = $value.Trim().Trim('"').Trim("'")
        # Don't clobber a value already set in the real environment (it wins).
        if (-not [Environment]::GetEnvironmentVariable($key)) {
          Set-Item -Path "Env:\$key" -Value $value
        }
      }
    }
  }
  Write-Host ".env loaded from $Path" -ForegroundColor DarkGray
}

Import-DotEnv $EnvFile

# Resolve credentials: explicit param > SIXEYES_* > ADMIN_* (.env) > prompt.
if (-not $Username) { $Username = $env:SIXEYES_USERNAME; if (-not $Username) { $Username = $env:ADMIN_USERNAME } }
if (-not $Password) { $Password = $env:SIXEYES_PASSWORD; if (-not $Password) { $Password = $env:ADMIN_PASSWORD } }
if (-not $Username) { $Username = 'adm@adm.com' }

# --- container path -> Windows path -----------------------------------------
function Convert-ToHostPath([string]$p) {
  if (-not $p) { return $null }
  if ($p -match '^/host/([A-Za-z])(/.*)?$') {
    $drive = $Matches[1].ToUpper()
    $rest  = ($Matches[2] -replace '/', '\')
    return "$drive`:$rest"
  }
  if ($p -like '/app/downloads*') {
    if (-not $DownloadsHostDir) { throw "DownloadsHostDir not set; cannot map $p" }
    $rest = $p.Substring('/app/downloads'.Length) -replace '/', '\'
    return (Join-Path $DownloadsHostDir $rest.TrimStart('\'))
  }
  return ($p -replace '/', '\')   # already a windows-ish path
}

# --- API helpers ------------------------------------------------------------
$script:Token = $null
function Connect-Api {
  if (-not $Password) {
    $cred = Get-Credential -UserName $Username -Message 'SixEyes API password'
    $script:Username = $cred.UserName
    $script:Password = $cred.GetNetworkCredential().Password
  }
  $body = @{ username = $Username; password = $Password } | ConvertTo-Json
  $res  = Invoke-RestMethod -Method Post -Uri "$ApiBase/auth/login" -ContentType 'application/json' -Body $body
  $script:Token = $res.token
}
function Invoke-Api([string]$Method, [string]$Path, $Body) {
  $headers = @{ Authorization = "Bearer $script:Token" }
  $args = @{ Method = $Method; Uri = "$ApiBase$Path"; Headers = $headers }
  if ($Body) { $args.ContentType = 'application/json'; $args.Body = ($Body | ConvertTo-Json) }
  try { return Invoke-RestMethod @args }
  catch {
    if ($_.Exception.Response -and $_.Exception.Response.StatusCode.value__ -eq 401) {
      Connect-Api; $headers.Authorization = "Bearer $script:Token"; $args.Headers = $headers
      return Invoke-RestMethod @args
    }
    throw
  }
}
function Set-InstallStatus([int]$Id, [string]$Status, [string]$Message) {
  Invoke-Api 'Put' "/torrents/$Id/install/status" @{ status = $Status; message = $Message } | Out-Null
}

# --- find the installer -----------------------------------------------------
function Find-Setup([string]$root) {
  if (-not (Test-Path $root)) { return $null }
  # shallowest setup.exe wins (FitGirl puts it at the game root)
  Get-ChildItem -Path $root -Recurse -Filter 'setup.exe' -File -ErrorAction SilentlyContinue |
    Sort-Object { ($_.FullName -split '[\\/]').Count } |
    Select-Object -First 1
}

function Install-Game($job) {
  $hostPath = Convert-ToHostPath $job.savePath
  Write-Host "[$($job.id)] $($job.title)" -ForegroundColor Cyan
  Write-Host "      savePath: $($job.savePath) -> $hostPath"

  $setup = Find-Setup $hostPath
  if (-not $setup) {
    $msg = "No setup.exe found under $hostPath"
    Write-Host "      $msg" -ForegroundColor Yellow
    if (-not $DryRun) { Set-InstallStatus $job.id 'FAILED' $msg }
    return
  }

  $safeName = ($job.title -replace '[\\/:*?"<>|]', '_').Trim()
  if (-not $safeName) { $safeName = "game-$($job.id)" }
  $target = Join-Path $InstallRoot $safeName

  # FitGirl repacks are InnoSetup-based: these flags do a no-UI install.
  $flags = @('/VERYSILENT','/SUPPRESSMSGBOXES','/NORESTART','/NOCANCEL','/SP-', "/DIR=$target")

  Write-Host "      setup:   $($setup.FullName)"
  Write-Host "      command: `"$($setup.FullName)`" $($flags -join ' ')" -ForegroundColor Green

  if ($DryRun) { Write-Host "      [dry-run] not executing" -ForegroundColor DarkGray; return }

  Set-InstallStatus $job.id 'INSTALLING' "Running setup -> $target"
  try {
    $p = Start-Process -FilePath $setup.FullName -ArgumentList $flags -Wait -PassThru
    if ($p.ExitCode -eq 0) {
      Set-InstallStatus $job.id 'INSTALLED' "Installed to $target"
      Write-Host "      INSTALLED -> $target" -ForegroundColor Green
    } else {
      Set-InstallStatus $job.id 'FAILED' "setup.exe exited with code $($p.ExitCode)"
      Write-Host "      FAILED (exit $($p.ExitCode))" -ForegroundColor Red
    }
  } catch {
    Set-InstallStatus $job.id 'FAILED' $_.Exception.Message
    Write-Host "      FAILED: $($_.Exception.Message)" -ForegroundColor Red
  }
}

# --- main loop --------------------------------------------------------------
Write-Host "SixEyes auto-installer -> $ApiBase  (installs to $InstallRoot)  DryRun=$DryRun" -ForegroundColor Cyan
Connect-Api
Write-Host "Authenticated. Watching the install queue every ${PollSeconds}s. Ctrl+C to stop.`n"

while ($true) {
  try {
    $queue = Invoke-Api 'Get' '/torrents/install/queue'
    foreach ($job in @($queue)) { Install-Game $job }
  } catch {
    Write-Host "poll error: $($_.Exception.Message)" -ForegroundColor Yellow
  }
  Start-Sleep -Seconds $PollSeconds
}
