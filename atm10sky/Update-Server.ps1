[CmdletBinding()]
param([string]$ServerDirectory)
$ErrorActionPreference = 'Stop'
if (-not $ServerDirectory) { $ServerDirectory = Read-Host 'Paste the prepared ATM10Sky server folder path' }
$root = (Resolve-Path -LiteralPath $ServerDirectory).Path
$contents = Join-Path $root 'SERVER_CONTENTS.txt'
if (-not (Test-Path -LiteralPath $contents) -or (Get-Content -LiteralPath $contents -Raw) -notmatch 'pack:\s*ATM10SKY 2\.0\.6') { throw 'This requires a prepared ATM10SKY 2.0.6 server.' }
$lock = Join-Path $root 'world\session.lock'
if (Test-Path -LiteralPath $lock) { $handle = [IO.File]::Open($lock,'Open','ReadWrite','None'); $handle.Dispose() }
$mods = Join-Path $root 'mods'
$backup = Join-Path $root ('skycraft-update-backups\' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $backup -Force | Out-Null
foreach ($pattern in @('skycraft-*-atm10sky.*.jar','e4mc-neoforge-*.jar')) {
    $incoming = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'files') -Filter $pattern -File)
    if ($incoming.Count -ne 1) { throw 'Extract a complete release containing exactly one bridge and relay jar.' }
    $jar = $incoming[0]
    $activePattern = if ($jar.Name.StartsWith('skycraft-')) { 'skycraft-*.jar' } else { 'e4mc-*.jar' }
    foreach ($old in Get-ChildItem -LiteralPath $mods -Filter $activePattern -File) {
        $handle = [IO.File]::Open($old.FullName,'Open','ReadWrite','None'); $handle.Dispose()
        Copy-Item -LiteralPath $old.FullName -Destination $backup
    }
    $target = Join-Path $mods $jar.Name
    Copy-Item -LiteralPath $jar.FullName -Destination $target -Force
    if ((Get-FileHash -LiteralPath $target).Hash -ne (Get-FileHash -LiteralPath $jar.FullName).Hash) { throw 'Jar copy verification failed.' }
    Get-ChildItem -LiteralPath $mods -Filter $activePattern -File | Where-Object Name -ne $jar.Name | ForEach-Object { Move-Item -LiteralPath $_.FullName -Destination (Join-Path $backup $_.Name) -Force }
}
. (Join-Path $PSScriptRoot 'Set-CompatibilityConfig.ps1')
Set-SkyCraftCompatibilityConfig -ConfigDirectory (Join-Path $root 'config')
foreach ($name in @('START-SKYCRAFT-SERVER.cmd','Write-Server-Address.ps1')) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $root -Force }
Write-Host 'Server bridge updated. World, players, quests, ops, and settings were retained.'
Write-Host "Previous jars: $backup"
