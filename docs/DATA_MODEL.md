# 데이터 모델

Room v2의 실제 구현을 설명합니다. 기준은 [SPEC.md](SPEC.md) 7~8·12·16절이며 AI 규칙은 [AI_DESIGN.md](AI_DESIGN.md)에 있습니다. 한 기기에서 한 사용자를 전제로 `memory-steps.db`에 저장합니다.

## 테이블과 연결

생성 스키마는 `app/schemas/com.example.memorysteps.data.LearningDatabase/1.json`과 `2.json`입니다. v1의 다섯 테이블을 유지하면서 nullable 연결 열 두 개와 AI 테이블 열 개를 추가합니다.

| 테이블 | 실제 키·책임 |
| --- | --- |
| training_cycles | UUID PK. 모드·날짜·앱 버전·4/3/3 순환 위치·현재 슬롯·진행/완료/종료 상태·modeEpochId |
| cycle_slots | (cycleId, slotIndex) PK. 순서·유형·기억/대기/보기/풀이 제한·조건 버전·유효 완료 문제·appliedDecisionId |
| problem_attempts | UUID PK. 슬롯별 교체 순번·실행 토큰·생성 버전·대상/보기 순서 JSON·실제 시간·정오·시도 수·무효 사유·learningStatus |
| choice_attempts | (problemId, attemptIndex) PK. 선택 보기·정오·풀이 누적 시간·단조 시각·날짜용 시각. 문제/보기 UNIQUE |
| training_progress | singleton=1. 현재 진행 중 사이클 ID |
| algorithm_epochs | UUID PK. BANDIT/COMPARISON 모드와 시작/종료 시각 |
| algorithm_config | singleton=1. 현재 epochId |
| difficulty_states | type PK. 현재 조건·conditionVersion·appliedDecisionId |
| learning_bundles | UUID PK. 유형·epoch·조건 버전/전체 스냅샷·생성 버전·sourceDecisionId·상태·통계 합계/개수·출처 |
| bundle_members | problemId PK, (bundleId, ordinal) UNIQUE. 묶음/문제 FK. 한 문제는 한 묶음에만 포함 |
| ai_decisions | UUID PK, inputBundleId UNIQUE. 알고리즘 버전·후보/점수·행동·이유·전후 조건·복원 이벤트 계획·적용/보상 상태 |
| bandit_arms | (type, action, algorithmVersion) PK. 적용 경험·할인 유효 횟수·보상 합계·갱신 시각 |
| reward_receipts | decisionId PK, bundleId UNIQUE. 보상을 제공한 다음 묶음·첫 정답 수·보상·적용 시각 |
| reduction_history | 증가 정수 PK. 유형·MEMORY/SOLVE·결정 ID·단축 전후 값·남은 단축량·ACTIVE/RESTORED/CLOSED |
| restoration_events | (decisionId, reductionId) PK. 실제 복원량. 같은 복원 결정을 같은 이력에 두 번 적용하지 않음 |

AI 상태 간 참조는 Repository의 동일 트랜잭션 안에서 일관성을 검사합니다. 실제 SQL 외래 키와 인덱스는 생성 스키마에 따릅니다. 유형/epoch별 OPEN 묶음 최대 한 개, 유형별 PENDING 결정 최대 한 개는 트랜잭션 검사로 제한합니다.

대기 결정은 `ai_decisions.status=PENDING`으로 조회하며 난이도 행에 별도 pending ID를 중복 저장하지 않습니다. 보상 대기는 `rewardStatus`로 관리하므로 별도 DecisionOutcome 테이블은 없습니다. 풀이 제한 해제 시 활성 SOLVE 이력을 CLOSED로 종료해 이전 선택지 단계의 단축량을 재사용하지 않습니다.

## 시간과 조건 스냅샷

시간은 정수 밀리초입니다. 풀이 제한 없음은 null이며 0초가 아닙니다. 한 사이클을 시작할 때 세 유형의 현재 조건을 조회하고 10개 슬롯마다 고정합니다. 다음 문제 생성·이어하기는 그 슬롯의 조건을 사용하며 현재 난이도로 과거 조건을 재구성하지 않습니다.

UUID는 문제 표시 전에 저장합니다. 콘텐츠 JSON에는 실제 대상·보기·순서와 버전이 있어 난수 시드를 재실행할 필요가 없습니다. 순서는 슬롯/시도/묶음 순번과 복원 이력 증가 ID로 관리합니다. 이벤트 판정에는 단조 시계를, 날짜 표시에만 벽시계를 사용합니다.

묶음 통계는 첫 정답 수 C와 M/A/W의 밀리초 합계·개수를 저장합니다. 평균을 반올림해서 원본처럼 저장하지 않습니다. 조건·후보·복원 계획 JSON은 `AdaptiveCodec`으로 직렬화하며 무한 UCB 점수는 문자열 INFINITY로 보존합니다.

## 학습 포함 상태

| learningStatus | 사용자 성적 | AI 묶음 |
| --- | --- | --- |
| INCLUDED | 포함 | 한 번 포함 |
| AFTER_BUNDLE_COMPLETE | 포함 | 같은 사이클에서 묶음 완료 후 남은 동일 유형 문항이므로 제외 |
| LEGACY_RECORD_ONLY | 포함 | 최초 보정 창 밖의 v1 기록이므로 제외 |
| EXCLUDED_INVALID | 제외 | 중단 등 무효 문항이므로 제외 |
| PENDING_ALGORITHM | 완료된 경우 포함 | v1 원본 또는 아직 완료되지 않은 문항의 초기 값. 유효 완료 트랜잭션에서 배정 |

