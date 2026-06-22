# 49. HTTP 클라이언트의 "자동 동작" 함정 + Rate Limit 세 방향

## 한 줄 원리
범용 HTTP 클라이언트는 표준 상태코드(401·407·429·503)에 **내장 동작을 알아서 끼얹는다.**
그게 우리 ACL/재시도 로직과 충돌하면, **그 자동 동작을 꺼서** 책임을 우리 쪽에 명시적으로 모은다.

## A. 자동 동작 함정 (log_44 재방문 — 그땐 "사실"만, 오늘 "왜")

- **HttpURLConnection (= `SimpleClientHttpRequestFactory`)**: 401을 받으면 *자체 인증 처리*를 함 →
  그 과정에서 응답 바디 스트림을 소모 → 우리 `translateErrorStatus`가 읽을 땐 **빈손** →
  토스 에러코드 매핑이 깨짐(UNAUTHORIZED_KEY 등을 못 꺼냄).
  - **왜 401만?** 401·407은 HTTP 인증 챌린지(WWW-Authenticate/Proxy-Authenticate) 코드라
    클라이언트가 가로채 처리한다. 400·404·500은 그냥 우리한테 넘김.
  - **"스트림 소모"** = 응답 바디는 한 방향 `InputStream`. 먼저 끝까지 읽으면 되감기 불가 →
    두 번째 읽기는 빈손.
  - **진단법**: 헤더가 안 갔으면 *모든* 요청이 401이어야 함. 근데 200(정상 결제)은 됐다 →
    헤더는 무죄, 문제는 **받는 쪽**.
  - **해결**: Apache HttpComponents로 교체(401 자동 인증 처리를 안 함 → 바디 보존).

- **Apache HttpClient5** (step3에서 새로 발견): 기본 `DefaultHttpRequestRetryStrategy`가
  **429·503을 자동 재시도**(Retry-After까지 존중) → 내 `RetryAfterInterceptor`와 **이중 재시도** +
  우리 maxAttempts 무시. 증상: 테스트에서 `sleeps=[]`인데 `body=ok`·`requestCount=2`
  (재시도는 됐는데 내 인터셉터가 안 함). → `disableAutomaticRetries()`로 꺼서 인터셉터가 단독 책임.

> 두 사건 같은 뿌리: "클라이언트가 표준 코드에 친절하게 끼어든다" → 우리 명시적 로직과 충돌.

## B. Rate Limit = 같은 토큰 버킷, 세 방향 (step3 거꾸로 이해)

- **토큰 버킷**: capacity(허용 버스트)·refillPerSec(평균 초당 상한). lazy refill(경과시간×refill,
  capacity 상한). 시간은 `LongSupplier` 주입(가짜 시계로 결정적 테스트). `synchronized`로
  동시에도 정확히 capacity개만 통과.
- **세 메커니즘** (처음엔 다 뭉쳤다가 인출로 분리):
  1. **인바운드**: 손님이 우리 한도 초과 → **우리가** 429+Retry-After를 손님에게
     (`HandlerInterceptor.preHandle`, 컨트롤러 도달 전 차단).
  2. **아웃바운드 reactive**: 우리가 토스 한도 초과 → **토스가** 429 → 기다렸다 재시도
     (이미 보냄 = "아직 처리 안 됨"이라 재시도 안전).
  3. **아웃바운드 proactive**: 토스가 429 주기 **전**에 우리가 미리 판단 → **아예 안 보냄**
     (fail-fast, `OutboundRateLimitException` → 503).

## 학습 방식 아쉬운 점
- 마감 때문에 step3를 코치가 통째로 구현 → "코드는 도는데 이해는 안 생김"의 끝판왕(front-running 최대치).
  학습자가 이를 스스로 알아채 "거꾸로 이해" 요청 — 오늘 최고의 자기주도 신호.
- 빌드/테스트 운영에서 코치 실수로 시간 낭비: 데몬 반복 kill→콜드스타트, `| tail`로 진행 가림,
  stale XML 오독, 동시성 테스트 풀<작업수 데드락. (도구 운영도 학습 흐름의 일부 — 가시성 먼저.)

## 다음 사이클 키워드 (i+1)
1. **capacity↑(순간 버스트) vs refillPerSec↑(평균 처리량)** — 각각 뭘 키우나? / 흐름 파악
2. **인바운드 한도(우리 처리 용량) ≠ 아웃바운드 한도(상대가 우리에게 허용한 몫)** — 같은 값 두면
   안 되는 이유? / 흐름 파악
3. **fail-fast 거부 vs 토큰 찰 때까지 블로킹 대기** — 장단점(대기는 호출을 매끄럽게 흘리지만
   스레드 점유 + 결정적 테스트 어려움) / 코드 적용
4. **read timeout 재시도("이미 처리됐을 수 있음") vs 429 재시도("아직 처리 안 됨")** —
   각각의 안전장치를 멱등성과 연결 / 흐름 파악 (#36·log_44 가족)
5. **Rate Limit(throughput=양) vs 서킷 브레이커(연속 실패 시 양 무관 차단)** — 차이·상호보완 / 흐름 파악
