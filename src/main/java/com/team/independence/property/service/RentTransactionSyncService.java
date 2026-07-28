package com.team.independence.property.service;

public interface RentTransactionSyncService {
    void sync(String regionCode, String dealYm);
    void syncAll();
}
