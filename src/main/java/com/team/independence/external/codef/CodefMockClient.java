package com.team.independence.external.codef;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.independence.external.codef.dto.CodefAccountRequest;
import com.team.independence.external.codef.dto.CodefApiResponse;
import com.team.independence.external.codef.dto.CodefBankAccountResponse;
import com.team.independence.external.codef.dto.CodefCardResponse;
import com.team.independence.external.codef.dto.CodefStockAccountResponse;
import com.team.independence.external.codef.dto.CodefStockFinancialAssetsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 테스트 전용 CODEF 더미 클라이언트
 * 실제 API를 호출하지 않고 하드코딩된 응답을 반환합니다.
 * connectedId, accessToken, organization 파라미터는 모두 무시됩니다.
 *
 * 은행 더미 계좌 구성:
 *   입출금(11), 자유적금(12), 주택청약종합저축(12), 정기예금(13) 각 1건
 *   마이너스통장(11, overdraftFlag=1) — 제외 대상
 *   은행펀드 — 1건 (valuationAmount=null)
 *   신용대출 — 1건
 *
 * 증권 더미 계좌 구성:
 *   위탁계좌(STOCK_ACCOUNT_NO) — valuationAmt=10,000,000 / depositReceived=500,000
 *   CMA계좌(CMA_ACCOUNT_NO) — depositReceived=3,000,000 (valuationAmt 없음)
 */
@Slf4j
@Component
@Profile("test")
@RequiredArgsConstructor
public class CodefMockClient implements CodefClient {

    public static final String STOCK_ACCOUNT_NO = "12345678";
    public static final String CMA_ACCOUNT_NO = "87654321";

    private final ObjectMapper objectMapper;

    @Override
    public CodefBankAccountResponse getBankAccountList(String accessToken, String connectedId,
                                                       String organization, String birthDate) {
        log.debug("[Mock] getBankAccountList: org={}", organization);
        return parse(BANK_RESPONSE, CodefBankAccountResponse.class);
    }

    @Override
    public CodefStockAccountResponse getStockAccountList(String accessToken, String connectedId,
                                                         String organization) {
        log.debug("[Mock] getStockAccountList: org={}", organization);
        return parse(STOCK_RESPONSE, CodefStockAccountResponse.class);
    }

    @Override
    public CodefStockFinancialAssetsResponse getStockFinancialAssets(String accessToken, String connectedId,
                                                                      String organization, String account) {
        log.debug("[Mock] getStockFinancialAssets: account={}", account);
        String json = CMA_ACCOUNT_NO.equals(account) ? FINANCIAL_ASSETS_CMA : FINANCIAL_ASSETS_STOCK;
        return parse(json, CodefStockFinancialAssetsResponse.class);
    }

    @Override
    public CodefApiResponse createAccount(String accessToken, CodefAccountRequest.CodefAccountItem item) {
        return parse(API_SUCCESS_RESPONSE, CodefApiResponse.class);
    }

    @Override
    public CodefApiResponse addAccount(String accessToken, String connectedId,
                                       CodefAccountRequest.CodefAccountItem item) {
        return parse(API_SUCCESS_RESPONSE, CodefApiResponse.class);
    }

    @Override
    public CodefApiResponse deleteAccount(String accessToken, String connectedId,
                                          CodefAccountRequest.CodefAccountItem item) {
        return parse(API_SUCCESS_RESPONSE, CodefApiResponse.class);
    }

    @Override
    public CodefCardResponse getCardList(String accessToken, String connectedId, String organization,
                                         String cardNo, String cardPassword, String birthDate) {
        log.debug("[Mock] getCardList: org={}", organization);
        return parse(CARD_RESPONSE, CodefCardResponse.class);
    }

