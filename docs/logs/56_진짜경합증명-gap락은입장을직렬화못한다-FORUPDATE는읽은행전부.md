# 56. 진짜 경합 증명 — gap 락은 입장을 직렬화 못 한다 + FOR UPDATE는 읽은 행 전부를 잠근다

**날짜**: 2026-07-06 (arc '동시성 손끝 증명' 마지막 칸 — **arc 닫힘**)
**학습 범위**: hot 마지막 칸 "동시성에서 정말 직렬화되나 — 진짜 멀티스레드 증명"(예고 log_28·47). Testcontainers MySQL + 동시 스레드로 상태 CAS·새치기 가드를 증명 — 하다가 **진짜 데드락 결함을 발견·해부·수정**. 재방문: log_17·26(gap lock), log_47(상태 CAS·"가드는 동시성에 못 닫는다"), log_43(UNIQUE 백스톱 3중 방어), log_28(스레드 테스트).

분류: DB·트랜잭션(동시성) + 테스트(실제성) — 코드 적용 + 실전 판단

## 1. 출발 인출 — "메커니즘 증명"과 "진짜 경합 증명"의 차이
- 학습자 인출: 낙관(CAS)은 H2에서도 affected rows로 증명 가능 / gap lock은 H2 미지원이라 불가 → Testcontainers. **절반 정답** — 못 증명한 축이 하나 더 있었다.
- 기존 CAS 테스트(PaymentServiceTest)의 주석이 자백: "**동시 진입 흉내**" — 스레드 하나가 낡은 객체 둘로 *순차* 호출. 순차 흉내는 check-then-act 같은 코드 결함은 잡지만, **진짜 동시 도착에서 DB가 딱 하나만 통과시키는지**는 못 잡는다.
- 기존 스레드 테스트(ReservationConcurrencyTest)는 진짜 스레드지만 단언이 `>= 1`로 물러서 있었다(주석: "H2는 gap lock 미지원으로 엄격한 검증 불가"). → 두 구멍 = 이번 칸의 작업 목록 (학습자가 도출).

## 2. 첫 벽 — MySQL TIMESTAMP는 32비트 유닉스 초(2038 한계)
- 컨테이너는 떴는데 schema.sql이 거부: `Invalid default value for 'deleted_at'` — 소프트삭제 센티널 `'9999-12-31'`.
- MySQL TIMESTAMP = 1970 기점 32비트 부호 있는 초 = **2038-01-19까지**(2³¹초 ≈ 68년 — 학습자가 산수로 도출). H2의 TIMESTAMP는 범위가 넓어 그냥 받았던 것 — **같은 타입 이름, 다른 물리 표현**.
- "왜 그따구로 설계?" → 그 시절엔 합리적(4바이트가 사치 아니던 시대의 time_t 관습 + 이제 와 넓히면 호환 파괴). Y2K와 같은 종류의 빚.
- 수정: `deleted_at DATETIME`(에포크가 아니라 달력 값 저장, ~9999, H2 호환).

## 3. CAS 진짜 경합 — 통과
- 10 스레드가 같은 NEEDS_CHECK 스냅샷을 들고 latch 동시 출발 → `complete()`(UPDATE WHERE status=expected) → **승자 정확히 1, 패자 9, 최종 CONFIRMED**. log_47의 낙관 CAS가 진짜 경합에서 직렬화됨을 손끝으로 확인.

## 4. 예약 가드 — 성공은 정확히 1인데, 패자가 500으로 죽었다
- 3 스레드 동시 예약: successCount=1 ✅ (새치기 방어는 안 뚫림) — 그러나 패자 2가 409가 아니라 `CannotAcquireLockException: **Deadlock found**`. H2에서는 곱게 409로 지던 것이 진짜 MySQL에선 사용자에게 500. **H2가 보여줄 수 없었던 사고.**
- 여기서 이론 시뮬레이션을 멈추고 **`SHOW ENGINE INNODB STATUS`의 LATEST DETECTED DEADLOCK을 깠다** (PROCESS 권한 필요 → root 접속). 현장 기록:
  - **Tx1(희생)**: 대기열 SELECT FOR UPDATE 실행 중 / 쥔 것: reservations **supremum X (gap)** / 기다림: **times PRIMARY 행 X**
  - **Tx2(승자)**: INSERT 실행 중 / 쥔 것: **times 행 X** / 기다림: reservations supremum **insert intention** ← Tx1의 gap이 막음
