# brain-walk · MemorySteps

Android 태블릿에서 인터넷 없이 사용하는 시니어 기억력 게임입니다. 색·그림·숫자 훈련과 기기 내부 기록을 단계별 PR로 개발합니다.

현재 앱에서는 **학습하기 → 게임 시작 → 기억 → 대기 → 보기 선택 → 10문제 결과**까지 플레이하고 기록을 기기에 저장합니다. 메인은 큰 ‘기억 산책’ 제목과 **학습하기·학습 현황·게임 설명** 세 버튼으로 구성합니다.

학습하기의 **게임 시작**은 색·그림·숫자를 섞은 정식 10문제입니다. 아래 **연습하기 → 색·그림·숫자**는 각 유형 10문제이며 정식 성적과 AI 학습 기록에서 제외합니다. 대상을 기억한 뒤 **다음**을 누르면 3초 대기 후 보기가 나타납니다. 크림색 바탕과 짙은 남색, 큰 글자·버튼을 사용합니다.

게임 안내·기억 대상·카운트다운은 중앙에 크게 표시하고 **다음 버튼은 하단에 고정**합니다. 정답은 중앙 문구, 오답은 남은 기회로 안내합니다. 문제 번호 옆 **일시정지**에서 계속하기·뒤로가기·홈 이동을 선택하며 시스템 뒤로가기도 이 메뉴를 엽니다.

정식 게임의 문제·보기·조건·정오·선택 시간·완료 진행은 Room에 자동 저장합니다. 앱 종료 후 **게임 이어하기**에서 완료 기록은 유지하고 중단된 문항만 같은 조건의 새 문제로 교체합니다. 진행 종료도 이미 완료한 기록을 지우지 않습니다. **학습 현황**에서 누적 결과·유형별 정답 수와 최근 30회 기록을 봅니다.

현재 조건은 기억 20초·대기 3초·보기 4개·풀이 제한 없음입니다. 완료한 종합 게임 수에 따른 4/3/3 순환도 영구 유지합니다. 적응 난이도와 AI 알고리즘은 아직 미구현이며, 이번 저장은 이후 학습에 사용할 원본 데이터를 보존하는 단계입니다.

## 실행

1. Android Studio에서 저장소 루트를 엽니다.
2. SDK Manager에서 Android SDK Platform 36과 Build Tools 36.0.0을 설치합니다.
3. Gradle JDK는 JDK 21을 사용합니다. 이 PC에서는 Corretto 21로 검증하며 앱의 Java/Kotlin 출력은 17입니다.
4. Android Studio가 생성하는 `local.properties`의 `sdk.dir`에 로컬 SDK 경로를 지정합니다. 이 파일은 커밋하지 않습니다.
5. Gradle 동기화 후 `app`을 Android 8.0(API 26) 이상 기기 또는 에뮬레이터에서 실행합니다.

Windows 명령줄:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

macOS/Linux에서는 `./gradlew`를 사용합니다. 첫 빌드는 Gradle과 의존성 다운로드를 위해 인터넷이 필요합니다. 앱 자체는 네트워크 권한 없이 실행됩니다.

한글 Windows 경로에서 JVM 테스트 클래스 로딩이 실패하는 Gradle 8 인코딩 문제를 피하도록 `file.encoding=COMPAT`를 적용했습니다. JDK 21의 시스템 문자셋을 사용하며 소스 파일은 UTF-8을 유지합니다. 자세한 환경과 제한은 [검증 보고서](docs/TEST_REPORT.md)에 기록했습니다.

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
| Room / KSP | 2.8.4 / 2.2.21-2.0.5 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 |
| SDK Build Tools | 36.0.0 |
| Java / Kotlin 바이트코드 | 17 |

버전 원본은 `gradle/libs.versions.toml`과 `gradle/wrapper/gradle-wrapper.properties`입니다. 호환성 근거와 선택 이유는 [결정 기록](docs/DECISIONS.md)에 있습니다.

## 구조

```text
app/src/main/
  java/com/example/memorysteps/
    MainActivity.kt
    game/                 문제 모델·세 유형 생성·판정·10문제 사이클
    data/                 Room DB·DAO·원본 기록 저장·종료 후 복구
    ui/                   메인·학습 선택·학습 현황·안내·게임·ViewModel
  res/                    한국어 문자열·앱 테마·직접 제작한 벡터 아이콘
app/src/androidTest/      메뉴·10문제 완료·연습 분리·Room 복구/중복/롤백 검증
app/schemas/              버전별 Room 스키마
app/src/test/             게임 엔진 JVM 테스트
gradle/                   버전 카탈로그·Wrapper
docs/                     명세·설계·검증·결정 기록
```

`difficulty/`, `ai/`는 후속 기능 PR에서 추가합니다. ViewModel + StateFlow와 Coroutines가 화면·저장 이벤트를 순서대로 처리합니다. DataStore는 실제 설정 기능을 추가할 때 도입합니다. Room 스키마 변경 시 기록 보존 마이그레이션이 필요하며 파괴적 초기화를 사용하지 않습니다.

## 문서와 개발 순서

- [기준 명세](docs/SPEC.md)
- [AI 설계](docs/AI_DESIGN.md)
- [데이터 모델](docs/DATA_MODEL.md)
- [게임 엔진과 화면 연결 계약](docs/GAME_ENGINE.md)
- [규칙 검증 예제](docs/RULE_EXAMPLES.md)
- [화면 디자인 방향](docs/UI_DIRECTION.md)
- [검증 보고서](docs/TEST_REPORT.md)
- [결정 기록](docs/DECISIONS.md)
- [외부 자료·라이선스](THIRD_PARTY_NOTICES.md)

게임 엔진/플레이 PR #3이 develop에 병합된 후 클래식 메뉴와 Room 저장·복구를 구현했습니다. 다음에는 보존한 원본 기록을 유형별 학습 묶음·난이도·AI에 연결합니다. 개발 중인 PR은 `develop`을 대상으로 하며, 의존하는 앞 PR이 병합된 뒤 최신 `develop`에서 다음 브랜치를 만듭니다.

브랜치 흐름은 `feature/* → develop → main`입니다. `develop`에 개발 결과를 모으고, 앱 완성 및 최종 검증 후 별도 PR로 `main`에 반영합니다.

사용자 승인에 따라 Public 저장소를 사용합니다. 목적별 작은 커밋과 초안 PR로 검토하고 자동 병합·강제 push는 하지 않습니다. `.idea/`, `local.properties`, 빌드 결과, 개인 플레이 DB, 토큰, 서명 키는 커밋하지 않습니다.
