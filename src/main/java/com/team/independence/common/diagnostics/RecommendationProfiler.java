package com.team.independence.common.diagnostics;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 추천 30초 타임아웃 진단용 임시 계측기
 */
public final class RecommendationProfiler {

    private static final AtomicLong mcCalls = new AtomicLong();
    private static final AtomicLong mcNanos = new AtomicLong();
    private static final AtomicLong pmDbQueries = new AtomicLong();
    private static final AtomicLong pmDbNanos = new AtomicLong();
    private static final AtomicLong pmCacheHits = new AtomicLong();

    private RecommendationProfiler() {
    }

    public static void reset() {
        mcCalls.set(0);
        mcNanos.set(0);
        pmDbQueries.set(0);
        pmDbNanos.set(0);
        pmCacheHits.set(0);
    }

    public static void recordMc(long nanos) {
        mcCalls.incrementAndGet();
        mcNanos.addAndGet(nanos);
    }

    public static void recordPmDb(long nanos) {
        pmDbQueries.incrementAndGet();
        pmDbNanos.addAndGet(nanos);
    }

    public static void recordPmHit() {
        pmCacheHits.incrementAndGet();
    }

    public static String summary() {
        return String.format(
                "MC호출=%d회 MC누적=%dms | 가격모델DB쿼리=%d회 DB누적=%dms 캐시HIT=%d회",
                mcCalls.get(), mcNanos.get() / 1_000_000,
                pmDbQueries.get(), pmDbNanos.get() / 1_000_000, pmCacheHits.get());
    }
}
