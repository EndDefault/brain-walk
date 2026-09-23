# 데이터 모델

Room v3의 실제 구현입니다. 기준은 [SPEC.md](SPEC.md), 학습 규칙은 [AI_DESIGN.md](AI_DESIGN.md)에 있습니다. 한 기기에 한 사용자이며 `memory-steps.db`를 사용합니다. `app/schemas/com.example.memorysteps.data.LearningDatabase/1.json`~`3.json`을 보존합니다.

## 게임과 공통 학습

화면의 종합 게임 한 회가 AI 학습 묶음 하나입니다. 게임을 시작할 때 공통 조건을 조회해 색·그림·숫자 10개 슬롯에 동일하게 고정합니다. `learning_bundles.cycleId`는 UNIQUE이며, 묶음에 넣는 문항의 cycleId와 slotIndex를 검사해 다른 게임의 부분 기록을 합치지 않습니다.

AI 테이블의 기존 type 열은 학습 범위 키로 사용합니다. 신규 AI 데이터는 COMBINED 하나이며 공통 난이도 행 1개, 행동별 밴딧 값 3개를 둡니다. 이전 COLOR/PICTURE/NUMBER AI 행은 보관합니다. 문제/슬롯의 type은 실제 소재 유형을 계속 보존하므로 유형별 성적을 참고할 수 있습니다.

## 테이블

| 테이블 | 실제 키·내용 |
| --- | --- |
| training_cycles | UUID PK. MIXED/단일 유형 모드·날짜·앱 버전·순환 위치·현재 슬롯·진행/완료/종료 상태·modeEpochId |
| cycle_slots | (cycleId, slotIndex) PK. 문제 유형·조건 스냅샷·조건 버전·유효 완료 문항·적용 결정 |
| problem_attempts | UUID PK. 슬롯/교체 순번·runToken·대상/보기 순서 JSON·생성 버전·실제 시간·정오·시도·무효 사유·학습 포함 상태 |
| choice_attempts | (problemId, attemptIndex) PK, (problemId, optionId) UNIQUE. 선택·정오·누적 시간·단조/날짜 시각 |
| training_progress | singleton=1. 현재 진행 사이클 ID |
| algorithm_epochs | UUID PK. BANDIT/COMPARISON과 시작/종료 시각 |
| algorithm_config | singleton=1. 현재 epochId와 learningScope=COMBINED |
| difficulty_states | type PK. 공통 조건·conditionVersion·appliedDecisionId |
| learning_bundles | UUID PK, nullable cycleId UNIQUE. 범위·epoch·조건 전체·생성 버전 조합·이전 결정·상태·합산 통계·출처 |
| bundle_members | problemId PK, (bundleId, ordinal) UNIQUE. 문제/묶음 FK. 한 문제를 한 묶음에만 연결 |
| ai_decisions | UUID PK, inputBundleId UNIQUE. 버전·후보/점수·행동·이유·전후 조건·복원 계획·적용/보상 상태 |
| bandit_arms | (type, action, algorithmVersion) PK. 적용 경험·할인 유효 횟수·보상 합계 |
| reward_receipts | decisionId PK, bundleId UNIQUE. 다음 완료 게임의 첫 정답 수·보상·적용 시각 |
| reduction_history | 증가 정수 PK. 범위·MEMORY/SOLVE·단축 전후 값·남은 단축량·ACTIVE/RESTORED/CLOSED |
| restoration_events | (decisionId, reductionId) PK. 실제 복원량과 중복 방지 |

SQL 제약은 생성 스키마를 따릅니다. 나머지 참조 일관성은 Repository의 같은 트랜잭션에서 검사합니다. 풀이 제한 해제 시 활성 SOLVE 이력을 CLOSED로 종료해 이전 보기 단계의 단축량을 재사용하지 않습니다.

## 기록과 시간

시간은 정수 밀리초이며 풀이 제한 없음은 null입니다. 날짜용 벽시계와 판정용 단조 시계를 구분합니다. 실제 대상·보기 순서를 JSON에 보존하며, 이어하기는 현재 난이도로 과거 조건을 추정하지 않고 저장된 슬롯 스냅샷을 사용합니다. 업그레이드 전에 시작한 게임의 서로 다른 유형별 조건도 그대로 복원합니다.

통계에는 C와 M/A/W의 합계·개수를 저장합니다. 조건·후보·복원 계획은 AdaptiveCodec으로 직렬화하고 무한 UCB 점수는 문자열 INFINITY로 저장합니다. 세 유형의 생성 버전을 조합한 문자열과 개별 문제 생성 버전을 모두 확인합니다.

