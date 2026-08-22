package com.team.independence.compare.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team.independence.compare.mapper.GoalSnapshotMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.team.independence.compare.service.CompareCacheStore;

/**
 * 목표 스냅샷 생성 배치 구현.
 *
 * <p>실제 집계는 SQL 한 방(INSERT ... SELECT)으로 끝낸다. 회원을 자바로 순회하며
 * 한 명씩 INSERT 하면 수천 번 왕복하게 되는데, 그럴 이유가 없는 작업이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoalSnapshotBatchServiceImpl implements GoalSnapshotBatchService {

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    private final GoalSnapshotMapper goalSnapshotMapper;
    private final CompareCacheStore compareCacheStore;

    @Override
    @Transactional
    public int generate(String snapshotYm) {
        // 나이는 "집계 기준일 시점의 만 나이"로 굳힌다. 배치가 하루 늦게 돌더라도
        // 매월 1일 기준으로 계산해야 같은 달 안에서 값이 흔들리지 않는다.
        LocalDate baseDate = LocalDate.parse(snapshotYm + "01", DateTimeFormatter.BASIC_ISO_DATE);

        int affected = goalSnapshotMapper.insertSnapshots(snapshotYm, baseDate);
        log.info("[배치] 목표 스냅샷 생성 완료. snapshotYm={}, 처리 {}건", snapshotYm, affected);
        compareCacheStore.evictAll();
        return affected;
    }

    @Override
    public int generateForCurrentMonth() {
        return generate(LocalDate.now().format(YM));
    }
}