    private <T> T parse(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("[Mock] 응답 파싱 실패: " + clazz.getSimpleName(), e);
        }
    }

    // 더미 Response JSON
    private static final String BANK_RESPONSE = "{"
            + "\"result\":{\"code\":\"CF-00000\",\"message\":\"성공\"},"
            + "\"data\":{"
            + "  \"resDepositTrust\":["
            + "    {\"resAccountDisplay\":\"111-1111-1111\",\"resAccountBalance\":\"1000000\","
            + "     \"resAccountDeposit\":\"11\",\"resAccountName\":\"입출금통장\","
            + "     \"resAccountCurrency\":\"KRW\",\"resOverdraftAcctYN\":\"0\","
            + "     \"resAccountStartDate\":\"20230101\"},"
            + "    {\"resAccountDisplay\":\"222-2222-2222\",\"resAccountBalance\":\"2000000\","
            + "     \"resAccountDeposit\":\"12\",\"resAccountName\":\"자유적금\","
            + "     \"resAccountCurrency\":\"KRW\",\"resOverdraftAcctYN\":\"0\","
            + "     \"resAccountStartDate\":\"20240101\",\"resAccountEndDate\":\"20260101\"},"
            + "    {\"resAccountDisplay\":\"333-3333-3333\",\"resAccountBalance\":\"300000\","
            + "     \"resAccountDeposit\":\"12\",\"resAccountName\":\"주택청약종합저축\","
            + "     \"resAccountCurrency\":\"KRW\",\"resOverdraftAcctYN\":\"0\"},"
            + "    {\"resAccountDisplay\":\"444-4444-4444\",\"resAccountBalance\":\"5000000\","
            + "     \"resAccountDeposit\":\"13\",\"resAccountName\":\"정기예금\","
            + "     \"resAccountCurrency\":\"KRW\",\"resOverdraftAcctYN\":\"0\","
            + "     \"resAccountStartDate\":\"20240601\",\"resAccountEndDate\":\"20250601\"},"
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
            + "     \"resLoanBalance\":\"10000000\","
            + "     \"resLoanStartDate\":\"20230101\",\"resLoanEndDate\":\"20280101\"}"
            + "  ]"
            + "}}";

    private static final String STOCK_RESPONSE = "{"
            + "\"result\":{\"code\":\"CF-00000\",\"message\":\"성공\"},"
            + "\"data\":["
            + "  {\"resAccount\":\"" + STOCK_ACCOUNT_NO + "\","
            + "   \"resAccountDisplay\":\"123-456-78\",\"resAccountName\":\"위탁계좌\","
            + "   \"resValuationAmt\":\"10000000\",\"resDepositReceived\":\"500000\","
            + "   \"resValuationPL\":\"500000\",\"resPurchaseAmount\":\"9500000\","
            + "   \"resEarningsRate\":\"5.26\"},"
            + "  {\"resAccount\":\"" + CMA_ACCOUNT_NO + "\","
            + "   \"resAccountDisplay\":\"876-543-21\",\"resAccountName\":\"CMA계좌\","
            + "   \"resDepositReceived\":\"3000000\"}"
            + "]}";

    private static final String FINANCIAL_ASSETS_STOCK = "{"
            + "\"result\":{\"code\":\"CF-00000\",\"message\":\"성공\"},"
            + "\"data\":{"
            + "  \"resAccount\":\"" + STOCK_ACCOUNT_NO + "\","
            + "  \"resItemList\":["
            + "    {\"resProductTypeCd\":\"01\",\"resProductType\":\"주식\","
            + "     \"resItemCode\":\"005930\",\"resItemName\":\"삼성전자\","
            + "     \"resValuationAmt\":\"10000000\",\"resPurchaseAmount\":\"9500000\"}"
            + "  ]}}";

    private static final String FINANCIAL_ASSETS_CMA = "{"
            + "\"result\":{\"code\":\"CF-00000\",\"message\":\"성공\"},"
            + "\"data\":{"
            + "  \"resAccount\":\"" + CMA_ACCOUNT_NO + "\","
            + "  \"resItemList\":["
            + "    {\"resProductTypeCd\":\"03\",\"resProductType\":\"CMA\","
            + "     \"resItemName\":\"CMA-RP\",\"resValuationAmt\":\"3000000\"}"
            + "  ]}}";

    private static final String API_SUCCESS_RESPONSE = "{"
            + "\"result\":{\"code\":\"CF-00000\",\"message\":\"성공\"},"
            + "\"data\":{\"connectedId\":\"mock-connected-id\","
            + "\"successList\":[],\"errorList\":[]}}";

    private static final String CARD_RESPONSE = "{"
            + "\"result\":{\"code\":\"CF-00000\",\"message\":\"성공\"},"
            + "\"data\":["
            + "  {\"resCardNo\":\"1234-****-****-5678\",\"resSleepYN\":\"0\","
            + "   \"resCardName\":\"KB국민 My WE:SH 카드\",\"resCardType\":\"신용\","
            + "   \"resTrafficYN\":\"1\",\"resImageLink\":\"\","
            + "   \"resIssueDate\":\"20230101\",\"resValidPeriod\":\"202801\","
            + "   \"resState\":\"정상\"},"
            + "  {\"resCardNo\":\"9876-****-****-4321\",\"resSleepYN\":\"0\","
            + "   \"resCardName\":\"KB국민 노리체크카드\",\"resCardType\":\"체크\","
            + "   \"resTrafficYN\":\"0\",\"resImageLink\":\"\","
            + "   \"resIssueDate\":\"20220601\",\"resValidPeriod\":\"202706\","
            + "   \"resState\":\"정상\"}"
            + "]}";
}
