# brain-walk · MemorySteps

Android 태블릿에서 인터넷 없이 사용하는 시니어 기억력 게임입니다. 색·그림·숫자 훈련과 기기 내부 기록을 단계별 PR로 개발합니다.

현재 앱에서는 **학습하기 → 게임 시작 → 기억 → 대기 → 보기 선택 → 10문제 결과**까지 플레이하고 기록을 기기에 저장합니다. 메인은 큰 ‘기억 산책’ 제목과 **학습하기·학습 현황·게임 설명** 세 버튼으로 구성합니다.

학습하기의 **게임 시작**은 색·그림·숫자를 섞은 정식 10문제입니다. 아래 **연습하기 → 색·그림·숫자**는 각 유형 10문제이며 정식 성적과 AI 학습 기록에서 제외합니다. 대상을 기억한 뒤 **다음**을 누르면 3초 대기 후 보기가 나타납니다. 크림색 바탕과 짙은 남색, 큰 글자·버튼을 사용합니다.

게임 안내·기억 대상·카운트다운은 중앙에 크게 표시하고 **다음 버튼은 하단에 고정**합니다. 정답은 중앙 문구, 오답은 남은 기회로 안내합니다. 문제 번호 옆 **일시정지**에서 계속하기·뒤로가기·홈 이동을 선택하며 시스템 뒤로가기도 이 메뉴를 엽니다.

정식 게임의 문제·보기·조건·정오·선택 시간·완료 진행은 Room에 자동 저장합니다. 앱 종료 후 **게임 이어하기**에서 완료 기록은 유지하고 중단된 문항만 같은 조건의 새 문제로 교체합니다. 진행 종료도 이미 완료한 기록을 지우지 않습니다. **학습 현황**에서 누적 결과·유형별 정답 수와 최근 30회 기록을 봅니다.

초기 조건은 기억 20초·대기 3초·보기 4개·풀이 제한 없음입니다. **오프라인 Discounted UCB 밴딧 AI**가 한 종합 게임의 색·그림·숫자 10문제를 합산해 판단하고, 다음 게임의 **공통 난이도 한 개**를 정합니다. 첫 게임을 마치면 바로 조정할 수 있으며 세 유형에 같은 조건을 적용합니다. 잘 풀면 기억 시간·풀이 시간·보기 수·대기 시간이 규칙에 따라 조정되며, 어려워하면 단축 이력을 복원합니다. 연습은 초기 조건을 사용하며 AI를 학습시키지 않습니다.

학습 현황에서 공통 난이도와 이번 게임의 완료 기록을 확인합니다. 앱 종료·일시정지 후에는 같은 게임의 남은 문제를 이어갑니다. 사용자가 게임을 중간에 끝내면 성적은 보존하되 부분 기록을 다음 게임의 10문제에 섞지 않습니다.

기존 v1/v2 DB는 원본 기록을 보존해 v3로 옮깁니다. 이전 유형별 AI 이력은 보관하고, 통합 AI는 기본 난이도에서 새로 학습합니다. 업그레이드 전에 시작한 게임은 저장된 조건으로 마치고 성적만 남기며, 다음 새 종합 게임부터 통합 학습을 시작합니다. AI는 기기 내부 알고리즘이므로 외부 API·계정·인터넷 연결이 필요하지 않습니다.

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
    difficulty/           통계·난이도 후보·복원 규칙
    ai/                   Discounted UCB 선택·할인 보상
    data/                 Room v3·원본/게임별 통합 학습/결정 저장·종료 후 복구
    ui/                   메뉴·학습 현황·게임·ViewModel·개발 진단
  res/                    한국어 문자열·앱 테마·직접 제작한 벡터 아이콘
app/src/androidTest/      게임·AI 연결·마이그레이션·중복/롤백·화면 검증
app/schemas/              버전별 Room 스키마
app/src/test/             게임·난이도·밴딧 JVM 테스트
gradle/                   버전 카탈로그·Wrapper
docs/                     명세·설계·검증·결정 기록
```

ViewModel + StateFlow와 Coroutines가 화면·저장 이벤트를 순서대로 처리합니다. 답변·묶음·AI 보상·결정·게임 완료를 한 Room 트랜잭션으로 저장합니다. 디버그 빌드의 **학습 현황 → 개발용 AI 확인**에서 판단과 학습값을 보고, 진행 중인 게임이 없을 때 비교 규칙으로 전환할 수 있습니다. 배포용 빌드는 이 진입점을 제공하지 않습니다. DataStore는 실제 환경 설정 기능을 추가할 때 도입합니다. Room은 보존 마이그레이션을 사용하며 파괴적 초기화를 하지 않습니다.

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

클래식 메뉴·기록 보존 PR #4 병합 후 AI를 연결했으며, 최신 사용자 결정에 따라 게임별 합산 판단과 공통 난이도를 적용했습니다. 실물 태블릿과 시니어 사용성 검증, 조건별 성과 비교·효과음/설정은 남아 있습니다. 개발 중인 PR은 `develop`을 대상으로 하며, 의존하는 앞 PR이 병합된 뒤 최신 `develop`에서 다음 브랜치를 만듭니다.

브랜치 흐름은 `feature/* → develop → main`입니다. `develop`에 개발 결과를 모으고, 앱 완성 및 최종 검증 후 별도 PR로 `main`에 반영합니다.

사용자 승인에 따라 Public 저장소를 사용합니다. 목적별 작은 커밋과 초안 PR로 검토하고 자동 병합·강제 push는 하지 않습니다. `.idea/`, `local.properties`, 빌드 결과, 개인 플레이 DB, 토큰, 서명 키는 커밋하지 않습니다.
