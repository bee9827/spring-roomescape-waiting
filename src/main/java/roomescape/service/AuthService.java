package roomescape.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import roomescape.common.TokenProvider;
import roomescape.common.exception.RestApiException;
import roomescape.common.exception.status.AuthErrorStatus;
import roomescape.controller.dto.LoginRequest;
import roomescape.service.dto.result.MemberResult;
import roomescape.service.dto.result.ReservationResult;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final MemberService memberService;
    private final ReservationService reservationService;
    private final TokenProvider tokenProvider;

    public String createToken(LoginRequest loginRequest) {
        MemberResult memberResult = memberService.findByEmailAndPassword(loginRequest.email(), loginRequest.password());
        return tokenProvider.generateToken(memberResult.id(), memberResult.role());
    }

    public MemberResult getMember(Long id) {
        return memberService.findById(id);
    }

    public void validateOwner(Long reservationId, Long memberId) {
        ReservationResult reservationResult = reservationService.findById(reservationId);
        if (!checkOwner(memberId, reservationResult)) {
            throw new RestApiException(AuthErrorStatus.NOT_AUTHORIZED);
        }
    }

    private boolean checkOwner(Long memberId, ReservationResult reservationResult) {
        return reservationResult.memberResult().id().equals(memberId);
    }
}