- 반전 두 개(학습자의 두 모델을 증거로 탈락시킴):
  1. "첫 FOR UPDATE에서 줄 서겠지" → **빈 범위의 FOR UPDATE는 gap 락이고, gap끼리는 서로 안 막는다.** 몇 개든 같은 틈에 동시에 걸림. gap이 막는 건 오직 하나 — 그 틈에 끼어드는 **INSERT(insert intention)**. 즉 gap 락은 **입장을 직렬화하지 못하면서 출구(INSERT)만 서로 막는 데드락 제조기**.
  2. "대기열 SELECT가 왜 times 행을?" (FK S락 추측 → SELECT라 탈락) → **FOR UPDATE는 그 쿼리가 읽은 모든 행을 X로 잠근다 — JOIN으로 딸려 읽힌 행 포함.** waitings가 비어도 WHERE의 `time_id=1`이 `t.id=1`로 전파돼 times 행을 상수처럼 먼저 읽고 잠갔다. 대기열만 보려던 검사의 락 발자국이 공유 참조 행(times·themes·stores)까지 번진 것.
- cold에 잠자던 "**insert intention lock이 충돌해 대기하는 상대는 누구인가**"(log_26 예고)가 실전으로 닫힘 — **상대 = 같은 틈의 gap 락 보유자들**.

## 5. 수정 설계 — 고리의 변을 부순다 (학습자 A/B 제안 → A 단독의 함정을 시뮬레이션으로 자가 탈락 → 합본)
- 고리의 변 3개: ①Tx1의 gap(중복체크 FOR UPDATE) ②Tx2의 times 행(JOIN 발자국) ③INSERT(본업, 못 없앰).
- **A(JOIN 제거)만 적용 시뮬레이션**: 둘 다 ①② 통과 → ③에 나란히 도착 → **서로의 gap에 막혀 여전히 데드락**(INSERT vs INSERT). → 구조적 범인은 gap 락 = "빈 슬롯을 FOR UPDATE로 선점"이라는 발상 자체.
- 최종(A+B 합본, 변마다 다른 이유):
  1. **[B] 중복체크 FOR UPDATE 제거**(`existsBySlot`) — 검사는 평시 UX(친절한 409 빨리), **경합의 진실은 UNIQUE(theme,date,time,store,deleted_at) 백스톱**이 INSERT 시점에 가림. `DuplicateKeyException`→409 번역은 이미 있었다(log_43 3중 방어의 동형 — OrderService.createPending과 같은 패턴). log_47 봉인 재확인: **가드는 동시성에 못 닫는다 — 닫는 건 쓰기 지점의 원자적 장치**(그땐 CAS, 여기선 UNIQUE).
  2. **[A] 대기열 체크는 JOIN 없는 `existsBySlotForUpdate`** — waitings만 잠금. 남는 gap은 **waitings로 향하는 INSERT(대기 신청)만** 막는 한 방향 창살 = 이 가드의 존재 이유 그 자체라 데드락 재발 없음 (학습자 검증).
- 결과: MySQL 3 스레드 → **성공 1 + 409 2** 그린. H2 기존 테스트의 `>= 1` 사과문도 `== 1`로 조임(직렬화 주체가 gap이 아닌 UNIQUE로 바뀌어 H2에서도 성립). 전체 290 그린.

