[CmdletBinding()]
param(
    [string]$CurseForgeInstance,
    [string]$PrismRoot = (Join-Path $env:LOCALAPPDATA 'SkyCraft\Prism'),
    [string]$ServerAddress,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$packageRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$files = Join-Path $packageRoot 'files'
$skycraftJar = Get-ChildItem -LiteralPath $files -Filter 'skycraft-*-atm10sky.*.jar' -File | Select-Object -First 1
$e4mcJar = Get-ChildItem -LiteralPath $files -Filter 'e4mc-neoforge-*.jar' -File | Select-Object -First 1
if (-not $skycraftJar -or -not $e4mcJar) {
    throw 'This package is incomplete: the SkyCraft and e4mc jars must be in its files folder.'
}

if (-not $CurseForgeInstance) {
    $instanceFolders = @(
        (Join-Path $env:USERPROFILE 'curseforge\minecraft\Instances'),
        (Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'CurseForge\Minecraft\Instances'),
        (Join-Path $env:APPDATA 'CurseForge\minecraft\Instances')
    ) | Select-Object -Unique
    foreach ($instances in $instanceFolders) {
        $CurseForgeInstance = Get-ChildItem -LiteralPath $instances -Directory -ErrorAction SilentlyContinue |
            Where-Object {
                $manifestPath = Join-Path $_.FullName 'manifest.json'
                if (-not (Test-Path -LiteralPath $manifestPath)) { return $false }
                try {
                    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
                    return $manifest.name -eq 'ATM10SKY' -and $manifest.version -eq '2.0.6' -and
                        $manifest.minecraft.version -eq '1.21.1'
                } catch { return $false }
            } | Select-Object -First 1 -ExpandProperty FullName
        if ($CurseForgeInstance) { break }
    }
}
if (-not $CurseForgeInstance) {
    $CurseForgeInstance = Read-Host 'ATM10Sky was not found automatically. Paste its CurseForge instance folder path'
}
if (-not $CurseForgeInstance -or -not (Test-Path -LiteralPath $CurseForgeInstance)) {
    throw 'ATM10 To the Sky 2.0.6 was not found. Install it with CurseForge or pass -CurseForgeInstance.'
}

$manifestFile = Join-Path $CurseForgeInstance 'manifest.json'
$manifest = Get-Content -LiteralPath $manifestFile -Raw | ConvertFrom-Json
if ($manifest.name -ne 'ATM10SKY' -or $manifest.version -ne '2.0.6' -or $manifest.minecraft.version -ne '1.21.1') {
    throw 'The selected CurseForge instance must be ATM10SKY 2.0.6 on Minecraft 1.21.1.'
}
if (-not (Test-Path -LiteralPath (Join-Path $PrismRoot 'prismlauncher.exe'))) {
    throw "SkyCraft's portable Prism Launcher was not found at $PrismRoot. Install and run normal SkyCraft once first."
}

$instanceRoot = Join-Path $PrismRoot 'instances\SkyCraft ATM10SKY'
if (Test-Path -LiteralPath $instanceRoot) {
    if (-not $Force) {
        throw "The target already exists: $instanceRoot. Re-run with -Force to replace this generated profile."
    }
    $backup = "$instanceRoot.backup-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
    Move-Item -LiteralPath $instanceRoot -Destination $backup
    Write-Host "Existing generated profile moved to $backup"
}

$minecraft = Join-Path $instanceRoot '.minecraft'
New-Item -ItemType Directory -Path $minecraft -Force | Out-Null
Write-Host 'Copying the CurseForge instance. This can take several minutes...'
Copy-Item -Path (Join-Path $CurseForgeInstance '*') -Destination $minecraft -Recurse -Force

foreach ($name in @('saves', 'logs', 'crash-reports', 'screenshots', '.curseclient', 'downloads')) {
    $path = Join-Path $minecraft $name
    if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Recurse -Force }
}
foreach ($name in @('manifest.json', 'minecraftinstance.json')) {
    $path = Join-Path $minecraft $name
    if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Force }
}

