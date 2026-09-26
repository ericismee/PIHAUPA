$ErrorActionPreference = "Stop"

if (!(Test-Path "out")) {
    New-Item -ItemType Directory -Path "out" | Out-Null
}

javac -encoding UTF-8 -d out src\piHAUPA\*.java
if ($LASTEXITCODE -ne 0) { throw "Java compilation failed with exit code $LASTEXITCODE" }

$port = if ($args.Count -gt 0) { $args[0] } else { "8000" }
java -cp out piHAUPA.WebServer $port
