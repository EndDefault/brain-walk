# 출제 모델 작업 재개 메모

## 2026-10-01 현재 상태 — 아래의 과거 중지 기록보다 우선

추가 학습과 최종 시험을 완료했습니다. [v0.2 실험 모델](../models/question-author/v0.2-common-experimental/README.md)을 보관했으나 **앱 배포는 보류**합니다. 최종 시험은 전체 유형 판단 31/50, 약한 유형 구분 0/15, 관찰된 혼동 재출제 13/15입니다. 자세한 비교와 실패한 후보는 [결과 보고서](QUESTION_MODEL_REPORT.md)를 읽습니다.

- 선택된 모델: `.artifacts/question-ai/continued-v2/`. 1회차 가중치에서 전체 출제를 추가 2회 학습한 모델입니다. 최종 가중치는 `models/question-author/v0.2-common-experimental/`에도 보관합니다.
- 선택하지 않은 모델: `.artifacts/question-ai/focused-v3/`. 정답률 요약·유형 균형으로 2회 집중 보정했으나 검증 유형 판단이 7/30으로 떨어졌습니다. 이 가중치를 앱에 연결하지 않습니다.
- 두 실행의 `resume.pt`에는 전체 학습 상태가 있지만 이미 완료한 실행이므로 `--resume`으로 재학습하지 않습니다. 중지된 새 실행에 대해서만 같은 설정·소스와 `--resume`을 사용합니다. 추가 보정은 새 출력 디렉터리와 `--initial-adapter`로 시작합니다.
- 유형 판단 진단: `.artifacts/question-ai/*focus-probe.json`. 짧은 문맥·예시·어댑터 병합 여부 확인도 기준을 충족하지 못했습니다. 진단 정확도를 전체 출제 품질로 오해하지 않습니다.
- 학습 환경, 고정 원본 모델, 300/30/50 가상 자료는 같은 PC의 기존 `.local-tools/`와 `.artifacts/question-ai/`에 있습니다. 개인 플레이 DB는 사용하지 않았습니다.
- 최종 시험 50건은 선택 후 한 번 평가했습니다. 이후 이 결과를 보고 모델을 개선한다면 새로운 시험 분할이 필요합니다.
- 다음 과제는 약점 판단을 안정화할 입력·학습 자료·모델 구조를 재검토하는 것입니다. 모바일 변환·태블릿 실행·Android 연결은 미완료입니다. 현재 앱의 무작위 출제와 공통 난이도 밴딧은 그대로입니다.
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
