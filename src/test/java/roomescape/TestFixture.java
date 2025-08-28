package roomescape;

import lombok.Getter;
import roomescape.service.dto.command.MemberCreateCommand;
import roomescape.service.dto.command.ThemeCreateCommand;
import roomescape.service.dto.command.TimeSlotCreateCommand;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class TestFixture {
    public static final LocalDate DEFAULT_DATE = LocalDate.now().plusDays(1);

    @Getter
    private static final List<ThemeCreateCommand> themeCreateCommands = initThemes();
    @Getter
    private static final List<TimeSlotCreateCommand> timeSlotCreateCommands = initTimeSlotCommands();
    @Getter
    private static final List<MemberCreateCommand> memberCreateCommand = initMemberCommands();

    private static List<MemberCreateCommand> initMemberCommands() {
        List<MemberCreateCommand> memberCreateCommands = new ArrayList<>();

        memberCreateCommands.add(MemberCreateCommand.builder()
                .name("첫번째 사용자")
                .password("1111")
                .email("test1@email.com")
                .build());

        memberCreateCommands.add(MemberCreateCommand.builder()
                .name("두번째 사용자")
                .password("2222")
                .email("test2@email.com")
                .build());

        return memberCreateCommands;
    }


    private static List<ThemeCreateCommand> initThemes() {
        List<ThemeCreateCommand> themes = new ArrayList<>();

        themes.add(ThemeCreateCommand.builder()
                .name("축배의 방")
                .description("테스트용")
                .thumbnail("url")
                .build());
        themes.add(ThemeCreateCommand.builder()
                .name("저주의 방")
                .description("테스트용")
                .thumbnail("url")
                .build());
        themes.add(ThemeCreateCommand.builder()
                .name("승리의 방")
                .description("테스트용")
                .thumbnail("url")
                .build());
        return themes;
    }

    private static List<TimeSlotCreateCommand> initTimeSlotCommands() {
        List<TimeSlotCreateCommand> reservationTimes = new ArrayList<>();

        reservationTimes.add(TimeSlotCreateCommand.builder()
                .startAt(LocalTime.of(10, 0))
                .build());
        reservationTimes.add(TimeSlotCreateCommand.builder()
                .startAt(LocalTime.of(13, 0))
                .build());
        reservationTimes.add(TimeSlotCreateCommand.builder()
                .startAt(LocalTime.of(16, 0))
                .build());
        return reservationTimes;
    }
}
