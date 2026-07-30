package com.team.independence.property.service;

import com.team.independence.property.domain.HousingType;
import com.team.independence.property.domain.RentSyncLog;
import com.team.independence.property.domain.RentTransaction;
import com.team.independence.property.mapper.RentSyncLogMapper;
import com.team.independence.property.mapper.RentTransactionMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RentTransactionUnitSyncServiceImpl implements RentTransactionUnitSyncService {

    private final RentTransactionMapper rentTransactionMapper;
    private final RentSyncLogMapper rentSyncLogMapper;

    @Override
    @Transactional
    public void sync(String regionCode, String dealYm, HousingType housingType,
                     List<RentTransaction> items) {

        int deleted = rentTransactionMapper.deleteByUnit(regionCode, dealYm, housingType);

        // MySQL은 빈 VALUES 목록을 문법 오류로 처리한다. 거래가 없는 구간은 삭제만 하고 끝낸다.
        if (!items.isEmpty()) {
            rentTransactionMapper.insertBatch(items);
        }

        // 이력도 같은 트랜잭션에 둔다. 적재가 롤백됐는데 성공 이력만 남으면
        // 다음 실행에서 이 유닛을 건너뛰어 빈 구간이 영구히 방치된다.
        rentSyncLogMapper.upsert(RentSyncLog.builder()
            .regionCode(regionCode)
            .dealYm(dealYm)
            .housingType(housingType)
            .isSuccess(true)
            .insertedCnt(items.size())
            .build());

        log.debug("[국토부] 재적재 완료 - regionCode={}, dealYm={}, housingType={}, deleted={}, inserted={}",
            regionCode, dealYm, housingType, deleted, items.size());
    }
}
