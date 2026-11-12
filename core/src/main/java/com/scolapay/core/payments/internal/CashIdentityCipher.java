package com.scolapay.core.payments.internal;

import com.scolapay.core.shared.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class CashIdentityCipher {
    private final byte[] key;
    private final SecureRandom random=new SecureRandom();
    CashIdentityCipher(CashProperties properties) {
        byte[] decoded=null;
        String configured=properties.identityEncryptionKey();
        if(configured!=null&&!configured.isBlank()) {
            try{decoded=Base64.getDecoder().decode(configured);}catch(IllegalArgumentException invalid){throw invalidConfiguration();}
            if(decoded.length!=32)throw invalidConfiguration();
        }
        if(decoded==null&&(properties.cashReceptionsEnabled()||properties.cashIdentitiesEnabled()))throw invalidConfiguration();
        key=decoded;
    }
    private static IllegalStateException invalidConfiguration(){return new IllegalStateException("CASH_IDENTITY_KEY_MUST_BE_BASE64_32_BYTES");}
    private static ApiException unavailable(){return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"CASH_IDENTITY_UNAVAILABLE","Les données d'identité ne sont pas disponibles.");}
    String encrypt(UUID tenant,UUID id,String domain,String clear) {
        if(key==null)throw unavailable();
        try{
            byte[] nonce=new byte[12];random.nextBytes(nonce);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
            cipher.updateAAD(aad(tenant,id,domain));
            byte[] encrypted=cipher.doFinal(clear.getBytes(StandardCharsets.UTF_8));
            byte[] envelope=Arrays.copyOf(nonce,nonce.length+encrypted.length);
            System.arraycopy(encrypted,0,envelope,nonce.length,encrypted.length);
            return "v1:"+Base64.getEncoder().encodeToString(envelope);
        }catch(GeneralSecurityException failure){throw unavailable();}
    }
    String decrypt(UUID tenant,UUID id,String domain,String encrypted) {
        if(key==null||encrypted==null||!encrypted.startsWith("v1:")||encrypted.length()>4096)throw unavailable();
        try{
            byte[] bytes=Base64.getDecoder().decode(encrypted.substring(3));if(bytes.length<29)throw unavailable();
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,bytes,0,12));
            cipher.updateAAD(aad(tenant,id,domain));
            return new String(cipher.doFinal(bytes,12,bytes.length-12),StandardCharsets.UTF_8);
        }catch(GeneralSecurityException|IllegalArgumentException failure){throw unavailable();}
    }
    private static byte[] aad(UUID tenant,UUID id,String domain){return ("cash-identity/v1/"+domain+"/"+tenant+"/"+id).getBytes(StandardCharsets.UTF_8);}
}
