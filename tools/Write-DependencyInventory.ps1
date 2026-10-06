param(
    [string]$GradleCache = (Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1')
)

$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$inputPath = Join-Path $projectPath '.artifacts/dependencies.tsv'
$rows = Import-Csv -LiteralPath $inputPath -Delimiter "`t" -Header Scope,Group,Artifact,Version
$inventory = [System.Collections.Generic.List[string]]::new()
$inventory.Add('# 의존성 목록')
$inventory.Add('')
$inventory.Add('Gradle이 실제 선택한 직접·전이 의존성입니다. 앱·로컬 JVM 테스트·계측 테스트의 런타임 구성을 기준으로 하며 빌드 도구는 THIRD_PARTY_NOTICES.md에서 별도로 관리합니다.')
$inventory.Add('')
$inventory.Add('재생성: `gradlew -I tools/dependency-inventory.init.gradle :app:dependencyInventory` 실행 후 `pwsh -File tools/Write-DependencyInventory.ps1`을 실행합니다. POM 라이선스 메타데이터를 읽으며 원문 고지사항을 대체하지 않습니다.')
$inventory.Add('')
$inventory.Add('| 라이브러리 | 버전 | 포함 범위 | POM의 라이선스 | 출처 |')
$inventory.Add('| --- | --- | --- | --- | --- |')
$missing = [System.Collections.Generic.List[string]]::new()
foreach ($entry in ($rows | Group-Object Group,Artifact,Version | Sort-Object Name)) {
    $module = $entry.Group[0]
    $modulePath = Join-Path $GradleCache "$($module.Group)/$($module.Artifact)/$($module.Version)"
    $baseUrl = if ($module.Group.StartsWith('androidx.')) { 'https://dl.google.com/dl/android/maven2' } else { 'https://repo.maven.apache.org/maven2' }
    $groupPath = $module.Group.Replace('.', '/')
    $pomUrl = "$baseUrl/$groupPath/$($module.Artifact)/$($module.Version)/$($module.Artifact)-$($module.Version).pom"
    $pom = Get-ChildItem -LiteralPath $modulePath -Recurse -Filter '*.pom' | Select-Object -First 1
    if (-not $pom) {
        $pomCachePath = Join-Path $projectPath '.artifacts/poms'
        New-Item -ItemType Directory -Path $pomCachePath -Force | Out-Null
        $pomFile = Join-Path $pomCachePath "$($module.Group)-$($module.Artifact)-$($module.Version).pom"
        if (-not (Test-Path -LiteralPath $pomFile)) {
            Invoke-WebRequest -Uri $pomUrl -OutFile $pomFile
        }
        $pom = Get-Item -LiteralPath $pomFile
    }
    $licenseText = '확인 필요'
    $licenseSource = "[POM]($pomUrl)"
    if ($pom) {
        [xml]$metadata = Get-Content -LiteralPath $pom.FullName -Raw
        for ($depth = 0; $depth -lt 5 -and -not $metadata.project.licenses.license -and $metadata.project.parent; $depth++) {
            $parent = $metadata.project.parent
            $parentGroup = [string]$parent.groupId
            $parentArtifact = [string]$parent.artifactId
            $parentVersion = [string]$parent.version
            $parentBase = if ($parentGroup.StartsWith('androidx.')) { 'https://dl.google.com/dl/android/maven2' } else { 'https://repo.maven.apache.org/maven2' }
            $parentUrl = "$parentBase/$($parentGroup.Replace('.', '/'))/$parentArtifact/$parentVersion/$parentArtifact-$parentVersion.pom"
            $parentCache = Join-Path $projectPath '.artifacts/poms'
            New-Item -ItemType Directory -Path $parentCache -Force | Out-Null
            $parentFile = Join-Path $parentCache "$parentGroup-$parentArtifact-$parentVersion.pom"
            if (-not (Test-Path -LiteralPath $parentFile)) { Invoke-WebRequest -Uri $parentUrl -OutFile $parentFile }
            [xml]$metadata = Get-Content -LiteralPath $parentFile -Raw
            $licenseSource = "[POM]($pomUrl), [부모 POM]($parentUrl)"
        }
        $licenses = @($metadata.project.licenses.license)
        if ($licenses.Count -gt 0 -and $licenses[0]) {
            $licenseText = ($licenses | ForEach-Object {
                $name = ([string]$_.name).Replace('|', '/')
                if ($_.url) { "[$name]($($_.url))" } else { $name }
            }) -join '; '
        }
    }
    $coordinate = "$($module.Group):$($module.Artifact)"
    if ($licenseText -eq '확인 필요') { $missing.Add($coordinate) }
    $scope = if ($entry.Group.Scope -contains 'debugRuntimeClasspath') { '앱' } else { '테스트 전용' }
    $inventory.Add("| $coordinate | $($module.Version) | $scope | $licenseText | $licenseSource |")
}
$outputPath = Join-Path $projectPath 'docs/DEPENDENCIES.md'
[System.IO.File]::WriteAllText($outputPath, (($inventory -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))
Write-Output "Inventory written. Unique modules: $(($rows | Group-Object Group,Artifact,Version).Count)"
if ($missing.Count -gt 0) { throw "Missing license metadata: $($missing -join ', ')" }
