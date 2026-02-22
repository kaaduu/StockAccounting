package cz.datesoft.stockAccounting;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

public class EncryptionUtils {
  private static final String ALGORITHM = "AES";
  private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
  private static final String KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256";
  private static final int KEY_LENGTH = 256;
  private static final int IV_LENGTH = 16;
  private static final int SALT_LENGTH = 16;
  private static final int ITERATIONS = 100000;
  private static final int HMAC_LENGTH = 32;

  private EncryptionUtils() {
  }

  public static byte[] encryptData(byte[] data, char[] password) throws GeneralSecurityException {
    byte[] salt = generateRandomBytes(SALT_LENGTH);
    byte[] iv = generateRandomBytes(IV_LENGTH);
    SecretKey key = deriveKey(password, salt);

    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
      byte[] encryptedData = cipher.doFinal(data);

      byte[] hmac = computeHMAC(key, salt, iv, encryptedData);

      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      outputStream.write(salt);
      outputStream.write(iv);
      outputStream.write(encryptedData);
      outputStream.write(hmac);

      return outputStream.toByteArray();
    } catch (Exception e) {
      throw new GeneralSecurityException("Chyba při šifrování dat", e);
    }
  }

  public static byte[] decryptData(byte[] encryptedData, char[] password) throws GeneralSecurityException {
    try {
      if (encryptedData.length < SALT_LENGTH + IV_LENGTH + HMAC_LENGTH) {
        throw new GeneralSecurityException("Neplatný formát šifrovaných dat");
      }

      int offset = 0;
      byte[] salt = Arrays.copyOfRange(encryptedData, offset, offset + SALT_LENGTH);
      offset += SALT_LENGTH;

      byte[] iv = Arrays.copyOfRange(encryptedData, offset, offset + IV_LENGTH);
      offset += IV_LENGTH;

      byte[] ciphertext = Arrays.copyOfRange(encryptedData, offset, encryptedData.length - HMAC_LENGTH);
      offset += ciphertext.length;

      byte[] receivedHmac = Arrays.copyOfRange(encryptedData, offset, encryptedData.length);

      SecretKey key = deriveKey(password, salt);
      byte[] computedHmac = computeHMAC(key, salt, iv, ciphertext);

      if (!MessageDigest.isEqual(receivedHmac, computedHmac)) {
        throw new GeneralSecurityException("Neplatný HMAC - poškozená data nebo špatné heslo");
      }

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
      return cipher.doFinal(ciphertext);
    } catch (Exception e) {
      throw new GeneralSecurityException("Chyba při dešifrování dat", e);
    }
  }

  public static void encryptStream(InputStream input, OutputStream output, char[] password) throws GeneralSecurityException, IOException {
    byte[] salt = generateRandomBytes(SALT_LENGTH);
    byte[] iv = generateRandomBytes(IV_LENGTH);
    SecretKey key = deriveKey(password, salt);

    output.write(salt);
    output.write(iv);

    Cipher cipher = Cipher.getInstance(TRANSFORMATION);
    cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));

    byte[] buffer = new byte[4096];
    int bytesRead;
    byte[] encryptedBuffer;

    while ((bytesRead = input.read(buffer)) != -1) {
      encryptedBuffer = cipher.update(buffer, 0, bytesRead);
      if (encryptedBuffer != null) {
        output.write(encryptedBuffer);
      }
    }

    encryptedBuffer = cipher.doFinal();
    byte[] finalData = encryptedBuffer;
    byte[] hmac = computeHMAC(key, salt, iv, finalData);
    output.write(finalData);
    output.write(hmac);
  }

  public static void decryptStream(InputStream input, OutputStream output, char[] password) throws GeneralSecurityException, IOException {
    byte[] salt = new byte[SALT_LENGTH];
    byte[] iv = new byte[IV_LENGTH];

    if (input.read(salt) != SALT_LENGTH || input.read(iv) != IV_LENGTH) {
      throw new GeneralSecurityException("Neplatný formát šifrovaných dat");
    }

    SecretKey key = deriveKey(password, salt);
    Cipher cipher = Cipher.getInstance(TRANSFORMATION);
    cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));

    ByteArrayOutputStream ciphertextBuffer = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int bytesRead;

    while (input.available() > HMAC_LENGTH) {
      bytesRead = input.read(buffer);
      ciphertextBuffer.write(buffer, 0, bytesRead);
    }

    byte[] ciphertext = ciphertextBuffer.toByteArray();
    byte[] hmac = new byte[HMAC_LENGTH];
    input.read(hmac);

    byte[] computedHmac = computeHMAC(key, salt, iv, ciphertext);

    if (!MessageDigest.isEqual(hmac, computedHmac)) {
      throw new GeneralSecurityException("Neplatný HMAC - poškozená data nebo špatné heslo");
    }

    byte[] decryptedData = cipher.doFinal(ciphertext);
    output.write(decryptedData);
  }

  private static SecretKey deriveKey(char[] password, byte[] salt) throws GeneralSecurityException {
    try {
      SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM);
      PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);
      byte[] keyBytes = factory.generateSecret(spec).getEncoded();
      return new SecretKeySpec(keyBytes, ALGORITHM);
    } catch (Exception e) {
      throw new GeneralSecurityException("Chyba při odvození klíče", e);
    }
  }

  private static byte[] computeHMAC(SecretKey key, byte[] salt, byte[] iv, byte[] ciphertext) throws GeneralSecurityException {
    try {
      byte[] data = new byte[salt.length + iv.length + ciphertext.length];
      System.arraycopy(salt, 0, data, 0, salt.length);
      System.arraycopy(iv, 0, data, salt.length, iv.length);
      System.arraycopy(ciphertext, 0, data, salt.length + iv.length, ciphertext.length);

      javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
      javax.crypto.spec.SecretKeySpec hmacKey = new javax.crypto.spec.SecretKeySpec(key.getEncoded(), "HmacSHA256");
      mac.init(hmacKey);
      return mac.doFinal(data);
    } catch (Exception e) {
      throw new GeneralSecurityException("Chyba při výpočtu HMAC", e);
    }
  }

  private static byte[] generateRandomBytes(int length) {
    byte[] bytes = new byte[length];
    new SecureRandom().nextBytes(bytes);
    return bytes;
  }
}
