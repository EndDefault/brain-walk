param([string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk")
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskRuntime = Join-Path $taskRoot '.local-tools/llama.cpp'
$taskCommit = '5e03bdd8700948b9c41c54dd1b00f28a2aebc03f'
if ((git -C $taskRuntime rev-parse HEAD) -ne $taskCommit) { throw 'Expected pinned llama.cpp checkout' }
# Export the pinned source into a separate temporary native build directory.
$taskBuildRoot = Join-Path $env:TEMP 'MemoryStepsAuthor-5e03bdd'
$taskSource = Join-Path $taskBuildRoot 'source'
$taskLlama = Join-Path $taskBuildRoot 'llama'
New-Item -ItemType Directory -Force -Path $taskSource,$taskLlama | Out-Null
$taskArchive = Join-Path $taskBuildRoot 'llama.tar'
if (-not (Test-Path (Join-Path $taskLlama 'CMakeLists.txt'))) {
    git -C $taskRuntime archive --format=tar -o $taskArchive HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Runtime export failed' }
    tar -xf $taskArchive -C $taskLlama
    if ($LASTEXITCODE -ne 0) { throw 'Runtime extraction failed' }
}
Copy-Item -LiteralPath (Join-Path $taskRoot 'app/src/main/cpp/CMakeLists.txt'),(Join-Path $taskRoot 'app/src/main/cpp/question_author.cpp') -Destination $taskSource -Force
$taskNdk = (Join-Path $Sdk 'ndk/28.2.13676358').Replace('\','/')
$taskCmake = Join-Path $taskRoot '.local-tools/question-ai-venv/Lib/site-packages/cmake/data/bin/cmake.exe'
$taskNinja = Join-Path $Sdk 'cmake/3.22.1/bin/ninja.exe'
foreach ($taskAbi in @('x86_64','arm64-v8a')) {
    $taskBuild = Join-Path $taskBuildRoot "$taskAbi-v3"
    $taskBin = "$taskNdk/toolchains/llvm/prebuilt/windows-x86_64/bin"
    $taskTools = @('-DGIT_EXECUTABLE=C:/Program Files/Git/cmd/git.exe')
    foreach ($taskTool in @('AR','RANLIB','STRIP','NM','OBJDUMP','OBJCOPY','READELF','DLLTOOL','ADDR2LINE')) {
        $taskTools += "-DCMAKE_$taskTool=$taskBin/llvm-$($taskTool.ToLower()).exe"
    }
    $taskTools += "-DCMAKE_LINKER=$taskBin/ld.lld.exe"
    $taskTools += "-DCMAKE_TAPI=$taskBin/llvm-ar.exe"
    $taskTools += "-DCMAKE_C_COMPILER_AR=$taskBin/llvm-ar.exe"
    $taskTools += "-DCMAKE_CXX_COMPILER_AR=$taskBin/llvm-ar.exe"
    $taskTools += "-DCMAKE_C_COMPILER_RANLIB=$taskBin/llvm-ranlib.exe"
    $taskTools += "-DCMAKE_CXX_COMPILER_RANLIB=$taskBin/llvm-ranlib.exe"
    & $taskCmake -S $taskSource -B $taskBuild -G Ninja "-DCMAKE_MAKE_PROGRAM=$taskNinja" "-DCMAKE_TOOLCHAIN_FILE=$taskNdk/build/cmake/android.toolchain.cmake" "-DANDROID_ABI=$taskAbi" '-DANDROID_PLATFORM=android-26' '-DCMAKE_BUILD_TYPE=Release' '-DCMAKE_FIND_USE_SYSTEM_ENVIRONMENT_PATH=OFF' "-DFETCHCONTENT_SOURCE_DIR_LLAMA=$taskLlama" @taskTools
    if ($LASTEXITCODE -ne 0) { throw "Configure failed: $taskAbi" }
    & $taskCmake --build $taskBuild --target memory_author -j 6
    if ($LASTEXITCODE -ne 0) { throw "Native compile failed: $taskAbi" }
    $taskDest = Join-Path $taskRoot "app/src/main/jniLibs/$taskAbi"
    New-Item -ItemType Directory -Force -Path $taskDest | Out-Null
    Copy-Item -LiteralPath (Join-Path $taskBuild 'libmemory_author.so') -Destination $taskDest -Force
    $taskTriple = if ($taskAbi -eq 'x86_64') { 'x86_64-linux-android' } else { 'aarch64-linux-android' }
    Copy-Item -LiteralPath (Join-Path $taskNdk "toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/lib/$taskTriple/libc++_shared.so") -Destination $taskDest -Force
    Get-ChildItem -LiteralPath $taskDest -Filter '*.so' | ForEach-Object {
        & "$taskBin/llvm-strip.exe" --strip-unneeded $_.FullName
        if ($LASTEXITCODE -ne 0) { throw 'Native strip failed' }
    }
}
