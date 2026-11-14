# Builds the Windows packages and the MSI installer on a CI runner (GitHub
# Actions or Gitea Actions); called by .github/workflows/build-windows.yml and
# .gitea/workflows/build-windows.yml.
#
# Expects a JDK 21 in JAVA_HOME and Python 3 on the PATH. Creates what is
# git-ignored and set up by hand on a developer machine:
# - a Java runtime made with jlink (build-jre.txt)
# - the WiX Toolset 3.14 binaries (build-wix.txt)
# Output: build/DocFetcher-<version>-Windows-64bit.msi and
# build/DocFetcher-<version>-Windows-64bit-Portable.zip

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "../..")
Set-Location $root

$tools = if ($env:RUNNER_TEMP) { $env:RUNNER_TEMP } else { [IO.Path]::GetTempPath() }
$tools = Join-Path $tools "docfetcher-build-tools"
New-Item -ItemType Directory -Force $tools | Out-Null

function Check-Exit($what) {
	if ($LASTEXITCODE -ne 0) { throw "$what failed (exit code $LASTEXITCODE)" }
}

# JDK: javac and jar must be on the PATH for build.py
if (-not $env:JAVA_HOME) { throw "JAVA_HOME is not set (JDK 21 needed)" }
$env:PATH = (Join-Path $env:JAVA_HOME "bin") + [IO.Path]::PathSeparator + $env:PATH
& java -version
Check-Exit "java -version"

# Java runtime for the packages, the same modules as in tasks/skill.md
$jre = Join-Path $tools "jre-windows"
if (Test-Path $jre) { Remove-Item -Recurse -Force $jre }
& jlink --add-modules "java.se,jdk.unsupported,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.localedata,jdk.charsets,jdk.zipfs,jdk.management,jdk.net,jdk.accessibility,jdk.httpserver" `
	--strip-debug --no-header-files --no-man-pages --output $jre
Check-Exit "jlink"

# WiX Toolset 3.14 (binaries only, no installation needed)
$wix = Join-Path $tools "wix314"
if (-not (Test-Path (Join-Path $wix "light.exe"))) {
	$zip = Join-Path $tools "wix314-binaries.zip"
	Invoke-WebRequest -UseBasicParsing -OutFile $zip `
		"https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip"
	Expand-Archive -Force $zip $wix
}

# The git-ignored configuration files of build.py
"jre_windows=$($jre -replace '\\', '/')" | Set-Content -Encoding ascii build-jre.txt
"wix=$($wix -replace '\\', '/')" | Set-Content -Encoding ascii build-wix.txt

# Python package for the manual
& python -m pip install --quiet markdown
Check-Exit "pip install markdown"

& python build.py windows
Check-Exit "build.py"

# build.py skips the MSI with only a note if WiX is missing: fail instead
$msi = Get-ChildItem build -Filter "*.msi" | Select-Object -First 1
$zipPkg = Get-ChildItem build -Filter "*-Portable.zip" | Select-Object -First 1
if (-not $msi) { throw "No MSI installer under build/" }
if (-not $zipPkg) { throw "No portable package under build/" }
Write-Host "Built $($msi.Name) and $($zipPkg.Name)"
