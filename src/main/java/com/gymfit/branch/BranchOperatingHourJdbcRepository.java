package com.gymfit.branch;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class BranchOperatingHourJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<BranchOperatingHour> findAllByBranchId(
            Long branchId
    ) {
        return jdbcTemplate.query(
                """
                SELECT
                    id,
                    branch_id,
                    day_of_week,
                    open_time,
                    close_time
                FROM branch_operating_hour
                WHERE branch_id = ?
                ORDER BY day_of_week
                """,
                (rs, rowNum) ->
                        BranchOperatingHour.builder()
                                .id(rs.getLong("id"))
                                .branchId(
                                        rs.getLong("branch_id")
                                )
                                .dayOfWeek(
                                        rs.getInt("day_of_week")
                                )
                                .openTime(
                                        rs.getTime("open_time")
                                                .toLocalTime()
                                )
                                .closeTime(
                                        rs.getTime("close_time")
                                                .toLocalTime()
                                )
                                .build(),
                branchId
        );
    }

    public void replaceAll(
            Long branchId,
            List<OperatingHourValue> hours
    ) {
        jdbcTemplate.update(
                """
                DELETE FROM branch_operating_hour
                WHERE branch_id = ?
                """,
                branchId
        );

        for (OperatingHourValue hour : hours) {
            LocalTime openTime =
                    hour.openTime()
                            .truncatedTo(
                                    ChronoUnit.SECONDS
                            );

            LocalTime closeTime =
                    hour.closeTime()
                            .truncatedTo(
                                    ChronoUnit.SECONDS
                            );

            jdbcTemplate.update(
                    """
                    INSERT INTO branch_operating_hour (
                        branch_id,
                        day_of_week,
                        open_time,
                        close_time
                    )
                    VALUES (?, ?, ?, ?)
                    """,
                    branchId,
                    hour.dayOfWeek(),
                    Time.valueOf(openTime),
                    Time.valueOf(closeTime)
            );
        }
    }

    public record OperatingHourValue(
            Integer dayOfWeek,
            LocalTime openTime,
            LocalTime closeTime
    ) {
    }
}