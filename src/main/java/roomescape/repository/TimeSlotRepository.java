package roomescape.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import roomescape.domain.TimeSlot;
import roomescape.service.dto.result.AvailableTimeSlotResult;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {
    TimeSlot findByStartAt(LocalTime startAt);

    boolean existsByStartAt(LocalTime time);

    @Query("""
            SELECT new roomescape.service.dto.result.AvailableTimeSlotResult(
                    ts,
                    EXISTS(
                        SELECT 1
                        FROM Reservation r
                        WHERE r.timeSlot = ts
                          AND r.theme.id = :themeId
                          AND r.date = :date
                    )
            )
            FROM TimeSlot ts
            ORDER BY ts.startAt
            """)
    List<AvailableTimeSlotResult> findAvailable(Long themeId, LocalDate date);
}
