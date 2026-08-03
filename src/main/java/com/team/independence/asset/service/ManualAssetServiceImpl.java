package com.team.independence.asset.service;

import com.team.independence.asset.domain.ManualAsset;
import com.team.independence.asset.dto.ManualAssetRequest;
import com.team.independence.asset.dto.ManualAssetResponse;
import com.team.independence.asset.mapper.ManualAssetMapper;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ManualAssetServiceImpl implements ManualAssetService {

    private final ManualAssetMapper manualAssetMapper;

    @Override
    public List<ManualAssetResponse> getManualAssets(Long memberId) {
        return manualAssetMapper.findAllByMemberId(memberId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ManualAssetResponse createManualAsset(Long memberId, ManualAssetRequest request) {
        ManualAsset manualAsset = ManualAsset.builder()
                .memberId(memberId)
                .assetType(request.getAssetType())
                .amount(request.getAmount())
                .build();
        manualAssetMapper.insert(manualAsset);
        return toResponse(manualAsset);
    }

    @Override
    @Transactional
    public ManualAssetResponse updateManualAsset(Long memberId, Long id, ManualAssetRequest request) {
        ManualAsset manualAsset = findOwnedOrThrow(memberId, id);
        manualAsset.setAmount(request.getAmount());
        manualAssetMapper.update(manualAsset);
        return toResponse(manualAsset);
    }

    @Override
    @Transactional
    public void deleteManualAsset(Long memberId, Long id) {
        findOwnedOrThrow(memberId, id);
        manualAssetMapper.delete(id);
    }

    private ManualAsset findOwnedOrThrow(Long memberId, Long id) {
        ManualAsset manualAsset = manualAssetMapper.findById(id);
        if (manualAsset == null || !manualAsset.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ASSET_MANUAL_NOT_FOUND);
        }
        return manualAsset;
    }

    private ManualAssetResponse toResponse(ManualAsset manualAsset) {
        return ManualAssetResponse.builder()
                .id(manualAsset.getId())
                .assetType(manualAsset.getAssetType())
                .amount(manualAsset.getAmount())
                .createdAt(manualAsset.getCreatedAt())
                .updatedAt(manualAsset.getUpdatedAt())
                .build();
    }
}
