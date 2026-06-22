package com.ssm.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CaptchaService {
    private static final long TOKEN_TTL_MS = 5 * 60 * 1000;
    private static final int MAX_CHALLENGES = 2000;
    private static final int CHAR_COUNT = 6;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String CHAR_POOL = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, CaptchaEntry> challenges = new ConcurrentHashMap<>();
    private final byte[] hmacKey;

    public CaptchaService(
            @Value("${app.security.captcha-hmac-key:ems-default-captcha-key-change-me}") String key
    ) {
        this.hmacKey = key.getBytes(StandardCharsets.UTF_8);
    }

    public CaptchaChallenge generateChallenge() {
        char[] chars = new char[CHAR_COUNT];
        for (int i = 0; i < CHAR_COUNT; i++) {
            chars[i] = CHAR_POOL.charAt(secureRandom.nextInt(CHAR_POOL.length()));
        }
        String answer = new String(chars);
        long expiresAt = System.currentTimeMillis() + TOKEN_TTL_MS;
        String nonce = generateNonce();
        String payload = nonce + ":" + expiresAt;
        String signature = sign(payload);
        String token = base64UrlEncode(payload.getBytes(StandardCharsets.UTF_8))
                + "." + base64UrlEncode(signature.getBytes(StandardCharsets.UTF_8));
        challenges.put(nonce, new CaptchaEntry(answer, expiresAt));
        cleanupExpired();
        return new CaptchaChallenge(token, renderImage(answer));
    }

    public boolean verify(String token, String submittedAnswer) {
        if (token == null || token.isEmpty() || submittedAnswer == null || submittedAnswer.isEmpty()) {
            return false;
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2) {
                return false;
            }
            String payload = base64UrlDecode(parts[0]);
            String signature = base64UrlDecode(parts[1]);
            String expectedSignature = sign(payload);
            if (!MessageDigest.isEqual(
                    signature.getBytes(StandardCharsets.UTF_8),
                    expectedSignature.getBytes(StandardCharsets.UTF_8))) {
                return false;
            }
            String[] segments = payload.split(":");
            if (segments.length != 2) {
                return false;
            }
            String nonce = segments[0];
            long expiresAt = Long.parseLong(segments[1]);
            if (System.currentTimeMillis() > expiresAt) {
                challenges.remove(nonce);
                return false;
            }
            CaptchaEntry entry = challenges.remove(nonce);
            if (entry == null || System.currentTimeMillis() > entry.expiresAt) {
                return false;
            }
            return MessageDigest.isEqual(
                    entry.answer.toLowerCase().getBytes(StandardCharsets.UTF_8),
                    submittedAnswer.toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String renderImage(String answer) {
        int width = 280;
        int height = 80;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(245 + secureRandom.nextInt(10), 248 + secureRandom.nextInt(7), 250 + secureRandom.nextInt(5)));
            g.fillRect(0, 0, width, height);
            for (int i = 0; i < 22; i++) {
                g.setColor(randomColor(80, 190, 90));
                g.setStroke(new BasicStroke(1f + secureRandom.nextFloat() * 2f));
                g.drawLine(secureRandom.nextInt(width), secureRandom.nextInt(height), secureRandom.nextInt(width), secureRandom.nextInt(height));
            }
            for (int i = 0; i < 90; i++) {
                g.setColor(randomColor(70, 200, 100));
                int size = 1 + secureRandom.nextInt(4);
                g.fillOval(secureRandom.nextInt(width), secureRandom.nextInt(height), size, size);
            }
            int charWidth = width / (answer.length() + 1);
            Font baseFont = new Font("Consolas", Font.BOLD, 34);
            for (int i = 0; i < answer.length(); i++) {
                String text = String.valueOf(answer.charAt(i));
                int x = charWidth * (i + 1) - 8 + secureRandom.nextInt(16);
                int y = height / 2 + secureRandom.nextInt(18) - 9;
                double angle = (secureRandom.nextDouble() - 0.5D) * 0.55D;
                AffineTransform original = g.getTransform();
                g.rotate(angle, x, y);
                g.setFont(baseFont.deriveFont((float) (30 + secureRandom.nextInt(8))));
                g.setColor(randomColor(20, 130, 255));
                FontMetrics metrics = g.getFontMetrics();
                g.drawString(text, x - metrics.stringWidth(text) / 2, y + metrics.getAscent() / 2 - 4);
                g.setTransform(original);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Captcha image rendering failed", e);
        } finally {
            g.dispose();
        }
    }

    private Color randomColor(int min, int max, int alpha) {
        int bound = Math.max(1, max - min);
        return new Color(
                min + secureRandom.nextInt(bound),
                min + secureRandom.nextInt(bound),
                min + secureRandom.nextInt(bound),
                alpha
        );
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        challenges.entrySet().removeIf(entry -> entry.getValue().expiresAt < now);
        if (challenges.size() <= MAX_CHALLENGES) {
            return;
        }
        int removeCount = challenges.size() - MAX_CHALLENGES;
        for (String nonce : challenges.keySet()) {
            if (removeCount-- <= 0) {
                break;
            }
            challenges.remove(nonce);
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(hmacKey, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return base64UrlEncode(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC signing failed", e);
        }
    }

    private String generateNonce() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return base64UrlEncode(bytes);
    }

    private String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private String base64UrlDecode(String encoded) {
        byte[] decoded = Base64.getUrlDecoder().decode(encoded);
        return new String(decoded, StandardCharsets.UTF_8);
    }

    public static class CaptchaChallenge {
        public final String token;
        public final String image;

        public CaptchaChallenge(String token, String image) {
            this.token = token;
            this.image = image;
        }
    }

    private static class CaptchaEntry {
        final String answer;
        final long expiresAt;

        CaptchaEntry(String answer, long expiresAt) {
            this.answer = answer;
            this.expiresAt = expiresAt;
        }
    }
}
