# 출제 모델 작업 재개 메모

**2026-10-06 최신 상태:** 사용자의 당일 연결 요청으로 공통 LoRA v0.2를 병합한 Qwen3 모델을 Android에 탑재하고 실제 오프라인 출제·저장·복원을 검증했습니다. 결과 화면의 다음 문제 준비도 연결했습니다. [현재 구현과 검증](ANDROID_QUESTION_AUTHOR.md)을 우선 확인합니다. 아래의 미연결/배포 보류 문구는 당시 평가·작업 기록이며, 약점 분류 성능의 한계는 여전히 유효합니다. 개인 최고 기록은 미구현입니다.


## 2026-10-06 제출 전 UI 정리

사용자가 오늘 범위를 **점검·디자인·화면 내용 수정**으로 제한했습니다. 생성 모델 연결이나 추가 학습은 진행하지 않았습니다. 홈·학습 선택·설명 메뉴를 남색/청록 포인트로 정리하고 학습 현황에 숫자 요약·유형별 막대그래프·최근 완료 게임 정답률 추이를 적용했습니다. 개발 진단/글꼴 고지 화면 버튼은 제거했으며 폰트 라이선스 원문은 저장소와 APK에 보존했습니다. 게임 플레이 화면·난이도·Room 저장 로직은 변경하지 않았습니다. 아래 10월 1일 생성 모델 연동 계획은 후속 작업 기록입니다.

현재 UI와 제출 전 검증은 [디자인 방향](UI_DIRECTION.md) 및 [검증 보고서](TEST_REPORT.md)의 2026-10-06 항목을 확인합니다.

## 2026-10-01 귀가 전 인계 — 아래의 과거 중지 기록보다 우선

### 다음 작업 방향과 실제 구현 상태

사용자가 정한 게임의 중심은 **AI 출제 → 사용자의 도전 → 성적에 따른 다음 난이도 → 개인 최고 기록 갱신**입니다. 여기서 확인하는 수준은 게임 안에서의 수행 수준입니다. 약점 유형을 정확히 분류하는 추가 학습보다, 현재 보관 모델로 Android 오프라인 출제가 가능한지 확인하고 게임에 연결하는 시연 작업을 우선합니다. 이 우선순위 변경이 모델의 기존 평가 실패나 제품 배포 기준을 통과했다는 뜻은 아닙니다.

사용자가 제안한 대기 시간 완화 방식은 **결과 화면을 보는 동안 다음 10문제를 백그라운드에서 생성**하는 것입니다. 마지막 문항의 성적 저장과 다음 난이도 확정이 끝난 뒤, 그 기록과 조건을 입력으로 생성·검증·저장합니다. 다시 도전을 누를 때 준비가 끝났으면 저장된 계획을 사용하고, 아직 준비 중이면 남은 준비 시간만 안내합니다. 첫 게임은 별도 준비가 필요합니다. 결과 화면을 강제로 오래 표시하지 않습니다.

**위 생성 연동·미리 생성·개인 최고 기록 기능은 아직 구현하지 않았습니다.** 현재 앱은 기존 무작위 문제 생성과 공통 난이도 밴딧으로 동작합니다. PC에서는 학습한 AI의 문제 생성을 확인했으나 Android 실행·메모리·속도는 미검증입니다. PC의 10문제 생성 중앙값 8.29초를 태블릿 성능으로 간주하지 않습니다.

집에서 이어서 할 순서:

