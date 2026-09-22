# brain-walk · MemorySteps

Android 태블릿에서 인터넷 없이 사용하는 시니어 기억력 게임입니다. 색·그림·숫자 훈련과 기기 내부 기록을 단계별 PR로 개발합니다.

현재 단계는 **프로젝트 기본 실행 환경**입니다. 홈의 빈 기록 상태와 이용 안내를 실행할 수 있으며, 실제 문제 풀이·저장·난이도 조절·AI는 후속 PR에서 구현합니다.

## 실행

1. Android Studio에서 저장소 루트를 엽니다.
2. SDK Manager에서 Android SDK Platform 36과 Build Tools 36.0.0을 설치합니다.
3. Gradle JDK는 JDK 17 또는 21을 사용합니다. 이 PC에서는 Corretto 21로 검증합니다.
4. Android Studio가 생성하는 `local.properties`의 `sdk.dir`에 로컬 SDK 경로를 지정합니다. 이 파일은 커밋하지 않습니다.
5. Gradle 동기화 후 `app`을 Android 8.0(API 26) 이상 기기 또는 에뮬레이터에서 실행합니다.

Windows 명령줄:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

macOS/Linux에서는 `./gradlew`를 사용합니다. 첫 빌드는 Gradle과 의존성 다운로드를 위해 인터넷이 필요합니다. 앱 자체는 네트워크 권한 없이 실행됩니다.

디버그 APK: `app/build/outputs/apk/debug/app-debug.apk`

## 고정한 빌드 구성

| 항목 | 버전 |
| --- | --- |
| Gradle Wrapper | 8.14.4 · 공식 SHA-256 검증 |
| Android Gradle Plugin | 8.13.2 |
| Kotlin / Compose compiler plugin | 2.2.21 |
| Compose BOM | 2026.02.01 |
| Activity Compose | 1.12.4 |
| Navigation Compose | 2.9.8 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 |
| SDK Build Tools | 36.0.0 |
| Java / Kotlin 바이트코드 | 17 |

버전 원본은 `gradle/libs.versions.toml`과 `gradle/wrapper/gradle-wrapper.properties`입니다. 호환성 근거와 선택 이유는 [결정 기록](docs/DECISIONS.md)에 있습니다.

## 구조

```text
app/src/main/
  java/com/example/memorysteps/
    MainActivity.kt
    ui/                   홈·이용 안내·Navigation·테마
  res/                    한국어 문자열·앱 테마·직접 제작한 벡터 아이콘
app/src/androidTest/      화면 이동·Activity 재생성 검증
gradle/                   버전 카탈로그·Wrapper
docs/                     명세·설계·검증·결정 기록
```

`game/`, `difficulty/`, `ai/`, `data/`는 해당 기능 PR에서 실제 코드와 함께 추가합니다. ViewModel + StateFlow, Room, DataStore, Coroutines도 해당 상태·저장 기능에서 도입합니다.

## 문서와 개발 순서

- [기준 명세](docs/SPEC.md)
- [AI 설계](docs/AI_DESIGN.md)
- [데이터 모델](docs/DATA_MODEL.md)
- [검증 보고서](docs/TEST_REPORT.md)
- [결정 기록](docs/DECISIONS.md)
- [외부 자료·라이선스](THIRD_PARTY_NOTICES.md)

이번 PR 다음에는 명세·AI·데이터 설계를 구체화하는 문서 PR을 진행하고, 문제 엔진과 게임 화면 순으로 개발합니다. 앞 PR이 병합된 뒤 최신 `main`에서 다음 브랜치를 만듭니다.

사용자 승인에 따라 Public 저장소를 사용합니다. 목적별 작은 커밋과 초안 PR로 검토하고 자동 병합·강제 push는 하지 않습니다. `.idea/`, `local.properties`, 빌드 결과, 개인 플레이 DB, 토큰, 서명 키는 커밋하지 않습니다.
