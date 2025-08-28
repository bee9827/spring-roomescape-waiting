package roomescape.domain;

import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.FutureOrPresent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import roomescape.common.exception.RestApiException;
import roomescape.common.exception.status.ReservationErrorStatus;


@Entity
@Table(
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "RESERVATION_DUPLICATE",
                        columnNames = {
                                "theme_id",
                                "date",
                                "time_slot_id"
                        })
        }
)
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) //쓰레드 세이프 하지 않다.
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "memeber_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theme_id")
    private Theme theme;

    @FutureOrPresent
    @Column(name = "date")
    private LocalDate date;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_slot_id")
    private TimeSlot timeSlot;

    @OneToMany(mappedBy = "reservation", fetch = FetchType.LAZY)
    private List<WaitingReservation> waitingReservations;

    private ReservationStatus reservationStatus;

    @Builder
    public Reservation(Member member, LocalDate date, TimeSlot timeSlot, Theme theme) {
        validatePast(date, timeSlot.getStartAt());

        this.date = date;
        this.timeSlot = timeSlot;
        this.theme = theme;
        this.member = member;
        reservationStatus = ReservationStatus.BOOKED;
    }

    private void validatePast(LocalDate date, LocalTime time) throws RestApiException {
        if (date == null || time == null) {
            throw new RestApiException(ReservationErrorStatus.INVALID_DATE_TIME);
        }
        LocalDateTime dateTime = LocalDateTime.of(date, time);
        if (LocalDateTime.now().isAfter(dateTime)) {
            throw new RestApiException(ReservationErrorStatus.INVALID_DATE_TIME);
        }
    }

    public void waiting(Member member) {
        if (waitingReservations == null) {
            waitingReservations = new ArrayList<>();
        }
        waitingReservations.add(new WaitingReservation(member, this));
    }

    public void cancelBooked() {
        if (waitingReservations == null || waitingReservations.isEmpty()) {
            return;
        }
        WaitingReservation waitingReservation = getFirstWaiting();
        promoteWaiting(waitingReservation);
    }

    public void cancelWaiting(WaitingReservation waitingReservation) {
        validateWaitingReservationExists(waitingReservation);
        waitingReservations.remove(waitingReservation);
    }

    private WaitingReservation getFirstWaiting() {
        return waitingReservations.stream()
                .findFirst()
                .orElse(null);
    }

    private void promoteWaiting(WaitingReservation waitingReservation) {
        validateWaitingReservationExists(waitingReservation);
        this.member = waitingReservation.getMember();

        cancelWaiting(waitingReservation);
    }

    private void validateWaitingReservationExists(WaitingReservation waitingReservation) {
        if (!this.waitingReservations.contains(waitingReservation)) {
            throw new IllegalStateException("잘못된 호출입니다. waitingReservation이 존재하지 않습니다.");
        }
    }
}


