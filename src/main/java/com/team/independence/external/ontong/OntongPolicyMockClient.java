package com.team.independence.external.ontong;

import com.team.independence.external.ontong.dto.OntongPolicyItem;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("local")
public class OntongPolicyMockClient implements OntongPolicyClient {

    @Override
    public List<OntongPolicyItem> fetchHousingPolicies() {
        log.info("[온통청년] Mock 클라이언트 사용 (local 프로필)");

        OntongPolicyItem item1 = new OntongPolicyItem();
        setField(item1, "plcyNo", "20240101000000000001");
        setField(item1, "plcyNm", "[Mock] 청년 전세자금 대출");
        setField(item1, "plcyExplnCn", "청년을 위한 전세자금 대출 지원 정책입니다.");
        setField(item1, "lclsfNm", "주거");
        setField(item1, "mclsfNm", "금융지원");
        setField(item1, "plcySprtCn", "전세자금 최대 2억 원 저금리 대출");
        setField(item1, "sprvsnInstCdNm", "국토교통부");
        setField(item1, "aplyPrdSeCd", "0057001");
        setField(item1, "sprtTrgtMinAge", "19");
        setField(item1, "sprtTrgtMaxAge", "34");
        setField(item1, "sprtTrgtAgeLmtYn", "Y");
        setField(item1, "aplyUrlAddr", "https://apply.lh.or.kr/");
        setField(item1, "aplyYmd", "");

        OntongPolicyItem item2 = new OntongPolicyItem();
        setField(item2, "plcyNo", "20240101000000000002");
        setField(item2, "plcyNm", "[Mock] 청년 월세 지원");
        setField(item2, "plcyExplnCn", "청년 월세 부담 완화를 위한 월세 보조금 지원입니다.");
        setField(item2, "lclsfNm", "주거");
        setField(item2, "mclsfNm", "주거비지원");
        setField(item2, "plcySprtCn", "월 최대 20만 원, 최장 12개월 지원");
        setField(item2, "sprvsnInstCdNm", "국토교통부");
        setField(item2, "aplyPrdSeCd", "0057002");
        setField(item2, "bizPrdBgngYmd", "20240101");
        setField(item2, "bizPrdEndYmd", "20241231");
        setField(item2, "sprtTrgtMinAge", "19");
        setField(item2, "sprtTrgtMaxAge", "39");
        setField(item2, "sprtTrgtAgeLmtYn", "Y");
        setField(item2, "aplyYmd", "20240101 ~ 20241231");

        return List.of(item1, item2);
    }

    private void setField(OntongPolicyItem item, String field, String value) {
        try {
            var f = OntongPolicyItem.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(item, value);
        } catch (Exception ignored) {
        }
    }
}
