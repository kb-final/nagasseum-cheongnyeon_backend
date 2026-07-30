package com.team.independence.property.service;

import com.team.independence.property.domain.HousingType;
import com.team.independence.property.domain.RentTransaction;
import java.util.List;

/**
 * (지역, 연월, 주택유형) 유닛 하나를 전량 재적재하는 트랜잭션 경계.
 *
 * <p>국토부 API 응답은 해당 구간의 완전한 정답지이므로, 구간을 통째로 비우고 다시 채운다.
 * 행 단위 식별자(일련번호·호수·신고시각)가 없어 신규/정정을 구분할 수 없기 때문이다.
 *
 * <p>수집(HTTP)을 담당하는 {@link RentTransactionSyncService}와 반드시 다른 빈이어야 한다.
 * 같은 클래스 안에서 호출하면 Spring 프록시를 타지 않아 {@code @Transactional}이 무시되고,
 * DELETE만 커밋된 뒤 INSERT가 실패하면 해당 구간 데이터가 사라진다.
 */
public interface RentTransactionUnitSyncService {

    /**
     * @param items 이미 파싱이 끝난 거래 목록. HTTP 호출은 트랜잭션 밖에서 끝내고 결과만 넘긴다.
     */
    void sync(String regionCode, String dealYm, HousingType housingType, List<RentTransaction> items);
}
