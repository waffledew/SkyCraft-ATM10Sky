[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)] [string]$ServerZip,
    [Parameter(Mandatory = $true)] [string]$Destination,
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
if (-not (Test-Path -LiteralPath $ServerZip)) { throw "Server archive not found: $ServerZip" }
if (Test-Path -LiteralPath $Destination) {
    if (-not $Force) { throw "Destination already exists: $Destination. Use -Force only for an empty/rebuild destination." }
    if (Get-ChildItem -LiteralPath $Destination -Force -ErrorAction SilentlyContinue) {
        throw 'For safety, -Force does not erase a non-empty server folder. Choose a new destination.'
    }
} else {
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
}

Write-Host 'Extracting the official ATM10Sky server pack...'
Expand-Archive -LiteralPath $ServerZip -DestinationPath $Destination -Force
$contents = Join-Path $Destination 'SERVER_CONTENTS.txt'
if (-not (Test-Path -LiteralPath $contents) -or
    (Get-Content -LiteralPath $contents -Raw) -notmatch 'pack:\s*ATM10SKY 2\.0\.6') {
    throw 'The archive is not the official ATM10SKY 2.0.6 server pack.'
}

$mods = Join-Path $Destination 'mods'
Copy-Item -LiteralPath $skycraftJar.FullName -Destination (Join-Path $mods $skycraftJar.Name) -Force
Copy-Item -LiteralPath $e4mcJar.FullName -Destination (Join-Path $mods $e4mcJar.Name) -Force

$propertiesFile = Join-Path $Destination 'server.properties'
$properties = if (Test-Path -LiteralPath $propertiesFile) {
    [System.Collections.Generic.List[string]](Get-Content -LiteralPath $propertiesFile)
} else {
    [System.Collections.Generic.List[string]]@(
        '# SkyCraft ATM10Sky dedicated server',
        'allow-flight=true',
        'max-tick-time=180000',
        'motd=SkyCraft ATM10Sky',
        'level-type=skycraft\:mirror'
    )
}

function Set-ServerProperty {
    param([string]$Name, [string]$Value)
    $replacement = "$Name=$Value"
    for ($i = 0; $i -lt $properties.Count; $i++) {
        if ($properties[$i] -match "^$([regex]::Escape($Name))=") {
            $properties[$i] = $replacement
            return
        }
    }
    $properties.Add($replacement)
}

Set-ServerProperty -Name 'allow-flight' -Value 'true'
Set-ServerProperty -Name 'max-tick-time' -Value '180000'
Set-ServerProperty -Name 'motd' -Value 'SkyCraft ATM10Sky'
Set-ServerProperty -Name 'level-type' -Value 'skycraft\:mirror'
Set-Content -LiteralPath $propertiesFile -Value $properties -Encoding ascii

$jvmFile = Join-Path $Destination 'user_jvm_args.txt'
$jvm = Get-Content -LiteralPath $jvmFile
if ($jvm -notcontains '-Xms2G') { Add-Content -LiteralPath $jvmFile -Value '-Xms2G' }
if ($jvm -notcontains '-Xmx4G') { Add-Content -LiteralPath $jvmFile -Value '-Xmx4G' }
if ($jvm -notcontains '--enable-preview') { Add-Content -LiteralPath $jvmFile -Value '--enable-preview' }

Write-Host "Prepared server: $Destination" -ForegroundColor Green
Write-Host 'The shared server uses SkyCraft mirror world generation instead of a generated sky island.'
Write-Host 'Run startserver.bat, read/accept the Minecraft EULA, and wait for the e4mc Domain assigned line.'
Write-Host 'The e4mc address changes each time the server restarts.'
