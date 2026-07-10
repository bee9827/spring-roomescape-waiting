# 61. 패키지 사이클 끊기 — outbox는 시간적 결합, DIP는 구조적 결합 / 가드가 64개 사이클을 폭로

**날짜**: 2026-07-10
**학습 범위**: 룸이스케이프(spring-roomescape-waiting)의 마지막 남은 패키지 사이클을 DIP로 끊고 ArchUnit 가드로 못 박기 (Level 2 마무리 #1·#2, 코드 적용). [Level2_정리 아키텍처 카드](../Level2_정리/아키텍처의존성.md)·log_41·42의 실전 적용.

분류: 아키텍처 / 의존성 — 코드 적용 (프로젝트: spring-roomescape-waiting, 미러 O)

## 무엇을 했나 (커밋)

- `48e62568` — DIP로 **두 사이클** 끊기: reservation↔waiting + reservation↔promotion. reservation이 포트(`WaitingQueryPort`·`PromotionEnqueuePort`)를 정의하고 waiting·promotion이 구현 → reservation이 두 패키지를 import 안 함.
- `11c46d74` — ArchUnit 가드: reservation은 waiting·promotion을 의존 금지 + ACL(토스는 payment.toss에 가둠).

## 핵심 인출 1 — outbox는 "시간적" 결합, DIP는 "구조적" 결합

두 종류의 결합을 갈라야 사이클이 보인다:
- **시간적 결합**(언제 실행되나): 취소 순간 승격까지 동기로 도나? → **outbox가 끊는다**(취소는 태스크만 기록, 워커가 나중에). 비동기.
- **구조적 결합**(누가 누굴 import하나): reservation이 `PromotionService`를 import하나? → outbox가 있어도 **여전히 import** → 안 끊김.
- **ArchUnit은 구조(import)를 본다** → outbox가 있어도 reservation↔promotion은 사이클로 걸린다.
- **DIP는 정확히 반대**: 런타임 호출 방향(reservation이 waiting에게 물어봄)은 유지하면서 **컴파일 시점 import 방향만 뒤집는다**(포트를 consumer가 소유, provider가 구현). outbox(시간만 끊음)와 DIP(구조를 뒤집음)가 대칭.

## 핵심 인출 2 — "코드 옮기기 ≠ 의존 뒤집기"

reservation→waiting edge는 `ReservationCreator` 한 파일의 두 usage였고, 처방이 달랐다:
- **usage 2**(`createFromPromotion(Waiting)`) = **옮기기**로 해결: promote() 호출을 `PromotionService`(이미 Waiting을 쥔 오케스트레이터)로 이동, `createFromPromotion(Reservation)`으로 변경. promotion이 원래 둘 다 알아도 되는 자리라 OK.
- **usage 1**(`existsBySlotForUpdate`) = **옮기면 안 끊긴다**: 검사를 WaitingService로 옮겨도 유저 직접 예약 흐름을 reservation이 구동하니 결국 reservation이 불러야 함 → 여전히 reservation→waiting. 그래서 **DIP**(포트)가 필요.
- 교훈: 메서드를 다른 클래스로 옮기는 것과, 의존 방향을 뒤집는 것은 다른 도구다.

## 핵심 인출 3 — 사이클은 클래스가 아니라 패키지(묶음) 단위

- 규칙: 패키지 A→B = A의 **아무 클래스 하나**라도 B의 아무 클래스를 import하면. 특정 클래스끼리인지는 무관.
- 그래서 `ReservationService`가 `PromotionService`만 콕 집어 써도, promotion이 reservation을 쓰면 **두 묶음이 사이클**. 팀 비유: 두 팀이 서로 없이는 이해·테스트·재사용 불가.
- **모듈러 모놀리스 vs MSA**: 지금은 컴파일 시점 경계(ArchUnit 강제)를 가진 한 앱. acyclic하게 갈라두면 나중에 진짜 MSA로 떼어낼 수 있다 — **acyclic 모놀리스가 MSA의 전제조건**.

## 핵심 인출 4 — 가드가 폭로한 진실 (log_41 실증)

- 호기심 "왜 promotion을 이렇게 설계했지?"가 **숨은 두 번째 사이클**(reservation↔promotion)을 파냈다. outbox의 시간적 분리에 가려져 사람 눈엔 안 보였던 것.
- ArchUnit 전역 `slices().beFreeOfCycles()`를 켜니 **64개 사이클** — auth·common·theme·time이 깊게 얽힘. BACKLOG의 "사이클 1건뿐"은 완전 오판. **사람은 1개로 봤고 기계는 64개를 봤다** = "왜 자동 강제가 필요한가"의 완벽한 실증.
- 대응(사전 합의 "크면 예외+백로그"): 전역 무순환은 별도 arc로, 지금은 **타겟 규칙**(reservation은 waiting·promotion 금지)으로 오늘 작업만 잠금. ACL 규칙은 통과.

## 부수 관찰 — 슬라이스 테스트 @Import 마찰

새 빈(`WaitingQueryAdapter`)을 추가하자 그걸 @Import로 나열하던 슬라이스 테스트 3곳이 컨텍스트 로딩 실패 → 3곳 모두 수동 추가. "빈을 손으로 나열"하는 방식의 대가. 테스트 미러링/구조 재고의 동기(백로그).

## 검증

베이스라인 그린 → 리팩터 후 그린 → 가드 추가 후 전체 그린. 독립 코드 리뷰(에이전트) 통과(promote()는 순수 팩토리라 옮겨도 트랜잭션·예외 동작 동일 확인).

## 학습법 회고

- **잘된 것**: 합의 게이트 준수 — DIP 설계를 학습자가 추측·확정한 뒤 타이핑. 특히 학습자의 "왜 이렇게 설계했지?" 호기심이 숨은 사이클을 파냈고, "코드 옮기기 vs 의존 뒤집기"를 트레이스로 스스로 구분. 가드가 64개를 폭로한 순간이 개념(log_41)을 몸으로 박음.
- **바꿀 것**: 추가 없음 — 인출→대조→합의→구현 루프가 이번 코드 적용에서도 안정적으로 돎. 다만 "재방문 대상이 실제 코드"일 때 **가드를 먼저 켜서 현실을 측정하고 시작**하면(64사이클을 미리 알았으면 스코프를 처음부터 좁혔을 것) 좋겠다는 관찰.

## 다음
*Level 2 마무리 계속: 테스트 소스 패키지 미러링(#3)이 남음. 또는 새 arc '코드베이스 전역 무순환(64사이클)'. BACKLOG에서 선택.*
