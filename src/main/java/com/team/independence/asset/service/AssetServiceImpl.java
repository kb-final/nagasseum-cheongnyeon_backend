package com.team.independence.asset.service;

import com.team.independence.asset.domain.AssetSummary;
import com.team.independence.asset.domain.ConnectedAccount;
import com.team.independence.asset.domain.ConnectedInstitution;
import com.team.independence.asset.dto.AssetAccountQueryItem;
import com.team.independence.asset.dto.AssetLinkRequest;
import com.team.independence.asset.dto.AssetLinkResponse;
import com.team.independence.asset.dto.AssetNetWorthBreakdown;
import com.team.independence.asset.dto.LinkedOrganizationResponse;
import com.team.independence.asset.dto.UnlinkOrganizationResponse;
import com.team.independence.asset.domain.Institution;
import com.team.independence.asset.mapper.AssetAccountMapper;
import com.team.independence.asset.mapper.AssetSummaryMapper;
import com.team.independence.asset.mapper.ConnectedAccountMapper;
import com.team.independence.asset.mapper.ConnectedInstitutionMapper;
import com.team.independence.asset.mapper.InstitutionMapper;
import com.team.independence.asset.mapper.LoanAccountMapper;
import com.team.independence.asset.mapper.ManualAssetMapper;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.common.security.AesEncryptor;
import com.team.independence.external.codef.CodefClient;
import com.team.independence.external.codef.CodefProperties;
import com.team.independence.external.codef.CodefRsaEncryptor;
import com.team.independence.external.codef.CodefTokenManager;
import com.team.independence.external.codef.dto.CodefAccountRequest;
import com.team.independence.external.codef.dto.CodefApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetServiceImpl implements AssetService {

    private static final String LOCK_KEY_PREFIX = "asset:link:lock:";
    private static final long LOCK_TTL_SECONDS = 30L;

    /** STOCK/FUND 계좌 평가금액 인정률. 자산 요약 API(AssetSummaryServiceImpl)와 동일 기준. */
    private static final double INVESTMENT_RECOGNITION_RATE = 0.7;

    private final ConnectedAccountMapper connectedAccountMapper;
    private final ConnectedInstitutionMapper connectedInstitutionMapper;
    private final InstitutionMapper institutionMapper;
    private final AssetAccountMapper assetAccountMapper;
    private final AssetSummaryMapper assetSummaryMapper;
    private final LoanAccountMapper loanAccountMapper;
    private final ManualAssetMapper manualAssetMapper;
    private final CodefClient codefClient;
    private final CodefTokenManager codefTokenManager;
    private final CodefProperties codefProperties;
    private final CodefRsaEncryptor rsaEncryptor;
    private final AesEncryptor aesEncryptor;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    public AssetLinkResponse linkAccount(Long memberId, AssetLinkRequest request) {
        log.debug("linkAccount request: memberId={}, request={}", memberId, request);
        String lockKey = LOCK_KEY_PREFIX + memberId;
        boolean locked = Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL_SECONDS, TimeUnit.SECONDS)
        );
        if (!locked) {
            throw new BusinessException(ErrorCode.ASSET_SYNC_IN_PROGRESS);
        }

        try {
            String accessToken = codefTokenManager.getAccessToken();
            String encryptedPassword = rsaEncryptor.encrypt(codefProperties.getPublicKey(), request.getPassword());
            String birthDate = request.getBirthDate();

            String clientType = request.getClientType() != null ? request.getClientType()
                    : "ST".equals(request.getBusinessType()) ? "A" : "P";

            CodefAccountRequest.CodefAccountItem item = CodefAccountRequest.CodefAccountItem.builder()
                    .countryCode(request.getCountryCode() != null ? request.getCountryCode() : "KR")
                    .businessType(request.getBusinessType())
                    .clientType(clientType)
                    .organization(request.getOrganization())
                    .loginType(request.getLoginType())
                    .id(request.getId())
                    .password(encryptedPassword)
                    .birthDate(birthDate)
                    .loginTypeLevel(request.getLoginTypeLevel())
                    .clientTypeLevel(request.getClientTypeLevel())
                    .cardNo(request.getCardNo())
                    .cardPassword(request.getCardPassword())
                    .build();

            ConnectedAccount existing = connectedAccountMapper.findByMemberId(memberId);

            if (existing == null) {
                return create(memberId, accessToken, item, request);
            } else {
                return add(existing, accessToken, item, request);
            }
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    private AssetLinkResponse create(Long memberId, String accessToken,
                                     CodefAccountRequest.CodefAccountItem item,
                                     AssetLinkRequest request) {
        CodefApiResponse response = codefClient.createAccount(accessToken, item);
        String connectedId = response.getData().getConnectedId();

        ConnectedAccount account = ConnectedAccount.builder()
                .memberId(memberId)
                .connectedId(aesEncryptor.encrypt(connectedId))
                .birthDate(request.getBirthDate())
                .build();
        connectedAccountMapper.insert(account);

        saveInstitution(account.getId(), request);

        return AssetLinkResponse.builder()
                .connectedId(connectedId)
                .organization(request.getOrganization())
                .action("CREATED")
                .build();
    }

    private AssetLinkResponse add(ConnectedAccount existing, String accessToken,
                                  CodefAccountRequest.CodefAccountItem item,
                                  AssetLinkRequest request) {
        String connectedId = aesEncryptor.decrypt(existing.getConnectedId());
        codefClient.addAccount(accessToken, connectedId, item);

        saveInstitution(existing.getId(), request);

        return AssetLinkResponse.builder()
                .connectedId(connectedId)
                .organization(request.getOrganization())
                .action("ADDED")
                .build();
    }

    private void saveInstitution(Long connectedAccountId, AssetLinkRequest request) {
        ConnectedInstitution institution = ConnectedInstitution.builder()
                .connectedAccountId(connectedAccountId)
                .institutionCode(request.getOrganization())
                .build();
        connectedInstitutionMapper.insert(institution);
    }

    @Override
    public List<LinkedOrganizationResponse> getConnections(Long memberId) {
        ConnectedAccount account = connectedAccountMapper.findByMemberId(memberId);
        if (account == null) {
            return List.of();
        }
        return connectedInstitutionMapper.findAllWithOrganizationByConnectedAccountId(account.getId());
    }

    @Override
    @Transactional
    public UnlinkOrganizationResponse unlinkOrganization(Long memberId, String organizationCode) {
        ConnectedAccount account = connectedAccountMapper.findByMemberId(memberId);
        if (account == null) {
            throw new BusinessException(ErrorCode.ASSET_ORGANIZATION_NOT_CONNECTED);
        }

        ConnectedInstitution institution = connectedInstitutionMapper
                .findByConnectedAccountIdAndInstitutionCode(account.getId(), organizationCode);
        if (institution == null) {
            throw new BusinessException(ErrorCode.ASSET_ORGANIZATION_NOT_CONNECTED);
        }

        Institution institutionInfo = institutionMapper.findByCode(organizationCode);

        String connectedId = aesEncryptor.decrypt(account.getConnectedId());
        String accessToken = codefTokenManager.getAccessToken();

        CodefAccountRequest.CodefAccountItem item = CodefAccountRequest.CodefAccountItem.builder()
                .countryCode("KR")
                .businessType(institutionInfo.getBusinessType())
                .clientType("P")
                .organization(organizationCode)
                .loginType(institutionInfo.getLoginType())
                .build();

        // CODEF 삭제 성공 후에만 DB 삭제
        codefClient.deleteAccount(accessToken, connectedId, item);

        assetAccountMapper.deleteByConnectedInstitutionId(institution.getId());
        loanAccountMapper.deleteByConnectedInstitutionId(institution.getId());
        connectedInstitutionMapper.deleteByConnectedAccountIdAndInstitutionCode(account.getId(), organizationCode);

        if (connectedInstitutionMapper.countByConnectedAccountId(account.getId()) == 0) {
            connectedAccountMapper.deleteById(account.getId());
        }

        Long totalAssets = assetAccountMapper.sumCurrentValueByMemberId(memberId);
        Long loanBalance = loanAccountMapper.sumLoanBalanceByMemberId(memberId);
        assetSummaryMapper.upsert(AssetSummary.builder()
                .memberId(memberId)
                .totalAssets(totalAssets != null ? totalAssets : 0L)
                .loanBalance(loanBalance != null ? loanBalance : 0L)
                .syncedAt(LocalDateTime.now())
                .build());

        return UnlinkOrganizationResponse.builder()
                .organizationCode(organizationCode)
                .organizationName(institutionInfo.getName())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public void validateConnectedAccountExists(Long memberId) {
        if (connectedAccountMapper.findByMemberId(memberId) == null) {
            throw new BusinessException(ErrorCode.ASSET_NOT_LINKED);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AssetNetWorthBreakdown getNetWorthBreakdown(Long memberId) {
        long interestBearingAssets = assetAccountMapper.sumCurrentValueByMemberIdAndCategories(
                memberId, List.of("DEPOSIT_SAVINGS"));
        long investmentRecognized = investmentRecognizedAmount(memberId);
        long cashAndEtcAssets = assetAccountMapper.sumCurrentValueByMemberIdAndCategories(
                memberId, List.of("CASH", "ETC"));
        long manualAssets = manualAssetMapper.sumAmountByMemberId(memberId);
        long loanBalance = loanAccountMapper.sumLoanBalanceByMemberId(memberId);

        long flatRecognizedAssets = investmentRecognized + cashAndEtcAssets + manualAssets - loanBalance;

        return AssetNetWorthBreakdown.builder()
                .interestBearingAssets(interestBearingAssets)
                .flatRecognizedAssets(flatRecognizedAssets)
                .build();
    }

    /**
     * INVESTMENT 계좌 인정액 합계.
     * STOCK/FUND(평가금액 있는 계좌)는 평가금액×70% + 입금대기금, 그 외(CMA 등)는 current_value 그대로.
     * 자산 요약 API(AssetSummaryServiceImpl.computeBalance)와 동일한 계산 기준을 따른다.
     */
    private long investmentRecognizedAmount(Long memberId) {
        long total = 0L;
        for (AssetAccountQueryItem account : assetAccountMapper.findWithInstitutionByMemberId(memberId)) {
            if (!"INVESTMENT".equals(account.getAssetCategory())) {
                continue;
            }
            String type = account.getAccountType();
            if (("STOCK".equals(type) || "FUND".equals(type)) && account.getValuationAmount() != null) {
                long deposit = account.getDepositReceived() != null ? account.getDepositReceived() : 0L;
                total += Math.round(account.getValuationAmount() * INVESTMENT_RECOGNITION_RATE) + deposit;
            } else {
                total += account.getCurrentValue() != null ? account.getCurrentValue() : 0L;
            }
        }
        return total;
    }
}
