package com.team.independence.external.ontong;

import com.team.independence.external.ontong.dto.OntongPolicyItem;
import java.util.List;

public interface OntongPolicyClient {

    List<OntongPolicyItem> fetchHousingPolicies();
}