$mods = Join-Path $minecraft 'mods'
foreach ($pattern in @('iris-neoforge-*.jar', 'sodium-neoforge-*.jar')) {
    Get-ChildItem -LiteralPath $mods -Filter $pattern -File -ErrorAction SilentlyContinue | ForEach-Object {
        Move-Item -LiteralPath $_.FullName -Destination ($_.FullName + '.disabled')
    }
}
Get-ChildItem -LiteralPath $mods -Filter 'skycraft-*.jar' -File -ErrorAction SilentlyContinue | Remove-Item -Force
Get-ChildItem -LiteralPath $mods -Filter 'e4mc-*.jar' -File -ErrorAction SilentlyContinue | Remove-Item -Force
Copy-Item -LiteralPath $skycraftJar.FullName -Destination (Join-Path $mods $skycraftJar.Name)
Copy-Item -LiteralPath $e4mcJar.FullName -Destination (Join-Path $mods $e4mcJar.Name)

$config = Join-Path $minecraft 'config'
New-Item -ItemType Directory -Path $config -Force | Out-Null
Set-Content -LiteralPath (Join-Path $config 'flywheel-client.toml') -Value 'backend = "flywheel:off"'
if (-not $PSBoundParameters.ContainsKey('ServerAddress')) {
    $ServerAddress = Read-Host 'Server address (host: localhost:25565; friends: the e4mc.link address; leave blank to set later)'
}
$ServerAddress = $ServerAddress.Trim()
if ($ServerAddress -match '[\r\n=]') { throw 'The server address contains invalid characters.' }
Set-Content -LiteralPath (Join-Path $config 'skycraft.properties') -Value @(
    '# SkyCraft: leave empty for your own world; use localhost:25565 for a server on this PC,'
    '# or use the host''s something.e4mc.link address.',
    "join=$ServerAddress"
)

$maxMemory = 8192
try {
    $physicalMemoryMb = [math]::Floor((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1MB)
    if ($physicalMemoryMb -ge 24576) { $maxMemory = 10240 }
    elseif ($physicalMemoryMb -ge 16384) { $maxMemory = 8192 }
    elseif ($physicalMemoryMb -ge 12288) { $maxMemory = 6144 }
    else {
        $maxMemory = 4096
        Write-Warning 'ATM10Sky normally needs at least 12-16 GB of system RAM. This PC may struggle.'
    }
} catch {
    Write-Warning 'Could not detect system RAM; using an 8 GB Minecraft limit.'
}

Set-Content -LiteralPath (Join-Path $instanceRoot 'instance.cfg') -Value @"
[General]
InstanceType=OneSix
name=SkyCraft ATM10SKY
iconKey=default
notes=SkyCraft compatibility profile for ATM10 To the Sky 2.0.6. Iris and Sodium are disabled.
OverrideJavaArgs=true
JvmArgs="--enable-preview --enable-native-access=ALL-UNNAMED -Dskycraft.startHidden=true -Dskycraft.quitWithSkyrim=true"
OverrideMemory=true
MinMemAlloc=1024
MaxMemAlloc=$maxMemory
OverrideConsole=true
ShowConsole=false
AutoCloseConsole=false
ShowConsoleOnError=true
AutomaticJava=true
OverrideJavaLocation=false
ConfigVersion=1.3
"@

Set-Content -LiteralPath (Join-Path $instanceRoot 'mmc-pack.json') -Value @'
{
  "components": [
    { "cachedName": "LWJGL 3", "cachedVersion": "3.3.3", "cachedVolatile": true, "dependencyOnly": true, "uid": "org.lwjgl3", "version": "3.3.3" },
    { "cachedName": "Minecraft", "cachedRequires": [{ "suggests": "3.3.3", "uid": "org.lwjgl3" }], "cachedVersion": "1.21.1", "important": true, "uid": "net.minecraft", "version": "1.21.1" },
    { "cachedName": "NeoForge", "cachedRequires": [{ "equals": "1.21.1", "uid": "net.minecraft" }], "cachedVersion": "21.1.250", "uid": "net.neoforged", "version": "21.1.250" }
  ],
  "formatVersion": 1
}
'@

Write-Host "Installed Prism profile: $instanceRoot" -ForegroundColor Green
Write-Host 'Install the supplied Vortex patch after normal SkyCraft. Skyrim will launch the ATM10Sky profile automatically.'
Write-Host 'Minecraft and its console stay hidden during normal play; Prism appears only for sign-in or an error.'
