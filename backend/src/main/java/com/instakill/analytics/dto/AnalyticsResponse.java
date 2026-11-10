package com.instakill.analytics.dto;

import java.util.List;

public record AnalyticsResponse(
        List<WeeklyStats> weeklyStats,
        List<TopPost> topPosts,
        double likeToCommentRatio,
        MediaStats mediaStats,
        double percentileRank
) {
    public record WeeklyStats(String week, long posts, long likes, long comments) {}
    public record TopPost(String id, String content, long engagement) {}
    public record MediaStats(double avgPhotoLikes, double avgVideoLikes) {}
}