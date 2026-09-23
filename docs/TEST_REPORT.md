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

## 2026-09-22 · 설계 문서 구체화

대상: `feature/game-spec`, 기준 `develop`의 PR #1 병합 커밋 `7ad0221`.

- 변경 범위: 난이도·AI 계약, 저장 키·트랜잭션·복구, 규칙 예제, 차분한 기록형 디자인, 종합 훈련 시작 경로와 순환 기준.
- 검토: 사용자 명세와 C 경계·반올림·P=15·복원 잔량·다음 묶음 보상·4/3/3 순환을 대조했습니다. 명세를 구체화한 판단은 DECISIONS.md에 기록했습니다.
- 문서 검사: `git diff --check` 통과, 로컬 Markdown 링크 18개 확인·누락 0개입니다.
- 앱 소스·리소스·의존성은 변경하지 않았으므로 이번 문서 PR에서 빌드와 기기 테스트를 재실행하지 않습니다. 위 첫 PR의 결과는 이전 코드에 대한 실행 기록입니다.
- RULE_EXAMPLES.md는 구현할 테스트의 입력·기대값입니다. **난이도·AI 로직 테스트가 통과했다는 뜻이 아닙니다.** 해당 엔진 PR에서 실제 테스트를 추가합니다.
- UI_DIRECTION.md는 선택한 디자인의 구현 기준이며, 이번 PR에서 화면 디자인이나 종합 훈련 플레이 기능을 완료했다고 보고하지 않습니다.

## 2026-09-23 · 게임 엔진

대상: `feature/game-engine`, 기준 develop의 PR #2 병합 커밋 `dd00acd`. Windows 11, Corretto 21.0.10, 기존 고정 Gradle/AGP/Kotlin/SDK에서 실행했습니다.

| 검증 | 결과 |
| --- | --- |
| `:app:testDebugUnitTest` | **38개 통과, 실패·건너뜀 0**: 생성 8, 모델 검증 7, 진행/판정 23 |
| 생성 조합 | 세 유형 × 다섯 보기 수 × 100시드 = 1,500개 문제에서 정답 하나·중복 없는 보기 확인 |
| `:app:assembleDebug` | 통과, 디버그 APK 생성 |
| `:app:lintDebug` | 통과: 오류 0, 기존 버전 권고 경고 8 |
| 의존성/라이선스 추출 | 앱 81·JVM 테스트 83·계측 테스트 82 외부 모듈, 중복 제거 98개·미확인 메타데이터 0 |

진행 테스트에는 1·2·3회차 정답, 세 번 오답, 중복 오답 무시, 첫 선택/누적 시간 분리, 무제한 풀이, 보기 렌더링 지연, 14,999ms 정답과 15,000ms 이상 시간 초과, 각 단계의 중단, 완료 결과 불변성, 읽기 전용 스냅샷을 포함했습니다. 가짜 단조 시계로 실제 대기 없이 경계를 검증합니다.

### 해결한 실행 환경 문제

