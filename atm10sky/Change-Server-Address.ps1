[CmdletBinding()]
param(
    [string]$ServerAddress,
    [string]$PrismRoot = (Join-Path $env:LOCALAPPDATA 'SkyCraft\Prism')
)

$ErrorActionPreference = 'Stop'
$instanceRoot = Join-Path $PrismRoot 'instances\SkyCraft ATM10SKY'
$propertiesFile = Join-Path $instanceRoot '.minecraft\config\skycraft.properties'

if (-not (Test-Path -LiteralPath $instanceRoot)) {
    throw "The SkyCraft ATM10SKY profile was not found at $instanceRoot. Run INSTALL-ATM10SKY.cmd first."
}

if (-not $PSBoundParameters.ContainsKey('ServerAddress')) {
    $ServerAddress = Read-Host "Paste the host's new e4mc.link address (host PC: localhost:25565)"
}

$ServerAddress = $ServerAddress.Trim()
if (-not $ServerAddress) { throw 'No server address was entered.' }
if ($ServerAddress -match '[\r\n=\s/]') {
    throw 'Enter only the server address, such as example-name.na.e4mc.link or localhost:25565.'
}
if ($ServerAddress -ne 'localhost:25565' -and $ServerAddress -notmatch '^[A-Za-z0-9.-]+(?::\d{1,5})?$') {
    throw 'The server address format is not valid.'
}

$configDirectory = Split-Path -Parent $propertiesFile
New-Item -ItemType Directory -Path $configDirectory -Force | Out-Null
$lines = [System.Collections.Generic.List[string]]::new()
if (Test-Path -LiteralPath $propertiesFile) {
    foreach ($line in Get-Content -LiteralPath $propertiesFile) { $lines.Add($line) }
} else {
    $lines.Add('# SkyCraft ATM10Sky multiplayer server.')
    $lines.Add('# The host uses localhost:25565; friends use the current e4mc.link address.')
}

$updated = $false
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '^join=') {
        $lines[$i] = "join=$ServerAddress"
        $updated = $true
        break
    }
}
if (-not $updated) { $lines.Add("join=$ServerAddress") }

Set-Content -LiteralPath $propertiesFile -Value $lines -Encoding ascii
Write-Host "Server address updated to: $ServerAddress" -ForegroundColor Green
Write-Host 'The new address will be used the next time Skyrim launches SkyCraft.'
