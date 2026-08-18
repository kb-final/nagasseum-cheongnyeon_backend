package com.team.independence.asset.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.independence.asset.domain.account.AssetAccount;
import com.team.independence.asset.domain.account.CardAccount;
import com.team.independence.asset.domain.codef.ConnectedAccount;
import com.team.independence.asset.domain.codef.ConnectedInstitution;
import com.team.independence.asset.domain.account.LoanAccount;
import com.team.independence.asset.dto.sync.AssetSyncResponse;
import com.team.independence.asset.dto.sync.AssetSyncResponse.InstitutionSyncResult;
import com.team.independence.asset.dto.sync.SyncJobStatusResponse;
import com.team.independence.asset.domain.summary.AssetSummary;
import com.team.independence.asset.mapper.AssetAccountMapper;
import com.team.independence.asset.mapper.AssetSummaryMapper;
import com.team.independence.asset.mapper.CardAccountMapper;
import com.team.independence.asset.mapper.ConnectedAccountMapper;
import com.team.independence.asset.mapper.ConnectedInstitutionMapper;
import com.team.independence.asset.mapper.InstitutionMapper;
import com.team.independence.asset.domain.codef.Institution;
import com.team.independence.asset.mapper.LoanAccountMapper;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.common.security.AesEncryptor;
import com.team.independence.external.codef.CodefClient;
import com.team.independence.external.slack.SlackNotifier;
import com.team.independence.external.codef.CodefTokenManager;
import com.team.independence.external.codef.dto.CodefBankAccountResponse;
import com.team.independence.external.codef.dto.CodefBankAccountResponse.CodefDepositItem;
import com.team.independence.external.codef.dto.CodefBankAccountResponse.CodefLoanItem;
import com.team.independence.external.codef.dto.CodefCardResponse;
import com.team.independence.external.codef.dto.CodefCardResponse.CodefCardItem;
import com.team.independence.external.codef.dto.CodefStockAccountResponse;
import com.team.independence.external.codef.dto.CodefStockAccountResponse.CodefStockAccountItem;
import com.team.independence.external.codef.dto.CodefStockFinancialAssetsResponse;
import com.team.independence.external.codef.dto.CodefStockFinancialAssetsResponse.CodefStockItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetSyncServiceImpl implements AssetSyncService {

    private static final String BUSINESS_TYPE_STOCK = "ST";
    private static final String BUSINESS_TYPE_BANK = "BK";
    private static final String BUSINESS_TYPE_CARD = "CD";

    private static final String DEPOSIT_CODE_DEMAND = "11";
    private static final String DEPOSIT_CODE_SAVINGS = "12";
    private static final String DEPOSIT_CODE_DEPOSIT = "13";

    private static final String PRODUCT_TYPE_STOCK = "01";
    private static final String PRODUCT_TYPE_FUND = "02";
    private static final String PRODUCT_TYPE_DEMAND = "03";

    private static final String CURRENCY_KRW = "KRW";
    private static final String OVERDRAFT_FLAG = "1";

    private final AssetSyncJobStore jobStore;
    private final ConnectedAccountMapper connectedAccountMapper;
    private final ConnectedInstitutionMapper connectedInstitutionMapper;
    private final InstitutionMapper institutionMapper;
    private final AssetAccountMapper assetAccountMapper;
    private final CardAccountMapper cardAccountMapper;
    private final LoanAccountMapper loanAccountMapper;
    private final AssetSummaryMapper assetSummaryMapper;
    private final CodefClient codefClient;
    private final CodefTokenManager codefTokenManager;
    private final AesEncryptor aesEncryptor;
    private final ObjectMapper objectMapper;
    private final SlackNotifier slackNotifier;

    @Override
    public SyncJobStatusResponse getSyncStatus(String jobId) {
        String status = jobStore.getStatus(jobId);
        if (status == null) {
            throw new BusinessException(ErrorCode.ASSET_SYNC_JOB_NOT_FOUND);
        }
        return SyncJobStatusResponse.builder()
                .jobId(jobId)
                .status(status)
                .errorMessage(AssetSyncJobStore.STATUS_FAILED.equals(status) ? jobStore.getError(jobId) : null)
                .resultUrl(AssetSyncJobStore.STATUS_SUCCESS.equals(status) ? jobStore.getResultUrl(jobId) : null)
                .build();
    }

    @Override
    public void syncAll() {
        List<Long> memberIds = connectedAccountMapper.findAllMemberIds();
        log.info("[배치] 자산 동기화 대상 회원 수: {}", memberIds.size());

        List<SyncFailure> failures = new ArrayList<>();
        for (Long memberId : memberIds) {
            try {
                syncAccounts(memberId);
            } catch (Exception e) {
                log.error("[배치] 회원 자산 동기화 실패: memberId={}", memberId, e);
                failures.add(new SyncFailure(memberId, e));
            }
        }

        if (!failures.isEmpty()) {
            StringBuilder sb = new StringBuilder("[자산 동기화 배치 실패] ")
                    .append(failures.size()).append("건\n");
            for (SyncFailure f : failures) {
                sb.append("• memberId=").append(f.memberId)
                  .append(" / ").append(f.reason).append("\n");
            }
            slackNotifier.sendBatchFailureSummary("asset-sync", sb.toString().trim());
        }

        log.info("[배치] 자산 동기화 완료 — 성공: {}, 실패: {}",
                memberIds.size() - failures.size(), failures.size());
    }

    @Override
    @Transactional
    public AssetSyncResponse syncAccounts(Long memberId) {
        ConnectedAccount connectedAccount = connectedAccountMapper.findByMemberId(memberId);
        if (connectedAccount == null) {
            throw new BusinessException(ErrorCode.ASSET_NOT_LINKED);
        }

        String connectedId = aesEncryptor.decrypt(connectedAccount.getConnectedId());
        String birthDate = connectedAccount.getBirthDate();
        String accessToken = codefTokenManager.getAccessToken();

        List<ConnectedInstitution> institutions =
                connectedInstitutionMapper.findAllByConnectedAccountId(connectedAccount.getId());

        List<InstitutionSyncResult> results = new ArrayList<>();
        int syncedCount = 0;
        int failedCount = 0;

        for (ConnectedInstitution institution : institutions) {
            InstitutionSyncResult result = syncInstitution(
                    institution, connectedId, birthDate, accessToken);
            results.add(result);
            if (result.isSuccess()) syncedCount++;
            else failedCount++;
        }

        LocalDateTime now = LocalDateTime.now();
        connectedAccount.setSyncStatus(failedCount == 0 ? "SUCCESS" : syncedCount > 0 ? "PARTIAL" : "FAILED");
        connectedAccount.setLastSyncedAt(now);
        connectedAccountMapper.updateSyncStatus(connectedAccount);

        // 자산 합계 캐시 갱신
        Long totalAssets = assetAccountMapper.sumCurrentValueByMemberId(memberId);
        Long loanBalance = loanAccountMapper.sumLoanBalanceByMemberId(memberId);
        assetSummaryMapper.upsert(AssetSummary.builder()
                .memberId(memberId)
                .totalAssets(totalAssets != null ? totalAssets : 0L)
                .loanBalance(loanBalance != null ? loanBalance : 0L)
                .syncedAt(now)
                .build());

        return AssetSyncResponse.builder()
                .syncedCount(syncedCount)
                .failedCount(failedCount)
                .institutions(results)
                .build();
    }

    private InstitutionSyncResult syncInstitution(ConnectedInstitution institution,
                                                   String connectedId,
                                                   String birthDate,
                                                   String accessToken) {
        String institutionCode = institution.getInstitutionCode();
        Institution institutionInfo = institutionMapper.findByCode(institutionCode);
        String orgName = institutionInfo != null ? institutionInfo.getName() : institutionCode;
        String businessType = institutionInfo != null ? institutionInfo.getBusinessType() : BUSINESS_TYPE_BANK;

        try {
            if (BUSINESS_TYPE_STOCK.equals(businessType)) {
                return syncStockInstitution(institution, connectedId, accessToken, institutionCode, orgName);
            }
            if (BUSINESS_TYPE_CARD.equals(businessType)) {
                return syncCardInstitution(institution, connectedId, birthDate, accessToken, institutionCode, orgName);
            }
            return syncBankInstitution(institution, connectedId, birthDate, accessToken, institutionCode, orgName);
        } catch (Exception e) {
            log.error("계좌 동기화 실패: org={}", institutionCode, e);
            return recordFailure(institution, orgName, "SYNC_ERROR", e.getMessage());
        }
    }

    private InstitutionSyncResult syncCardInstitution(ConnectedInstitution institution,
                                                       String connectedId, String birthDate,
                                                       String accessToken,
                                                       String institutionCode, String orgName) {
        CodefCardResponse response =
                codefClient.getCardList(accessToken, connectedId, institutionCode, "", "", birthDate);

        if (!response.isSuccess()) {
            return recordFailure(institution, orgName, response.getResultCode(), response.getResultMessage());
        }

        List<CardAccount> cardAccounts = new ArrayList<>();
        for (CodefCardItem item : response.getCards()) {
            cardAccounts.add(CardAccount.builder()
                    .connectedInstitutionId(institution.getId())
                    .cardNo(item.getResCardNo())
                    .isSleep(item.getResSleepYN() != null ? item.getResSleepYN() : "N")
                    .cardName(item.getResCardName())
                    .cardType(item.getResCardType())
                    .isTraffic(item.getResTrafficYN() != null ? item.getResTrafficYN() : "N")
                    .imageLink(item.getResImageLink())
                    .issueDate(parseDate(item.getResIssueDate()))
                    .validPeriod(item.getResValidPeriod())
                    .state(item.getResState())
                    .rawResponse(toJson(item))
                    .build());
        }

        cardAccountMapper.deleteByConnectedInstitutionId(institution.getId());
        if (!cardAccounts.isEmpty()) cardAccountMapper.insertAll(cardAccounts);

        institution.setStatus("ACTIVE");
        institution.setLastSyncedAt(LocalDateTime.now());
        institution.setLastAttemptedAt(LocalDateTime.now());
        institution.setLastErrorCode(null);
        institution.setLastErrorMessage(null);
        connectedInstitutionMapper.updateSyncResult(institution);

        log.info("카드 동기화 완료: org={}, 카드수={}", institutionCode, cardAccounts.size());

        return InstitutionSyncResult.builder()
                .organizationCode(institutionCode)
                .organizationName(orgName)
                .success(true)
                .cardAccountCount(cardAccounts.size())
                .build();
    }

    private InstitutionSyncResult syncBankInstitution(ConnectedInstitution institution,
                                                       String connectedId, String birthDate,
                                                       String accessToken,
                                                       String institutionCode, String orgName) {
        CodefBankAccountResponse response =
                codefClient.getBankAccountList(accessToken, connectedId, institutionCode, birthDate);

        if (!response.isSuccess()) {
            return recordFailure(institution, orgName, response.getResultCode(), response.getResultMessage());
        }

        CodefBankAccountResponse.CodefBankData data = response.getData();
        List<AssetAccount> assetAccounts = parseAssetAccounts(institution.getId(), data);
        List<LoanAccount> loanAccounts = parseLoanAccounts(institution.getId(), data);

        saveAccounts(institution, assetAccounts, loanAccounts);

        log.info("은행 계좌 동기화 완료: org={}, 자산계좌={}, 대출계좌={}",
                institutionCode, assetAccounts.size(), loanAccounts.size());

        return InstitutionSyncResult.builder()
                .organizationCode(institutionCode)
                .organizationName(orgName)
                .success(true)
                .assetAccountCount(assetAccounts.size())
                .loanAccountCount(loanAccounts.size())
                .build();
    }

    private InstitutionSyncResult syncStockInstitution(ConnectedInstitution institution,
                                                        String connectedId, String accessToken,
                                                        String institutionCode, String orgName) {
        CodefStockAccountResponse accountListResponse =
                codefClient.getStockAccountList(accessToken, connectedId, institutionCode);

        if (!accountListResponse.isSuccess()) {
            return recordFailure(institution, orgName,
                    accountListResponse.getResultCode(), accountListResponse.getResultMessage());
        }

        List<AssetAccount> assetAccounts = new ArrayList<>();
        for (CodefStockAccountItem account : accountListResponse.getData()) {
            // 외화 계좌 제외
            if (account.getResDepositReceivedF() != null && !account.getResDepositReceivedF().isBlank()) {
                log.debug("외화 계좌 제외: {}", account.getResAccountDisplay());
                continue;
            }

            CodefStockFinancialAssetsResponse assetsResponse =
                    codefClient.getStockFinancialAssets(accessToken, connectedId, institutionCode,
                            account.getResAccount());

            String accountType = classifyStockAccountType(account.getResAccountName(),
                    assetsResponse.isSuccess() ? assetsResponse.getData().getResItemList() : List.of());

            Long valuationAmt = parseAmount(account.getResValuationAmt());
            Long depositReceived = parseAmount(account.getResDepositReceived());
            Long currentValue = (valuationAmt != null || depositReceived != null)
                    ? (valuationAmt != null ? valuationAmt : 0L) + (depositReceived != null ? depositReceived : 0L)
                    : null;
            BigDecimal earningsRate = parseRate(account.getResEarningsRate());
            Long valuationPl = parseAmount(account.getResValuationPL());
            Long purchaseAmount = parseAmount(account.getResPurchaseAmount());

            assetAccounts.add(AssetAccount.builder()
                    .connectedInstitutionId(institution.getId())
                    .accountType(accountType)
                    .assetCategory("INVESTMENT")
                    .accountDisplay(account.getResAccountDisplay())
                    .productName(account.getResAccountName())
                    .currentValue(currentValue)
                    .valuationAmount(valuationAmt)
                    .depositReceived(depositReceived)
                    .valuationPl(valuationPl)
                    .purchaseAmount(purchaseAmount)
                    .earningsRate(earningsRate)
                    .rawResponse(toJson(account))
                    .build());
        }

        saveAccounts(institution, assetAccounts, List.of());

        log.info("증권 계좌 동기화 완료: org={}, 자산계좌={}", institutionCode, assetAccounts.size());

        return InstitutionSyncResult.builder()
                .organizationCode(institutionCode)
                .organizationName(orgName)
                .success(true)
                .assetAccountCount(assetAccounts.size())
                .loanAccountCount(0)
                .build();
    }

    private void saveAccounts(ConnectedInstitution institution,
                               List<AssetAccount> assetAccounts, List<LoanAccount> loanAccounts) {
        assetAccountMapper.deleteByConnectedInstitutionId(institution.getId());
        loanAccountMapper.deleteByConnectedInstitutionId(institution.getId());

        if (!assetAccounts.isEmpty()) assetAccountMapper.insertAll(assetAccounts);
        if (!loanAccounts.isEmpty()) loanAccountMapper.insertAll(loanAccounts);

        institution.setStatus("ACTIVE");
        institution.setLastSyncedAt(LocalDateTime.now());
        institution.setLastAttemptedAt(LocalDateTime.now());
        institution.setLastErrorCode(null);
        institution.setLastErrorMessage(null);
        connectedInstitutionMapper.updateSyncResult(institution);
    }

    /**
     * 증권 계좌 유형 분류
     * CMA 계좌명 우선 → 보유종목 상품유형코드 순서로 판별
     * 01=주식(STOCK), 02=펀드(FUND), 03=CMA(DEMAND)
     */
    private String classifyStockAccountType(String accountName, List<CodefStockItem> items) {
        if (accountName != null && accountName.contains("CMA")) return "DEMAND";

        for (CodefStockItem item : items) {
            String code = item.getResProductTypeCd();
            if (PRODUCT_TYPE_STOCK.equals(code)) return "STOCK";
            if (PRODUCT_TYPE_FUND.equals(code)) return "FUND";
            if (PRODUCT_TYPE_DEMAND.equals(code)) return "DEMAND";
        }
        return "STOCK";
    }

    private BigDecimal parseRate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<AssetAccount> parseAssetAccounts(Long connectedInstitutionId,
                                                   CodefBankAccountResponse.CodefBankData data) {
        List<AssetAccount> accounts = new ArrayList<>();

        for (CodefDepositItem item : data.getResDepositTrust()) {
            if (isExcluded(item)) continue;

            String accountType = classifyDepositType(item);
            String assetCategory = toAssetCategory(accountType);
            Long balance = parseAmount(item.getResAccountBalance());

            accounts.add(AssetAccount.builder()
                    .connectedInstitutionId(connectedInstitutionId)
                    .accountType(accountType)
                    .assetCategory(assetCategory)
                    .accountDisplay(item.getResAccountDisplay())
                    .productName(item.getResAccountName())
                    .currentValue(balance)
                    .startDate(parseDate(item.getResAccountStartDate()))
                    .maturityDate(parseDate(item.getResAccountEndDate()))
                    .rawResponse(toJson(item))
                    .build());
        }

        for (CodefBankAccountResponse.CodefFundItem item : data.getResFund()) {
            Long balance = parseAmount(item.getResAccountBalance());
            accounts.add(AssetAccount.builder()
                    .connectedInstitutionId(connectedInstitutionId)
                    .accountType("FUND")
                    .assetCategory("INVESTMENT")
                    .accountDisplay(item.getResAccountDisplay())
                    .productName(item.getResAccountName())
                    .currentValue(balance)
                    .rawResponse(toJson(item))
                    .build());
        }

        return accounts;
    }

    private List<LoanAccount> parseLoanAccounts(Long connectedInstitutionId,
                                                 CodefBankAccountResponse.CodefBankData data) {
        List<LoanAccount> loans = new ArrayList<>();

        for (CodefLoanItem item : data.getResLoan()) {
            Long balance = parseAmount(item.getResLoanBalance());
            loans.add(LoanAccount.builder()
                    .connectedInstitutionId(connectedInstitutionId)
                    .loanName(item.getResAccountName())
                    .accountDisplay(item.getResAccountDisplay())
                    .loanBalance(balance != null ? balance : 0L)
                    .startDate(parseDate(item.getResLoanStartDate()))
                    .endDate(parseDate(item.getResLoanEndDate()))
                    .rawResponse(toJson(item))
                    .build());
        }

        return loans;
    }

    /**
     * 마이너스통장 또는 외화 계좌는 제외
     */
    private boolean isExcluded(CodefDepositItem item) {
        if (OVERDRAFT_FLAG.equals(item.getResOverdraftAcctYN())) return true;
        if (item.getResAccountCurrency() != null && !CURRENCY_KRW.equals(item.getResAccountCurrency())) return true;
        return false;
    }

    /**
     * resAccountDeposit 코드 → 계좌 유형
     * 12코드(적금, 청약 혼용)는 상품명으로 구분
     * 주택청약종합저축은 법적으로 정해진 단일 상품명이므로 안전한 판별 기준
     */
    private String classifyDepositType(CodefDepositItem item) {
        String depositCode = item.getResAccountDeposit();
        if (DEPOSIT_CODE_DEMAND.equals(depositCode)) return "DEMAND";
        if (DEPOSIT_CODE_DEPOSIT.equals(depositCode)) return "DEPOSIT";
        if (DEPOSIT_CODE_SAVINGS.equals(depositCode)) {
            String name = item.getResAccountName();
            if (name != null && name.contains("청약")) return "SUBSCRIPTION";
            return "SAVINGS";
        }
        return "DEPOSIT";
    }

    private String toAssetCategory(String accountType) {
        switch (accountType) {
            case "DEMAND": return "CASH";
            case "SUBSCRIPTION": return "SUBSCRIPTION";
            case "FUND":
            case "STOCK": return "INVESTMENT";
            default: return "DEPOSIT_SAVINGS";
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank() || value.length() != 8) return null;
        try {
            return LocalDate.of(
                    Integer.parseInt(value.substring(0, 4)),
                    Integer.parseInt(value.substring(4, 6)),
                    Integer.parseInt(value.substring(6, 8)));
        } catch (NumberFormatException | DateTimeParseException e) {
            return null;
        }
    }

    private Long parseAmount(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("금액 파싱 실패: '{}'", value);
            return null;
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static class SyncFailure {
        final Long memberId;
        final String reason;

        SyncFailure(Long memberId, Throwable e) {
            this.memberId = memberId;
            this.reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }
    }

    private InstitutionSyncResult recordFailure(ConnectedInstitution institution,
                                                 String orgName,
                                                 String errorCode,
                                                 String errorMessage) {
        institution.setStatus("ERROR");
        institution.setLastAttemptedAt(LocalDateTime.now());
        institution.setLastErrorCode(errorCode);
        institution.setLastErrorMessage(errorMessage != null && errorMessage.length() > 1000
                ? errorMessage.substring(0, 1000) : errorMessage);
        connectedInstitutionMapper.updateSyncResult(institution);

        return InstitutionSyncResult.builder()
                .organizationCode(institution.getInstitutionCode())
                .organizationName(orgName)
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}
