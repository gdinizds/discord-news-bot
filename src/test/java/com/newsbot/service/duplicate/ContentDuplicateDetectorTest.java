package com.newsbot.service.duplicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ContentDuplicateDetectorTest {

    @InjectMocks
    private ContentDuplicateDetector contentDuplicateDetector;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(contentDuplicateDetector, "similarityThreshold", 0.8);
    }

    @Test
    void generateContentHash_shouldGenerateConsistentHash() {
        String content = "This is a test content";
        
        String hash1 = contentDuplicateDetector.generateContentHash(content);
        String hash2 = contentDuplicateDetector.generateContentHash(content);
        
        assertNotNull(hash1);
        assertEquals(hash1, hash2, "Hash should be consistent for the same content");
    }
    

    @Test
    void generateContentHash_shouldGenerateDifferentHashesForDifferentContent() {
        String content1 = "This is a test content";
        String content2 = "This is a different test content";
        
        String hash1 = contentDuplicateDetector.generateContentHash(content1);
        String hash2 = contentDuplicateDetector.generateContentHash(content2);
        
        assertNotNull(hash1);
        assertNotNull(hash2);
        assertNotEquals(hash1, hash2, "Hashes should be different for different content");
    }

    @Test
    void generateContentHash_shouldHandleNullContent() {
        String hash = contentDuplicateDetector.generateContentHash(null);
        
        assertNotNull(hash);
        assertEquals(contentDuplicateDetector.generateContentHash(""), hash, 
                "Null content should be treated as empty string");
    }

    @Test
    void areContentsSimilar_shouldReturnTrueForSimilarContent() {
        String content1 = "Breaking news: New iPhone 15 released today";
        String content2 = "Breaking News: New iPhone 15 Released Today!";
        
        boolean result = contentDuplicateDetector.areContentsSimilar(content1, content2);
        
        assertTrue(result, "Contents with minor differences should be considered similar");
    }

    @Test
    void areContentsSimilar_shouldReturnFalseForDifferentContent() {
        String content1 = "Breaking news: New iPhone 15 released today";
        String content2 = "Microsoft announces new Surface laptop with improved features";
        
        boolean result = contentDuplicateDetector.areContentsSimilar(content1, content2);
        
        assertFalse(result, "Different contents should not be considered similar");
    }

    @Test
    void areContentsSimilar_shouldHandleNullContent() {
        String content = "Test content";
        
        assertFalse(contentDuplicateDetector.areContentsSimilar(null, content),
                "Null content should not be similar to any content");
        assertFalse(contentDuplicateDetector.areContentsSimilar(content, null), 
                "No content should be similar to null content");
        assertFalse(contentDuplicateDetector.areContentsSimilar(null, null), 
                "Null contents should not be similar to each other");
    }

    @Test
    void areContentsSimilar_shouldNormalizeContentBeforeComparison() {
        String content1 = "Breaking NEWS: iPhone-15 released!!!";
        String content2 = "breaking news iphone15 released";
        
        boolean result = contentDuplicateDetector.areContentsSimilar(content1, content2);
        
        assertTrue(result, "Contents should be normalized before comparison");
    }
}