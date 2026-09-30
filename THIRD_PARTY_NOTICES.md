# 외부 자료와 라이선스

확인일: 2026-09-30. 앱에는 외부 폰트 Pretendard 1.3.9를 포함합니다. 앱 밖의 출제 보정 실험에는 Qwen3-0.6B 사전 학습 모델을 사용합니다. 외부 이미지·음원·개인 학습 데이터는 없으며 추가 보정 자료는 직접 만든 가상 상황입니다. 앱 아이콘은 직접 작성한 VectorDrawable이며, 게임 그림 16개와 색 구성은 `ui/components/MemoryItemView.kt`의 Canvas 도형으로 직접 작성했습니다. ViewModel/Compose Lifecycle 2.9.4와 Coroutines Android 1.9.0은 기존 전이 버전과 같은 직접 의존성으로 선언했습니다.

## 출제 모델 보정 실험

공식 [Qwen/Qwen3-0.6B](https://huggingface.co/Qwen/Qwen3-0.6B)의 커밋 `c1899de289a04d12100db370d81485cdf75e47ca`를 사용합니다. 원본 [LICENSE](https://huggingface.co/Qwen/Qwen3-0.6B/blob/c1899de289a04d12100db370d81485cdf75e47ca/LICENSE)는 Apache-2.0이며 [저장소 사본](licenses/Qwen3-0.6B/LICENSE.txt)에 보존합니다. 해당 버전 파일 목록에 별도 NOTICE는 없습니다. 원본 가중치는 수정하지 않고 로컬 캐시에 보관하며, 추가 학습한 LoRA 보정 가중치는 변경 사실·원본 버전·평가와 함께 별도 실험 산출물로 관리합니다. Qwen의 보증·제휴를 표시하지 않습니다.

원본 `model.safetensors` SHA-256: `f47f71177f32bcd101b7573ec9171e6a57f4f4d31148d38e382306f42996874b`. 1.5GB 원본과 Python 실행 환경은 Git에 포함하지 않습니다. 학습 도구의 고정 직접 의존성은 `training/question_author/requirements.txt`에 기록하며 이 도구들은 Android 앱 런타임에 포함되지 않습니다. 모바일 모델로 변환·배포할 때는 변환된 파일과 사용하는 Android 런타임의 고지를 별도로 검증합니다.

## 앱에 포함한 글꼴

| 항목 | 내용 |
| --- | --- |
| 글꼴·제작자 | Pretendard 1.3.9 · 길형진(Kil Hyung-jin) |
| 공식 출처 | [Pretendard 저장소](https://github.com/orioncactus/pretendard), 태그 v1.3.9 / 커밋 `5c41199ea0024a9e0b2cb31735265056e5472d76` |
| 원본 파일 | 해당 커밋의 `packages/pretendard/dist/public/static/Pretendard-Regular.otf`, `Pretendard-Bold.otf`, `Pretendard-Black.otf` |
| 라이선스 | [SIL Open Font License 1.1 원문](https://github.com/orioncactus/pretendard/blob/5c41199ea0024a9e0b2cb31735265056e5472d76/LICENSE) |
| 변경 여부 | 폰트 내용 수정·변환·서브셋 없음. Android 리소스 규칙에 맞춰 파일명만 소문자로 저장 |
| 고지 보존 | [저장소 원문](licenses/Pretendard-OFL-1.1.txt) 및 APK의 `assets/licenses/Pretendard-OFL-1.1.txt`. 앱의 **게임 설명 → 글꼴 저작권·라이선스**에서 오프라인 열람 |

사용한 v1.3.9의 라이선스 원문에 있는 저작권자 Kil Hyung-jin과 Reserved Font Name **Pretendard** 고지를 보존했습니다. 폰트 자체를 단독 판매하지 않으며 앱에 함께 배포합니다. 추후 폰트 내용을 수정한다면 OFL의 Reserved Font Name 조건을 다시 확인해야 합니다. 앱 코드는 폰트를 포함한다는 이유만으로 OFL로 바뀌지 않습니다.

배포한 원본 확인용 SHA-256:

```text
pretendard_regular.otf  3FFBACDE6AB8411F1D2DB54BB9B1F0B3EE2A738932033722CF0388C06AED1C93
pretendard_bold.otf     2E91915FAB54DF71CC9598EBF608B2BDB54C6FE3C066AC61DFF0BC44FCA71CC7
pretendard_black.otf    94628B0BCEA8936B6E5C30D98D685EB9BBAFFB0FE2ED255542ECC656C248E021
```

## 앱·테스트 라이브러리

실제로 해석한 103개 고유 모듈의 버전, 배포 POM, 라이선스 원문 링크를 [전체 의존성 목록](docs/DEPENDENCIES.md)에 기록했습니다. 디버그 앱 런타임에는 86개, JVM 테스트에는 88개, 계측 테스트에는 82개 외부 모듈이 있으며 목록은 중복됩니다. Room 2.8.4와 SQLite 2.6.2가 추가되었습니다. JVM 테스트의 앱 자체 모듈은 외부 의존성에서 제외합니다. 이 목록은 출시 APK의 확정 목록이 아니며 출시 준비 시 release 구성으로 다시 점검합니다.

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
| Kotlin Symbol Processing | 2.2.21-2.0.5 | [KSP](https://github.com/google/ksp), Apache-2.0 |
| Room compiler | 2.8.4 | [AndroidX Room 소스](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/room/), Apache-2.0 |
| Android SDK Platform / Build Tools | 36 / 36.0.0 | [Android SDK 약관](https://developer.android.com/studio/terms); 개발 환경에서만 사용하며 SDK 배포물은 저장소에 포함하지 않음 |

## 이후 자료 추가 시

새 그림·음원·폰트·데이터를 추가하는 PR에서 출처 URL, 버전, 저작자, 라이선스, 상업적 이용 조건과 필수 고지사항을 함께 기록합니다. 연구 참고 문헌은 명세의 배경 자료이며 앱의 시간 설정·인지 향상·치매 예방 효과를 입증하는 근거로 사용하지 않습니다.
