<#
.SYNOPSIS
  Elevated half of auto-install. Runs from the "SixEyes Installer" scheduled task
  (highest privileges) because FitGirl setups demand admin. SixEyes itself stays
  unprivileged: it only writes a job file and triggers the task.

  The job file is untrusted input, so this re-checks everything: the setup must be a
  setup*.exe inside one of the download roots fixed at registration time, and only the
  install folder is taken from the job - the command line is built here.
#>
param(
  [Parameter(Mandatory)][string]$JobFile,
  [Parameter(Mandatory)][string]$AllowedRoots   # ';'-separated, fixed when registering
)

$ErrorActionPreference = 'Stop'
$resultFile = "$JobFile.result"

function Write-Result([int]$code, [string]$message) {
  $json = @{ exitCode = $code; message = $message; finishedAt = (Get-Date).ToString('o') } | ConvertTo-Json
  # [IO.File] writes UTF-8 with no BOM; Set-Content -Encoding UTF8 on PS 5.1 adds one,
  # and the leading BOM makes the controller's JSON parser reject the result forever.
  [IO.File]::WriteAllText($resultFile, $json, (New-Object Text.UTF8Encoding $false))
}

# A symlink/junction named setup.exe (or a linked parent dir) inside an allowed root would
# point FullName at the link but Start() at the real target, escaping the root to run an
# arbitrary binary elevated. The unprivileged side can plant such a link, so refuse any
# reparse point on setup.exe or on any parent up to (and including) the matched root.
function Test-NoReparse([string]$path, [string]$rootDir) {
  $p = $path
  while ($p) {
    if (-not (Test-Path -LiteralPath $p)) { return $false }
    if (([IO.File]::GetAttributes($p) -band [IO.FileAttributes]::ReparsePoint) -ne 0) { return $false }
    if ($p.TrimEnd('\') -ieq $rootDir.TrimEnd('\')) { return $true }
    $parent = [IO.Path]::GetDirectoryName($p)
    if ($parent -eq $p) { return $false }   # reached the drive root without meeting rootDir
    $p = $parent
  }
  return $false
}

try {
  $job = Get-Content -LiteralPath $JobFile -Raw | ConvertFrom-Json
  $setup = (Get-Item -LiteralPath $job.setup).FullName
  $dir = [string]$job.dir

  $inRoot = $false
  foreach ($r in ($AllowedRoots -split ';' | Where-Object { $_ })) {
    $rootDir = (Get-Item -LiteralPath $r).FullName
    $root = $rootDir.TrimEnd('\') + '\'
    if ($setup.StartsWith($root, [StringComparison]::OrdinalIgnoreCase) -and (Test-NoReparse $setup $rootDir)) { $inRoot = $true }
  }
  if (-not $inRoot) { Write-Result 98 "Refused: $setup is outside the download folders or crosses a link"; exit 98 }
  if ([IO.Path]::GetFileName($setup) -notmatch '^setup.*\.exe$') { Write-Result 98 'Refused: not a setup*.exe'; exit 98 }
  # FitGirl repacks only: their layout always ships MD5\fitgirl-bins.md5 next to setup.exe.
  $md5 = [IO.Path]::Combine([IO.Path]::GetDirectoryName($setup), 'MD5', 'fitgirl-bins.md5')
  if (-not (Test-Path -LiteralPath $md5)) { Write-Result 98 'Refused: not a FitGirl repack layout'; exit 98 }
  if (-not [IO.Path]::IsPathRooted($dir) -or $dir -match '["\r\n]') { Write-Result 98 'Refused: bad install folder'; exit 98 }

  $psi = New-Object System.Diagnostics.ProcessStartInfo
  $psi.FileName = $setup
  # No /COMPONENTS: forcing "text" also dropped the voice packs the user chose to download
  # (TLOU ships Portuguese-Brazil as component locra). The installer's own default selection
  # skips selective files that are absent. The redists it starts are killed by the watcher.
  # /VERYSILENT does NOT skip [Run] postinstall entries (only skipifsilent ones).
  $psi.Arguments = "/VERYSILENT /SUPPRESSMSGBOXES /NORESTART /NOCANCEL /SP- `"/DIR=$dir`""
  $psi.WorkingDirectory = [IO.Path]::GetDirectoryName($setup)
  $psi.UseShellExecute = $false

  $hostsFile = Join-Path $env:SystemRoot 'System32\drivers\etc\hosts'
  $hostsBefore = (Get-FileHash -LiteralPath $hostsFile -Algorithm SHA256).Hash

  $p = [System.Diagnostics.Process]::Start($psi)

  # Post-install [Run] steps that block or misbehave unattended: QuickSFV (GUI window; we
  # verify natively before this), the redist installers, and the repack's host.cmd, which
  # edits the Windows hosts file. Kill them as they appear; only ones launched from the
  # install folder, or host.cmd by name.
  $killed = New-Object System.Collections.Generic.List[string]
  $dirPrefix = $dir.TrimEnd('\') + '\'
  while (-not $p.WaitForExit(500)) {
    Get-CimInstance Win32_Process -Filter "Name='QuickSFV.exe' OR Name='dxwebsetup.exe' OR Name LIKE 'vc_redist%' OR Name='cmd.exe'" -ErrorAction SilentlyContinue | ForEach-Object {
      $hit = if ($_.Name -ieq 'cmd.exe') { $_.CommandLine -match 'host\.cmd' }
             else { $_.ExecutablePath -and $_.ExecutablePath.StartsWith($dirPrefix, [StringComparison]::OrdinalIgnoreCase) }
      if ($hit) {
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
        $killed.Add($_.Name)
      }
    }
  }

  $msg = "setup.exe exited with code $($p.ExitCode)"
  if ($killed.Count) { $msg += "; stopped: $(($killed | Select-Object -Unique) -join ', ')" }
  if ((Get-FileHash -LiteralPath $hostsFile -Algorithm SHA256).Hash -ne $hostsBefore) {
    $msg += '; WARNING: the hosts file changed during install - review it'
  }
  Write-Result $p.ExitCode $msg
  exit $p.ExitCode
} catch {
  Write-Result 97 $_.Exception.Message
  exit 97
}
