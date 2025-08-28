package roomescape.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import roomescape.domain.Theme;
import roomescape.service.dto.result.PopularThemeResult;

import java.util.List;

@Repository
public interface ThemeRepository extends JpaRepository<Theme, Long> {
    @Query("""
            SELECT new roomescape.service.dto.result.PopularThemeResult(t, COUNT(r))
            FROM Theme AS t
            INNER JOIN Reservation AS r
            ON t = r.theme
            GROUP BY t
            ORDER BY COUNT(r) DESC
            """)
    List<PopularThemeResult> findPopularThemes();
}
