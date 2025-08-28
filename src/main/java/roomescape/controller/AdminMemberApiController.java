package roomescape.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import roomescape.controller.dto.response.MemberResponse;
import roomescape.service.MemberService;

import java.util.List;

@RestController
@RequestMapping("/admin/members")
@RequiredArgsConstructor
public class AdminMemberApiController {
    private final MemberService memberService;

    @GetMapping
    public ResponseEntity<List<MemberResponse>> getMembers() {
        List<MemberResponse> responseDto = memberService.findAll().stream()
                .map(MemberResponse::from)
                .toList();
        return ResponseEntity.ok(responseDto);
    }
}
