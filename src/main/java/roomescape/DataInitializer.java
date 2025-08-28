package roomescape;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import roomescape.controller.dto.ThemeResponse;
import roomescape.controller.dto.response.TimeSlotResponse;
import roomescape.domain.Member;
import roomescape.domain.Role;
import roomescape.repository.MemberRepository;
import roomescape.service.MemberService;
import roomescape.service.ReservationService;
import roomescape.service.ThemeService;
import roomescape.service.TimeSlotService;
import roomescape.service.dto.command.MemberCreateCommand;
import roomescape.service.dto.command.ReservationCreateCommand;
import roomescape.service.dto.command.ThemeCreateCommand;
import roomescape.service.dto.command.TimeSlotCreateCommand;
import roomescape.service.dto.result.MemberResult;

import java.time.LocalDate;
import java.time.LocalTime;


@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {
    private final ReservationService reservationService;
    private final TimeSlotService timeSlotService;
    private final ThemeService themeService;
    private final MemberService memberService;
    private final MemberRepository memberRepository;

    @Override
    public void run(ApplicationArguments args) throws Exception {


        MemberCreateCommand member = MemberCreateCommand.builder()
                .name("용성")
                .email("ehfrhfo9494@naver.com")
                .password("1007")
                .build();
        MemberCreateCommand member2 = MemberCreateCommand.builder()
                .name("test")
                .email("test@email.com")
                .password("test")
                .build();

        Member admin = Member.builder()
                .name("admin")
                .email("admin@naver.com")
                .password("1007")
                .role(Role.ADMIN)
                .build();

        memberRepository.save(admin);

        TimeSlotCreateCommand time = TimeSlotCreateCommand.builder()
                .startAt(LocalTime.of(10, 0))
                .build();

        TimeSlotCreateCommand time2 = TimeSlotCreateCommand.builder()
                .startAt(LocalTime.of(13, 0))
                .build();

        TimeSlotCreateCommand time3 = TimeSlotCreateCommand.builder()
                .startAt(LocalTime.of(16, 0))
                .build();

        ThemeCreateCommand theme = ThemeCreateCommand.builder()
                .name("The Haunted Mansion")
                .description("Solve the mysteries of the haunted mansion to escape.")
                .thumbnail("https://i.pinimg.com/236x/6e/bc/46/6ebc461a94a49f9ea3b8bbe2204145d4.jpg")
                .build();

        ThemeCreateCommand theme2 = ThemeCreateCommand.builder()
                .name("Secret Agent Mission")
                .description("Complete your secret agent mission before time runs out.")
                .thumbnail("https://i.pinimg.com/236x/6e/bc/46/6ebc461a94a49f9ea3b8bbe2204145d4.jpg")
                .build();

        ThemeCreateCommand theme3 = ThemeCreateCommand.builder()
                .name("Pirate''s Treasure")
                .description("Find the hidden pirate''s treasure in this thrilling adventure.")
                .thumbnail("https://i.pinimg.com/236x/6e/bc/46/6ebc461a94a49f9ea3b8bbe2204145d4.jpg")
                .build();

        MemberResult savedMember = memberService.save(member);
        MemberResult savedMember2 = memberService.save(member2);

        TimeSlotResponse savedTimeSlot = TimeSlotResponse.from(timeSlotService.save(time));
        TimeSlotResponse savedTimeSlot2 = TimeSlotResponse.from(timeSlotService.save(time2));
        TimeSlotResponse savedTimeSlot3 = TimeSlotResponse.from(timeSlotService.save(time3));
        ThemeResponse savedTheme = ThemeResponse.from(themeService.save(theme));
        ThemeResponse savedTheme2 = ThemeResponse.from(themeService.save(theme2));
        ThemeResponse savedTheme3 = ThemeResponse.from(themeService.save(theme3));

        ReservationCreateCommand reservation = ReservationCreateCommand.builder()
                .memberId(savedMember.id())
                .themeId(savedTheme.id())
                .date(LocalDate.now().plusDays(1))
                .timeSlotId(savedTimeSlot.id())
                .build();

        ReservationCreateCommand reservation2 = ReservationCreateCommand.builder()
                .memberId(savedMember.id())
                .themeId(savedTheme2.id())
                .date(LocalDate.now().plusDays(1))
                .timeSlotId(savedTimeSlot2.id())
                .build();

        ReservationCreateCommand reservation3 = ReservationCreateCommand.builder()
                .memberId(savedMember.id())
                .themeId(savedTheme3.id())
                .date(LocalDate.now().plusDays(1))
                .timeSlotId(savedTimeSlot3.id())
                .build();

        ReservationCreateCommand waitingReservation = ReservationCreateCommand.builder()
                .memberId(savedMember2.id())
                .themeId(savedTheme3.id())
                .date(LocalDate.now().plusDays(1))
                .timeSlotId(savedTimeSlot3.id())
                .build();

        reservationService.save(reservation);
        reservationService.save(reservation2);
        reservationService.save(reservation3);
        reservationService.save(waitingReservation);
    }
}