| learningStatus | 사용자 성적 | AI 처리 |
| --- | --- | --- |
| INCLUDED | 포함 | 해당 게임 묶음에 포함. 완성된 묶음만 학습 |
| EXCLUDED_INVALID | 제외 | 중단 등 무효 문항 |
| LEGACY_RECORD_ONLY | 포함 | 과거 원본 보관, 통합 학습 제외 |
| LEGACY_SCOPE_RECORD_ONLY | 포함 | 업그레이드 전에 시작한 게임의 남은 유효 문항 |
| AFTER_BUNDLE_COMPLETE | 포함 | v2의 기록 전용 상태를 보관. 신규 생성하지 않음 |
| EXCLUDED_TRAINING_MODE | 포함 | 저장 API로 만든 단일 유형 정식 기록. 통합 학습 제외 |
| PENDING_ALGORITHM | 완료된 경우 포함 | 미완료/v1 원본 초기 값. 유효 완료 트랜잭션에서 배정 |

화면의 연습은 Room에 저장하지 않습니다. 동일 슬롯의 무효 문제를 여러 번 교체해도 finalizedProblemId는 유효 완료 하나만 가리킵니다. KEEP도 새 결정과 조건 버전이므로 이후 게임의 보상을 구분할 수 있습니다.

## 원자성·중단

ViewModel은 입력 시각을 먼저 잡고 저장 이벤트를 직렬 처리합니다. 문제/선택 저장 → 슬롯 확정 → 해당 게임의 묶음 배정 → 10개 완성 시 합산 통계/이전 선택 보상 → 새 판단 → 공통 조건 적용/복원 이력 → 게임 완료/진행 해제를 한 Room 트랜잭션으로 처리합니다. 화면에는 커밋 성공 후 결과를 공개합니다. 같은 완료가 동시에 재전송되어도 고유 키와 슬롯 확정 검사로 중복 학습하지 않습니다.

종합 회전은 `COMPLETED AND MIXED` 행 수 `% 3`으로 구합니다. 앱 종료·일시정지에서는 완료 문제와 같은 게임의 OPEN 묶음을 유지합니다. 강제 종료 후 남은 ACTIVE 문항은 INVALID로 보존하고 같은 슬롯·조건의 새 문제로 교체합니다. 이전 프로세스의 단조 시각 원점을 재사용하지 않습니다.

명시적인 진행 종료는 사이클 STOPPED와 묶음 CLOSED_BY_CYCLE_STOPPED를 함께 저장합니다. 완료 성적은 보존하지만 10개 미만으로 판단/보상을 만들지 않으며 다음 게임에 부분 기록을 넘기지 않습니다. 이전 적용 결정의 보상 연결은 다음 온전한 완료 게임까지 유지합니다. STOPPED는 종합 회전을 진행하지 않습니다.

## v1/v2 업그레이드

Room AutoMigration 1→2→3은 기존 원본과 AI 테이블을 모두 보존합니다. v3는 algorithm_config.learningScope를 PER_TYPE 기본값으로 추가하고 learning_bundles.cycleId를 nullable로 추가합니다. 파괴적 초기화는 없습니다.

최초 Repository 초기화는 기존 PER_TYPE 범위의 OPEN 묶음·대기 판단·미보상 연결을 SCOPE_CHANGE 종료 상태로 보관하고 이전 epoch를 닫습니다. 기본 조건(기억 20초·대기 3초·보기 4개·풀이 무제한), 학습값 0인 COMBINED 정책과 새 epoch를 한 트랜잭션으로 생성합니다. 이전 단축량·보상·원본을 새로운 정책에 합산하거나 소급 학습하지 않습니다.

기존 진행 게임의 epoch와 슬롯은 바꾸지 않습니다. 기존 조건대로 마친 결과는 성적에만 남기고, 다음 새 종합 게임부터 통합 학습을 합니다. config.learningScope=COMBINED이면 이 초기화를 반복하지 않습니다. DB 도입 이전에 이미 사라진 메모리 기록은 복구할 수 없습니다.

## 모드와 화면

디버그에서 진행 중 게임이 없을 때 BANDIT/COMPARISON을 바꿉니다. 새 epoch를 만들고 보상 대기를 닫되 공통 난이도·복원 이력·기존 통합 학습값은 유지합니다. 이전 유형별 보관 행은 수정하지 않습니다.

학습 현황은 전체 성적·유형별 참고 성적·최근 30회와 공통 난이도 한 개를 표시합니다. 개발 진단은 통합 정책의 최근 판단/후보/보상/행동별 학습값만 조회합니다. 수식은 게임 화면에 표시하지 않습니다. 조건별 비교·효과음/환경 설정은 후속 범위이며 아직 DataStore 의존성은 없습니다.
