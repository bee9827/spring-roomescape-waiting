package roomescape.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import roomescape.TestDataInitializer;
import roomescape.common.exception.RestApiException;
import roomescape.common.exception.status.ThemeErrorStatus;
import roomescape.domain.Reservation;
import roomescape.domain.Theme;
import roomescape.repository.MemberRepository;
import roomescape.repository.ReservationRepository;
import roomescape.repository.ThemeRepository;
import roomescape.repository.TimeSlotRepository;
import roomescape.service.dto.command.ThemeCreateCommand;
import roomescape.service.dto.result.PopularThemeResult;
import roomescape.service.dto.result.ThemeResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ThemeServiceTest {
    @Autowired
    private ThemeService themeService;

    @Autowired
    private ThemeRepository themeRepository;

    @Autowired
    private TestDataInitializer testDataInitializer;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private TimeSlotRepository timeSlotRepository;
    @Autowired
    private MemberRepository memberRepository;

    @BeforeEach
    public void setUp() {
        testDataInitializer.setUp();
    }

    @Test
    @DisplayName("findAll: 모든 테마 조회에 성공한다.")
    void findAll() {
        List<ThemeResult> themeResults = themeService.findAll();
        List<String> expectedToString = themeRepository.findAll()
                .stream()
                .map(ThemeResult::from)
                .map(Record::toString)
                .toList();

        for (int i = 0; i < themeResults.size(); i++) {
            ThemeResult themeResult = themeResults.get(i);
            assertThat(themeResult.toString()).isEqualTo(expectedToString.get(i));
        }
    }

    @Test
    void findById() {
    }

    @Test
    @DisplayName("save: 저장에 성공한다.")
    void save() {
        ThemeCreateCommand themeCreateCommand = ThemeCreateCommand.builder()
                .name("저장 테스트 테마")
                .description("저장에 성공한다.")
                .thumbnail("url.com")
                .build();

        ThemeResult themeResult = themeService.save(themeCreateCommand);

        assertThat(themeRepository.existsById(themeResult.id())).isTrue();
    }

    @Test
    @DisplayName("getPopularThemes: 예약이 많이된 순서로 테마를 출력한다.(예약이 한번도 안된 테마는 출력하지 않는다.)")
    void getPopularThemes() {
        List<PopularThemeResult> popularThemes = themeService.getPopularThemes();

        Map<Theme, Long> reservedTheme = reservationRepository.findAll()
                .stream()
                .collect(Collectors.groupingBy(
                        Reservation::getTheme,
                        Collectors.counting()
                ));

        List<PopularThemeResult> expectedPopularThemeResults = new ArrayList<>();
        reservedTheme.forEach(
                (theme, count) ->
                        expectedPopularThemeResults.add(PopularThemeResult.of(theme, count))
        );
        expectedPopularThemeResults.sort(
                Comparator.comparingLong(PopularThemeResult::count)
                        .reversed()
                        .thenComparing(popularThemeResult -> popularThemeResult.themeResult().id())
        );


        for (int i = 0; i < popularThemes.size(); i++) {
            assertThat(popularThemes.get(i).toString()).isEqualTo(expectedPopularThemeResults.get(i).toString());
        }
    }


    @Nested
    @DisplayName("deleteById: ")
    class DeleteById {

        @Test
        @DisplayName("삭제에 성공한다.")
        void deleteById() {
            Theme theme = Theme.builder()
                    .name("삭제용 테마")
                    .thumbnail("url.com")
                    .description("삭제될 예정입니다.")
                    .build();

            themeRepository.save(theme);

            themeService.deleteById(theme.getId());
            assertThat(themeRepository.existsById(theme.getId())).isFalse();
        }

        @Test
        @DisplayName("예약이 존재한다면 예외를 던진다.")
        void reservationExistException() {
            Reservation reservation = reservationRepository.findAll().getFirst();

            assertThatThrownBy(() -> themeService.deleteById(reservation.getTheme().getId()))
                    .isInstanceOf(RestApiException.class)
                    .hasMessage(ThemeErrorStatus.RESERVATION_EXIST.getMessage());
        }
    }
}