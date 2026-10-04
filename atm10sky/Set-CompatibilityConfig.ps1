function Set-SkyCraftCompatibilityConfig {
    param([Parameter(Mandatory = $true)][string]$ConfigDirectory)
    $ErrorActionPreference = 'Stop'
    New-Item -ItemType Directory -Path $ConfigDirectory -Force | Out-Null
    $path = Join-Path $ConfigDirectory 'lithium.properties'
    $lines = if (Test-Path -LiteralPath $path) { @(Get-Content -LiteralPath $path) } else { @() }
    # These replace/skip vanilla block collision queries. SkyCraft adds streamed
    # Skyrim terrain there; it is not stored as ordinary Minecraft chunk blocks.
    $options = @('mixin.entity.collisions.movement', 'mixin.entity.collisions.intersection', 'mixin.minimal_nonvanilla.collisions.empty_space')
    foreach ($option in $options) {
        $pattern = '^\s*' + [regex]::Escape($option) + '\s*[:=]'
        $lines = @($lines | Where-Object { $_ -notmatch $pattern })
        $lines += "$option=false"
    }
    if (Test-Path -LiteralPath $path) {
        $before = Get-Content -LiteralPath $path -Raw
        if (($before.TrimEnd() -replace '\r\n', "`n") -eq ($lines -join "`n")) { return }
        Copy-Item -LiteralPath $path -Destination ($path + '.skycraft-backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    }
    Set-Content -LiteralPath $path -Value $lines -Encoding ascii
}
