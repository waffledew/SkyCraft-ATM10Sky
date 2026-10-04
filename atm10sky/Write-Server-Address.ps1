[CmdletBinding()]
param(
    [string]$ServerDirectory = $PSScriptRoot,
    [switch]$IgnoreExisting
)

$ErrorActionPreference = 'Stop'
$logFiles = @(
    (Join-Path $ServerDirectory 'logs\latest.log'),
    (Join-Path $ServerDirectory 'logs\debug.log')
)
$addressFile = Join-Path $ServerDirectory 'SERVER-ADDRESS.txt'
$deadline = [DateTime]::UtcNow.AddMinutes(10)
$startedAt = Get-Date
$tailBytes = 4MB

Set-Content -LiteralPath $addressFile -Encoding ascii -Value @(
    'SKYCRAFT ATM10SKY SERVER ADDRESS',
    '================================',
    '',
    'Waiting for e4mc to assign an address...'
)

while ([DateTime]::UtcNow -lt $deadline) {
    foreach ($logFile in $logFiles) {
        if (-not (Test-Path -LiteralPath $logFile)) { continue }

        try {
            # NeoForge truncates an existing log and can regrow it beyond its old length before this
            # watcher gets a timeslice. Following the previous byte offset would then skip the new
            # address. Re-read a bounded tail and use the log timestamp to reject the prior session.
            $share = [System.IO.FileShare]::ReadWrite -bor [System.IO.FileShare]::Delete
            $stream = [System.IO.File]::Open($logFile, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, $share)
            try {
                $stream.Position = [Math]::Max(0L, $stream.Length - $tailBytes)
                $reader = [System.IO.StreamReader]::new($stream)
                try { $newText = $reader.ReadToEnd() } finally { $reader.Dispose() }
            } finally {
                $stream.Dispose()
            }

            $matches = [regex]::Matches($newText, '\[(\d{2}[A-Za-z]{3}\d{4} \d{2}:\d{2}:\d{2}\.\d{3})\].*Domain assigned:\s*([a-z0-9.-]+\.e4mc\.link)', 'IgnoreCase')
            for ($i = $matches.Count - 1; $i -ge 0; $i--) {
                $match = $matches[$i]
                $loggedAt = [DateTime]::ParseExact($match.Groups[1].Value, 'ddMMMyyyy HH:mm:ss.fff', [Globalization.CultureInfo]::InvariantCulture)
                if ($IgnoreExisting -and $loggedAt -lt $startedAt.AddSeconds(-2)) { continue }

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
