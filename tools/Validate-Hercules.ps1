param([switch]$Device)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot
$studioJava = 'C:\Program Files\Android\Android Studio\jbr'
if (Test-Path "$studioJava\bin\java.exe") { $env:JAVA_HOME = $studioJava }
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    throw 'Configura JAVA_HOME con JDK 21 o usa el JBR de Android Studio.'
}
& "$env:JAVA_HOME\bin\java.exe" -version
& .\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'La validación falló. Corrige el error antes de entregar el APK.' }
if ($Device) {
    $sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "$env:LOCALAPPDATA\Android\Sdk" }
    $adbPath = Join-Path $sdkRoot 'platform-tools\adb.exe'
    if (-not (Test-Path $adbPath)) { throw 'No se encontró adb para las pruebas instrumentadas.' }
    $devices = & $adbPath devices
    if ($LASTEXITCODE -ne 0 -or -not ($devices -match '\sdevice$')) { throw 'No hay un dispositivo autorizado. No se inicia una espera indefinida.' }
    & .\gradlew.bat :app:connectedDebugAndroidTest
    if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas instrumentadas.' }
}
Copy-Item 'app\build\outputs\apk\debug\app-debug.apk' 'Hercules-v1.21-panel-descanso.apk' -Force
Write-Output 'APK generado tras validaciones locales: Hercules-v1.21-panel-descanso.apk. Usa el mismo keystore de depuración de v1.18 para actualizar.'
