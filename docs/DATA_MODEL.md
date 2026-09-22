# 데이터 모델

상태: 저장 기능 구현 전 설계 계약입니다. 현재 DB·Repository는 없습니다. 저장 기능 PR에서 이 계약을 Room 엔티티·DAO·마이그레이션·트랜잭션 테스트로 구현합니다. 기준은 [SPEC.md](SPEC.md) 7~8·12·16절입니다.

| 데이터 | 책임 |
| --- | --- |
| 문제 기록 | 문제 ID·유형·생성 버전·조건 전체·선택 내역·실제 노출/풀이 시간·정오/시도/시간 초과/무효 여부 |
| 화면 사이클 | 사용자에게 보이는 10문제·훈련 모드·혼합 4/3/3 순환·진행/완료 상태 |
| 유형별 학습 묶음 | 같은 유형·조건의 유효 10문제·AI 학습 포함 여부·완료 상태 |
| 난이도 상태 | 유형별 기억 시간·대기 시간·선택지 수·nullable 풀이 제한 |
| 복원 이력 | 단축 전후 값·미복원 잔량·선택지 단계·복원 이벤트 |
| AI 결정 | 허용 후보·선택 행동·선택 모드·적용 대기/적용 상태·변경 전후 조건·다음 묶음 연결 |
| 밴딧 학습값 | 유형/행동별 선택 경험·할인 유효 횟수·누적 보상·마지막 갱신 시각 |
| 설정(DataStore) | 안내·효과음·개발 비교 모드 등 |

## 식별자와 관계

한 기기에 한 사용자입니다. ID는 충돌을 피할 수 있는 문자열 UUID로 발급하고, 문제 표시 전에 영속화합니다. 순서·중복 방지는 벽시계 대신 명시적인 순번과 고유 제약으로 처리합니다.

| 엔티티 | 키·관계 | 주요 필드·제약 |
| --- | --- | --- |
| TrainingCycle | PK cycleId | trainingMode=MIXED/SINGLE, nullable singleType, modeEpochId, status=IN_PROGRESS/COMPLETED, createdAt, completedAt, mixedRotationIndex |
| CycleSlot | PK (cycleId, slotIndex), FK cycleId | slotIndex 0~9, type, 조건 스냅샷, conditionVersion, generatorVersion, nullable finalizedProblemId, nullable appliedDecisionId |
| ProblemAttempt | PK problemId, FK slot | runToken, target/options/order payload, generatedAt, phase, status=ACTIVE/COMPLETED/INVALID, invalidReason, actualMemoryMs, usedNextButton, firstChoiceMs, solveElapsedMs, attemptsCount, firstCorrect, finalCorrect, timedOut, algorithmMode, 학습 포함 사유 |
| ChoiceAttempt | PK (problemId, attemptIndex), FK problemId | optionId, correct, elapsedSinceSolveStartMs, wallClockAt. attemptIndex 1~3, UNIQUE(problemId, optionId) |
| LearningBundle | PK bundleId, FK modeEpochId | type, conditionVersion와 전체 조건, generatorVersion, status=OPEN/COMPLETED/CLOSED_BY_MODE_CHANGE, nullable sourceDecisionId, C/M/A/W와 계산 원본 합계·개수 |
| BundleMember | PK (bundleId, ordinal), FK bundleId/problemId | ordinal 0~9, UNIQUE(problemId). 한 문제를 두 묶음에 넣지 않음 |
| DifficultyState | PK type | 현재 조건, conditionVersion, nullable pendingDecisionId, nullable appliedDecisionId, solveHistoryEpoch |
| DifficultyDecision | PK decisionId, UNIQUE(inputBundleId) | type, modeEpochId, algorithmVersion, source=BANDIT/FIXED/COMPARISON, action, candidate snapshots와 점수, before/after, status=PENDING/APPLIED/CANCELLED_BY_MODE_CHANGE, timestamps |
| RestorationEntry | PK entryId, FK decisionId | type, axis=MEMORY/SOLVE, sequence, before/after, remainingReductionMs, solveHistoryEpoch, status=ACTIVE/RESTORED/CLOSED |
| RestorationEvent | PK eventId, UNIQUE(decisionId, entryId) | actualRestoredMs. 같은 복원 결정을 같은 이력에 두 번 반영하지 않음 |
| BanditState | PK (type, algorithmVersion, action) | hasEverApplied, effectiveCount, rewardSum, updatedAt |
| RewardReceipt | PK decisionId, UNIQUE(bundleId), FK decisionId/bundleId | reward, p, appliedAt. 보상 멱등성의 기준 |
| DecisionOutcome | PK decisionId, nullable UNIQUE(bundleId), FK decisionId/bundleId | 보상 연결 상태=WAITING/REWARDED/CANCELLED_BY_MODE_CHANGE. 다음 묶음 생성 전 bundleId는 null. 비교/고정 결정은 보상 연결을 만들지 않음 |
| AlgorithmEpoch | PK modeEpochId | algorithmMode=BANDIT/COMPARISON, openedAt, closedAt |
| TrainingProgress | 고정 단일행 | nullable activeCycleId, completedMixedCycleCount, 현재 modeEpochId |

