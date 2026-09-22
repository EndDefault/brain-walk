# 결정 기록

## 2026-09-22 · 첫 PR

- **공개 저장소 사용:** 사용자 후속 지시가 최초 명세의 비공개 원칙보다 우선합니다. `EndDefault/brain-walk`를 Public으로 유지합니다.
- **원격 초기 이력 유지:** 기존 `main`의 `aa4e485`는 README만 포함한 초기 커밋입니다. 이를 부모로 사용하며 기존 `.idea/` 파일은 삭제하거나 변경하지 않습니다.
- **첫 PR 범위:** 빌드·기본 실행·홈 빈 상태·이용 안내·화면 이동 확인까지입니다. 훈련 유형 카드는 안내이며 누르는 버튼이 아닙니다. 게임 시작, 그래프, 가짜 성적은 제공하지 않습니다.
- **이름:** 기존 앱이나 Android 패키지가 없어 명세의 임시 프로젝트 이름 `MemorySteps`, 패키지 `com.example.memorysteps`를 사용합니다. 저장소 이름 `brain-walk`는 유지합니다. 화면에는 한국어 이름 ‘기억 산책’과 MemorySteps를 함께 표시합니다.
- **커밋 단위:** 기준 명세, 빌드 설정, 기본 화면, 실행 검증·문서를 목적별로 구분합니다. 무관한 파일을 섞거나 파일 수만을 기준으로 나누지 않습니다.
- **Android 버전:** 초기 최소 지원은 API 26(Android 8.0)입니다. compile/target API 36, Build Tools 36.0.0으로 고정합니다. 실제 사용자 기기는 아직 확인되지 않았습니다.
- **호환되는 안정 버전 고정:** 설치된 SDK 36과 검증 가능한 AGP 8 계열에 맞춰 AGP 8.13.2, Gradle 8.14.4, Kotlin 2.2.21, Compose BOM 2026.02.01, Activity 1.12.4, Navigation 2.9.8을 선택합니다. 최신 버전을 자동 추적하지 않습니다. Compose compiler plugin과 Kotlin 버전은 일치시킵니다.
- **JDK:** Gradle 실행은 설치된 Corretto 21, Java/Kotlin 출력은 17로 통일합니다. PC별 JDK 절대 경로는 저장소에 넣지 않습니다.
- **Windows 한글 경로:** 사용자 작업 경로를 유지하기 위해 `android.overridePathCheck=true`를 사용하고 실제 빌드 결과로 확인합니다.
- **가로 화면 우선:** 태블릿 가로 화면에서는 두 열, 작은 화면과 글자 확대에서는 한 열로 배치합니다. 회전을 강제 고정하지 않고 시스템 방향과 창 크기에 적응합니다. 모든 내용은 스크롤할 수 있고 버튼은 최소 64dp입니다.
- **로컬 저장 원칙:** 네트워크 권한과 서버 SDK를 추가하지 않습니다. 백업을 비활성화합니다. Room/DataStore 및 민감하지 않은 학습 기록의 명시적인 백업 정책은 저장 기능 PR에서 구체화합니다.
- **외부 자산:** 폰트는 기기 기본 폰트, 앱 아이콘은 직접 작성한 VectorDrawable입니다. 다운로드한 이미지·음원은 없습니다.
- **다음 단계:** 본 PR 검토·병합 후 문서 PR에서 난이도 규칙 예제, 복원 이력, AI 보상 연결, 데이터 트랜잭션을 구체화합니다. 자동 병합하지 않습니다.

## 버전 근거

2026-09-22 공식 문서와 배포물을 확인했습니다.

- [AGP 8.13 호환성](https://developer.android.com/build/releases/agp-8-13-0-release-notes): Gradle 최소 8.13, JDK 최소 17, API 36 지원.
- [Kotlin Gradle plugin 호환성](https://kotlinlang.org/docs/gradle-configure-project.html), [Kotlin 릴리스](https://kotlinlang.org/docs/releases.html).
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom), [버전 매핑](https://developer.android.com/develop/ui/compose/bom/bom-mapping).
- [Activity 릴리스](https://developer.android.com/jetpack/androidx/releases/activity), [Navigation 릴리스](https://developer.android.com/jetpack/androidx/releases/navigation).
- [Gradle 배포 체크섬](https://services.gradle.org/distributions/gradle-8.14.4-bin.zip.sha256).
