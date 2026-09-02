# PowerShell script to generate a production Android keystore and encode it for GitHub Secrets
param (
    [string]$KeystoreFile = "wasm-release.jks",
    [string]$Alias = "wasm_release_key",
    [string]$Password = ""
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   Wasm - Android Production Keystore Generator          " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

if ([string]::IsNullOrWhiteSpace($Password)) {
    $Password = Read-Host -Prompt "Enter a secure password for your keystore & key" -AsSecureString
    $BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($Password)
    $PasswordPlain = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
} else {
    $PasswordPlain = $Password
}

# Locate keytool
$keytoolPath = "keytool"
if (-not (Get-Command "keytool" -ErrorAction SilentlyContinue)) {
    if ($env:JAVA_HOME) {
        $keytoolPath = Join-Path $env:JAVA_HOME "bin\keytool.exe"
    } else {
        Write-Error "keytool could not be found in PATH or JAVA_HOME. Please ensure JDK 17 is installed."
        exit 1
    }
}

Write-Host "`n[1/3] Generating RSA 2048-bit Keystore: $KeystoreFile..." -ForegroundColor Yellow
& $keytoolPath -genkeypair -v `
    -keystore $KeystoreFile `
    -alias $Alias `
    -keyalg RSA `
    -keysize 2048 `
    -validity 10000 `
    -storepass $PasswordPlain `
    -keypass $PasswordPlain `
    -dname "CN=Wasm Mobile, OU=Production, O=Wasm, L=Universal, ST=State, C=US"

if ($LASTEXITCODE -ne 0) {
    Write-Error "Keystore generation failed."
    exit $LASTEXITCODE
}

Write-Host "`n[2/3] Encoding Keystore to Base64..." -ForegroundColor Yellow
$bytes = [System.IO.File]::ReadAllBytes((Resolve-Path $KeystoreFile))
$base64String = [Convert]::ToBase64String($bytes)
$base64File = "$KeystoreFile.base64.txt"
[System.IO.File]::WriteAllText($base64File, $base64String)

Write-Host "`n[3/3] Done!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Add the following 4 secrets in GitHub (Settings > Secrets and variables > Actions):" -ForegroundColor White
Write-Host "----------------------------------------------------------"
Write-Host "1. KEYSTORE_BASE64    -> (Contents of $base64File)" -ForegroundColor Yellow
Write-Host "2. KEYSTORE_PASSWORD  -> [Your chosen password]" -ForegroundColor Yellow
Write-Host "3. KEY_ALIAS          -> $Alias" -ForegroundColor Yellow
Write-Host "4. KEY_PASSWORD       -> [Your chosen password]" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "NOTE: Keep $KeystoreFile safe! NEVER commit .jks files to Git." -ForegroundColor Red
