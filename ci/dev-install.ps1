<#
.SYNOPSIS
  Build the floating-button app locally and put it on the attached phone.

.DESCRIPTION
  The everyday loop: edit, run this, look at the phone. Builds only :bubble by default,
  which skips the diagnostic app's Firebase / Room / KSP surface and takes a fraction of
  the time.

.EXAMPLE
  .\ci\dev-install.ps1
  .\ci\dev-install.ps1 -Variant debug
  .\ci\dev-install.ps1 -Module app -NoLaunch
#>
param(
  [ValidateSet("release", "debug")] [string] $Variant = "release",
  [ValidateSet("bubble", "app")]    [string] $Module  = "bubble",
  [string] $Adb = "D:\android-tools\Sdk\platform-tools\adb.exe",
  [switch] $NoLaunch,
  [switch] $SkipBuild
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root

try {
  if (-not (Test-Path $Adb)) {
    throw "adb not found at $Adb - pass -Adb <path to adb.exe>"
  }

  $devices = & $Adb devices | Select-String -Pattern "\sdevice$"
  if (-not $devices) {
    throw "No device attached. Check the cable and that USB debugging is on."
  }
  Write-Host "device: $($devices[0].ToString().Trim())" -ForegroundColor Cyan

  $task = ":{0}:assemble{1}" -f $Module, (Get-Culture).TextInfo.ToTitleCase($Variant)
  if (-not $SkipBuild) {
    Write-Host "building $task" -ForegroundColor Cyan
    & .\gradlew.bat $task
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed" }
  }

  $apk = Join-Path $root ("{0}\build\outputs\apk\{1}\{0}-{1}.apk" -f $Module, $Variant)
  if (-not (Test-Path $apk)) { throw "Built, but no APK at $apk" }
  "{0}  ({1:N0} bytes)" -f $apk, (Get-Item $apk).Length | Write-Host

  $pkg = if ($Module -eq "bubble") { "com.aistudio.ipcsolution.bubble" } else { "com.aistudio.ipcsolution.poc" }

  Write-Host "installing $pkg" -ForegroundColor Cyan
  & $Adb install -r $apk
  if ($LASTEXITCODE -ne 0) {
    # Only a key change causes this, and the keys are fixed in ci/ - so it means the phone
    # still holds a build from before those were committed.
    Write-Host "install refused; uninstalling the old signature and retrying" -ForegroundColor Yellow
    & $Adb uninstall $pkg | Out-Null
    & $Adb install -r $apk
    if ($LASTEXITCODE -ne 0) { throw "Install failed" }
  }

  # Saves tapping through the dialogs on every reinstall.
  & $Adb shell appops set $pkg SYSTEM_ALERT_WINDOW allow | Out-Null
  foreach ($p in @("READ_CALL_LOG", "READ_PHONE_STATE", "POST_NOTIFICATIONS")) {
    & $Adb shell pm grant $pkg "android.permission.$p" 2>$null | Out-Null
  }

  if (-not $NoLaunch) {
    & $Adb logcat -c
    if ($Module -eq "bubble") {
      & $Adb shell am start -a com.example.bubble.action.CALL_LOG -n "$pkg/com.example.bubble.BubbleActivity"
    } else {
      & $Adb shell monkey -p $pkg -c android.intent.category.LAUNCHER 1 | Out-Null
    }
    Start-Sleep -Seconds 4
    $crash = & $Adb logcat -d -b crash | Select-String "FATAL EXCEPTION" | Select-Object -First 1
    if ($crash) {
      Write-Host "CRASHED:" -ForegroundColor Red
      & $Adb logcat -d -b crash | Select-Object -First 25
      exit 1
    }
    Write-Host "running, no crash" -ForegroundColor Green
  }
}
finally {
  Pop-Location
}