`CycleSlot.finalizedProblemId`는 해당 슬롯의 **완료된 유효 문항 하나**만 가리킵니다. 같은 슬롯에 무효 ProblemAttempt가 여러 개 있어도 유효 문제 수는 1입니다. 연습은 별도 메모리 상태로 실행하며 Room의 성적·학습 테이블에 넣지 않습니다.

DifficultyDecision의 고정 복원 행동은 `RESTORE_FULL`/`RESTORE_PARTIAL`로 기록합니다. BanditState의 행동 키는 명세의 KEEP/ADJUST_MEMORY/ADJUST_SOLVE 세 개뿐이며 고정 복원을 추가하지 않습니다.

Room 기본 `Index`로 표현하기 어려운 ‘유형·epoch별 OPEN 묶음 최대 1개’ 같은 조건은 Repository 단일 트랜잭션으로 검사하고 경쟁 호출 테스트를 추가합니다. SQL 부분 인덱스를 사용하게 되면 마이그레이션과 해당 인덱스 검증을 함께 작성합니다.

## 문제 조건과 학습 포함 구분

문제마다 기억·대기·선택지·풀이 제한, 생성 버전과 소재 ID, 선택지 순서를 스냅샷으로 남깁니다. 현재 DifficultyState를 조회해 과거 조건을 추정하지 않습니다.

| learningDisposition | 사용자 유효 기록 | BundleMember |
| --- | --- | --- |
| INCLUDED | 포함 | 해당 묶음에 1회 포함 |
| AFTER_BUNDLE_COMPLETE | 포함 | 제외. 사이클 중 묶음 완료 후 남은 같은 유형 문항 |
| INVALID | 제외 | 제외. 무효 이력은 보존 |

화면 사이클과 학습 묶음은 다대다 관계이며 BundleMember가 연결을 담당합니다. 종합·유형별 훈련이 같은 유형의 OPEN 묶음을 공유합니다. 유효 문제 수와 AI 학습 포함 수는 서로 다른 집계입니다.

단순 조건 값이 같은 KEEP 전후도 서로 다른 적용 결정·묶음입니다. 조건 버전, 적용 결정 ID, 알고리즘 epoch로 학습 연결을 구분합니다. 같은 조건 비교용 키는 별도로 유형·기억·대기·선택지·풀이 제한·생성 버전을 사용하며 버전 번호 자체가 다르다는 이유로 같은 실제 조건을 비교에서 제외하지 않습니다.

## 트랜잭션 경계

### 사이클 시작

진행 중인 사이클이 없음을 확인하고 사이클과 10개 슬롯을 함께 생성합니다. 종합 배정은 `completedMixedCycleCount % 3`으로 결정하고 섞은 슬롯 계획을 저장합니다. 유형별 사이클은 해당 카운터를 변경하지 않습니다. 이어하기는 새 사이클이나 새 계획을 만들지 않습니다.

### 답변과 문제 완료

하나의 게임 상태 처리기가 답변·타임아웃·화면 이탈 이벤트를 순서대로 처리합니다. 현재 runToken과 problemId가 다른 지연 이벤트는 무시합니다. 같은 오답은 다시 기록하지 않고 풀이 시작 시각도 바꾸지 않습니다.

한 Room 트랜잭션에서 다음을 수행합니다.

1. 이미 끝난 문항 또는 finalizedProblemId가 있는 슬롯이면 중복 완료를 반환합니다.
2. 선택 내역·문제 결과를 저장하고 슬롯에 유효 완료 문항을 연결합니다.
3. 학습 대상이면 OPEN 묶음에 유일한 BundleMember를 추가합니다. 같은 유형의 대기 결정이 있으면 AFTER_BUNDLE_COMPLETE로 남깁니다.
4. 묶음이 10개가 됐다면 완료 통계를 저장합니다. 이전 밴딧 결정에 대한 RewardReceipt가 없을 때만 할인·보상을 반영하고 receipt를 삽입합니다.
5. 갱신한 학습값으로 다음 결정을 만들어 PENDING으로 저장합니다. inputBundleId 고유 제약으로 중복 결정을 막습니다.
6. 마지막 슬롯까지 끝났으면 아래 사이클 완료 처리를 **동일 트랜잭션** 안에서 수행합니다.

