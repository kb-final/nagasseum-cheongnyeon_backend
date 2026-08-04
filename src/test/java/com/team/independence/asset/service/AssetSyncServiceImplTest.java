package com.team.independence.asset.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.team.independence.asset.domain.AssetAccount;
import com.team.independence.asset.domain.ConnectedAccount;
import com.team.independence.asset.domain.ConnectedInstitution;
import com.team.independence.asset.domain.Institution;
import com.team.independence.asset.domain.LoanAccount;
import com.team.independence.asset.mapper.AssetAccountMapper;
import com.team.independence.asset.mapper.AssetSummaryMapper;
import com.team.independence.asset.mapper.ConnectedAccountMapper;
import com.team.independence.asset.mapper.ConnectedInstitutionMapper;
import com.team.independence.asset.mapper.InstitutionMapper;
import com.team.independence.asset.mapper.LoanAccountMapper;
import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import com.team.independence.common.security.AesEncryptor;
import com.team.independence.external.codef.CodefClient;
import com.team.independence.external.codef.CodefMockClient;
import com.team.independence.external.codef.CodefTokenManager;
import com.team.independence.external.codef.dto.CodefBankAccountResponse;
import com.team.independence.external.codef.dto.CodefStockAccountResponse;
import com.team.independence.external.codef.dto.CodefStockFinancialAssetsResponse;
import com.team.independence.external.slack.SlackNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class  AssetSyncServiceImplTest {

    @Mock ConnectedAccountMapper connectedAccountMapper;
    @Mock ConnectedInstitutionMapper connectedInstitutionMapper;
    @Mock InstitutionMapper institutionMapper;
    @Mock AssetAccountMapper assetAccountMapper;
    @Mock LoanAccountMapper loanAccountMapper;
    @Mock AssetSummaryMapper assetSummaryMapper;
    @Mock CodefClient codefClient;
    @Mock CodefTokenManager codefTokenManager;
    @Mock AesEncryptor aesEncryptor;
    @Mock SlackNotifier slackNotifier;

    AssetSyncServiceImpl service;

    final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private <T> T parse(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() {
        service = new AssetSyncServiceImpl(
                connectedAccountMapper, connectedInstitutionMapper, institutionMapper,
                assetAccountMapper, loanAccountMapper, assetSummaryMapper,
                codefClient, codefTokenManager, aesEncryptor, objectMapper, slackNotifier);
    }

    private ConnectedAccount connectedAccount(long id) {
        return ConnectedAccount.builder()
                .id(id).memberId(1L).connectedId("enc-id").birthDate("001010")
                .connectedStatus("ACTIVE").build();
    }

    private ConnectedInstitution institution(long id, String code) {
        return ConnectedInstitution.builder()
                .id(id).connectedAccountId(10L).institutionCode(code).status("ACTIVE").build();
    }

    private void stubCommonDeps(long connectedAccountId, long institutionId, String orgCode, String businessType) {
        when(connectedAccountMapper.findByMemberId(1L)).thenReturn(connectedAccount(connectedAccountId));
        when(aesEncryptor.decrypt("enc-id")).thenReturn("real-connected-id");
        when(codefTokenManager.getAccessToken()).thenReturn("mock-token");
        when(connectedInstitutionMapper.findAllByConnectedAccountId(connectedAccountId))
                .thenReturn(List.of(institution(institutionId, orgCode)));
        when(institutionMapper.findByCode(orgCode))
                .thenReturn(Institution.builder().code(orgCode).name("테스트기관").businessType(businessType).build());
        when(assetAccountMapper.sumCurrentValueByMemberId(1L)).thenReturn(0L);
        when(loanAccountMapper.sumLoanBalanceByMemberId(1L)).thenReturn(0L);
    }

    // ===== 은행 계좌 동기화 =====
    @Test
    @DisplayName("은행 계좌 동기화 - 마이너스통장 제외, 나머지 5건 저장")
    void syncAccounts_bank_savesCorrectAccountCount() {
        stubCommonDeps(10L, 100L, "088", "BK");
        when(codefClient.getBankAccountList(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(parse(BANK_RESPONSE, CodefBankAccountResponse.class));

        service.syncAccounts(1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AssetAccount>> captor = ArgumentCaptor.forClass(List.class);
        verify(assetAccountMapper).deleteByConnectedInstitutionId(100L);
        verify(assetAccountMapper).insertAll(captor.capture());

        List<AssetAccount> saved = captor.getValue();
        // 입출금, 자유적금, 청약, 정기예금, 은행펀드 = 5건 (마이너스통장 제외)
        assertThat(saved).hasSize(5);

        verify(loanAccountMapper).deleteByConnectedInstitutionId(100L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LoanAccount>> loanCaptor = ArgumentCaptor.forClass(List.class);
        verify(loanAccountMapper).insertAll(loanCaptor.capture());
        assertThat(loanCaptor.getValue()).hasSize(1);
    }

    @Test
    @DisplayName("은행 계좌 동기화 - 청약 계좌 유형이 SUBSCRIPTION으로 분류된다")
    void syncAccounts_bank_classifiesSubscriptionAccount() {
        stubCommonDeps(10L, 100L, "088", "BK");
        when(codefClient.getBankAccountList(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(parse(BANK_RESPONSE, CodefBankAccountResponse.class));

        service.syncAccounts(1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AssetAccount>> captor = ArgumentCaptor.forClass(List.class);
        verify(assetAccountMapper).insertAll(captor.capture());

        List<AssetAccount> saved = captor.getValue();
        assertThat(saved).anyMatch(a -> "SUBSCRIPTION".equals(a.getAccountType())
                && "주택청약종합저축".equals(a.getProductName()));
        assertThat(saved).anyMatch(a -> "SAVINGS".equals(a.getAccountType())
                && "자유적금".equals(a.getProductName()));
        assertThat(saved).anyMatch(a -> "DEMAND".equals(a.getAccountType()));
        assertThat(saved).anyMatch(a -> "DEPOSIT".equals(a.getAccountType()));
        assertThat(saved).anyMatch(a -> "FUND".equals(a.getAccountType()));
    }

    // ===== 증권 계좌 동기화 =====
    @Test
    @DisplayName("증권 STOCK 계좌 - currentValue = valuationAmt + depositReceived로 저장된다")
    void syncAccounts_stock_storesRawSumAsCurrentValue() {
        stubCommonDeps(10L, 100L, "240", "ST");
        when(codefClient.getStockAccountList(anyString(), anyString(), anyString()))
                .thenReturn(parse(STOCK_RESPONSE, CodefStockAccountResponse.class));
        when(codefClient.getStockFinancialAssets(anyString(), anyString(), anyString(),
                anyString())).thenReturn(parse(FINANCIAL_ASSETS_STOCK, CodefStockFinancialAssetsResponse.class));

        service.syncAccounts(1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AssetAccount>> captor = ArgumentCaptor.forClass(List.class);
        verify(assetAccountMapper).insertAll(captor.capture());

        AssetAccount stockAccount = captor.getValue().stream()
                .filter(a -> "STOCK".equals(a.getAccountType()))
                .findFirst().orElseThrow();

        assertThat(stockAccount.getValuationAmount()).isEqualTo(10_000_000L);
        assertThat(stockAccount.getDepositReceived()).isEqualTo(500_000L);
        assertThat(stockAccount.getCurrentValue()).isEqualTo(10_500_000L);
    }

    @Test
    @DisplayName("증권 CMA 계좌 - 계좌명 기반으로 DEMAND 유형으로 분류된다")
    void syncAccounts_stock_classifiesCmaByAccountName() {
        stubCommonDeps(10L, 100L, "240", "ST");
        when(codefClient.getStockAccountList(anyString(), anyString(), anyString()))
                .thenReturn(parse(STOCK_RESPONSE, CodefStockAccountResponse.class));
        when(codefClient.getStockFinancialAssets(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(parse(FINANCIAL_ASSETS_STOCK, CodefStockFinancialAssetsResponse.class));

        service.syncAccounts(1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AssetAccount>> captor = ArgumentCaptor.forClass(List.class);
        verify(assetAccountMapper).insertAll(captor.capture());

        assertThat(captor.getValue())
                .anyMatch(a -> "DEMAND".equals(a.getAccountType()) && "CMA계좌".equals(a.getProductName()));
    }

    // ===== 동기화 실패 시 =====
    @Test
    @DisplayName("syncAll - 한 회원 실패 시 나머지 회원은 계속 동기화되고 Slack 알림이 전송된다")
    void syncAll_isolatesFailureAndNotifiesSlack() {
        when(connectedAccountMapper.findAllMemberIds()).thenReturn(List.of(1L, 2L, 3L));

        // memberId=1: 정상
        ConnectedAccount account1 = connectedAccount(10L);
        when(connectedAccountMapper.findByMemberId(1L)).thenReturn(account1);
        when(aesEncryptor.decrypt("enc-id")).thenReturn("real-id");
        when(codefTokenManager.getAccessToken()).thenReturn("token");
        when(connectedInstitutionMapper.findAllByConnectedAccountId(10L)).thenReturn(List.of());
        when(assetAccountMapper.sumCurrentValueByMemberId(anyLong())).thenReturn(0L);
        when(loanAccountMapper.sumLoanBalanceByMemberId(anyLong())).thenReturn(0L);

        // memberId=2: 실패 (연동 계좌 없음)
        when(connectedAccountMapper.findByMemberId(2L)).thenReturn(null);

        // memberId=3: 정상
        ConnectedAccount account3 = ConnectedAccount.builder()
                .id(30L).memberId(3L).connectedId("enc-id").birthDate("001010")
                .connectedStatus("ACTIVE").build();
        when(connectedAccountMapper.findByMemberId(3L)).thenReturn(account3);
        when(connectedInstitutionMapper.findAllByConnectedAccountId(30L)).thenReturn(List.of());

        service.syncAll();

        ArgumentCaptor<String> slackCaptor = ArgumentCaptor.forClass(String.class);
        verify(slackNotifier, times(1)).sendBatchFailureSummary(slackCaptor.capture());
        assertThat(slackCaptor.getValue()).contains("memberId=2");

        verify(assetSummaryMapper, times(2)).upsert(any());
    }

    @Test
    @DisplayName("syncAll - 모든 회원 성공 시 Slack 알림을 보내지 않는다")
    void syncAll_noSlackWhenAllSucceed() {
        when(connectedAccountMapper.findAllMemberIds()).thenReturn(List.of(1L));
        when(connectedAccountMapper.findByMemberId(1L)).thenReturn(connectedAccount(10L));
        when(aesEncryptor.decrypt("enc-id")).thenReturn("real-id");
        when(codefTokenManager.getAccessToken()).thenReturn("token");
        when(connectedInstitutionMapper.findAllByConnectedAccountId(10L)).thenReturn(List.of());
        when(assetAccountMapper.sumCurrentValueByMemberId(1L)).thenReturn(0L);
        when(loanAccountMapper.sumLoanBalanceByMemberId(1L)).thenReturn(0L);

        service.syncAll();

        verify(slackNotifier, never()).sendBatchFailureSummary(anyString());
    }

    // 더미 Response JSON (CodefMockClient와 동일)
    private static final String BANK_RESPONSE = "{"
            + "\"result\":{\"code\":\"CF-00000\",\"message\":\"성공\"},"
            + "\"data\":{"
            + "  \"resDepositTrust\":["
            + "    {\"resAccountDisplay\":\"111-1111-1111\",\"resAccountBalance\":\"1000000\","
            + "     \"resAccountDeposit\":\"11\",\"resAccountName\":\"입출금통장\","
            + "     \"resAccountCurrency\":\"KRW\",\"resOverdraftAcctYN\":\"0\"},"
            + "    {\"resAccountDisplay\":\"222-2222-2222\",\"resAccountBalance\":\"2000000\","
            + "     \"resAccountDeposit\":\"12\",\"resAccountName\":\"자유적금\","
            + "     \"resAccountCurrency\":\"KRW\",\"resOverdraftAcctYN\":\"0\"},"
            + "    {\"resAccountDisplay\":\"333-3333-3333\",\"resAccountBalance\":\"300000\","
            + "     \"resAccountDeposit\":\"12\",\"resAccountName\":\"주택청약종합저축\","
            + "     \"resAccountCurrency\":\"KRW\",\"resOverdraftAcctYN\":\"0\"},"
            + "    {\"resAccountDisplay\":\"444-4444-4444\",\"resAccountBalance\":\"5000000\","
            + "     \"resAccountDeposit\":\"13\",\"resAccountName\":\"정기예금\","
            + "     \"resAccountCurrency\":\"KRW\",\"resOverdraftAcctYN\":\"0\"},"
            + "    {\"resAccountDisplay\":\"555-5555-5555\",\"resAccountBalance\":\"1000000\","
            + "     \"resAccountDeposit\":\"11\",\"resAccountName\":\"마이너스통장\","
            + "     \"resAccountCurrency\":\"KRW\",\"resOverdraftAcctYN\":\"1\"}"
            + "  ],"
            + "  \"resFund\":["
            + "    {\"resAccountDisplay\":\"666-6666-6666\",\"resAccountBalance\":\"3000000\","
            + "     \"resAccountName\":\"국내주식형펀드\"}"
            + "  ],"
            + "  \"resLoan\":["
            + "    {\"resAccountDisplay\":\"777-7777-7777\",\"resAccountName\":\"신용대출\","
            + "     \"resLoanBalance\":\"10000000\"}"
            + "  ]"
            + "}}";

    private static final String STOCK_RESPONSE = "{"
            + "\"result\":{\"code\":\"CF-00000\",\"message\":\"성공\"},"
            + "\"data\":["
            + "  {\"resAccount\":\"" + CodefMockClient.STOCK_ACCOUNT_NO + "\","
            + "   \"resAccountDisplay\":\"123-456-78\",\"resAccountName\":\"위탁계좌\","
            + "   \"resValuationAmt\":\"10000000\",\"resDepositReceived\":\"500000\"},"
            + "  {\"resAccount\":\"" + CodefMockClient.CMA_ACCOUNT_NO + "\","
            + "   \"resAccountDisplay\":\"876-543-21\",\"resAccountName\":\"CMA계좌\","
            + "   \"resDepositReceived\":\"3000000\"}"
            + "]}";

    private static final String FINANCIAL_ASSETS_STOCK = "{"
            + "\"result\":{\"code\":\"CF-00000\",\"message\":\"성공\"},"
            + "\"data\":{\"resAccount\":\"" + CodefMockClient.STOCK_ACCOUNT_NO + "\","
            + "\"resItemList\":["
            + "  {\"resProductTypeCd\":\"01\",\"resItemName\":\"삼성전자\","
            + "   \"resValuationAmt\":\"10000000\"}"
            + "]}}";
}
