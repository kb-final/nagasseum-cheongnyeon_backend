package com.team.independence.property.dto;

import com.team.independence.property.domain.DealType;
import com.team.independence.property.domain.HousingType;

/**
 * PriceModel 배치 조회의 조합 키. 지역 코드는 배치 호출 인자로 공유되므로 여기에 두지 않는다.
 *
 * <p>Realistic·HoldOut이 40조합을 한 번에 준비할 때 캐시 조회·결과 매핑에 쓴다.
 */
public record PriceModelKey(HousingType housingType, DealType dealType, int areaMin, int areaMax) {}
