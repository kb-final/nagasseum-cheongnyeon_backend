package com.team.independence.property.mapper;

import com.team.independence.property.domain.RentTransaction;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RentTransactionMapper {
    void insert(RentTransaction rentTransaction);
    void insertBatch(List<RentTransaction> rentTransactions);
}
