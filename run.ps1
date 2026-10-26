# Compiles and starts FinCore without Maven (only a JDK 21 is required).
# Usage: .\run.ps1 [dataDirectory]
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$sources = Get-ChildItem -Recurse -Filter *.java src\main\java | ForEach-Object FullName
if (Test-Path out) { Remove-Item -Recurse -Force out }
javac -encoding UTF-8 --release 21 -Xlint:all,-serial -d out $sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

java -cp out com.fincore.App @args
