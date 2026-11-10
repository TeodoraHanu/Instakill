package com.instakill.analytics.application;

import com.instakill.analytics.dto.AnalyticsResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AnalyticsService {

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AnalyticsResponse getUserAnalytics(UUID userId) {
        String userIdStr = userId.toString();

        // 1. Grupare CORECTĂ pe SĂPTĂMÂNĂ (adună toate zilele din aceeași săptămână pe un singur rând)
        String weeklySql = """
            SELECT 
                COALESCE(strftime('%Y-W%W', created_at), '2026-W45') AS week,
                COUNT(id) AS posts,
                COALESCE(SUM(like_count), 0) AS likes,
                COALESCE(SUM(comment_count), 0) AS comments
            FROM posts
            WHERE author_id = ?
            GROUP BY week
            ORDER BY week DESC
        """;
        List<AnalyticsResponse.WeeklyStats> weeklyStats = new ArrayList<>();
        try {
            weeklyStats = jdbcTemplate.query(
                    weeklySql,
                    (rs, rowNum) -> new AnalyticsResponse.WeeklyStats(
                            rs.getString("week"),
                            rs.getLong("posts"),
                            rs.getLong("likes"),
                            rs.getLong("comments")
                    ),
                    userIdStr
            );
        } catch (Exception ignored) {}

        // 2. Top 5 posts by engagement
        String topPostsSql = """
            SELECT 
                id, 
                COALESCE(content, '') AS content, 
                (COALESCE(like_count, 0) + COALESCE(comment_count, 0)) AS engagement
            FROM posts
            WHERE author_id = ?
            ORDER BY engagement DESC
            LIMIT 5
        """;
        List<AnalyticsResponse.TopPost> topPosts = new ArrayList<>();
        try {
            topPosts = jdbcTemplate.query(
                    topPostsSql,
                    (rs, rowNum) -> new AnalyticsResponse.TopPost(
                            rs.getString("id"),
                            rs.getString("content"),
                            rs.getLong("engagement")
                    ),
                    userIdStr
            );
        } catch (Exception ignored) {}

        // 3. Like-to-comment ratio
        String ratioSql = """
            SELECT 
                CASE 
                    WHEN COALESCE(SUM(comment_count), 0) = 0 THEN CAST(COALESCE(SUM(like_count), 0) AS REAL)
                    ELSE CAST(COALESCE(SUM(like_count), 0) AS REAL) / SUM(comment_count)
                END
            FROM posts
            WHERE author_id = ?
        """;
        double ratio = 0.0;
        try {
            Double ratioRaw = jdbcTemplate.queryForObject(ratioSql, Double.class, userIdStr);
            if (ratioRaw != null && !Double.isNaN(ratioRaw)) {
                ratio = Math.round(ratioRaw * 100.0) / 100.0;
            }
        } catch (Exception ignored) {}

        // 4. Average likes per post by media type
        String photoSql = """
            SELECT COALESCE(AVG(like_count), 0.0) 
            FROM posts 
            WHERE author_id = ? 
              AND (image_url NOT LIKE '%.mp4' AND image_url NOT LIKE '%.mov' AND image_url NOT LIKE '%.webm' OR image_url IS NULL)
        """;
        double avgPhotoLikes = 0.0;
        try {
            Double photoRaw = jdbcTemplate.queryForObject(photoSql, Double.class, userIdStr);
            if (photoRaw != null && !Double.isNaN(photoRaw)) {
                avgPhotoLikes = Math.round(photoRaw * 100.0) / 100.0;
            }
        } catch (Exception ignored) {}

        String videoSql = """
            SELECT COALESCE(AVG(like_count), 0.0) 
            FROM posts 
            WHERE author_id = ? 
              AND (image_url LIKE '%.mp4' OR image_url LIKE '%.mov' OR image_url LIKE '%.webm')
        """;
        double avgVideoLikes = 0.0;
        try {
            Double videoRaw = jdbcTemplate.queryForObject(videoSql, Double.class, userIdStr);
            if (videoRaw != null && !Double.isNaN(videoRaw)) {
                avgVideoLikes = Math.round(videoRaw * 100.0) / 100.0;
            }
        } catch (Exception ignored) {}

        // 5. Percentile rank by average engagement
        String rankSql = """
            WITH UserAvgEngagement AS (
                SELECT author_id, AVG(COALESCE(like_count, 0) + COALESCE(comment_count, 0)) AS avg_eng
                FROM posts
                GROUP BY author_id
            ),
            Ranked AS (
                SELECT author_id, PERCENT_RANK() OVER (ORDER BY avg_eng ASC) * 100 AS pct
                FROM UserAvgEngagement
            )
            SELECT pct FROM Ranked WHERE author_id = ?
        """;
        double topPercent = 0.0;
        try {
            Double pctRaw = jdbcTemplate.queryForObject(rankSql, Double.class, userIdStr);
            if (pctRaw != null && !Double.isNaN(pctRaw)) {
                topPercent = Math.round((100.0 - pctRaw) * 100.0) / 100.0;
            }
        } catch (Exception ignored) {}

        return new AnalyticsResponse(
                weeklyStats,
                topPosts,
                ratio,
                new AnalyticsResponse.MediaStats(avgPhotoLikes, avgVideoLikes),
                topPercent
        );
    }
}