1. 원본 모델과 `v0.2-common-experimental` 보정 가중치를 모바일 실행 형식으로 준비하고 Android에서 완전 오프라인으로 10문제 생성이 가능한지, 시간·메모리·출력 유효성을 먼저 측정합니다. 재학습부터 시작하지 않습니다.
2. 생성기/재료 목록과 Android 검증기를 연결합니다. 잘못된 문제·시간 초과에는 기존 출제로 전환하며 AI 출제와 대체 출제를 구분해서 기록합니다.
3. 결과 저장 후 다음 문제 준비를 시작합니다. 현재 `LearningRepository.saveInsideTransaction`은 게임 완료 시 다음 난이도를 적용하고, `TrainingViewModel.change`는 저장 완료 후 화면 상태를 공개합니다. **생성은 저장 트랜잭션 밖의 별도 작업**으로 실행해 결과 화면과 사용자 입력을 막지 않습니다.
4. 준비된 계획은 조건·완료 게임·모델 버전과 함께 저장합니다. 조건 변경, 중복 요청, 앱 재시작과 늦게 도착한 응답을 처리하고 같은 계획을 두 게임이 소비하지 않도록 합니다. 상세 계약은 [출제 AI 정의](AI_QUESTION_GENERATION.md)를 따릅니다.
5. 최고 기록은 난이도·보기 수·제한 시간 등 비교 가능한 조건을 기준으로 정의하고 저장·표시합니다. 점수 공식과 UI는 아직 정하지 않았습니다.

### 다른 PC에서 받기

