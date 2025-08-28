# 📝 Reservation 도메인 리팩토링 기록
---

## 1. 문제 상황 (Before)
- 하나의 Reservation 테이블에 BOOKED / WAITING / CANCELED 등 모든 상태가 함께 저장됨

- 중복 예약 이슈
    - 예약 생성 시 단순히 existsByDateAndThemeAndTimeSlot(...) 체크만 수행 → 멀티스레드 환경에서 동시에 BOOKED 저장되는 경우 발생

- 상태 조건부 유니크 제약 불가
    - BOOKED 상태에만 UNIQUE를 적용하고 싶었지만, 상태가 한 테이블에 섞여 있어 DB 레벨에서 제약 걸기가 어려움

- 대기열 관리 복잡
    - WAITING 순서를 status로 구분하여 같은 테이블에서 관리 → 대기자 순번 관리/승급 로직이 불투명하고 트랜잭션 충돌 가능성 ↑

- 정규화 위반
    - BOOKED/WAITING 데이터가 본질적으로 다른 성격인데 한 테이블에 섞여 있어 3NF 위반에 가까운 구조
  

## 2. 리팩토링 목표

- 정규화 (3NF) 준수: BOOKED와 WAITING 엔티티를 분리

- 동시성 안전성 확보: 슬롯 단위로 중복 예약 불가 보장

- 대기열 승급 원자화: 예약 취소 시 대기열 1순위가 자동으로 승급

- 확장성 고려: capacity(정원) 속성 추가 → 한 슬롯에 여러 예약 허용 가능

- 비즈니스 의미 강화: 대기자 순서를 명확한 position 컬럼으로 관리

## 3. 최종 설계 (After)

### 엔티티 구성

1. ReservationSlot
    - 예약 단위 슬롯 정의
    - `UNIQUE(theme_id, date, time_slot_id)`
    - `capacity`(정원) 속성으로 동시에 BOOKED 가능 인원 제어
   
2. Reservation
    - **BOOKED 상태 전용 테이블**
    - 제약: UNIQUE(slot_id, member_id) (같은 사람이 같은 슬롯 중복 예약 불가)
    - 예약 취소 시 삭제 처리
3. Waiting
- **WAITING 상태 전용 테이블**
- `UNIQUE(slot_id, member_id)`
- `position` 컬럼(append-only)으로 대기 순서 부여 → 삭제 시 재번호 매김 없음
- 승급 로직: 예약 취소 시 `ORDER BY position ASC LIMIT 1`로 1명 승급