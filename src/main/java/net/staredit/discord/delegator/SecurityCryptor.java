package net.staredit.discord.delegator;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.stream.Collectors;

public class SecurityCryptor {

    public static String getResourceAsString(String resourceFileName) throws IOException {
        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        InputStream is = null;
        InputStreamReader inputStreamReader = null;
        BufferedReader bufferedReader = null;

        try {
            is = classLoader.getResourceAsStream(resourceFileName);
            if ( is == null )
                throw new FileNotFoundException(resourceFileName);

            inputStreamReader = new InputStreamReader(is);
            bufferedReader = new BufferedReader(inputStreamReader);
            String result = bufferedReader.lines().collect(Collectors.joining(System.lineSeparator()));
            if ( result == null || result.isEmpty() )
                throw new RuntimeException("Unexpected empty resource file!");
            else
                return result;
        } finally {
            try {
                if ( bufferedReader != null )
                    bufferedReader.close();
            } finally {
                try {
                    if ( inputStreamReader != null )
                        inputStreamReader.close();
                } finally {
                    if ( bufferedReader != null )
                        bufferedReader.close();
                }
            }
        }
    }

    public static SecretKey getKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        String rawKey = new SecurityKey().getKey() + getResourceAsString("secure/key");
        String salt = getResourceAsString("secure/salt");;
        KeySpec keySpec = new PBEKeySpec(rawKey.toCharArray(), salt.getBytes(StandardCharsets.UTF_8), 65536, 256);
        return new SecretKeySpec(secretKeyFactory.generateSecret(keySpec).getEncoded(), "AES");
    }

    public static String getRawEncryptedData() throws IOException {
        return getResourceAsString("secure/data");
    }

    public static String encrypt(String data) throws NoSuchPaddingException, NoSuchAlgorithmException, IOException,
            InvalidKeySpecException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException,
            InvalidAlgorithmParameterException {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, getKey(), new IvParameterSpec(new byte[16]));
        return Base64.getEncoder().encodeToString(cipher.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    public static String decrypt(String data) throws NoSuchPaddingException, NoSuchAlgorithmException,
            IllegalBlockSizeException, BadPaddingException, IOException, InvalidKeySpecException, InvalidKeyException,
            InvalidAlgorithmParameterException {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, getKey(), new IvParameterSpec(new byte[16]));
        return new String(cipher.doFinal(Base64.getDecoder().decode(data)), StandardCharsets.UTF_8);
    }
}