현재 작업은 `feature/adaptive-learning`에 있고 [PR #5](https://github.com/EndDefault/brain-walk/pull/5)는 `develop` 대상 초안입니다. PR 병합 전에는 `develop`만 받아서 최신 모델 작업이 있다고 생각하지 않습니다. `main`에는 병합하지 않습니다.

처음 받는 경우:

```powershell
git clone --branch feature/adaptive-learning https://github.com/EndDefault/brain-walk.git brain-walk
Set-Location brain-walk
```

이미 받은 저장소라면 먼저 `git status`로 집 PC의 변경을 확인하고, 변경을 보존한 상태에서 다음 명령으로 갱신합니다. 충돌이나 미커밋 변경을 강제로 덮어쓰지 않습니다.

```powershell
git fetch origin
git switch feature/adaptive-learning
git pull --ff-only origin feature/adaptive-learning
```

Git에는 앱 소스·학습 도구·약 9MB 보정 가중치·평가 보고서·재현 방법이 있습니다. **원본 모델 약 1.5GB, Python 가상 환경, 로컬 전체 학습 상태 `resume.pt`, Android SDK/개인 설정, 플레이 DB는 포함하지 않습니다.** 모델 실행에는 보정 가중치와 정확한 원본 모델이 함께 필요합니다. [학습 안내](../training/question_author/README.md)의 ‘최초 환경과 자료 준비’로 원본·환경을 준비하고, 보관 모델을 사용하므로 최초 학습이나 완료 실행 재개 명령을 실행할 필요는 없습니다. 기존 Python 학습/평가 스크립트는 CUDA GPU를 요구하며 CPU/Android 추론 경로는 별도 준비 대상입니다. Android 빌드는 집 PC의 SDK 경로를 설정합니다.

재개 요청 예시: “`docs/QUESTION_MODEL_HANDOFF.md`를 읽고, 보관 모델의 Android 오프라인 문제 생성부터 확인해 줘. 결과 화면에서 다음 10문제를 미리 생성하는 흐름과 개인 최고 기록 기능을 이어서 구현하고, 기능별 커밋과 develop 대상 PR로 관리해 줘.”

### 완료한 모델 실험

추가 학습과 최종 시험을 완료했습니다. [v0.2 실험 모델](../models/question-author/v0.2-common-experimental/README.md)을 보관했으나 **앱 배포는 보류**합니다. 최종 시험은 전체 유형 판단 31/50, 약한 유형 구분 0/15, 관찰된 혼동 재출제 13/15입니다. 자세한 비교와 실패한 후보는 [결과 보고서](QUESTION_MODEL_REPORT.md)를 읽습니다.

- 선택된 모델: `.artifacts/question-ai/continued-v2/`. 1회차 가중치에서 전체 출제를 추가 2회 학습한 모델입니다. 최종 가중치는 `models/question-author/v0.2-common-experimental/`에도 보관합니다.
- 선택하지 않은 모델: `.artifacts/question-ai/focused-v3/`. 정답률 요약·유형 균형으로 2회 집중 보정했으나 검증 유형 판단이 7/30으로 떨어졌습니다. 이 가중치를 앱에 연결하지 않습니다.
- 두 실행의 `resume.pt`에는 전체 학습 상태가 있지만 이미 완료한 실행이므로 `--resume`으로 재학습하지 않습니다. 중지된 새 실행에 대해서만 같은 설정·소스와 `--resume`을 사용합니다. 추가 보정은 새 출력 디렉터리와 `--initial-adapter`로 시작합니다.
- 유형 판단 진단: `.artifacts/question-ai/*focus-probe.json`. 짧은 문맥·예시·어댑터 병합 여부 확인도 기준을 충족하지 못했습니다. 진단 정확도를 전체 출제 품질로 오해하지 않습니다.
- 학습 환경, 고정 원본 모델, 300/30/50 가상 자료는 같은 PC의 기존 `.local-tools/`와 `.artifacts/question-ai/`에 있습니다. 개인 플레이 DB는 사용하지 않았습니다.
- 최종 시험 50건은 선택 후 한 번 평가했습니다. 이후 이 결과를 보고 모델을 개선한다면 새로운 시험 분할이 필요합니다.
- 약점 판단을 안정화할 입력·학습 자료·모델 구조 개선은 후속 연구로 남깁니다. 다음 개발은 위 사용자 우선순위에 따라 모바일 실행 검증과 시연 연결부터 시작합니다. 현재 앱의 무작위 출제와 공통 난이도 밴딧은 그대로입니다.
- 이번 작업의 계약/패키징 18개와 로컬 모델 검증 12개, 총 30개 테스트를 통과했습니다. Android 코드를 바꾸지 않아 Android 테스트는 다시 실행하지 않았습니다.
- Git 브랜치는 `feature/adaptive-learning`, PR #5는 `develop` 대상입니다. `main`에는 병합하지 않습니다. 모델 학습·평가 프로세스는 모두 종료했습니다.

## 2026-09-30 중지 당시의 기록

아래는 당시 상태를 보존한 이력이며 현재 재개 명령이나 기능 상태가 아닙니다.

2026-09-30, 사용자가 귀가 후 이어서 작업하기 위해 학습을 중지했습니다.

## 보존된 상태

- 작업 브랜치 `feature/adaptive-learning`, PR #5의 대상은 `develop`. `main`에 병합하지 않습니다.
- 데이터/도구 계약, 공통 모델 학습/평가 도구는 기능별 커밋으로 구분했습니다.
- 학습 300, 검증 30, 시험 50개 가상 프로필입니다. 실제 사용자 기록은 사용하지 않았습니다.
- Qwen3-0.6B LoRA 학습은 총 3회 중 1회를 완료했습니다. 중지 전 마지막 로그는 2회차 48/300개입니다. 이는 마지막 로그 위치이며 추가로 처리한 중간 가중치는 저장되지 않았습니다.
- [Git에 보관한 1회차 체크포인트](../models/question-author/v0.1-epoch1-checkpoint/README.md): 약 9MB 가중치, 원본 버전, 라이선스, 자료 해시, 환경, 학습 보고서.
- 검증 손실은 0.98328 → 0.73513. 출제 정확도는 아직 평가하지 않았습니다.
- 자료·도구 계약·패키징 검증 테스트 16개 통과. 현재 앱 코드는 이번 모델 실험에서 변경하지 않았습니다.
- Android 생성 모델 연결·태블릿 성능 시험·최종 시험은 미완료입니다.

## 이 PC의 로컬 파일

- Python 환경: `.local-tools/question-ai-venv/`.
- 원본 모델과 버전 메타데이터: `.artifacts/question-ai/base/`, `base-manifest.json`.
- 가상 자료: `.artifacts/question-ai/data/`. 현재 생성기로 재생성한 세 분할의 SHA-256이 원래 학습 자료와 일치함을 확인했습니다.
- 원래 학습 로그와 보고서: `.artifacts/question-ai/training-v1.log`, `run-v1/`.
- 중지 상태를 명시한 보관용 사본: `.artifacts/question-ai/run-v1-paused/`.
- 학습 전 기본 모델의 10건 시험: `.artifacts/question-ai/base-validation.json`. 전체 출제 형식 통과 0/10. 보정 후 결과와 혼동하지 않습니다.

위 로컬 환경·원본 모델·생성 자료는 Git에서 제외합니다. 다른 PC에서는 저장소와 [준비 안내](../training/question_author/README.md)를 이용해 환경/원본 모델/가상 자료를 다시 준비합니다. Git에 들어간 보정 가중치는 다시 학습하지 않고도 평가에 사용할 수 있습니다. 현재 측정용 학습/평가 명령은 CUDA GPU를 요구합니다.

## 다음에 진행할 순서

1. 이 문서와 실제 Git 상태를 확인합니다. 추가 학습이나 평가가 별도로 시작되었는지 확인한 뒤 중복 실행을 피합니다.
2. 저장된 1회차 모델을 검증 자료에서 먼저 평가합니다. 기본 생성과 `--constrained` 출력을 구분하고, 출력 제한 코드가 보장한 형식 정확도를 모델 자체 능력으로 해석하지 않습니다.
3. 보정이 더 필요하면 `train.py`에 기존 LoRA를 학습 가능한 상태로 로드하는 경로를 추가합니다. **현재 스크립트에는 이어 학습 옵션이 없습니다.** 기존 모델을 초기값으로 새 옵티마이저로 시작하는 추가 학습임을 기록합니다. 정확한 중간 재개를 위해서는 앞으로 옵티마이저·스케줄러·난수 상태도 함께 저장해야 합니다. 대안은 새 출력 폴더에서 원래 3회 학습을 처음부터 재현하는 것입니다.
4. 검증 자료로 설정을 결정한 후 분리된 시험 50건을 평가합니다. 약한 유형 판단, 기록된 혼동 반영, 최근/게임 내 반복, 전체 생성 실패를 집계합니다. 시험 결과로 다시 튜닝하면 새 시험 분할을 마련합니다.
5. 평가가 기준을 만족한 모델만 모바일 변환·오프라인 실행·앱 연결 후보로 검토합니다. PC GPU 시간은 태블릿 속도가 아닙니다. 사용자 요청 없이 `main`에 넣지 않습니다.

```powershell
$py = '.local-tools/question-ai-venv/Scripts/python.exe'
$env:HF_HUB_OFFLINE = '1'
$env:TRANSFORMERS_OFFLINE = '1'
& $py -X utf8 training/question_author/evaluate.py --base .artifacts/question-ai/base --adapter models/question-author/v0.1-epoch1-checkpoint --data .artifacts/question-ai/data/validation.jsonl --limit 10 --output .artifacts/question-ai/epoch1-validation-raw.json
& $py -X utf8 training/question_author/evaluate.py --constrained --base .artifacts/question-ai/base --adapter models/question-author/v0.1-epoch1-checkpoint --data .artifacts/question-ai/data/validation.jsonl --output .artifacts/question-ai/epoch1-validation-constrained.json
```

이미 존재하는 평가 파일은 덮어쓰지 말고 읽거나 새 실행 이름을 사용합니다. 학습 당시 보고서의 소스 해시는 시작 시점 파일입니다. 이후 카탈로그 일치 검사·제한 생성·평가 및 패키징 검사를 추가했으며, 생성 자료의 세 분할 해시는 그대로 유지됩니다.

## 자원 및 기기 상태

학습 프로세스만 중지했습니다. 메모리 부족으로 에뮬레이터 종료를 검토했으나 **종료 명령을 실행하지 않았습니다.** `Medium_Tablet` 에뮬레이터의 앱·DB·표시 설정은 이번 중지 작업에서 변경하지 않았습니다. 원래 테스트 앱의 기록을 초기화하지 않습니다. 다음 학습에서는 메모리 사용과 CUDA 캐시를 점검합니다.
