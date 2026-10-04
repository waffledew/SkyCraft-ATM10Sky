[CmdletBinding()]
param([string]$ConfigDirectory)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Set-CompatibilityConfig.ps1')
if (-not $ConfigDirectory) {
    $server = Read-Host 'For a server, paste its folder path. For this PC''s client, leave blank'
    $ConfigDirectory = if ($server.Trim()) { Join-Path $server.Trim().Trim('"') 'config' } else {
        Join-Path $env:LOCALAPPDATA 'SkyCraft\Prism\instances\SkyCraft ATM10SKY\.minecraft\config'
    }
}
if (-not (Test-Path -LiteralPath $ConfigDirectory)) { throw 'Configuration folder not found. Install the client or prepare the server first.' }
Set-SkyCraftCompatibilityConfig -ConfigDirectory $ConfigDirectory
Write-Host 'Terrain collision compatibility settings applied. Restart Minecraft and the server.'
