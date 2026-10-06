# v0.2 공통 출제 보정 모델 — 실험용

2026-10-01 · **학습 완료 / 앱 배포 기준 미달 / Android 미연결**

Qwen3-0.6B의 고정 버전 `c1899de289a04d12100db370d81485cdf75e47ca`에 적용하는 LoRA 가중치입니다. 1회차 체크포인트에서 전체 출제를 추가 2회 보정했습니다. 같은 가중치에 이전 보정 내용이 포함되므로 원본 모델과 이 어댑터 하나를 로드합니다. 이전 어댑터를 겹쳐 적용하지 않습니다.

300개 가상 사용자 프로필로 학습했으며 개인 플레이 기록은 포함하지 않습니다. 실제 사용자 자료로 검증한 모델은 아닙니다.

- 가중치 SHA-256: `7efd310878a09d108ba1538e3b747328ea50e41892a1936b8c6927ea27e23744`.
- 최종 시험 50건: 유형 판단 **31/50**, 약한 유형이 있는 사례 **0/15**, 혼동 재출제 **13/15**.
- 출제 형식 통과율 100.0%와 대상 반복 방지는 **코드의 출력 제한을 함께 사용한 결과**입니다.
- PC GPU 생성 중앙값 8.29초. 태블릿 실행은 미검증입니다.

유형 판단이 배포 기준 90%에 미달하므로 **앱에서 사용할 완성 모델로 취급하지 않습니다.** [전체 결과와 제외 후보](../../../docs/QUESTION_MODEL_REPORT.md)에 실패를 포함한 평가를 기록했습니다.

이 모델의 평가 입력은 원래 횟수 기반 기록입니다. `--constrained --fast-constrained`를 사용하고, 제외된 다른 실험의 `--record-rates`를 추가하지 않습니다.

```powershell
$py = '.local-tools/question-ai-venv/Scripts/python.exe'
& $py -X utf8 training/question_author/evaluate.py --constrained --fast-constrained --base .artifacts/question-ai/base --adapter models/question-author/v0.2-common-experimental --data .artifacts/question-ai/data/validation.jsonl --output .artifacts/question-ai/recheck-v0.2.json
```

원본 모델은 약 1.5GB이며 Git에 포함하지 않습니다. [환경과 고정 원본 준비](../../../training/question_author/README.md)를 먼저 진행합니다. [Apache-2.0 원문](LICENSE.txt), [변경·학습 기록](training_report.json), [파일별 검증값과 배포 기준](manifest.json)을 함께 보존했습니다. Qwen 원본에 가상 자료로 추가 보정한 변경본입니다.
