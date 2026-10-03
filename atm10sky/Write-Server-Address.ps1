[CmdletBinding()]
param(
    [string]$ServerDirectory = $PSScriptRoot,
    [switch]$IgnoreExisting
)

$ErrorActionPreference = 'Stop'
$logFile = Join-Path $ServerDirectory 'logs\debug.log'
$addressFile = Join-Path $ServerDirectory 'SERVER-ADDRESS.txt'
$deadline = [DateTime]::UtcNow.AddMinutes(10)
$startedAt = Get-Date
$position = 0L
$fileCreated = 0L

if ($IgnoreExisting -and (Test-Path -LiteralPath $logFile)) {
    $existing = Get-Item -LiteralPath $logFile
    $position = $existing.Length
    $fileCreated = $existing.CreationTimeUtc.Ticks
}

Set-Content -LiteralPath $addressFile -Encoding ascii -Value @(
    'SKYCRAFT ATM10SKY SERVER ADDRESS',
    '================================',
    '',
    'Waiting for e4mc to assign an address...'
)

while ([DateTime]::UtcNow -lt $deadline) {
    if (-not (Test-Path -LiteralPath $logFile)) {
        Start-Sleep -Milliseconds 500
        continue
    }

    try {
        $item = Get-Item -LiteralPath $logFile
        if (($fileCreated -ne 0 -and $item.CreationTimeUtc.Ticks -ne $fileCreated) -or $item.Length -lt $position) {
            $position = 0L
        }
        $fileCreated = $item.CreationTimeUtc.Ticks

        $share = [System.IO.FileShare]::ReadWrite -bor [System.IO.FileShare]::Delete
        $stream = [System.IO.File]::Open($logFile, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, $share)
        try {
            $stream.Position = [Math]::Min($position, $stream.Length)
            $reader = [System.IO.StreamReader]::new($stream)
            try {
                $newText = $reader.ReadToEnd()
                $nextPosition = $stream.Position
            } finally { $reader.Dispose() }
            $position = $nextPosition
        } finally {
            $stream.Dispose()
        }

        $matches = [regex]::Matches($newText, '\[(\d{2}[A-Za-z]{3}\d{4} \d{2}:\d{2}:\d{2}\.\d{3})\].*Domain assigned:\s*([a-z0-9.-]+\.e4mc\.link)', 'IgnoreCase')
        if ($matches.Count -gt 0) {
            $match = $matches[$matches.Count - 1]
            $loggedAt = [DateTime]::ParseExact($match.Groups[1].Value, 'ddMMMyyyy HH:mm:ss.fff', [Globalization.CultureInfo]::InvariantCulture)
            if ($IgnoreExisting -and $loggedAt -lt $startedAt.AddSeconds(-2)) {
                Start-Sleep -Milliseconds 500
                continue
            }
            $address = $match.Groups[2].Value.ToLowerInvariant()
            Set-Content -LiteralPath $addressFile -Encoding ascii -Value @(
                'SKYCRAFT ATM10SKY SERVER ADDRESS',
                '================================',
                '',
                $address,
                '',
                "Updated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')",
                '',
                'Send the address above to friends.',
                'Friends run CHANGE-SERVER-ADDRESS.cmd and paste it.',
                'The host continues to use localhost:25565.'
            )
            exit 0
        }
    } catch [System.IO.IOException] {
        # The logger may be rotating this file; retry after it finishes.
    }

    Start-Sleep -Milliseconds 500
}

Set-Content -LiteralPath $addressFile -Encoding ascii -Value @(
    'SKYCRAFT ATM10SKY SERVER ADDRESS',
    '================================',
    '',
    'No e4mc address was found within 10 minutes.',
    'Check the server console and logs\debug.log for startup errors.'
)
exit 1
