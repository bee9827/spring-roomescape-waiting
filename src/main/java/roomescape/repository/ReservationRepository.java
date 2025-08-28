package roomescape.repository;

import jakarta.validation.constraints.FutureOrPresent;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import roomescape.domain.Member;
import roomescape.domain.Reservation;
import roomescape.domain.Theme;
import roomescape.domain.TimeSlot;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    boolean existsByThemeId(Long id);

    boolean existsByTimeSlotId(Long id);

    boolean existsByDateAndThemeAndTimeSlot(LocalDate date, Theme theme, TimeSlot timeSlot);

    public Reservation findByThemeAndTimeSlotAndDate(Theme theme, TimeSlot timeSlot, LocalDate date);

    @Query("""
            SELECT r FROM Reservation r
            JOIN FETCH r.timeSlot
            JOIN FETCH r.theme
            JOIN FETCH r.date
            JOIN FETCH r.waitingReservations
            """)
    List<Reservation> findAll();

    @Query("""
            SELECT EXISTS(
                SELECT 1 FROM Reservation r
                WHERE r.theme = :theme
                AND r.date = :date
                AND r.timeSlot = :timeSlot
            )
            """)
    boolean isDuplicated(Theme theme, LocalDate date, TimeSlot timeSlot);

    @Query("""
                SELECT r FROM Reservation r
                JOIN FETCH r.theme
                JOIN FETCH r.timeSlot
                JOIN FETCH r.member
                JOIN FETCH r.waitingReservations
                WHERE (:memberId IS NULL OR r.member.id = :memberId)
                  AND (:themeId IS NULL OR r.theme.id = :themeId)
                  AND (
                    (:dateFrom IS NULL OR r.date >= :dateFrom)
                    AND (:dateTo IS NULL OR r.date <= :dateTo)
                  )
            """)
    List<Reservation> filter(Long memberId, Long themeId, LocalDate dateFrom, LocalDate dateTo);

}