첫 JVM 테스트 실행은 테스트 본문에 도달하지 못하고 클래스 세 개 모두 `ClassNotFoundException`으로 실패했습니다. 컴파일된 클래스는 존재했으며 Gradle의 테스트 인수 파일은 UTF-8, Java 실행기의 시스템 문자셋은 MS949였습니다. `file.encoding=COMPAT`로 인수 파일과 시스템 인코딩을 맞춘 뒤 38개가 통과했고, 해당 설정을 저장한 상태로 테스트·빌드·Lint를 다시 통과했습니다. [Gradle의 비 ASCII 경로 테스트 이슈](https://github.com/gradle/gradle/issues/30391)와 [JDK 인코딩 호환 옵션](https://docs.oracle.com/en/java/javase/21/migrate/preparing-migration.html)을 참고했습니다.

이 설정은 JDK 21에서 검증했습니다. OS 문자셋 자체가 폴더 이름을 표현하지 못하는 환경까지 검증한 것은 아닙니다. Java 소스는 UTF-8을 명시하고 Kotlin 소스도 UTF-8을 유지합니다.

### 재현

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
.\gradlew.bat -I tools/dependency-inventory.init.gradle :app:dependencyInventory
pwsh -File tools/Write-DependencyInventory.ps1
```

- JVM 보고서: `app/build/reports/tests/testDebugUnitTest/index.html`
- Lint 보고서: `app/build/reports/lint-results-debug.html`
- APK: `app/build/outputs/apk/debug/app-debug.apk`
- 로컬 실행 기록: `.artifacts/game-engine-verification.log`

### 이번 범위에서 실행하지 않은 검증

UI·Android 생명주기 연결은 아직 없으므로 에뮬레이터의 게임 플레이·화면 표시 시점·백그라운드 복귀 테스트는 실행하지 않았습니다. 기존 홈/안내 UI는 변경하지 않아 계측 테스트도 재실행하지 않았습니다. 위 첫 PR의 기기 실행 결과는 과거 코드의 기록입니다. 실제 그림 렌더링, 색 구분 사용성, 16개 보기 접근성, 저장·프로세스 복구, 4/3/3 사이클, 난이도/밴딧 검증은 해당 후속 PR에 남습니다.

## 2026-09-23 · 시작 버튼에서 실제 플레이까지

사용자 후속 요청으로 아직 병합 전인 `feature/game-engine`에 화면과 메모리 내 사이클을 추가했습니다. 위 엔진 단독 검증 이후의 최신 결과입니다.

| 검증 | 결과 |
| --- | --- |
| JVM 테스트 | **45개 통과**, 실패·건너뜀 0. 기존 엔진 38 + 사이클 7 |
| 태블릿 계측 테스트 | **5개 통과**, 실패 0. Medium_Tablet API 34, 2560×1600px/320dpi/글자 100% |
| 작은 화면·오프라인 계측 테스트 | 동일 **5개 통과**, 실패 0. 720×1600px/320dpi → 360×800dp, 글자 200%, Wi-Fi·모바일 데이터 OFF |
| 최종 APK·계측 APK 빌드 | `assembleDebug`, `assembleDebugAndroidTest` 통과 |
| 최종 Lint | 오류 0, 버전 권고 경고 **11**. 미사용 문자열 경고 9개는 리소스 정리로 해결 |
| 외부 의존성 | 기존 고유 98개 유지, 라이선스 메타데이터 미확인 0 |

계측 테스트는 기존 안내 이동/재생성 2개와 다음 게임 테스트 3개입니다.

1. 홈의 종합·색·그림·숫자 시작 버튼 네 개가 기억 화면과 다음 버튼으로 연결됩니다. 홈 복귀와 진행 종료 확인도 실행합니다.
2. 종합 훈련 10문제에서 기억 대상을 읽고 다음 → 실제 3초 대기 → 정답 보기를 선택합니다. 마지막 전체 결과와 홈의 첫 선택 정답 10/10 표시를 확인합니다.
3. 오답 보기 비활성화 후 정답, 다음 문제에서 Activity 재생성, 중단 안내와 새 문제 교체, 홈 왕복 이어하기를 확인합니다. 완료한 첫 문제는 보존되고 문제 순서가 2/10으로 유지됩니다.

첫 태블릿 실행의 실패 1개는 테스트 헬퍼가 스크롤 영역이 없는 종료 확인 대화상자에도 `performScrollTo()`를 적용한 문제였습니다. 해당 확인 버튼을 직접 클릭하도록 수정 후 5개가 통과했습니다. 화면 이탈 중인 이전 사이클의 정리 이벤트를 세션 ID로 제한하는 보완도 최종 태블릿/작은 화면 검증에 포함했습니다.

최종 Lint 경고는 OldTargetApi 1, AndroidGradlePluginVersion 2, GradleDependency 5, NewerVersionAvailable 3개입니다. Lifecycle/Coroutines를 기존 전이 버전과 같은 직접 의존성으로 명시하면서 버전 권고 대상이 늘었으며 경고를 숨기지 않았습니다.

### 실제 화면

![시작 버튼이 연결된 홈](screenshots/play-home-tablet.png)
![보기 선택 화면](screenshots/play-options-tablet.png)

[기억 화면](screenshots/play-memory-tablet.png) · [정답 결과](screenshots/play-result-tablet.png) · [작은 화면·글자 200% 홈](screenshots/play-home-largefont.png)

태블릿에서 실제 시작 → 기억 → 보기 → 정답 화면을 캡처하고 검토했습니다. 작은 화면에서는 문구가 줄바꿈되고 세로 스크롤로 아래 버튼에 접근할 수 있습니다. 작은 화면 검증 뒤 화면 크기·글자·네트워크 설정을 원래 값으로 복원했습니다.

### 재현과 한계

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:connectedDebugAndroidTest --console=plain
```

실행 로그는 `.artifacts/play-verification.log`, `.artifacts/play-small-offline-tests.txt`, 최종 문자열 정리 후 빌드는 `.artifacts/play-final-build.log`입니다. 작은 화면/오프라인 실행은 설치된 테스트 APK로 `am instrument -w`를 실행했습니다.

당시 모든 플레이는 초기 조건(20초·3초·보기 4개·풀이 무제한)이었습니다. 타이머 경계는 JVM에서 검증했으나 제한시간이 있는 실제 UI, 6/9/12/16개 보기의 기기 사용성, TalkBack·시니어 실물 평가와 API 26/36 실행은 미검증입니다. 이 단계에는 Room 저장·프로세스 복구가 없었습니다. 아래 저장 기능 검증이 이후 결과입니다.

## 2026-09-23 · 클래식 메뉴와 학습 기록 영구 저장

대상: `feature/classic-home-persistent-records`, 기준 develop의 PR #3 병합 커밋 `5388a69`. 기존 Windows/Corretto 21/Medium_Tablet API 34 환경을 사용했습니다.

| 검증 | 결과 |
| --- | --- |
| JVM 엔진·사이클 | **45개 통과**, 실패·건너뜀 0 |
| 태블릿 계측 테스트 | **13개 통과**, 실패 0: Room 7 + UI 6 |
| 360×800dp·글자 200%·Wi-Fi/데이터 OFF | UI **6개 통과**, 실패 0 |
| 최종 앱·계측 APK 빌드 | 통과 |
| 최종 Lint | 오류 0, 버전 권고 경고 **14**, 미사용 리소스 경고 0 |
| 의존성/라이선스 | 고유 **103개**, 앱 86·JVM 88·계측 82, 미확인 라이선스 메타데이터 0 |
| 실제 앱 강제 종료/재실행 | 완료 문제·정답·현재 슬롯 보존 및 새 문제로 이어하기 확인 |

### 저장 검증

실제 파일 기반 Room DB를 닫고 다시 여는 테스트를 사용했습니다. 인메모리 DB 복원으로 대신하지 않았습니다.

1. 오답→정답의 선택 내역/250ms·750ms 응답 시간/1,200ms 노출 시간 보존. 다음 문항 도중 DB를 닫고 단조 시각이 달라진 환경에서 재개해 완료 1문항 유지, 미완료 문항 무효, 같은 조건의 새 UUID·대상 확인.
2. 10문항 완료의 동시 재저장 5회에도 완료 종합 수 1, 유효 문제 10, 진행 포인터 해제. 재시작 후 다음 4문항 배정 유형은 그림.
3. 진행 종료는 STOPPED로 남기고 완료 문항을 유지. 종합 완료 수는 증가하지 않음.
4. 색·그림·숫자 콘텐츠/보기 순서/생성 버전과 7초·9.5초·16보기·15초 풀이 조건 왕복 보존.
5. 중복 문제 ID로 생성 실패 시 사이클·슬롯·진행 포인터 모두 롤백.
6. 복구로 무효화한 문항을 이전 프로세스가 덮어쓸 수 없음.
7. 연습을 저장소에 넣으려는 요청 거부, 성적 행 없음.

에뮬레이터에서도 정식 게임 첫 문항을 정답 처리하고 두 번째 기억 화면에서 `am force-stop`한 뒤 앱을 다시 실행했습니다. 학습 선택의 **10문제 중 1문제 완료**, 이어하기의 **2/10·중단 안내**, 새 문제로 계속하기의 **2/10·새 그림**을 확인했습니다. 학습 현황에는 기존 검증 게임 10문제와 새 완료 문제 1개를 합한 **11문제·첫 정답 11/11**이 남았습니다.

### 메뉴·접근성 검증

메인 클릭 가능 메뉴가 정확히 세 개인지, 학습 현황 빈 상태/설명 이동·Activity 재생성, 학습 선택의 네 시작 경로, 혼합 10문제 완료와 기록 화면 반영, 오답 비활성화·중단 재개, 연습 플레이 후 정식 DB가 비어 있는지를 검증했습니다.

작은 화면에서는 최종 APK로 같은 UI 테스트 6개를 실행했습니다. 글자 확대를 제한하지 않고 줄바꿈·스크롤로 모든 버튼에 접근합니다. 종료 후 화면 크기·글자·Wi-Fi·모바일 데이터 설정을 원래대로 복원했습니다. 최초 셸 호출의 인수 오류는 테스트 시작 전 발생했으며 사용자 번호 0과 인용한 클래스 목록을 지정한 재실행에서 통과했습니다.

![클래식 메인](screenshots/classic-home-tablet.png)
![학습 선택](screenshots/classic-learn-tablet.png)

[학습 현황](screenshots/records-tablet.png) · [작은 화면·글자 200%](screenshots/classic-home-largefont.png)

### 재현·보고서·남은 범위

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:connectedDebugAndroidTest --console=plain
```

- 전체 검증: `.artifacts/classic-records-verification.log`, 표준 Gradle JVM/계측 보고서.
- 미사용 문자열 정리 후 빌드·JVM·Lint: `.artifacts/classic-records-final-build.log`.
- 작은 화면·오프라인: `.artifacts/records-small-offline-tests.txt`의 `OK (6 tests)`.
- Lint 경고: OldTargetApi 1, AndroidGradlePluginVersion 2, GradleDependency 8, NewerVersionAvailable 3. 버전 권고를 숨기지 않았습니다.
- Room 스키마 v1 JSON을 커밋합니다. 첫 DB 버전이므로 이전 DB에서의 마이그레이션은 없으며, 향후 변경 시 기존 데이터를 보존하는 마이그레이션 테스트가 필요합니다.

저장 공간 부족·손상된 DB 오류 대화상자의 실제 기기 오류 주입, 실물 Galaxy Tab A9+·TalkBack·시니어 사용성·API 26/36 실행은 아직 검증하지 않았습니다. 앱 삭제/데이터 지우기는 이번 보존 범위가 아닙니다. AI 묶음·적응 난이도·밴딧 학습값은 아직 미구현이며 현재는 후속 학습에 필요한 원본만 영구 보존합니다.

## 2026-09-23 · 중앙 게임 화면과 하단 고정 진행 버튼

사용자 후속 요청으로 열려 있는 PR #4에 게임 화면 배치를 개선했습니다. 정답 문구·큰 대상·안내·카운트다운을 가운데 배치하고 진행 버튼을 본문 스크롤에서 분리했습니다. 이동 버튼은 상단 일시정지 메뉴로 옮겼습니다.

- `testDebugUnitTest`: 기존 엔진·사이클 **45개 통과**.
- `connectedDebugAndroidTest`: **15개 통과**(Room 7 + UI 8), 실패 0.
- 360×800dp·글자 200%·Wi-Fi/데이터 OFF에서 UI **8개 통과**, 실패 0. 고정 버튼·중앙 정답·일시정지 재생성도 같은 조건에서 통과했습니다. 검증 후 2560×1600px·글자 100%·원래 네트워크 설정으로 복원했습니다.
- `assembleDebug`, 테스트 APK 생성, `lintDebug` 통과. Lint 오류 0·기존 버전 권고 14개 유지.
- 본문을 스크롤해도 다음 버튼의 화면 좌표가 같고, 대상 아래에 위치하는지 검증했습니다. 정답 문구의 중심과 본문 영역 중심이 일치하는지도 검사했습니다.
- 오답 시 남은 기회가 2번으로 바뀌고 선택 보기는 비활성화되며 추가 오답 문구가 없는지 확인했습니다.
- 3초 대기 중 일시정지 → 3.5초 대기 후에도 보기/카운트다운/대상이 표시되지 않음 → Activity 재생성 후 메뉴 유지 → 새 문제로 재개해 1/10 유지와 새 문제 행 생성 → 홈 이동·저장 진행 유지까지 검증했습니다.
- 이미 맞힌 결과에서 일시정지 후 계속하면 같은 정답 화면으로 돌아오는 것도 확인했습니다. 기본 10문제 완료와 Room 기록 검증을 함께 통과했습니다.

태블릿의 실제 그림 연습에서 기억→3초 대기→오답 1회→정답→일시정지 화면을 캡처하고 검토했습니다. 기억 그림을 키우고, 정답 화면에서는 그림을 반복하지 않으며, 다음 버튼이 하단에 유지됩니다.

![중앙 기억 화면과 고정 다음 버튼](screenshots/focused-memory-tablet.png)
![큰 카운트다운](screenshots/focused-countdown-tablet.png)
![중앙 정답 표시](screenshots/focused-correct-tablet.png)

[오답 선택 후 보기](screenshots/focused-options-tablet.png) · [일시정지 메뉴](screenshots/focused-pause-tablet.png)

[작은 화면·글자 200% 기억 화면](screenshots/focused-memory-largefont.png) · [큰 카운트다운](screenshots/focused-countdown-largefont.png) · [일시정지 메뉴](screenshots/focused-pause-largefont.png)

전체 실행 로그는 `.artifacts/focused-play-verification.log`, 작은 화면 로그는 `.artifacts/focused-small-offline-tests.txt`입니다. 게임 엔진·Room 스키마·의존성 버전은 이번 화면 수정에서 변경하지 않았습니다. 실물 기기·TalkBack 등 앞 절의 미검증 범위는 그대로 남습니다.

## 2026-09-23 · 오프라인 AI와 적응 난이도

대상: `feature/adaptive-learning`, 앱 0.2.0, Room v2. PR #4 병합 develop `cfe1687`에서 시작했습니다. 기존 API 34 태블릿 에뮬레이터에서 검증했으며 의존성을 추가하지 않았습니다.

### 결과

| 검증 | 결과 |
| --- | --- |
| JVM 테스트 | 67개 통과: 기존 게임/사이클 45 + 난이도 15 + 밴딧 7 |
| Android 계측 | 25개 항목 확인: 기존 저장 7 + AI 저장 7 + v1 마이그레이션 1 + 화면 10 |
| 디버그·배포용 빌드 | assembleDebug / assembleRelease 통과. 배포 APK는 서명·스토어 게시하지 않음 |
| Lint | 오류 0, 경고 14. 기존 버전 권고: OldTargetApi 1, AGP 2, GradleDependency 8, NewerVersionAvailable 3 |
| 작은 화면·글자 200%·오프라인 | 360×800dp, Wi-Fi/데이터 OFF에서 UI 10개 항목 확인. 설정 복원 확인 |
| 강제 종료 후 실행 | UI에서 완료 10문제·첫 정답 10/10·색 기억 5초·다른 유형 20초 유지 확인 |
| 화면 검토 | 태블릿 16개 보기와 풀이 시간 전체 표시. 큰 글자에서는 가로·세로 스크롤로 처음/마지막 보기 접근 |

전체 계측 최초 실행은 24/25 통과했습니다. 16개 보기 테스트가 색 유형만 준비해 무작위 첫 유형이 다른 경우 실패하므로, 세 유형 모두 같은 테스트 조건을 준비하도록 수정한 뒤 해당 UI 클래스 2/2를 재검증했습니다. 작은 화면에서는 기존 UI 8개와 AI 현황 테스트가 통과했고, 16개 보기의 자동 스크롤은 가까운 가로 부모만 움직였습니다. Compose 테스트 소스를 확인해 세로/가로 컨테이너를 각각 움직이도록 보완한 뒤 AI UI 2/2를 통과했습니다. 앱 난이도 계산이나 저장 실패를 숨기거나 테스트를 제외하지 않았습니다.

### 검증한 연결

- 종합 3회/30문제에서 유형별 세 묶음 완성 → 게임 종료에만 각각 20초→5초 적용. 새 파일 연결에서도 다음 종합 조건과 4/3/3 회전 유지.
- 색 8개를 모은 뒤 종합에서 2개로 묶음 완성 → 재시작 후에도 대기 결정 유지 → 남은 색 2개는 기록 전용 → 종합 종료에만 조건 적용.
- 첫 판단 자체에 보상 없음 → 적용 이후 다음 10개에서 첫 정답 8개면 보상 1 → 같은 완료의 동시 콜백 5회·DB 재개방에도 보상/판단 중복 없음.
- 판단 삽입에 SQLite 오류 주입 → 마지막 정답·보상·학습값·게임 완료 동시 롤백 → 저장된 문제에서 재개 후 한 번만 반영.
- 복원은 축별 최신 단축 잔량만 사용하고 부분 복원 반올림/내림·40초 제한 해제·대기 우선 규칙 검증. DB에서는 부분/전체 복원과 재시작·중복 콜백을 검증.
- 밴딧/비교 전환은 진행 중 게임에서 차단. 새 epoch에서 미완성 묶음·보상 대기 분리, 비교 결과 밴딧 학습 제외.
- v1 스키마 JSON으로 실제 파일 DB와 완료 14개+진행 1개를 생성 → v2 자동 마이그레이션 → 콘텐츠·선택·성적·진행 보존 → 최근 10개 최초 보정과 대기 적용 → 재개방 시 중복 판단/소급 보상 없음.
- 규칙 경계 C=0~10, 밀리초 평균/반올림, 엄격한 W 비교, 보기 단계·15초 전환, 분모 0, 미시도 탐색/동점/무보상 경험, 할인 보상을 순수 테스트로 확인.
- 학습 현황의 실제 DB 난이도, Activity 재생성, 개발 진단 전환 제한, 기존 정식 10문제 완료/연습 제외/일시정지/고정 버튼 회귀 확인.

### 재현과 로그

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
# 특정 클래스 재검증: PowerShell에서는 -P 인자 전체를 인용
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.memorysteps.AdaptiveUiTest'
```

로컬 로그: `.artifacts/adaptive-full-verification.log`, `adaptive-ui-tests.log`, `adaptive-small-offline-tests.txt`, `adaptive-small-ui-tests.txt`, `adaptive-migration-test.log`, `adaptive-build-lint.log`, `adaptive-final-lint.log`. 생성 보고서는 `app/build/reports/`에 있습니다. 디버그 APK SHA-256: `AC4971213C8722ED2563AE01680364B0339C1030B9DC6E9B3A015268B16C5C2F`.

![재시작 후 현재 난이도](screenshots/focused-ai-current-difficulty.png)
![태블릿의 16개 보기와 풀이 시간](screenshots/ai-sixteen-tablet.png)

[개발 진단](screenshots/focused-ai-diagnostics.png) · [글자 200% 보기 스크롤](screenshots/ai-sixteen-largefont.png) · [글자 200% 학습 현황](screenshots/ai-records-largefont.png)

화면 수치는 검증용으로 만든 합성 기록입니다. 작은 화면에서는 모든 보기가 동시에 보이지 않으며 스크롤 중에도 풀이 시간이 진행됩니다. 실물 태블릿·시니어 사용성·TalkBack 전체 흐름·장기간 AI 성능/인지 효과는 이번 자동화 검증으로 확인한 범위가 아닙니다. 조건별 성과 비교·효과음/환경 설정은 후속 범위입니다.

## 2026-09-23 · 게임 1회 합산과 공통 난이도 변경

사용자 확정 `합산 판단 + 공통 난이도`를 적용한 앱 0.3.0 / Room v3입니다. 앞 절의 유형별 첫 조정/이전 기록 보정 정책은 이 버전에서 대체되었습니다.

| 검증 | 결과 |
| --- | --- |
| JVM | 67개 통과 |
| 전체 Android 계측 | 26개 통과, 실패/오류 0 |
| 디버그 빌드·Lint | 통과. Lint 오류 0·기존 버전 경고 14 |
| 작은 화면·오프라인 | 360×800dp, 글자 200%, 네트워크 OFF에서 변경된 AI 화면 테스트 2개 통과. 설정 복원 |

확인한 변경 동작:

- 첫 종합 게임 9개까지 난이도/판단 불변, 10번째 완료 시 세 유형 10개를 한 묶음으로 집계해 판단 1개 생성. 다음 게임의 모든 슬롯에 같은 5초 기억 조건 적용.
- 8문제를 완료하고 명시적으로 끝낸 게임은 원본 성적을 유지하며 묶음을 닫음. 새 게임 2개와 섞어서 판단하지 않음. 새 게임 10개 완료 후 한 번만 판단.
- 같은 게임을 중단/재개하면 기존 4문제와 나머지 6문제로 원래 묶음을 완료. 다음 온전한 게임의 결과로 이전 선택을 한 번만 보상.
- 동시 완료 콜백 5회에도 보상/판단 중복 없음, 판단 삽입 오류 시 답변·보상·게임 완료가 함께 롤백.
- 실제 v1→v3, v2→v3 파일 DB 마이그레이션 통과. 과거 콘텐츠/선택/성적/활성 슬롯과 유형별 학습값/복원량 보존. 이전 대기 판단·보상은 분리해 종료.
- 이전 버전에서 시작한 서로 다른 조건의 게임을 그대로 복구해 남은 6문제를 완료. 성적은 14→20으로 증가하고 통합 정책에는 판단/보상이 발생하지 않음. 그다음 새 게임부터 기본 공통 조건 적용.
- 공통 난이도 한 개 표시, 재생성 후 유지, 개발 모드 전환 제한과 16개 보기 접근, 기존 메뉴/게임/연습/일시정지 회귀 통과.

재현 명령:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest :app:lintDebug --console=plain
```

로그: `.artifacts/combined-verification.log`, `.artifacts/combined-small-ui-tests.txt`. 디버그 APK SHA-256: `0AB118BE0A27FCE8A18E50ED3BA998F40F136A7B95534310BF06732F01D7ACB4`.

![한 게임 결과와 공통 난이도](screenshots/combined-records-tablet.png)

[글자 200%의 공통 난이도](screenshots/combined-records-largefont.png)

화면은 합성 검증 기록입니다. 기존 실물 기기/장기간 AI 효과 검증 한계는 유지합니다. 이번 변경은 유형별 참고 성적 표시를 삭제하지 않으며, 난이도 판단과 적용 조건을 한 개로 통합합니다.

## 2026-09-23 · 풀이 시간 제한과 보기 증가 연속 검증

풀이 단계의 난이도가 바뀌지 않는다는 제보에 따라, 규칙 계산뿐 아니라 실제 답변 저장부터 다음 게임 출제까지 이어지는 회귀 테스트 2개를 추가했습니다. 앱 동작과 난이도 규칙은 변경하지 않았습니다.

- JVM 67개 통과, 디버그 앱/계측 APK 빌드 통과.
- `AdaptiveLearningTest` 9개 통과(기존 7개 + 연속 진행 2개). 전체 Android/UI 테스트를 다시 실행한 결과는 아닙니다.
- BANDIT와 COMPARISON 각각 초기 상태에서 26게임/260문제를 첫 선택 정답으로 완료했습니다. 난이도 행을 직접 주입하지 않고 실제 게임 엔진과 저장소를 사용했습니다.
- 첫 게임의 기억 시간 20→5초 이후 풀이 조절이 선택돼 제한 없음→40→35→30→25→20초→보기 증가/제한 해제로 이어짐을 확인했습니다. 보기 4→6→9→12→16개를 모두 거치며 마지막 게임의 실제 16개 보기 생성까지 검증했습니다.
- 매 게임 사이 DB를 닫고 다시 열어 조건이 보존되는지, 세 유형 모두 같은 조건을 쓰는지, 9문제까지는 판단/조건이 고정되고 10번째 완료 후 변경되는지 확인했습니다. 보상은 AI 모드에서만 25회, 비교 모드에서는 0회였습니다.

사용 중인 앱과 다른 `com.example.memorysteps.difficultyvalidation` 패키지에서 실행했습니다. 개인 플레이 DB를 초기화하거나 현재 게임을 완료 처리하지 않았으며, 이 테스트에 UI 조작은 포함되지 않습니다. 이전 검증에서 개발 앱에 비교 모드가 남아 있음을 확인했지만, 진행 중 게임을 중단시키거나 저장값을 임의로 바꾸지 않았습니다. 비교 모드에서도 풀이 제한/보기 증가 자체는 동작합니다.

격리 실행 설정은 `tools/isolated-tests.init.gradle`입니다. 재현 시 다음 명령으로 대상 클래스를 지정합니다.

```powershell
.\gradlew.bat -I tools/isolated-tests.init.gradle :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.memorysteps.AdaptiveLearningTest'
```

이번에는 해당 설정으로 앱과 계측 APK를 빌드하고, APK 패키지/계측 대상이 격리 이름인지 확인한 뒤 ADB로 설치 및 위 클래스만 실행했습니다. 로컬 로그: `.artifacts/solve-progression-build.log`, `.artifacts/solve-progression-device-tests.log`.
