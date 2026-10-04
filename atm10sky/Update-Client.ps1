[CmdletBinding()]
param([string]$PrismRoot = (Join-Path $env:LOCALAPPDATA 'SkyCraft\Prism'))

$ErrorActionPreference = 'Stop'
$packageRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$instance = Join-Path $PrismRoot 'instances\SkyCraft ATM10SKY'
$packPath = Join-Path $instance 'mmc-pack.json'
if (-not (Test-Path -LiteralPath $packPath)) { throw 'Install the SkyCraft ATM10SKY profile first with INSTALL-ATM10SKY.cmd.' }
$incoming = @(
    @(Get-ChildItem -LiteralPath (Join-Path $packageRoot 'files') -Filter 'skycraft-*-atm10sky.*.jar' -File),
    @(Get-ChildItem -LiteralPath (Join-Path $packageRoot 'files') -Filter 'e4mc-neoforge-*.jar' -File)
)
if ($incoming[0].Count -ne 1 -or $incoming[1].Count -ne 1) { throw 'The package must contain exactly one SkyCraft jar and one e4mc jar.' }
$pack = Get-Content -LiteralPath $packPath -Raw | ConvertFrom-Json
$minecraft = @($pack.components | Where-Object { $_.uid -eq 'net.minecraft' })
$neoforge = @($pack.components | Where-Object { $_.uid -eq 'net.neoforged' })
if ($minecraft.Count -ne 1 -or $minecraft[0].version -ne '1.21.1' -or $neoforge.Count -ne 1) {
    throw 'This updater requires the existing Minecraft 1.21.1 / NeoForge SkyCraft ATM10SKY profile.'
}
if (Get-ChildItem -LiteralPath $instance -Filter '*.lock' -File) { throw 'Close Skyrim and the Minecraft profile before updating.' }
Write-Host 'Close Skyrim and its Minecraft client before updating. Saves, settings, and the server address are retained.'
$backup = Join-Path $instance ('skycraft-update-backups\' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $backup -Force | Out-Null
Copy-Item -LiteralPath $packPath -Destination $backup
$mods = Join-Path $instance '.minecraft\mods'
foreach ($group in $incoming) {
    $jar = $group[0]
    $pattern = if ($jar.Name.StartsWith('skycraft-')) { 'skycraft-*.jar' } else { 'e4mc-*.jar' }
    $target = Join-Path $mods $jar.Name
    # Install first; obsolete jars are moved out of mods only after a verified copy.
    if (Test-Path -LiteralPath $target) { Copy-Item -LiteralPath $target -Destination $backup }
    Copy-Item -LiteralPath $jar.FullName -Destination $target -Force
    if ((Get-FileHash -LiteralPath $target).Hash -ne (Get-FileHash -LiteralPath $jar.FullName).Hash) { throw "Copy verification failed for $($jar.Name)." }
    Get-ChildItem -LiteralPath $mods -Filter $pattern -File | Where-Object { $_.Name -ne $jar.Name } |
        ForEach-Object { Move-Item -LiteralPath $_.FullName -Destination (Join-Path $backup $_.Name) }
}
$neoforge[0].version = '21.1.250'
if ($neoforge[0].PSObject.Properties.Name -contains 'cachedVersion') { $neoforge[0].cachedVersion = '21.1.250' }
Set-Content -LiteralPath $packPath -Value ($pack | ConvertTo-Json -Depth 20) -Encoding ascii
. (Join-Path $packageRoot 'Set-CompatibilityConfig.ps1')
Set-SkyCraftCompatibilityConfig -ConfigDirectory (Join-Path $instance '.minecraft\config')
Write-Host 'Updated SkyCraft and e4mc; NeoForge is pinned to 21.1.250.' -ForegroundColor Green
Write-Host "Installed Minecraft bridge: $($incoming[0][0].Name)" -ForegroundColor Green
Write-Host "Updated mods folder: $mods"
Write-Host "Previous files: $backup"
Write-Host 'Also install this release''s Vortex patch and update the stopped server. Every player must use the same release.'
Write-Host 'Start Skyrim through SKSE to use the generated profile.'
