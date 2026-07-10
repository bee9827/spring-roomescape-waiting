package roomescape.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 아키텍처 가드 — 사람이 매번 기억해 지키기 어려운 경계 규칙을 테스트로 자동 강제한다.
 * (reservation↔promotion 사이클은 outbox의 시간적 분리에 가려져 사람 눈엔 안 보였다 — 이런 숨은 위반을 기계가 잡는다.)
 *
 * 참고: 코드베이스 전역 slices().beFreeOfCycles()는 현재 64개 사이클(auth·common 등 깊게 얽힘)로 실패한다.
 * 전역 무순환은 별도 정리 arc의 몫이라, 지금은 방금 끊은 경계를 "못 되돌리게" 못 박는 타겟 규칙으로 좁힌다.
 */
@AnalyzeClasses(packages = "roomescape", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /**
     * 방금 DIP로 끊은 사이클을 되돌리지 못하게 박는다: reservation은 waiting·promotion을 향할 수 없다.
     * (역방향 waiting→reservation, promotion→reservation은 자연스러운 의존이라 허용 — reservation이 맨 아래.)
     */
    @ArchTest
    static final ArchRule reservation_should_not_depend_on_waiting_or_promotion =
            noClasses().that().resideInAPackage("roomescape.reservation..")
                    .should().dependOnClassesThat().resideInAnyPackage("roomescape.waiting..", "roomescape.promotion..");

    /**
     * ACL 경계: 외부(토스) 타입은 payment.toss 안에 가둔다.
     * 바깥은 PaymentGateway 포트로만 대화 — 토스 모델이 우리 도메인에 새어들지 않도록.
     */
    @ArchTest
    static final ArchRule toss_types_should_not_leak_outside_toss_package =
            classes().that().resideInAPackage("roomescape.payment.toss..")
                    .should().onlyHaveDependentClassesThat().resideInAPackage("roomescape.payment.toss..");
}
