<#
.SYNOPSIS
  One-time, run as Administrator: registers the elevated "SixEyes Installer" task that
  lets auto-install run FitGirl setups (they require admin) without a UAC prompt each time.

.EXAMPLE
  ./scripts/register-installer.ps1                       # repo downloads folder
  ./scripts/register-installer.ps1 -AllowedRoots 'D:\Downloads;E:\Torrents'
  ./scripts/register-installer.ps1 -Remove
#>
[CmdletBinding()]
param(
  [string]$AllowedRoots = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')) 'downloads'),
  [string]$JobFile = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')) 'java\controller\data\install-job.json'),
  [string]$TaskName = 'SixEyes Installer',
  [switch]$Remove
)

$ErrorActionPreference = 'Stop'

if ($Remove) {
  Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
  Write-Host "Removed task '$TaskName'." -ForegroundColor Green
  return
}

$runner = Join-Path $PSScriptRoot 'install-runner.ps1'
New-Item -ItemType Directory -Force -Path (Split-Path $JobFile) | Out-Null

$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument (
  "-NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$runner`" " +
  "-JobFile `"$JobFile`" -AllowedRoots `"$AllowedRoots`"")
$principal = New-ScheduledTaskPrincipal -UserId ([Security.Principal.WindowsIdentity]::GetCurrent().Name) `
  -LogonType Interactive -RunLevel Highest
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
  -ExecutionTimeLimit (New-TimeSpan -Hours 3)

Register-ScheduledTask -TaskName $TaskName -Action $action -Principal $principal -Settings $settings -Force | Out-Null
Write-Host "Registered '$TaskName' (elevated). Job file: $JobFile" -ForegroundColor Green
Write-Host "Allowed setup roots: $AllowedRoots"
Write-Host "Set INSTALL_ELEVATED_TASK='$TaskName' and INSTALL_JOB_FILE='$JobFile' for the controller (start.ps1 does it)."
