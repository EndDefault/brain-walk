# 공통 출제 모델 보정 실험

목표는 **한 사람의 플레이를 외운 모델을 배포하지 않고, 여러 기록 패턴을 해석해 문제를 구성하는 공통 모델을 만드는 것**입니다. 이 폴더는 기기에서 수집한 기록을 읽지 않습니다. 초기 실험은 직접 작성한 가상 사용자 데이터만 사용합니다. 실제 사용자에게 맞는 출제나 인지 효과를 검증한 학습 데이터가 아닙니다.

기본 모델은 [Qwen3-0.6B](https://huggingface.co/Qwen/Qwen3-0.6B)의 고정 커밋 `c1899de289a04d12100db370d81485cdf75e47ca`입니다. 원본 라이선스는 [Apache-2.0](../../licenses/Qwen3-0.6B/LICENSE.txt)입니다. LoRA는 원본 모델을 고정하고 일부 보정 가중치를 학습합니다. 보정 가중치만으로는 실행할 수 없으며 정확한 원본 모델과 함께 로드해야 합니다.

## 사용자 기록과 배포 모델의 구분

- 공통 보정 모델: 여러 가상 사용자의 사례로 개발 PC에서 학습합니다. 동일한 버전을 모든 설치에 제공합니다.
- 개인 기록: 앱을 사용하는 동안 해당 기기에만 저장합니다. 향후 출제 요청의 입력으로 사용하며 공통 모델 배포 파일에 섞지 않습니다.
- 기존 밴딧: 각 기기에서 난이도 선택 경험을 계속 학습하는 기존 기능입니다. 이 실험에서 개인 밴딧 상태를 복사하거나 미리 채우지 않습니다.

**Android 앱 연결은 아직 없습니다.** 이 Python 실험의 학습 가중치를 Git에 보관하는 것과 앱에서 실제 출제에 사용하는 것은 별도 단계입니다. 모델/양자화/런타임 조합과 오프라인 태블릿 실행을 확인하기 전에는 앱 모델로 승격하지 않습니다. Galaxy Tab A9+는 원래 유인물의 화면 설계 참고 기기이며 이 실험 PC의 GPU 측정값을 해당 태블릿 성능으로 해석하지 않습니다.

## 무엇을 가르치는가

앱이 정한 10개 슬롯과 보기 수를 입력으로 받습니다. 출력은 `submit_question_plan` 도구 요청 하나이며, 각 행에 **기억 대상 ID를 먼저**, 그 뒤 오답 보기 ID를 적습니다. 앱이 정답 위치를 섞고 채점합니다. 시간 제한과 종합 4/3/3 배정을 모델이 변경할 수 없습니다.

`contract.py`에 재료 ID·도구 명세·검증기가 있습니다. 재료는 기존 게임과 같은 색 16개 조합, 그림 16종, 숫자 10~99입니다. 자료 생성 전에 Kotlin의 색/그림 순서와 숫자 범위가 같은지 검사합니다. 아직 앱의 카탈로그 조회 API와 연결된 것은 아니므로 Android 연결 시 ID 변환도 검증해야 합니다.

가상 상황 10종:

| 상황 | 가르칠 행동 |
| --- | --- |
| 첫 사용, 기록 부족 | 약점을 단정하지 않고 기본 구성 |
| 색/그림/숫자 문제의 정답률이 낮음 | 해당 유형에서 관찰한 혼동 대상을 일부 다시 구성 |
| 전체 정답률이 낮음 | 한 유형만 약하다고 단정하지 않음 |
| 전체 정답률이 높음 | 다양한 대상 구성 |
| 느리지만 정답률이 높음 | 느린 답변만으로 약한 유형을 정하지 않음 |
| 유형 간 비슷한 정답률 | 균형 있게 구성 |
| 유형별 기록 수가 다름 | 정답 개수가 아니라 분모를 고려 |

초기 교사 라벨은 **유형별 비교 가능한 관찰 12개 이상, 최저 정답률이 다음 유형보다 0.20 이상 낮을 때 해당 유형에 주의**하는 실험 규칙으로 만듭니다. 이 수치는 사용자 능력/건강을 판정하는 검증된 기준이 아닙니다. 모델이 규칙과 출제 형식을 익히는지 확인하는 출발점입니다. 같은 혼동 대상을 계속 반복시키지 않고 최근/이번 게임의 대상 반복을 줄입니다. 약한 유형의 문항 수를 늘리지는 않습니다.

## 데이터 분리와 평가

기본 데이터는 학습 300명·검증 30명·최종 시험 50명의 **가상 프로필**입니다. 프로필 ID/시드는 분리하고 입력 중복도 검사합니다. 이는 실제 380명을 모집했다는 의미가 아닙니다. 동일한 사례 생성 규칙을 쓰므로 새로운 실제 사용자에 대한 일반화 성능을 증명하지 않습니다.

모든 유형과 보기 4/6/9/12/16개를 포함합니다. 학습의 손실은 정답 도구 요청 토큰에만 적용합니다. 검증 손실로 체크포인트를 선택하고 최종 시험은 가중치 갱신에 사용하지 않습니다.

`evaluate.py`는 출제 형식 통과율, 주의할 유형 선택, 관찰된 혼동의 재출제, 최근/게임 내 반복, PC 생성 시간을 측정합니다. 생성 실패를 분모에서 제거하지 않습니다. 무작위 참고 출제도 함께 측정합니다. 교사 규칙은 라벨을 만든 정답 기준이므로 같은 규칙으로 100%를 얻는 것은 모델의 우수성 근거가 아닙니다.

두 실행 방식을 구분합니다.

- 기본 생성: 모델이 도구 요청 전체를 출력하며 모든 오류를 그대로 기록합니다.
- `--constrained`: 앱 코드 역할의 토큰 제한이 JSON 형식·문제/보기 수·허용 재료·중복/최근 대상 제한을 보장합니다. **이 경우 형식 통과율을 모델이 스스로 달성한 능력으로 보고하지 않습니다.** 모델은 주의할 유형과 허용 범위 내 대상·오답을 선택합니다. 약한 유형이나 혼동 재출제 정답을 코드로 대신 넣지 않습니다.

`model_config.json`의 배포 기준은 실험 시작 전에 기록합니다. 최종 모델의 높은 형식 통과율만으로 승격하지 않고, 유형 선택·혼동 반영·대상 다양성과 오프라인 태블릿 실행을 확인합니다. 실물 검증 결과가 없으면 `production_ready=false`입니다.

## 재현

Python 3.12, 이 실험의 CUDA GPU 환경을 사용합니다. 개발 PC에서 모델/도구를 처음 받는 데는 인터넷이 필요합니다. 이후 학습·평가는 로컬 파일만 사용합니다. 시스템 환경을 덮어쓰지 않도록 별도 가상 환경을 만듭니다.

```powershell
python -m venv .local-tools/question-ai-venv
$py = '.local-tools/question-ai-venv/Scripts/python.exe'
& $py -m pip install torch==2.8.0 --index-url https://download.pytorch.org/whl/cu128
& $py -m pip install -r training/question_author/requirements.txt
& $py -X utf8 -m unittest discover -s training/question_author/tests -v
& $py -X utf8 training/question_author/build_data.py --output .artifacts/question-ai/data
& $py -X utf8 training/question_author/prepare_base.py --base .artifacts/question-ai/base --manifest .artifacts/question-ai/base-manifest.json
```

원본 모델은 위 고정 버전의 파일을 `.artifacts/question-ai/base`에 준비합니다. `model.safetensors`의 SHA-256은 `f47f71177f32bcd101b7573ec9171e6a57f4f4d31148d38e382306f42996874b`입니다. 같은 버전의 `config.json`, `generation_config.json`, 토크나이저 파일·라이선스를 함께 사용합니다. Hugging Face의 해당 버전 메타데이터(`?blobs=true`)를 `.artifacts/question-ai/base-manifest.json`에 보관합니다. 학습 스크립트는 버전과 가중치 해시가 맞지 않으면 중단합니다.

```powershell
$env:HF_HUB_OFFLINE = '1'
$env:TRANSFORMERS_OFFLINE = '1'
& $py -X utf8 training/question_author/evaluate.py --base .artifacts/question-ai/base --data .artifacts/question-ai/data/validation.jsonl --limit 10 --output .artifacts/question-ai/base-validation.json
& $py -X utf8 training/question_author/train.py --base .artifacts/question-ai/base --base-manifest .artifacts/question-ai/base-manifest.json --data .artifacts/question-ai/data --output .artifacts/question-ai/run-v1
& $py -X utf8 training/question_author/evaluate.py --base .artifacts/question-ai/base --adapter .artifacts/question-ai/run-v1/adapter --data .artifacts/question-ai/data/validation.jsonl --output .artifacts/question-ai/adapter-validation.json
& $py -X utf8 training/question_author/evaluate.py --constrained --base .artifacts/question-ai/base --adapter .artifacts/question-ai/run-v1/adapter --data .artifacts/question-ai/data/test.jsonl --output .artifacts/question-ai/adapter-test-constrained.json
```

기존 실험 결과를 덮어쓰지 않도록 새 실행 디렉터리와 보고서 파일명을 사용합니다. 학습 보고서에는 데이터/소스 해시, 시드, 고정 모델 버전, 학습 가능한 매개변수 수, 검증 손실, 선택 epoch, GPU 메모리와 실행 시간이 기록됩니다. 고정 시드는 재현을 돕지만 다른 하드웨어에서 부동소수점 결과까지 동일함을 보장하지 않습니다.

Git에는 학습/검증 코드·설정·합성 자료 생성기·평가 요약·선택한 소형 보정 가중치와 라이선스를 보관합니다. 1.5GB 원본 모델, 로컬 환경과 중간 체크포인트는 `.artifacts`/`.local-tools`에 두며 일반 Git 커밋에 넣지 않습니다. 최종 모바일 모델 파일은 추후 크기와 배포 방식에 맞춰 체크섬을 고정한 별도 아티팩트로 관리해야 합니다. 앱의 오프라인 실행 원칙은 유지합니다.

공식 참고: [Qwen 모델 카드](https://huggingface.co/Qwen/Qwen3-0.6B), [PEFT LoRA](https://huggingface.co/docs/peft/main/package_reference/lora), [LiteRT-LM의 Qwen3 변환 안내](https://github.com/google-ai-edge/LiteRT-LM/blob/main/models/qwen3/README.md). 런타임 안내가 있다는 사실만으로 보정 모델의 태블릿 실행 검증이 끝난 것은 아닙니다.
