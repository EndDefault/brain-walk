# 외부 자료와 라이선스

확인일: 2026-09-23. 외부 이미지·음원·폰트 파일·학습 데이터가 없습니다. 앱 아이콘은 직접 작성한 VectorDrawable이며, 화면 글꼴은 Android의 시스템 기본 글꼴입니다. 게임 그림 16개와 색 구성은 `MemoryItemView.kt`의 Canvas 도형으로 직접 작성했습니다. ViewModel/Compose Lifecycle 2.9.4와 Coroutines Android 1.9.0은 기존 전이 버전과 같은 직접 의존성으로 선언했습니다.

## 앱·테스트 라이브러리

실제로 해석한 98개 고유 모듈의 버전, 배포 POM, 라이선스 원문 링크를 [전체 의존성 목록](docs/DEPENDENCIES.md)에 기록했습니다. 디버그 앱 런타임에는 81개, JVM 테스트에는 83개, 계측 테스트에는 82개 외부 모듈이 있으며 목록은 중복됩니다. JVM 테스트의 앱 자체 모듈은 외부 의존성에서 제외합니다. 이 목록은 출시 APK의 확정 목록이 아니며 출시 준비 시 release 구성으로 다시 점검합니다.

| 구성 | 라이선스 | 출처 |
| --- | --- | --- |
| AndroidX Compose, Material 3, Activity, Navigation 및 AndroidX 전이 모듈 | Apache-2.0 | [AndroidX 소스](https://android.googlesource.com/platform/frameworks/support/), 전체 목록의 Google Maven POM |
| Kotlin 표준 라이브러리, Coroutines, Serialization, JetBrains Annotations | Apache-2.0 | [Kotlin](https://github.com/JetBrains/kotlin), [Coroutines](https://github.com/Kotlin/kotlinx.coroutines), 전체 목록의 Maven POM |
| Guava ListenableFuture, JSpecify | Apache-2.0 | [Guava](https://github.com/google/guava), [JSpecify](https://github.com/jspecify/jspecify) |
| AndroidX Test, Espresso 및 테스트용 주입·annotation 모듈 | Apache-2.0 | 전체 목록의 POM |
| JUnit 4.13.2 (테스트 전용) | EPL-1.0 | [JUnit](https://github.com/junit-team/junit4), [라이선스](https://www.eclipse.org/legal/epl-v10.html) |
| Hamcrest 1.3 (테스트 전용) | BSD-3-Clause | [Hamcrest](https://github.com/hamcrest/JavaHamcrest), 전체 목록의 부모 POM |

AndroidX 라이브러리 배포물의 `META-INF/.../LICENSE.txt`를 확인했습니다. 해당 메타데이터를 제거하는 패키징 제외 규칙을 추가하지 않습니다. Apache-2.0 원문은 [licenses/Apache-2.0.txt](licenses/Apache-2.0.txt)에 보존했습니다. POM에서 상속하는 라이선스는 부모 POM까지 확인했습니다.

위 라이선스는 상업적 사용을 허용하며 각각의 조건을 따라야 합니다. Apache-2.0은 라이선스 사본·기존 고지 보존과 수정 시 변경 고지, BSD-3-Clause는 저작권·조건·면책 고지 보존과 홍보상 보증 금지 조건을 둡니다. EPL-1.0은 해당 프로그램/수정물 배포에 대한 라이선스·소스 제공 조건이 있으며 현재 JUnit은 테스트 전용입니다. 출시 배포 전 실제 배포물의 NOTICE·LICENSE와 변경 여부를 다시 점검합니다.

## 빌드 도구

| 구성 | 고정 버전 | 출처·라이선스 |
| --- | --- | --- |
| Gradle / Wrapper | 8.14.4 | [Gradle](https://github.com/gradle/gradle), Apache-2.0; Wrapper의 원문 헤더 보존 |
| Android Gradle Plugin | 8.13.2 | [Android 도구 소스](https://android.googlesource.com/platform/tools/base/), Apache-2.0 |
| Kotlin Android / Compose compiler plugin | 2.2.21 | [Kotlin](https://github.com/JetBrains/kotlin), Apache-2.0 |
| Android SDK Platform / Build Tools | 36 / 36.0.0 | [Android SDK 약관](https://developer.android.com/studio/terms); 개발 환경에서만 사용하며 SDK 배포물은 저장소에 포함하지 않음 |

## 이후 자료 추가 시

새 그림·음원·폰트·데이터를 추가하는 PR에서 출처 URL, 버전, 저작자, 라이선스, 상업적 이용 조건과 필수 고지사항을 함께 기록합니다. 연구 참고 문헌은 명세의 배경 자료이며 앱의 시간 설정·인지 향상·치매 예방 효과를 입증하는 근거로 사용하지 않습니다.
