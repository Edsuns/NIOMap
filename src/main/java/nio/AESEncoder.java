package nio;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Created by Edsuns@qq.com on 2022/4/14.
 */
public class AESEncoder {

    static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    static final String ALGORITHM_RAW = ALGORITHM.substring(0, ALGORITHM.indexOf('/'));

    final SecretKey secretKey;
    final IvParameterSpec iv;

    /**
     * @param secretKey base64 encoded {@link SecretKey}
     * @param iv        base64 encoded {@link IvParameterSpec}
     */
    public AESEncoder(String secretKey, String iv) {
        this(new SecretKeySpec(Base64.getDecoder().decode(secretKey),
                ALGORITHM_RAW), new IvParameterSpec(Base64.getDecoder().decode(iv)));
    }

    public AESEncoder(byte[] secretKey, byte[] iv) {
        this(new SecretKeySpec(secretKey, ALGORITHM_RAW), new IvParameterSpec(iv));
    }

    public AESEncoder(SecretKey secretKey, IvParameterSpec iv) {
        this.secretKey = secretKey;
        this.iv = iv;
    }

    public byte[] encrypt(byte[] plain) throws BadPaddingException, IllegalBlockSizeException, InvalidKeyException {
        try {
            return encrypt(ALGORITHM, plain, secretKey, iv);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException
                | InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] decrypt(byte[] bytes) throws BadPaddingException, IllegalBlockSizeException, InvalidKeyException {
        try {
            return decrypt(ALGORITHM, bytes, secretKey, iv);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException
                | InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }
    }

    public String stringify() {
        return Base64.getEncoder().encodeToString(secretKey.getEncoded()) + ";"
                + Base64.getEncoder().encodeToString(iv.getIV());
    }

    public static AESEncoder parse(String stringify) {
        String[] s = stringify.split(";");
        if (s.length != 2) {
            throw new IllegalArgumentException();
        }
        return new AESEncoder(s[0], s[1]);
    }

    public static byte[] encrypt(String algorithm, byte[] plain, SecretKey key, IvParameterSpec iv)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
            InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.ENCRYPT_MODE, key, iv);
        return cipher.doFinal(plain);
    }

    public static byte[] decrypt(String algorithm, byte[] cipherText, SecretKey key, IvParameterSpec iv)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
            InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.DECRYPT_MODE, key, iv);
        return cipher.doFinal(cipherText);
    }

    public static AESEncoder generateEncoder() throws NoSuchAlgorithmException {
        return new AESEncoder(generateKey(), generateIv());
    }

    public static SecretKey generateKey() throws NoSuchAlgorithmException {
        return generateKey(128);
    }

    public static SecretKey generateKey(int n) throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");// only AES
        keyGenerator.init(n);
        return keyGenerator.generateKey();
    }

    private static final class SecureRandomHolder {
        static final SecureRandom INSTANCE = new SecureRandom();
    }

    public static IvParameterSpec generateIv() {
        byte[] iv = new byte[16];
        SecureRandomHolder.INSTANCE.nextBytes(iv);
        return new IvParameterSpec(iv);
    }
}
