<#
.SYNOPSIS
  Registers a logon task that runs start.ps1 (agent + controller) so SixEyes survives reboots.
  Remote access: Tailscale (no tunnel service needed). Run once; -Remove to undo.
#>
[CmdletBinding()]
param(
  [string]$TaskName = 'SixEyes',
  [switch]$Lan,
  [switch]$Remove
)
$ErrorActionPreference = 'Stop'
if ($Remove) { Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false; return }

$start = Join-Path $PSScriptRoot 'start.ps1'
$arg = "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$start`"" + $(if ($Lan) { ' -Lan' } else { '' })
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $arg
$user = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $user
$principal = New-ScheduledTaskPrincipal -UserId $user -LogonType Interactive -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
  -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1)
Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Force | Out-Null
Write-Host "Registered '$TaskName' (at logon, $user)." -ForegroundColor Green
