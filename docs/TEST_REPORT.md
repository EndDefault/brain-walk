# 검증 보고서

## 2026-09-22 · 프로젝트 기본 실행 환경

대상: `feature/project-setup`. 이번 검증은 기본 홈·이용 안내·화면 이동에 한정됩니다.

### 환경

| 항목 | 실제 확인값 |
| --- | --- |
| 호스트 | Windows 11, Java Corretto 21.0.10 |
| 빌드 | Gradle 8.14.4, AGP 8.13.2, Kotlin 2.2.21, SDK 36, Build Tools 36.0.0 |
| 실제 실행 기기 | Android Emulator `Medium_Tablet`, Android 14 / API 34, x86_64 |
| 기본 화면 | 2560×1600px, 320dpi → 1280×800dp, 가로 |
| 작은 화면 검증 | 같은 AVD에 720×1600px 적용 → 360×800dp, 세로, font_scale=2.0 |
| 참고 설계 기기 | Galaxy Tab A9+ 11인치. **해당 실물 기기에서는 미실행** |

### 실행 결과

| 검증 | 결과 |
| --- | --- |
| `:app:assembleDebug` | 통과, 디버그 APK 생성 |
| `:app:assembleDebugAndroidTest` | 통과, 계측 테스트 APK 생성 |
| `:app:lintDebug` | 통과: **오류 0, 경고 8** |
| `:app:connectedDebugAndroidTest` | 태블릿 가로 화면에서 **2개 통과, 실패 0** |
| 작은 세로 화면·글자 200%·Wi-Fi/모바일 데이터 OFF에서 `am instrument -w` | 동일한 **2개 통과, 실패 0** |
| 실제 화면 캡처 검토 | 가로 홈, 안내, 작은 화면 큰 글자 홈 확인. 세로 스크롤과 줄바꿈으로 내용 접근 가능 |
| APK manifest 검사 | minSdk=26, targetSdk=36, MainActivity 실행 등록 확인. **INTERNET 권한 없음** |
| 의존성/라이선스 추출 | 직접·전이 98개 모듈, 미확인 라이선스 메타데이터 0개 |

계측 테스트는 아래 두 동작을 검증합니다.

1. 빈 기록 안내 확인 → 이용 안내 열기 → 세 번째 안내 내용까지 스크롤 → 홈 복귀 버튼으로 돌아오기.
2. 이용 안내에서 Activity 재생성 → 목적지 유지 → 시스템 뒤로 가기로 홈 돌아오기.

작은 화면·오프라인 조건은 새로운 검증 조건이므로 동일 테스트를 재실행했습니다. 검증 후 화면 크기와 글자·네트워크 설정을 복원했습니다.

### 경고와 해결한 문제

- Lint 경고 8개는 `OldTargetApi` 1개, `AndroidGradlePluginVersion` 2개, `GradleDependency` 3개, `NewerVersionAvailable` 2개입니다. 고정한 API 36 및 안정 의존성보다 새로운 버전이 있다는 권고입니다. 경고를 숨기거나 baseline으로 제외하지 않았습니다. 업그레이드는 별도 호환성 검증 후 진행합니다.
- 초기 검사에서 발견된 API 27 전용 navigation bar 테마 속성은 제거하고 Activity의 edge-to-edge 처리에 맡겼습니다.
- Android 12 이상에서는 cloud backup과 device transfer를 모두 제외하는 `dataExtractionRules`를 적용했습니다. 이전 버전은 `allowBackup=false`와 제외 규칙을 사용합니다.
- 로컬 SDK 경로의 Windows 드라이브 구분자 이스케이프를 수정했습니다. `local.properties`는 Git에서 제외합니다.
- 한글 경로의 Android 빌드를 위한 `android.overridePathCheck=true` 경고가 있습니다. 이 경로에서 실제 빌드·설치·실행은 통과했습니다.

### 재현 명령과 결과 위치

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:connectedDebugAndroidTest --console=plain
.\gradlew.bat -I tools/dependency-inventory.init.gradle :app:dependencyInventory
pwsh -File tools/Write-DependencyInventory.ps1
```

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Lint: `app/build/reports/lint-results-debug.html`
- 계측 테스트: `app/build/reports/androidTests/connected/debug/index.html`
- 작은 화면·오프라인 실행 기록: 로컬 `.artifacts/small-largefont-offline-tests.txt` (`OK (2 tests)`)
- 디버그 APK SHA-256: `CE952C308CD6AA02B13D86D08B85A121D1C78BFED324B82DE1341AB367E55C46`

### 화면

![태블릿 가로 홈](screenshots/home-tablet.png)

![태블릿 이용 안내](screenshots/guide-tablet.png)

![작은 세로 화면·글자 200%](screenshots/home-small-largefont.png)

### 아직 검증하지 않은 항목

- 실제 Galaxy Tab A9+ 및 다른 실물 태블릿.
- 최소 지원 API 26, target API 36의 실제 실행. 현재 실행 검증은 API 34입니다.
- 화면 회전 센서와 분할 화면 조작. 현재 크기 변경·Activity 재생성만 검증했습니다.
- TalkBack 조작과 시니어 대상 사용성 평가.
- 타이머·재시도·16개 선택지·게임 중 백그라운드 중단·저장/복구·난이도/밴딧 로직: 이번 PR에 미구현이므로 미실행.
- 출시 서명·릴리스 APK·최종 전체 오프라인 훈련: 출시 준비 PR에서 수행합니다.