## 한 문장 봉인
> 빈 범위의 `FOR UPDATE`는 gap 락이 되는데 **gap끼리는 서로 안 막아 입장을 직렬화하지 못하면서, 서로의 INSERT만 막아 데드락을 만든다** — 그러니 "미리 잠가 선점"은 빈 슬롯에선 성립하지 않고, 경합의 진실은 **쓰기 지점의 원자적 장치(UNIQUE 백스톱)**가 가리고 검사는 UX로 물러난다. 덤: **FOR UPDATE는 그 쿼리가 읽은 모든 행을 잠근다(JOIN 포함)** — 잠그려던 테이블보다 락 발자국이 훨씬 넓을 수 있다. 그리고 이 전부는 이론이 아니라 **InnoDB의 데드락 현장 기록**이 가르쳐줬다 — H2 위의 그린은 "메커니즘 증명"일 뿐, 진짜 경합의 사고는 진짜 엔진 위에서만 보인다.

## 학습법 회고

### 잘된 것
- **증거 > 이론, 코치 포함**: 코치가 유도하던 시나리오(INSERT끼리 gap 데드락)도 실제 고리(JOIN이 잠근 times 행 경유)와 달랐고, 학습자가 이를 지적("그럼 아까 질문은 의미없는 거잖아"). INNODB STATUS를 까서 둘 다 증거로 정정 — arc 이름("손끝 증명")을 방법론이 스스로 증명.
- **탈락법이 촘촘히 작동**: S락 업그레이드 모델(다른 사고) → "블락됐다면 데드락이 안 났어야" / FK S락 추측 → "SELECT라 경로가 다름" / A 단독안 → ③ 시뮬레이션으로 자가 탈락. 모두 학습자가 자기 모델의 모순을 증거와 대조해 스스로 버림.
- **4단계 프로토콜 (a)~(d) 완주**: 에러를 날것으로 제시 → 개념 해법(DATETIME·수정안 A/B)은 학습자 → 용어·사실(supremum, insert intention, "읽은 행 전부") 코치 → 배선·타이핑 코치.
- 재방문 수확: log_47 "가드는 못 닫는다"가 새 사례(FOR UPDATE 가드)로 확장, log_43 UNIQUE 백스톱 패턴 재사용, cold의 insert intention 질문(log_26) 실전 닫힘.

### 메타
- 사이클 마무리에서 아쉬운 점·바꿀 것 질문에 학습자가 "로그 남겼어?"로 응답(마무리 속행 신호) — 회고 항목은 코치 초안으로 두고 학습자 확정 대기. 이월 관찰: 마무리 회고를 학습자 인출로 받는 리듬이 이번엔 생략됨.

### 바꿀 것 (다음) — 학습자 확정 (2026-07-06)
- **유지**: 4단계 프로토콜 + 증거 우선(이론 막히면 바로 현장 기록 깐다).
- **초안**: 코치의 유도 질문이 특정 시나리오를 전제할 때, 그 전제를 명시("내 가설은 X 경로")하고 증거로 함께 검증 — 이번에 학습자가 지적한 "의미없는 질문" 상황의 재발 방지.

## 씨앗 (본문에만)
- WaitingService의 enqueue 경로(`findQueueBySlotForUpdate` + INSERT)도 같은 JOIN 발자국·check-then-insert 구조 — 대기 신청 동시 경합에서 같은 부류의 사고 가능성 점검.
- MySQL 8 `SELECT ... FOR UPDATE OF <별칭>` — 잠글 테이블을 지정해 JOIN 발자국을 줄이는 문법.
- 데드락 패자 처리 정책 — 죽이는 게 아니라 재시도(deadlock victim retry)로 흡수하는 관례. 기존 '재시도 정책' 칸과 한 가족.
- 옵티마이저 상수 전파(`w.time_id=1` → `t.id=1` const 읽기)가 락 위치를 바꾼다 — 실행 계획이 곧 락 계획.

## 다음
*arc '동시성 손끝 증명' **닫힘**(log_55~56). 규칙대로 다음 arc는 🧊 cold 메뉴([BACKLOG](../BACKLOG.md))에서 통째로 끌어올린다 — 후보: reservation↔waiting 사이클 끊기+ArchUnit(구조/테스트), 재시도 정책(hot 잔여와 한 가족), JVM/런타임 등.*
