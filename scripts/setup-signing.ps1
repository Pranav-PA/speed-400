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

if (Test-Path $Out) {
    Write-Host "ERROR: $Out already exists. Refusing to overwrite an existing signing key." -ForegroundColor Red
    Write-Host "If you genuinely want a new one, move the old file aside first - and read"
    Write-Host "docs/releasing.md, because a new key means uninstall-and-reinstall on the tablet."
    exit 1
}

# keytool ships with any JDK. Android Studio bundles one, so look there before giving up.
$keytool = (Get-Command keytool -ErrorAction SilentlyContinue).Source
if (-not $keytool) {
    $candidates = @(
        "$env:ProgramFiles\Android\Android Studio\jbr\bin\keytool.exe",
        "$env:LOCALAPPDATA\Programs\Android Studio\jbr\bin\keytool.exe",
        "$env:ProgramFiles\Java\*\bin\keytool.exe",
        "$env:ProgramFiles\Eclipse Adoptium\*\bin\keytool.exe"
    )
    foreach ($c in $candidates) {
        $found = Get-Item $c -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { $keytool = $found.FullName; break }
    }
}
if (-not $keytool) {
    Write-Host "ERROR: keytool not found." -ForegroundColor Red
    Write-Host "It ships with any JDK. Either install one:"
    Write-Host "    winget install EclipseAdoptium.Temurin.17.JDK"
    Write-Host "or, if you have Android Studio, it is usually at:"
    Write-Host "    $env:ProgramFiles\Android\Android Studio\jbr\bin\keytool.exe"
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
