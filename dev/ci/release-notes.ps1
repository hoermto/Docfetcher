# Writes the release notes of the current version (current-version.txt) from
# website/content/extra/changelog.txt to build/release-notes.md, and checks
# that the tag being built (argument, e.g. 'release-3.2') matches the version.

param([string]$Tag = "")

$ErrorActionPreference = "Stop"
Set-Location (Resolve-Path (Join-Path $PSScriptRoot "../.."))

$version = (Get-Content current-version.txt -Raw).Trim()
if ($Tag -and $Tag -ne "release-$version") {
	throw "Tag '$Tag' doesn't match the version in current-version.txt ($version): expected 'release-$version'"
}

$lines = Get-Content -Encoding utf8 website/content/extra/changelog.txt
$notes = @()
$inSection = $false
foreach ($line in $lines) {
	if ($line -match '^==== .* - DocFetcher (\S+) ====$') {
		if ($inSection) { break }
		$inSection = ($Matches[1] -eq $version)
		continue
	}
	if (-not $inSection) { continue }
	if ($line -match '^=== (.*) ===$') { $notes += "### $($Matches[1])"; continue }
	if ($line -match '^  \* (.*)$') { $notes += "- $($Matches[1])"; continue }
	$notes += $line
}
if ($notes.Count -eq 0) { throw "No changelog section for DocFetcher $version" }

$notes += ""
$notes += "**Downloads:** ``DocFetcher-$version-Windows-64bit.msi`` installs DocFetcher for all users; " +
	"``DocFetcher-$version-Windows-64bit-Portable.zip`` runs without installation. " +
	"The files are not code-signed, so Windows SmartScreen may warn before the first start."

New-Item -ItemType Directory -Force build | Out-Null
$notes -join "`n" | Set-Content -Encoding utf8 build/release-notes.md
Write-Host "Release notes for DocFetcher $version written to build/release-notes.md"
