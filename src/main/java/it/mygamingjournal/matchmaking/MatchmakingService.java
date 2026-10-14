package it.mygamingjournal.matchmaking;

import it.mygamingjournal.common.paging.PageParams;
import it.mygamingjournal.common.paging.PageResponse;
import it.mygamingjournal.user.UserSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Plain SQL: Postgres array membership (= any) reads far better here than JPQL. */
@Service
@RequiredArgsConstructor
public class MatchmakingService {

    private static final String FROM_WHERE = """
            from play_availability a
            join game_entries e on e.id = a.entry_id
            join users u on u.id = e.user_id
            where e.rawg_game_id = :gameId
              and u.id <> :me
              and (a.available_until is null or a.available_until >= :today)
              and (cast(:platform as text) is null or cast(:platform as text) = any(a.platforms))
              and (cast(:language as text) is null or cast(:language as text) = any(a.languages))
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<AvailablePlayerResponse> findAvailablePlayers(long me, long rawgGameId, String platform,
                                                                      String language, PageParams paging) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("gameId", rawgGameId)
                .addValue("me", me)
                .addValue("today", LocalDate.now(clock))
                .addValue("platform", platform, Types.VARCHAR)
                .addValue("language", language, Types.VARCHAR)
                .addValue("limit", paging.pageSize())
                .addValue("offset", paging.offset());

        Long total = jdbc.queryForObject("select count(*) " + FROM_WHERE, params, Long.class);
        List<AvailablePlayerResponse> items = jdbc.query("""
                select u.id, u.username, u.display_name, u.avatar_url, u.level,
                       a.platforms, a.languages, a.available_until
                """ + FROM_WHERE + " order by lower(u.username) limit :limit offset :offset", params,
                (rs, rowNum) -> new AvailablePlayerResponse(
                        new UserSummary(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"),
                                rs.getString("avatar_url"), rs.getInt("level")),
                        toList(rs.getArray("platforms")),
                        toList(rs.getArray("languages")),
                        rs.getObject("available_until", LocalDate.class)));
        return new PageResponse<>(items, paging.page(), paging.pageSize(), total == null ? 0 : total);
    }

    private static List<String> toList(Array array) throws SQLException {
        return List.of((String[]) array.getArray());
    }
}
