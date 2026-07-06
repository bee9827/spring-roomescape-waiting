DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS promotion_outbox;
DROP TABLE IF EXISTS waitings;
DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS members;
DROP TABLE IF EXISTS stores;
DROP TABLE IF EXISTS times;
DROP TABLE IF EXISTS themes;

CREATE TABLE times
(
    id       BIGINT NOT NULL AUTO_INCREMENT,
    start_at TIME   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (start_at)
);

CREATE TABLE themes
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    name          VARCHAR(40) NOT NULL,
    thumbnail_url VARCHAR(2048),
    description   VARCHAR(400),
    price         BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE (name)
);

CREATE TABLE stores
(
    id   BIGINT      NOT NULL AUTO_INCREMENT,
    name VARCHAR(40) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE members
(
    id       BIGINT       NOT NULL AUTO_INCREMENT,
    name     VARCHAR(20)  NOT NULL,
    email    VARCHAR(100) NOT NULL,
    password VARCHAR(100) NOT NULL,
    role     VARCHAR(10)  NOT NULL DEFAULT 'USER',
    store_id BIGINT,
    PRIMARY KEY (id),
    UNIQUE (email),
    FOREIGN KEY (store_id) REFERENCES stores (id)
);

CREATE TABLE reservations
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    member_id  BIGINT      NOT NULL,
    date       DATE        NOT NULL,
    time_id    BIGINT      NOT NULL,
    theme_id   BIGINT      NOT NULL,
    store_id   BIGINT      NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'BOOKED',
    -- MySQL TIMESTAMP는 32비트 유닉스 초(~2038)라 9999 센티널을 못 담는다 → 달력 값을 그대로 저장하는 DATETIME
    deleted_at DATETIME    NOT NULL DEFAULT '9999-12-31 00:00:00',
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (member_id) REFERENCES members (id),
    FOREIGN KEY (time_id) REFERENCES times (id),
    FOREIGN KEY (theme_id) REFERENCES themes (id),
    FOREIGN KEY (store_id) REFERENCES stores (id),
    UNIQUE (theme_id, date, time_id, store_id, deleted_at)
);

CREATE TABLE orders
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    order_id        VARCHAR(64)  NOT NULL,
    idempotency_key VARCHAR(64)  NOT NULL, -- 주문당 고정. confirm 재시도가 토스에서 이중 승인되지 않게 헤더로 보냄
    reservation_id  BIGINT       NOT NULL,
    amount          BIGINT       NOT NULL,
    payment_key     VARCHAR(200),
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (order_id),
    UNIQUE (idempotency_key),
    UNIQUE (reservation_id), -- 한 예약엔 주문 1건만(결제 준비 멱등의 DB 백스톱: 동시 요청 경합도 차단)
    FOREIGN KEY (reservation_id) REFERENCES reservations (id)
);

CREATE TABLE waitings
(
    id         BIGINT    NOT NULL AUTO_INCREMENT,
    member_id  BIGINT    NOT NULL,
    date       DATE      NOT NULL,
    time_id    BIGINT    NOT NULL,
    theme_id   BIGINT    NOT NULL,
    store_id   BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (member_id) REFERENCES members (id),
    FOREIGN KEY (time_id) REFERENCES times (id),
    FOREIGN KEY (theme_id) REFERENCES themes (id),
    FOREIGN KEY (store_id) REFERENCES stores (id),
    -- 슬롯 선두 순서: 잠금 조회(WHERE date,time,theme,store)가 인덱스를 타야 gap 락이 그 슬롯의 틈에만 앉는다
    -- (member_id 선두면 leftmost prefix가 안 맞아 풀스캔 = 전 슬롯 대기 신청 블락).
    -- member 단독 조회(내 대기 목록)는 인덱스를 잃지만, 슬롯당 5개 제한으로 테이블이 항상 작아 수용.
    UNIQUE (date, time_id, theme_id, store_id, member_id)
);

CREATE TABLE promotion_outbox
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    date       DATE        NOT NULL,
    time_id    BIGINT      NOT NULL,
    theme_id   BIGINT      NOT NULL,
    store_id   BIGINT      NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (time_id) REFERENCES times (id),
    FOREIGN KEY (theme_id) REFERENCES themes (id),
    FOREIGN KEY (store_id) REFERENCES stores (id)
);

