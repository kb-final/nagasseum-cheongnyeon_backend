package com.team.independence.asset.service;

import com.team.independence.asset.domain.ConnectedAccount;
import com.team.independence.asset.domain.ConnectedInstitution;
import com.team.independence.asset.dto.AssetLinkRequest;
import com.team.independence.asset.dto.AssetLinkResponse;
import com.team.independence.asset.dto.LinkedOrganizationResponse;
import com.team.independence.asset.dto.UnlinkOrganizationResponse;
import com.team.independence.asset.domain.Institution;
import com.team.independence.asset.mapper.ConnectedAccountMapper;
import com.team.independence.asset.mapper.ConnectedInstitutionMapper;
import com.team.independence.asset.mapper.InstitutionMapper;
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

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetServiceImpl implements AssetService {

    private static final String LOCK_KEY_PREFIX = "asset:link:lock:";
    private static final long LOCK_TTL_SECONDS = 30L;

    private final ConnectedAccountMapper connectedAccountMapper;
    private final ConnectedInstitutionMapper connectedInstitutionMapper;
    private final InstitutionMapper institutionMapper;
    private final CodefClient codefClient;
    private final CodefTokenManager codefTokenManager;
    private final CodefProperties codefProperties;
    private final CodefRsaEncryptor rsaEncryptor;
    private final AesEncryptor aesEncryptor;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    public AssetLinkResponse linkAccount(Long memberId, AssetLinkRequest request) {
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

            CodefAccountRequest.CodefAccountItem item = CodefAccountRequest.CodefAccountItem.builder()
                    .countryCode(request.getCountryCode() != null ? request.getCountryCode() : "KR")
                    .businessType(request.getBusinessType())
                    .clientType(request.getClientType() != null ? request.getClientType() : "P")
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

        connectedInstitutionMapper.deleteByConnectedAccountIdAndInstitutionCode(account.getId(), organizationCode);

        if (connectedInstitutionMapper.countByConnectedAccountId(account.getId()) == 0) {
            connectedAccountMapper.deleteById(account.getId());
        }

        return UnlinkOrganizationResponse.builder()
                .organizationCode(organizationCode)
                .organizationName(institutionInfo.getName())
                .build();
    }
}
