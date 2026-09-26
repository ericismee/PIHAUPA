$ErrorActionPreference = "Stop"

if (!(Test-Path "out")) {
    New-Item -ItemType Directory -Path "out" | Out-Null
}

javac -encoding UTF-8 -d out src\piHAUPA\*.java
if ($LASTEXITCODE -ne 0) { throw "Java compilation failed with exit code $LASTEXITCODE" }

java -cp out piHAUPA.DesktopApp
