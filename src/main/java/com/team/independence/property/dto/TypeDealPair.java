package com.team.independence.property.dto;

import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;

/**
 * (주거유형, 거래유형) 쌍. PriceModel 배치 쿼리에서 (housing_type, deal_type) IN 절 대용으로 쓴다.
 * MyBatis foreach가 필드 접근으로 파라미터를 뽑을 수 있도록 record로 둔다.
 */
public record TypeDealPair(HousingType housingType, DealType dealType) {}
