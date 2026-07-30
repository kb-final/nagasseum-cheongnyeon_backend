package com.team.independence.property.service;

import com.team.independence.property.domain.HousingType;

/**
 * 국토부 전월세 실거래 API에서 거래 내역을 수집한다.
 *
 * <p>수집(HTTP)까지만 담당하고 적재는 {@link RentTransactionUnitSyncService}에 넘긴다.
 * 트랜잭션이 외부 API 응답을 기다리며 DB 커넥션을 붙잡지 않게 하기 위한 분리다.
 */
public interface RentTransactionSyncService {

    /**
     * 최근 6개월 × 전체 지역 × 주택유형 4종을 순회하며 동기화한다.
     *
     * <p>최초 수집/증분 수집을 분기하지 않는다. rent_sync_log의 조합별 성공 여부로 판단하므로
     * DB가 비어 있으면 자동으로 6개월 백필이 되고, 중간에 중단되어도 다음 실행에서 이어진다.
     */
    void syncAll();

    /**
     * 유닛 하나를 수집해 적재한다. 이력과 무관하게 무조건 수집하므로 수동 재수집에도 쓸 수 있다.
     *
     * <p>이름 그대로 collect(HTTP, 트랜잭션 밖) 후 sync(적재, 트랜잭션 안)로 넘어간다.
     * 이 메서드에는 {@code @Transactional}을 붙이면 안 된다 — 외부 API 응답을 기다리는 동안
     * DB 커넥션을 점유하게 된다.
     */
    void collectAndSync(String regionCode, String dealYm, HousingType housingType);
}
