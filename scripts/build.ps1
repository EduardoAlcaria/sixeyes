<#
.SYNOPSIS
  Builds SixEyes for a native (no Docker) run.

  Compiles the dashboard, copies it into the Spring Boot static resources so one process
  serves both the SPA and the API from the same origin, then packages the jar.

.EXAMPLE
  ./scripts/build.ps1
  ./scripts/build.ps1 -SkipTests
#>
[CmdletBinding()]
param(
  [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'

$root       = Resolve-Path (Join-Path $PSScriptRoot '..')
$dashboard  = Join-Path $root 'js\torrent-dashboard'
$controller = Join-Path $root 'java\controller'
$static     = Join-Path $controller 'src\main\resources\static'
$venv       = Join-Path $root 'python\.venv'
$venvPython = Join-Path $venv 'Scripts\python.exe'

# npm, uv and maven all write progress to stderr, which PowerShell 5.1 turns into a
# terminating error under ErrorActionPreference=Stop. Judge them by their exit code.
function Invoke-Native {
  param([Parameter(Mandatory)][scriptblock]$Command, [Parameter(Mandatory)][string]$What)
  $ErrorActionPreference = 'Continue'
  & $Command
  if ($LASTEXITCODE -ne 0) { throw "$What failed (exit $LASTEXITCODE)" }
}

# --- Python agent ------------------------------------------------------------
# libtorrent ships no cp314 wheel, so the venv must be 3.13 or older. uv fetches the
# runtime without touching the system Python.
if (-not (Test-Path $venvPython)) {
  Write-Host '==> Creating Python 3.13 venv' -ForegroundColor Cyan
  Invoke-Native { python -m uv venv $venv --python 3.13 } 'venv creation'
}
Write-Host '==> Installing Python dependencies' -ForegroundColor Cyan
Invoke-Native {
  python -m uv pip install --python $venvPython -r (Join-Path $root 'python\requirements.txt')
} 'pip install'

# --- Dashboard ---------------------------------------------------------------
Write-Host '==> Building dashboard' -ForegroundColor Cyan
Push-Location $dashboard
try {
  if (Test-Path 'package-lock.json') {
    Invoke-Native { npm ci } 'npm ci'
  } else {
    Invoke-Native { npm install } 'npm install'
  }
  Invoke-Native { npm run build } 'dashboard build'
} finally {
  Pop-Location
}

Write-Host '==> Copying dashboard into Spring static resources' -ForegroundColor Cyan
Get-ChildItem -Path $static -Exclude '.gitkeep' | Remove-Item -Recurse -Force
Copy-Item -Path (Join-Path $dashboard 'dist\*') -Destination $static -Recurse -Force

# --- Controller --------------------------------------------------------------
Write-Host '==> Packaging controller' -ForegroundColor Cyan
Push-Location $controller
try {
  # .cmd explicitly - PowerShell would otherwise try the extension-less shell script.
  if ($SkipTests) {
    Invoke-Native { .\mvnw.cmd -B clean package -DskipTests } 'maven package'
  } else {
    Invoke-Native { .\mvnw.cmd -B clean package } 'maven package'
  }
} finally {
  Pop-Location
}

Write-Host "`nBuild complete. Run ./scripts/start.ps1" -ForegroundColor Green
