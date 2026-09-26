$ErrorActionPreference = "Stop"

$copies = if ($args.Count -gt 0) { [int]$args[0] } else { 1000 }
$output = if ($args.Count -gt 1) { $args[1] } else { "examples\paper-scaled-$($copies * 9).txt" }
if ($copies -lt 1) { throw "Số lần lặp phải >= 1." }

$outputPath = [System.IO.Path]::GetFullPath($output)
$directory = [System.IO.Path]::GetDirectoryName($outputPath)
if (!(Test-Path -LiteralPath $directory)) {
    New-Item -ItemType Directory -Path $directory | Out-Null
}

$groups = @(
    @{ Name = "DB0"; Rows = @(
        "A:2 B:1 C:3", "C:1 D:2", "A:6 B:1 C:4 E:5",
        "A:4 B:4 E:3", "B:1 E:2 F:2") },
    @{ Name = "DB1"; Rows = @("A:3 F:3", "C:2 E:2") },
    @{ Name = "DB2"; Rows = @("B:5 C:1 D:3 E:2", "A:1 B:3 D:1") }
)

$writer = [System.IO.StreamWriter]::new($outputPath, $false, [System.Text.UTF8Encoding]::new($false))
try {
    $writer.WriteLine("# Tables 2-3, mỗi giao dịch gốc lặp $copies lần với TID duy nhất")
    $writer.WriteLine("upper=0.23")
    $writer.WriteLine("lower=0.10")
    $writer.WriteLine("")
    $writer.WriteLine("external:")
    $writer.WriteLine("A=2 B=5 C=1 D=3 E=6 F=4")
    foreach ($group in $groups) {
        $writer.WriteLine("")
        $writer.WriteLine("batch $($group.Name)")
        for ($copy = 1; $copy -le $copies; $copy++) {
            for ($row = 0; $row -lt $group.Rows.Count; $row++) {
                $writer.WriteLine("$($group.Name)_T$($row + 1)_$copy`: $($group.Rows[$row])")
            }
        }
    }
} finally {
    $writer.Dispose()
}

Write-Host "Đã tạo $($copies * 9) giao dịch: $outputPath"
