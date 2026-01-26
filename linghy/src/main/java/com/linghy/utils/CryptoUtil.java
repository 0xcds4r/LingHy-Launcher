package com.linghy.utils;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.*;

public final class CryptoUtil
{
    private static final String ENV_FILE = ".env";
    private static final String SECRET_KEY = "CF_SECRET";
    private static final int ITERATIONS = 65536;
    private static final int KEY_LEN = 256;

    private static String loadSecret() throws Exception
    {
        List<String> lines = Files.readAllLines(Path.of(ENV_FILE), StandardCharsets.UTF_8);
        for (String l : lines)
        {
            l = l.trim();
            if (l.isEmpty() || l.startsWith("#")) continue;
            int eq = l.indexOf('=');
            if (eq == -1) continue;
            String k = l.substring(0, eq).trim();
            if (k.equals(SECRET_KEY))
                return l.substring(eq + 1).trim();
        }
        throw new IllegalStateException("CF_SECRET not found in .env");
    }

    private static SecretKey deriveKey(byte[] salt) throws Exception
    {
        String secret = loadSecret();
        PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, ITERATIONS, KEY_LEN);
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return new SecretKeySpec(f.generateSecret(spec).getEncoded(), "AES");
    }

    public static void writeEncrypted(Path file, String apiKey) throws Exception
    {
        SecureRandom rnd = SecureRandom.getInstanceStrong();
        byte[] salt = new byte[16];
        byte[] iv   = new byte[16];
        rnd.nextBytes(salt);
        rnd.nextBytes(iv);

        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, deriveKey(salt), new IvParameterSpec(iv));

        byte[] enc = c.doFinal(apiKey.getBytes(StandardCharsets.UTF_8));

        byte[] out = new byte[32 + enc.length];
        System.arraycopy(salt, 0, out, 0, 16);
        System.arraycopy(iv, 0, out, 16, 16);
        System.arraycopy(enc, 0, out, 32, enc.length);

        Files.write(file, out);
    }

    public static String readEncrypted(Path file) throws Exception
    {
        byte[] all = Files.readAllBytes(file);

        byte[] salt = Arrays.copyOfRange(all, 0, 16);
        byte[] iv   = Arrays.copyOfRange(all, 16, 32);
        byte[] enc  = Arrays.copyOfRange(all, 32, all.length);

        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, deriveKey(salt), new IvParameterSpec(iv));

        return new String(c.doFinal(enc), StandardCharsets.UTF_8);
    }

    public static String readEncryptedBytes(byte[] all) throws Exception
    {
        byte[] salt = Arrays.copyOfRange(all, 0, 16);
        byte[] iv   = Arrays.copyOfRange(all, 16, 32);
        byte[] enc  = Arrays.copyOfRange(all, 32, all.length);

        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, deriveKey(salt), new IvParameterSpec(iv));

        return new String(c.doFinal(enc), StandardCharsets.UTF_8);
    }
}
