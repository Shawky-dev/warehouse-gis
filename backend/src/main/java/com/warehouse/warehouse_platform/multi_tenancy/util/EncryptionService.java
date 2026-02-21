package com.warehouse.warehouse_platform.multi_tenancy.util;

public interface EncryptionService {
    String encrypt(String strToEncrypt, String secret, String salt);
    String decrypt(String strToDecrypt, String secret, String salt);
}