연습은 Room에 넣지 않습니다. 슬롯의 finalizedProblemId는 유효 완료 문항 하나만 가리키므로 같은 슬롯의 중단 이력이 늘어도 성적이 중복되지 않습니다. KEEP 전후의 조건 값이 같아도 새 conditionVersion과 appliedDecisionId로 묶음/보상 연결을 나눕니다.

## 트랜잭션

`startFormal`은 진행 중 사이클이 없음을 확인하고 유형별 난이도를 동결한 세션·10개 슬롯·첫 문제를 생성합니다. 종합 회전은 별도 증가 값 없이 `COMPLETED AND MIXED` 사이클 수 `% 3`으로 계산합니다.

답변/타임아웃/중단은 ViewModel의 직렬 이벤트 처리기가 입력 시각을 먼저 잡아 저장합니다. 하나의 Room 트랜잭션에서 다음을 수행합니다.

1. 문제와 선택 내역을 저장합니다. 종료된 결과는 수정하지 않고 이전 runToken의 미완료 쓰기는 거부합니다.
2. 유효 완료 문제를 슬롯에 한 번 연결하고 해당 유형의 묶음에 배정하거나 기록 전용으로 표시합니다.
3. 묶음이 10개가 되면 통계를 확정합니다. 적용된 이전 밴딧 결정의 다음 묶음일 때만 할인·보상을 반영하고 고유 receipt를 삽입합니다.
4. 갱신된 학습값으로 새 후보와 행동을 선택해 PENDING 결정을 저장합니다.
5. 마지막 슬롯이 완료되면 사이클 COMPLETED, 대기 결정 적용, 복원 잔량 변경, 새 단축 이력 추가, activeCycleId 해제를 같은 트랜잭션에서 처리합니다.

결과는 커밋 성공 후 화면에 공개합니다. 중간 실패는 정답·묶음·보상·난이도·사이클 완료 전체를 롤백합니다. 같은 완료 콜백의 동시 재실행은 슬롯/문제/묶음/결정/보상 고유성을 통해 재학습하지 않습니다. 저장 오류에서 재시도하면 DB의 마지막 커밋으로 복구합니다.

## 중단과 명시적 종료

앱이 백그라운드로 가거나 화면을 떠나면 미완료 문제는 무효 이력으로 남습니다. 강제 종료로 쓰지 못했어도 다음 시작에서 남은 ACTIVE 문항을 INVALID로 처리합니다. 완료 문항과 순서를 복원하고 현재 중단 슬롯만 같은 유형·조건의 새 문제로 교체합니다. 이전 프로세스의 단조 시각 원점은 재사용하지 않습니다.

일시정지·홈 이동·프로세스 종료에서는 OPEN 묶음과 PENDING 결정을 유지하고 난이도를 바꾸지 않습니다. 사용자가 진행 중인 게임을 명시적으로 끝내면 사이클을 STOPPED로 남기고 이미 모인 대기 결정을 적용합니다. 미완성 묶음은 다음 게임으로 이어지며 완료 기록은 삭제하지 않습니다. STOPPED는 완료 종합 회전 수에 포함하지 않습니다.

## 기존 기록 마이그레이션

Room `AutoMigration(1, 2)`는 기존 다섯 테이블과 원본 값을 보존하면서 구조를 추가합니다. destructive fallback은 없습니다. 최초 Repository 초기화에서 AI epoch와 세 유형 상태·밴딧 값을 만들고, v1 활성 사이클에 epoch를 연결합니다.

유형별 초기 조건·같은 생성 버전의 최근 최대 10개 완료 문항을 LEGACY_CALIBRATION 묶음에 넣습니다. 나머지는 LEGACY_RECORD_ONLY로 성적에 남습니다. 10개 미만이면 새 기록과 이어서 모으고, 10개이면 첫 결정을 만듭니다. 활성 사이클이 있으면 끝날 때까지 적용을 미루며 없으면 즉시 적용합니다. 최초 보정 묶음에는 이전 결정이 없어 소급 밴딧 보상은 없습니다. 초기화도 한 트랜잭션이므로 중단/재시작으로 다시 적용되지 않습니다.

종전 DB 도입 이전의 메모리 전용 앱에서 이미 사라진 기록은 복구할 수 없습니다.

## 모드 전환과 조회

디버그 빌드에서 진행 중 사이클이 없을 때 BANDIT/COMPARISON을 바꿉니다. 새 epoch를 만들고 이전 미완성 묶음은 CLOSED_BY_MODE_CHANGE, 보상 대기는 CANCELLED_BY_MODE_CHANGE로 종료합니다. 난이도·복원 이력·이미 얻은 학습값은 유지하며 다음 묶음의 이전 결정 연결만 비웁니다.

학습 현황은 전체 유효 성적, 최근 30회 사이클, 현재 유형별 조건과 OPEN 묶음의 수집 수/PENDING 여부를 표시합니다. 개발 진단은 최근 판단 30개, 후보 점수, 보상 상태와 행동별 학습값을 조회합니다. 게임 화면에는 수식이나 보상 값을 표시하지 않습니다.

조건별 성과 비교·최소 표본 기준·기간 창과 안내/효과음 환경 설정은 후속 범위입니다. 알고리즘 모드는 Room에 저장하며 아직 DataStore 의존성은 없습니다.
