[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$PackageRoot,
    [Parameter(Mandatory=$true)][string]$CurseForgeInstance,
    [Parameter(Mandatory=$true)][string]$PrismExecutable,
    [Parameter(Mandatory=$true)][string]$ServerZip,
    [Parameter(Mandatory=$true)][string]$TestDirectory
)
$ErrorActionPreference = 'Stop'
if (Test-Path -LiteralPath $TestDirectory) { throw 'Choose a new empty test directory.' }
New-Item -ItemType Directory -Path $TestDirectory -Force | Out-Null
$prism = Join-Path $TestDirectory 'Prism with spaces'
New-Item -ItemType Directory -Path $prism -Force | Out-Null
Copy-Item -LiteralPath $PrismExecutable -Destination (Join-Path $prism 'prismlauncher.exe')
$manifest = Join-Path $CurseForgeInstance 'manifest.json'
$sourceHash = (Get-FileHash -LiteralPath $manifest).Hash
& (Join-Path $PackageRoot 'Install-Client.ps1') -CurseForgeInstance $CurseForgeInstance -PrismRoot $prism -ServerAddress 'friend-test.na.e4mc.link'
$instance = Join-Path $prism 'instances\SkyCraft ATM10SKY'
$client = Join-Path $instance '.minecraft'
if ($sourceHash -ne (Get-FileHash -LiteralPath $manifest).Hash) { throw 'Source manifest changed.' }
if (Test-Path -LiteralPath (Join-Path $client 'saves')) { throw 'Personal saves imported.' }
$sourceMods = @(Get-ChildItem -LiteralPath (Join-Path $CurseForgeInstance 'mods') -Filter '*.jar' -File | Where-Object Name -notmatch '^(iris-neoforge-|sodium-neoforge-|skycraft-|e4mc-)')
foreach ($mod in $sourceMods) {
    $copy = Join-Path $client ('mods\' + $mod.Name)
    if (-not (Test-Path -LiteralPath $copy) -or (Get-FileHash -LiteralPath $copy).Hash -ne (Get-FileHash -LiteralPath $mod.FullName).Hash) { throw "Missing or changed stock mod: $($mod.Name)" }
}
if (Get-ChildItem -LiteralPath (Join-Path $client 'mods') -Filter '*.jar' | Where-Object Name -match '^(iris-neoforge-|sodium-neoforge-)') { throw 'Incompatible rendering mods still active.' }
$settings = Join-Path $client 'config\skycraft.properties'
$settingsHash = (Get-FileHash -LiteralPath $settings).Hash
& (Join-Path $PackageRoot 'Update-Client.ps1') -PrismRoot $prism
if ($settingsHash -ne (Get-FileHash -LiteralPath $settings).Hash) { throw 'Address changed during update.' }
& (Join-Path $PackageRoot 'Enable-Windows-Pipe-Fix.ps1') -PrismRoot $prism
& (Join-Path $PackageRoot 'Enable-Windows-Pipe-Fix.ps1') -PrismRoot $prism
$jvmLine = Get-Content -LiteralPath (Join-Path $instance 'instance.cfg') | Where-Object { $_ -match '^JvmArgs=' }
if ([regex]::Matches($jvmLine, 'disable-uds-agent.jar').Count -ne 1) { throw 'Client repair not idempotent.' }
$agent = Join-Path $instance 'skycraft-support\disable-uds-agent.jar'
& 'C:\Program Files\Java\jdk-21\bin\java.exe' ('-javaagent:' + $agent) -version
if ($LASTEXITCODE -ne 0) { throw 'Optional agent failed on Java 21.' }
$server = Join-Path $TestDirectory 'Prepared server with spaces'
& (Join-Path $PackageRoot 'Prepare-Server.ps1') -ServerZip $ServerZip -Destination $server
New-Item -ItemType Directory -Path (Join-Path $server 'world') -Force | Out-Null
$sentinel = Join-Path $server 'world\KEEP-ME.txt'
Copy-Item -LiteralPath $manifest -Destination $sentinel
$sentinelHash = (Get-FileHash -LiteralPath $sentinel).Hash
& (Join-Path $PackageRoot 'Update-Server.ps1') -ServerDirectory $server
& (Join-Path $PackageRoot 'Update-Server.ps1') -ServerDirectory $server
if ($sentinelHash -ne (Get-FileHash -LiteralPath $sentinel).Hash) { throw 'Server updater changed world data.' }
& (Join-Path $PackageRoot 'Enable-Windows-Pipe-Fix.ps1') -ServerDirectory $server
& (Join-Path $PackageRoot 'Enable-Windows-Pipe-Fix.ps1') -ServerDirectory $server
if ([regex]::Matches((Get-Content -LiteralPath (Join-Path $server 'user_jvm_args.txt') -Raw), 'disable-uds-agent.jar').Count -ne 1) { throw 'Server repair not idempotent.' }
foreach ($path in @((Join-Path $client 'config\lithium.properties'),(Join-Path $server 'config\lithium.properties'))) {
    foreach ($setting in @('mixin.entity.collisions.movement=false','mixin.entity.collisions.intersection=false','mixin.minimal_nonvanilla.collisions.empty_space=false')) {
        if (@(Get-Content -LiteralPath $path) -notcontains $setting) { throw "Missing terrain setting: $setting" }
    }
}
Write-Host "PASS: stock import ($($sourceMods.Count) unchanged mods), spaces in paths, client/server updates, address/world preservation, terrain settings, optional agent and repeat repair."
Write-Host 'This verifies installation/configuration only, not a Minecraft login or remote Skyrim multiplayer session.'
