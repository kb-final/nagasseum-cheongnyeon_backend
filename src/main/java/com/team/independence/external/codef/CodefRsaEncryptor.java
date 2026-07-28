package com.team.independence.external.codef;

import com.team.independence.common.exception.BusinessException;
import com.team.independence.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * CODEF RSA 암호화 유틸리티 클래스
 * CODEF가 발급한 public_key(Base64)로 비밀번호 평문을 RSA/ECB/PKCS1Padding 방식으로 암호화합니다.
 * 암호화된 값을 그대로 CODEF 계정 등록 요청의 password 필드에 사용합니다.
 */
@Slf4j
@Component
public class CodefRsaEncryptor {

    private static final String ALGORITHM = "RSA/ECB/PKCS1Padding";

    /**
     * @param publicKeyBase64 CODEF가 제공한 Base64 인코딩된 RSA 공개키
     * @param plainPassword   암호화할 비밀번호 평문
     * @return Base64 인코딩된 RSA 암호문
     */
    public String encrypt(String publicKeyBase64, String plainPassword) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64);
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(keyBytes));

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);

            byte[] encrypted = cipher.doFinal(plainPassword.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(encrypted);

        } catch (Exception e) {
            log.error("RSA 암호화 실패: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.ASSET_RSA_ENCRYPT_FAILED);
        }
    }
}
