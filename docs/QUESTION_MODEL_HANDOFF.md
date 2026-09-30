# 출제 모델 작업 재개 메모

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
