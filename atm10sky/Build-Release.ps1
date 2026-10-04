[CmdletBinding()]
param(
    [string]$E4mcJar,
    [string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $root 'dist\atm10sky' }
if (-not $E4mcJar) {
    $E4mcJar = Join-Path $env:LOCALAPPDATA 'SkyCraft\Prism\instances\SkyCraft ATM10SKY\.minecraft\mods\e4mc-neoforge-6.1.1.jar'
}

$jar = Get-ChildItem -LiteralPath (Join-Path $root 'neoforge121\build\libs') -Filter 'skycraft-*-atm10sky.*.jar' -File |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
$dll = Join-Path $root 'skse\build\RelWithDebInfo\SkyCraft.dll'
foreach ($path in @($jar.FullName, $dll, $E4mcJar)) {
    if (-not $path -or -not (Test-Path -LiteralPath $path)) { throw "Missing release input: $path" }
}

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$stage = Join-Path $OutputDirectory 'SkyCraft-ATM10Sky-2.0.6'
if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
New-Item -ItemType Directory -Path (Join-Path $stage 'files') -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'README.md') -Destination $stage
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'START-HERE.txt') -Destination $stage
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'INSTALL-ATM10SKY.cmd') -Destination $stage
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Install-Client.ps1') -Destination $stage
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'CHANGE-SERVER-ADDRESS.cmd') -Destination $stage
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Change-Server-Address.ps1') -Destination $stage
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Prepare-Server.ps1') -Destination $stage
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'START-SKYCRAFT-SERVER.cmd') -Destination $stage
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Write-Server-Address.ps1') -Destination $stage
Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $stage 'files\skycraft-0.1.2-atm10sky.15.jar')
Copy-Item -LiteralPath $E4mcJar -Destination (Join-Path $stage 'files\e4mc-neoforge-6.1.1.jar')
Copy-Item -LiteralPath (Join-Path $root 'LICENSE') -Destination (Join-Path $stage 'LICENSE-SkyCraft.txt')
Copy-Item -LiteralPath (Join-Path $root 'THIRD-PARTY-NOTICES.md') -Destination $stage

$vortex = Join-Path $OutputDirectory 'vortex-stage'
if (Test-Path -LiteralPath $vortex) { Remove-Item -LiteralPath $vortex -Recurse -Force }
$plugins = Join-Path $vortex 'SKSE\Plugins'
New-Item -ItemType Directory -Path (Join-Path $plugins 'SkyCraft') -Force | Out-Null
Copy-Item -LiteralPath $dll -Destination (Join-Path $plugins 'SkyCraft.dll')
Copy-Item -LiteralPath (Join-Path $root 'skse\SkyCraft.ini') -Destination (Join-Path $plugins 'SkyCraft.ini')
Copy-Item -LiteralPath (Join-Path $root 'LICENSE') -Destination (Join-Path $plugins 'SkyCraft\LICENSE.txt')
Copy-Item -LiteralPath (Join-Path $root 'THIRD-PARTY-NOTICES.md') -Destination (Join-Path $plugins 'SkyCraft\THIRD-PARTY-NOTICES.md')

$vortexZip = Join-Path $stage 'SkyCraft-ATM10Sky-Vortex-Patch.zip'
Compress-Archive -Path (Join-Path $vortex '*') -DestinationPath $vortexZip -CompressionLevel Optimal -Force
Remove-Item -LiteralPath $vortex -Recurse -Force

$releaseZip = Join-Path $OutputDirectory 'SkyCraft-ATM10Sky-2.0.6.zip'
Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $releaseZip -CompressionLevel Optimal -Force

Get-FileHash -LiteralPath $releaseZip,$vortexZip -Algorithm SHA256 | Select-Object Path,Hash
