# Creates the ONE signing key this app will ever use, and prints the GitHub secrets
# that CI needs. Run it once, on your own machine. It never uploads anything.
#
#   powershell -ExecutionPolicy Bypass -File scripts\setup-signing.ps1
#
# WHY THIS MATTERS: Android refuses to install an update signed with a different key
# than the installed app. If this keystore is lost or regenerated, the only way to
# install a new build is to uninstall the old one first - which deletes the database.
# Back up release.jks somewhere you will still have in five years.

$ErrorActionPreference = 'Stop'

$Out   = if ($args.Count -ge 1) { $args[0] } else { 'release.jks' }
$Alias = 'speed400garage'

# A clone under system32 (or anywhere under the Windows directory) is almost always an
# accident: an elevated PowerShell starts there, so `git clone` lands the repo in a
# protected system folder. Gradle and the signing key do not belong there.
$here = (Get-Location).Path
if ($here -like "$env:WinDir*") {
    Write-Host "STOP: this repository is inside a Windows system directory." -ForegroundColor Red
    Write-Host "  $here"
    Write-Host ""
    Write-Host "That happens when PowerShell is opened as Administrator, which starts in"
    Write-Host "system32. Move it somewhere it belongs, then run this again:"
    Write-Host ""
    Write-Host "    Move-Item '$here' `"`$env:USERPROFILE\speed-400`"" -ForegroundColor Cyan
    Write-Host "    cd `"`$env:USERPROFILE\speed-400`"" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "You do not need Administrator for any part of this project."
    exit 1
}

if (Test-Path $Out) {
    Write-Host "ERROR: $Out already exists. Refusing to overwrite an existing signing key." -ForegroundColor Red
    Write-Host "If you genuinely want a new one, move the old file aside first - and read"
    Write-Host "docs/releasing.md, because a new key means uninstall-and-reinstall on the tablet."
    exit 1
}

# keytool ships with any JDK. Look in every place one plausibly lives before giving up.
$keytool = (Get-Command keytool -ErrorAction SilentlyContinue).Source
if (-not $keytool -and $env:JAVA_HOME) {
    $j = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
    if (Test-Path $j) { $keytool = $j }
}
if (-not $keytool) {
    $roots = @(
        "$env:ProgramFiles\Android\Android Studio*\jbr\bin\keytool.exe",
        "$env:ProgramFiles\Android\Android Studio*\jre\bin\keytool.exe",
        "$env:LOCALAPPDATA\Programs\Android Studio*\jbr\bin\keytool.exe",
        "$env:ProgramFiles\Eclipse Adoptium\jdk*\bin\keytool.exe",
        "$env:ProgramFiles\Java\jdk*\bin\keytool.exe",
        "$env:ProgramFiles\Microsoft\jdk*\bin\keytool.exe",
        "$env:ProgramFiles\Amazon Corretto\jdk*\bin\keytool.exe",
        "$env:ProgramFiles\BellSoft\*\bin\keytool.exe",
        "$env:ProgramFiles\Zulu\*\bin\keytool.exe",
        "${env:ProgramFiles(x86)}\Java\jdk*\bin\keytool.exe",
        "$env:LOCALAPPDATA\Programs\Eclipse Adoptium\jdk*\bin\keytool.exe"
    )
    # Newest version last in a sort, so take the last match.
    $hit = $roots |
        ForEach-Object { Get-Item $_ -ErrorAction SilentlyContinue } |
        Sort-Object FullName |
        Select-Object -Last 1
    if ($hit) { $keytool = $hit.FullName }
}
if (-not $keytool) {
    Write-Host "ERROR: keytool not found." -ForegroundColor Red
    Write-Host ""
    Write-Host "It ships with any JDK, and this script only needs it once - to create the"
    Write-Host "signing key. Install one, then CLOSE AND REOPEN PowerShell so PATH updates:"
    Write-Host ""
    Write-Host "    winget install EclipseAdoptium.Temurin.17.JDK" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "No winget? Download the Windows x64 .msi from https://adoptium.net/"
    Write-Host ""
    Write-Host "Already have a JDK or Android Studio somewhere unusual? Point at it directly:"
    Write-Host "    `$env:JAVA_HOME = 'C:\path\to\jdk'" -ForegroundColor Cyan
    Write-Host "    powershell -ExecutionPolicy Bypass -File scripts\setup-signing.ps1"
    exit 1
}
Write-Host "Using keytool at $keytool"
Write-Host ""

$p1 = Read-Host -AsSecureString 'Choose a keystore password (you will need it again)'
$p2 = Read-Host -AsSecureString 'Confirm'
$Pass  = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
            [Runtime.InteropServices.Marshal]::SecureStringToBSTR($p1))
$Pass2 = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
            [Runtime.InteropServices.Marshal]::SecureStringToBSTR($p2))

if ($Pass -ne $Pass2)  { Write-Host 'Passwords did not match.' -ForegroundColor Red; exit 1 }
if ($Pass.Length -lt 8) { Write-Host 'Use at least 8 characters.' -ForegroundColor Red; exit 1 }

& $keytool -genkeypair -v `
    -keystore $Out `
    -alias $Alias `
    -keyalg RSA -keysize 4096 `
    -validity 10950 `
    -storepass $Pass -keypass $Pass `
    -dname 'CN=Speed 400 Garage, OU=Personal, O=Pranav P Aradhya, L=Mysuru, C=IN'

if ($LASTEXITCODE -ne 0) { Write-Host 'keytool failed.' -ForegroundColor Red; exit 1 }

$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path $Out)))

Write-Host ""
Write-Host "Created $Out (valid 30 years). BACK IT UP NOW - losing it means losing the" -ForegroundColor Yellow
Write-Host "ability to update the app without wiping its data." -ForegroundColor Yellow
Write-Host ""
Write-Host "Add these four repository secrets at:"
Write-Host "  https://github.com/Pranav-PA/speed-400/settings/secrets/actions"
Write-Host ""
Write-Host "  KEYSTORE_PASSWORD  = the password you just chose"
Write-Host "  KEY_PASSWORD       = the same password"
Write-Host "  KEY_ALIAS          = $Alias"
Write-Host "  KEYSTORE_BASE64    = the contents of keystore-base64.txt (written below)"
Write-Host ""

# The base64 of a 4096-bit keystore is a few thousand characters. Pasting that out of a
# console window reliably is miserable, so it goes to a file you can open and Ctrl+A.
Set-Content -Path 'keystore-base64.txt' -Value $b64 -NoNewline -Encoding ascii
Write-Host "Wrote keystore-base64.txt - open it, select all, and paste as KEYSTORE_BASE64."
Write-Host "Delete that file once the secret is saved; it is as sensitive as the keystore."
Write-Host ""
Write-Host "With the gh CLI you can skip the copy-paste entirely:"
Write-Host "  gh secret set KEYSTORE_BASE64 --repo Pranav-PA/speed-400 < keystore-base64.txt"
Write-Host "  gh secret set KEYSTORE_PASSWORD --repo Pranav-PA/speed-400"
Write-Host "  gh secret set KEY_PASSWORD --repo Pranav-PA/speed-400"
Write-Host "  gh secret set KEY_ALIAS --repo Pranav-PA/speed-400 --body $Alias"
