# Builds unicorn (ARM guest only) as static libraries for Android and copies them into app/src/main/cpp/vmrp/unicorn.
# Needs: CMake, Git for Windows (its sh is used by qemu/configure), the NDK from local.properties/build.gradle.
# Usage: powershell -File tools/build_unicorn.ps1 -Source <unicorn 2.1.4 checkout> [-Abis arm64-v8a,armeabi-v7a,x86_64,x86]
param(
    [Parameter(Mandatory = $true)][string]$Source,
    [string[]]$Abis = @("arm64-v8a", "armeabi-v7a", "x86_64", "x86"),
    [string]$Ndk = "F:/jetbrains/AndroidStudioSDK/ndk/30.0.16248370",
    [string]$CMake = "D:\Program Files\CMake\bin\cmake.exe",
    [string]$GitUsrBin = "D:\Program Files\Git\usr\bin",
    [string]$Work = "$env:TEMP\unicorn-android"
)
$ErrorActionPreference = "Stop"
$env:PATH = "$GitUsrBin;$env:PATH"
$env:PKG_CONFIG = "false"
$out = Join-Path $PSScriptRoot "..\app\src\main\cpp\vmrp\unicorn"
$strip = "$Ndk/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe"

foreach ($abi in $Abis) {
    $build = Join-Path $Work $abi
    if (-not (Test-Path "$build\Makefile")) {
        New-Item -ItemType Directory -Force $build | Out-Null
        & $CMake -S $Source -B $build -G "Unix Makefiles" `
            "-DCMAKE_MAKE_PROGRAM=$Ndk/prebuilt/windows-x86_64/bin/make.exe" `
            "-DCMAKE_TOOLCHAIN_FILE=$Ndk/build/cmake/android.toolchain.cmake" `
            "-DANDROID_ABI=$abi" -DANDROID_PLATFORM=android-21 -DCMAKE_BUILD_TYPE=Release `
            -DUNICORN_ARCH=arm -DBUILD_SHARED_LIBS=OFF -DUNICORN_BUILD_TESTS=OFF `
            -DUNICORN_INSTALL=OFF -DUNICORN_LEGACY_STATIC_ARCHIVE=ON
        if ($LASTEXITCODE -ne 0) { throw "configure failed: $abi" }
    }
    & $CMake --build $build -j 12
    if ($LASTEXITCODE -ne 0) { throw "build failed: $abi" }
    $dst = Join-Path $out "lib\$abi"
    New-Item -ItemType Directory -Force $dst | Out-Null
    Copy-Item "$build\libunicorn.a" $dst -Force
    & $strip --strip-debug "$dst\libunicorn.a"
    Write-Host "$abi -> $dst"
}
New-Item -ItemType Directory -Force "$out\include" | Out-Null
Copy-Item -Recurse -Force "$Source\include\unicorn" "$out\include\"
Copy-Item -Force "$Source\COPYING" "$out\COPYING"
