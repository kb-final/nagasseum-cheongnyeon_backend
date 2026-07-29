package com.team.independence.common.security;

import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 대칭 암호화 클래스
 * Connected ID 등 민감 식별자를 DB에 저장할 때 사용합니다.
 * 출력 포맷: Base64(12-byte nonce || ciphertext+tag)
 */
@Component
public class AesEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BIT = 128;

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesEncryptor(@Value("${CODEF_ENCRYPT_KEY}") String keyBase64) {
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("CODEF_ENCRYPT_KEY must be 32 bytes (Base64-encoded 256-bit key)");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BIT, nonce));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));

            byte[] combined = new byte[NONCE_LENGTH + ciphertext.length];
            System.arraycopy(nonce, 0, combined, 0, NONCE_LENGTH);
            System.arraycopy(ciphertext, 0, combined, NONCE_LENGTH, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Connected ID 암호화에 실패했습니다.");
        }
    }

    public String decrypt(String encryptedBase64) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedBase64);

            byte[] nonce = new byte[NONCE_LENGTH];
            byte[] ciphertext = new byte[combined.length - NONCE_LENGTH];
            System.arraycopy(combined, 0, nonce, 0, NONCE_LENGTH);
            System.arraycopy(combined, NONCE_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BIT, nonce));

            return new String(cipher.doFinal(ciphertext), "UTF-8");
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Connected ID 복호화에 실패했습니다.");
        }
    }
}
