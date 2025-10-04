package com.newsbot.service.duplicate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentDuplicateDetector {
    @Value("${app.similarity.threshold:0.8}")
    private double similarityThreshold;

    private final JaroWinklerSimilarity jaroWinkler = new JaroWinklerSimilarity();

    public String generateContentHash(String content) {
        try {
            String normalizedContent = normalizeContent(content);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalizedContent.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("Error generating content hash", e);
            throw new RuntimeException("Error generating hash", e);
        }
    }

    public boolean areContentsSimilar(String content1, String content2) {
        if (content1 == null || content2 == null) {
            return false;
        }

        String normalized1 = normalizeContent(content1);
        String normalized2 = normalizeContent(content2);

        double similarity = jaroWinkler.apply(normalized1, normalized2);

        log.debug("Calculated similarity: {} between '{}' and '{}'",
                similarity,
                normalized1.substring(0, Math.min(50, normalized1.length())),
                normalized2.substring(0, Math.min(50, normalized2.length())));

        return similarity >= similarityThreshold;
    }

    private String normalizeContent(String content) {
        if (content == null) {
            return "";
        }

        return content
                .toLowerCase()
                .replaceAll("[^\\p{L}\\p{N}\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}