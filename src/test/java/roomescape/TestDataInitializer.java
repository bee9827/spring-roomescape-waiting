package roomescape;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import roomescape.domain.Member;
import roomescape.domain.Reservation;
import roomescape.domain.Theme;
import roomescape.domain.TimeSlot;
import roomescape.repository.MemberRepository;
import roomescape.repository.ReservationRepository;
import roomescape.repository.ThemeRepository;
import roomescape.repository.TimeSlotRepository;
import roomescape.service.dto.command.MemberCreateCommand;
import roomescape.service.dto.command.ThemeCreateCommand;
import roomescape.service.dto.command.TimeSlotCreateCommand;

import java.util.ArrayList;
import java.util.List;

import static roomescape.TestFixture.DEFAULT_DATE;

@Component
public class TestDataInitializer {
    @Autowired
    private DatabaseCleaner databaseCleaner;
    @Autowired
    private TimeSlotRepository timeSlotRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private ThemeRepository themeRepository;
    @Autowired
    private MemberRepository memberRepository;

    public void setUp() {
        databaseCleaner.clean();
        List<Theme> themes = TestFixture.getThemeCreateCommands()
                .stream()
                .map(ThemeCreateCommand::toEntity)
                .toList();
        List<TimeSlot> timeSlots = TestFixture.getTimeSlotCreateCommands()
                .stream()
                .map(TimeSlotCreateCommand::toEntity)
                .toList();
        timeSlotRepository.saveAll(timeSlots);
        themeRepository.saveAll(themes);


        List<Member> member = TestFixture.getMemberCreateCommand()
                .stream()
                .map(MemberCreateCommand::toEntity)
                .toList();
        memberRepository.saveAll(member);


        List<Reservation> reservations = initReservations(member.getFirst(), themes.getFirst(), timeSlots);
        reservationRepository.saveAll(reservations);
    }

    private List<Reservation> initReservations(Member member, Theme theme, List<TimeSlot> timeSlots) {
        List<Reservation> reservations = new ArrayList<>();

        for (TimeSlot timeSlot : timeSlots) {
            reservations.add(Reservation.builder()
                    .date(DEFAULT_DATE)
                    .member(member)
                    .theme(theme)
                    .timeSlot(timeSlot)
                    .build());
        }
        return reservations;
    }
}
