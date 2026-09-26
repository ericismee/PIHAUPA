$ErrorActionPreference = "Stop"

$rows = if ($args.Count -gt 0) { [int]$args[0] } else { 100000 }
$items = if ($args.Count -gt 1) { [int]$args[1] } else { 30 }
$width = if ($args.Count -gt 2) { [int]$args[2] } else { 4 }
$batches = if ($args.Count -gt 3) { [int]$args[3] } else { 10 }
$output = if ($args.Count -gt 4) { $args[4] } else { "examples\synthetic-$rows.txt" }

if (!(Test-Path "examples")) {
    New-Item -ItemType Directory -Path "examples" | Out-Null
}

$random = [System.Random]::new(20260821)
$writer = [System.IO.StreamWriter]::new($output, $false, [System.Text.UTF8Encoding]::new($false))
try {
    $writer.WriteLine("# Synthetic dataset: $rows transaction, $items item, $width item/giao dich, $batches batch")
    $writer.WriteLine("upper=0.02")
    $writer.WriteLine("lower=0.01")
    $writer.WriteLine("")
    $writer.WriteLine("external:")
    $external = for ($i = 1; $i -le $items; $i++) { "I$i=$($random.Next(1, 10))" }
    $writer.WriteLine(($external -join " "))

    $tid = 1
    for ($batch = 0; $batch -lt $batches; $batch++) {
        $start = [math]::Floor($rows * $batch / $batches)
        $end = [math]::Floor($rows * ($batch + 1) / $batches)
        $writer.WriteLine("")
        $writer.WriteLine("batch DB$batch")
        for ($row = $start; $row -lt $end; $row++) {
            $chosen = [System.Collections.Generic.HashSet[int]]::new()
            while ($chosen.Count -lt $width) {
                [void]$chosen.Add($random.Next(1, $items + 1))
            }
            $tokens = $chosen | Sort-Object | ForEach-Object { "I$($_):$($random.Next(1, 10))" }
            $writer.WriteLine("T$tid`: $($tokens -join ' ')")
            $tid++
        }
    }
} finally {
    $writer.Dispose()
}

Write-Host "Da tao file: $output"
Write-Host "Chay benchmark CLI: .\run.ps1 $output"
