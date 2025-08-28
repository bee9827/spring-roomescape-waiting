package roomescape.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roomescape.common.exception.RestApiException;
import roomescape.common.exception.status.MemberErrorStatus;
import roomescape.domain.Member;
import roomescape.repository.MemberRepository;
import roomescape.service.dto.command.MemberCreateCommand;
import roomescape.service.dto.result.MemberResult;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MemberService {
    private final MemberRepository memberRepository;

    public MemberResult save(MemberCreateCommand createCommand) {
        return MemberResult.from(save(createCommand.toEntity()));
    }

//    private void delete(Member entity) {
//        if (!memberRepository.existsById(entity.getId())) {
//            throw new RestApiException(MemberErrorStatus.NOT_FOUND);
//        }
//        memberRepository.deleteById(entity.getId());
//    }

    public List<MemberResult> findAll() {
        return memberRepository.findAll()
                .stream()
                .map(MemberResult::from)
                .toList();
    }

    public MemberResult findById(Long id) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new RestApiException(MemberErrorStatus.NOT_FOUND));

        return MemberResult.from(member);
    }

    public MemberResult findByEmailAndPassword(String email, String password) {
        Member member = findByEmail(email);
        if (!member.getPassword().equals(password)) {
            throw new RestApiException(MemberErrorStatus.INVALID_PASSWORD);
        }
        return MemberResult.from(member);
    }

    private Member findByEmail(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new RestApiException(MemberErrorStatus.NOT_FOUND));
    }

    private Member save(Member entity) {
        if (memberRepository.existsByEmail(entity.getEmail())) {
            throw new RestApiException(MemberErrorStatus.DUPLICATE_EMAIL);
        }
        return memberRepository.save(entity);
    }
}
