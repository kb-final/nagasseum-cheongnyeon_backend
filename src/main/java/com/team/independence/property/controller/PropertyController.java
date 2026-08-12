package com.team.independence.property.controller;

import com.team.independence.common.response.ApiResponse;
import com.team.independence.property.dto.PriceModelRequest;
import com.team.independence.property.dto.PriceModelResponse;
import com.team.independence.property.dto.RentMedianRequest;
import com.team.independence.property.dto.RentMedianResponse;
import com.team.independence.property.service.PriceModelService;
import com.team.independence.property.service.RentMedianService;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
public class PropertyController {

    private final RentMedianService rentMedianService;
    private final PriceModelService priceModelService;

    /**
     * 실거래 매물 중앙값 조회.
     *
     * <p>조건을 @ModelAttribute로 묶어 받으므로 타입 변환 실패·필수값 누락·min&gt;max가
     * 모두 BindException 하나로 모여 컨트롤러 진입 전에 400으로 떨어진다.
     */
    @GetMapping("/median")
    public ApiResponse<RentMedianResponse> getMedian(@Valid @ModelAttribute RentMedianRequest request) {
        return ApiResponse.ok(rentMedianService.getMedian(request));
    }

    @GetMapping("/price-model")
    public ApiResponse<PriceModelResponse> getPriceModel(@Valid @ModelAttribute PriceModelRequest request) {
        return ApiResponse.ok(priceModelService.estimate(request));
    }
}
