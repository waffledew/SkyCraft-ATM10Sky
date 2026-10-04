[CmdletBinding()]
param([string]$ServerDirectory, [string]$PrismRoot = (Join-Path $env:LOCALAPPDATA 'SkyCraft\Prism'))
$ErrorActionPreference = 'Stop'
$incoming = Join-Path $PSScriptRoot 'files\disable-uds-agent.jar'
if (-not (Test-Path -LiteralPath $incoming)) { throw 'Missing compatibility agent; extract the complete release first.' }
if ($ServerDirectory) {
    $root = (Resolve-Path -LiteralPath $ServerDirectory).Path
    if (-not (Test-Path -LiteralPath (Join-Path $root 'SERVER_CONTENTS.txt'))) { throw 'Select the prepared ATM10Sky server folder.' }
    $config = Join-Path $root 'user_jvm_args.txt'
    $lock = Join-Path $root 'world\session.lock'
    if (Test-Path -LiteralPath $lock) { $handle = [IO.File]::Open($lock,'Open','ReadWrite','None'); $handle.Dispose() }
} else {
    $root = Join-Path $PrismRoot 'instances\SkyCraft ATM10SKY'
    $config = Join-Path $root 'instance.cfg'
    if (Get-ChildItem -LiteralPath $root -Filter '*.lock' -ErrorAction SilentlyContinue) { throw 'Close Skyrim and Minecraft first.' }
}
if (-not (Test-Path -LiteralPath $config)) { throw 'Install the client or prepare the server first.' }
$support = Join-Path $root 'skycraft-support'
New-Item -ItemType Directory -Path $support -Force | Out-Null
$agent = Join-Path $support 'disable-uds-agent.jar'
Copy-Item -LiteralPath $incoming -Destination $agent -Force
$argument = '-javaagent:"' + ($agent -replace '\\','/') + '"'
$lines = @(Get-Content -LiteralPath $config)
Copy-Item -LiteralPath $config -Destination ($config + '.pipe-fix-backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
if ($ServerDirectory) {
    $lines = @($lines | Where-Object { $_ -notmatch '^-?"?-javaagent:.*disable-uds-agent\.jar' })
    # Java argument files accept a quoted whole argument, unlike Prism's JVM argument string.
    $lines += '"-javaagent:' + ($agent -replace '\\','/') + '"'
} else {
    $found = $false
    for ($i=0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^JvmArgs=(.*)$') {
            $value = $Matches[1]
            if ($value.StartsWith('"') -and $value.EndsWith('"')) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            $value = $value -replace '\\"','"'
            $value = $value -replace '-javaagent:(?:"[^"]*disable-uds-agent\.jar"|\S*disable-uds-agent\.jar)',''
            $value = ($value.Trim() + ' ' + $argument).Trim()
            $lines[$i] = 'JvmArgs="' + ($value -replace '"','\"') + '"'
            $found = $true
        }
    }
    if (-not $found) { throw 'JvmArgs was not found; reinstall/update the SkyCraft profile first.' }
}
Set-Content -LiteralPath $config -Value $lines -Encoding ascii
Write-Host 'Optional Windows pipe workaround enabled. Use Java 21; restart the client/server.'
