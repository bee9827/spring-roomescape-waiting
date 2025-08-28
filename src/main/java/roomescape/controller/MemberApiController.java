package roomescape.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import roomescape.controller.dto.request.MemberCreateRequest;
import roomescape.controller.dto.response.MemberResponse;
import roomescape.service.MemberService;

import static roomescape.controller.MemberApiController.BASE_URL;

@RestController
@RequestMapping(BASE_URL)
@RequiredArgsConstructor
public class MemberApiController {
    public static final String BASE_URL = "/members";

    private final MemberService memberService;

    @PostMapping
    public ResponseEntity<MemberResponse> createMember(
            @RequestBody
            @Valid
            MemberCreateRequest memberCreateRequest
    ) {
        MemberResponse responseDto = MemberResponse.from(memberService.save(memberCreateRequest.toCommand()));
        return ResponseEntity.ok().body(responseDto);
    }
}
