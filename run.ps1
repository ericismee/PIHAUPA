$ErrorActionPreference = "Stop"
if (!(Test-Path "out")) {
    New-Item -ItemType Directory -Path "out" | Out-Null
}
javac -encoding UTF-8 -d out src\piHAUPA\*.java
java -cp out piHAUPA.Main @args