### 사이클 완료

10개 슬롯에 각각 유효 완료 문항이 있을 때만 COMPLETED로 바꿉니다. 해당 사이클에서 대기한 유형별 결정을 한 번 적용하고 before/after와 복원 이력을 저장합니다. 종합이면 completedMixedCycleCount를 한 번 늘리고 activeCycleId를 비웁니다. 트랜잭션 실패 시 사이클 완료·조건 변경·카운터 증가 모두 롤백합니다.

학습값은 문제 결과를 바탕으로 계산하지만 게임 화면에 후보 점수·수식·내부 보상 값을 노출하지 않습니다. 개발 진단 화면에서 결정 ID와 실제 저장값을 조회합니다.

## 중단·시계·재개

- 판정과 시간 측정에는 주입한 단조 증가 시계를 사용합니다. 날짜 표시용 UTC 시각은 별도로 저장합니다.
- 풀이 시작점은 선택지 전체가 표시된 첫 프레임입니다. 그 시점에 시작 시각을 고정하고 답변 입력을 활성화합니다. 오답 후에도 같은 시작 시각을 사용합니다.
- 제한 시간이 있으면 판정 단조 시각이 마감보다 작을 때만 답변을 처리합니다. 마감과 같거나 크면 시간 초과입니다. UI 카운트다운 tick의 실행 순서로 판정하지 않습니다.
- 실제 게임 화면을 벗어나거나 앱이 백그라운드로 이동하면 진행 중 문항을 INVALID로 저장합니다. 내부 Compose 재구성만으로 문항을 무효 처리하지 않습니다.
- 프로세스가 종료돼 무효 저장을 못 했어도 시작 시 남아 있는 미완료 문항을 이전 runToken의 중단 문항으로 무효 처리합니다.
- 재개 시 완료 슬롯·묶음·대기 결정은 보존합니다. 미완료 슬롯에 같은 조건의 **새 problemId·새 문제 내용**을 생성하며 이전 문항을 이어서 정답 처리하지 않습니다.
- 단조 시각의 절대값을 재부팅·프로세스 재시작 이후 경과 시간 계산에 재사용하지 않습니다. 완료된 문항은 저장된 지속 시간으로 집계합니다.

## 모드 전환과 저장소 책임

종합/유형별 훈련은 같은 epoch와 누적 묶음을 공유합니다. 개발용 BANDIT/COMPARISON 전환은 진행 사이클이 없을 때 Room 트랜잭션으로 epoch를 변경하고 미완성 묶음·미보상 연결을 종료 상태로 보존합니다. 정확한 경계 정책은 [AI_DESIGN.md](AI_DESIGN.md)에 있습니다.

안내·효과음 등 단순 환경 설정은 DataStore에 둡니다. 학습 연결을 결정하는 알고리즘 모드의 적용 상태는 Room의 AlgorithmEpoch를 진실의 원천으로 삼습니다. DataStore와 Room 사이에 원자적 트랜잭션이 있다고 가정하지 않습니다.

## 기록·비교 조회

홈의 완료 사이클 수는 COMPLETED만, 유효 문제 수는 유효 완료 슬롯만 집계합니다. 유형별 첫 정답률은 첫 정답 수/해당 유형 유효 문제 수입니다. AFTER_BUNDLE_COMPLETE도 사용자 기록에는 포함합니다. 무효·연습은 제외합니다.

종합 결과에는 전체 10문제와 유형별 실제 문항 수(4/3/3)를 함께 보여줍니다. 동일 조건 비교는 조건 키와 알고리즘 모드를 확인하고 비교 모집단·문제 수를 함께 표시합니다. 자료가 없거나 부족하면 비교 불가로 안내하며 서로 다른 난이도를 합쳐 향상·저하로 표시하지 않습니다.

최소 표본 기준과 기간별 비교 창은 기록 화면 PR에서 명시하고 테스트합니다. 이 문서에서는 임의의 통계적 유의성 기준이나 인지 효과 판단을 추가하지 않습니다.
