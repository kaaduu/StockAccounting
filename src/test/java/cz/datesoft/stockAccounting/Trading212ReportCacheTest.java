package cz.datesoft.stockAccounting;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that Trading212ReportCache persists its report status cache to disk
 * and rehydrates it on the next instance creation.
 */
class Trading212ReportCacheTest {

    @TempDir
    Path tempDir;

    private String originalCacheBaseDir;

    @BeforeEach
    void setUp() {
        originalCacheBaseDir = Settings.getCacheBaseDir();
        Settings.setCacheBaseDir(tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        Settings.setCacheBaseDir(originalCacheBaseDir);
    }

    @Test
    void cacheSurvivesRestart() throws Exception {
        // Instance 1: populate cache via reflection (since cacheReportStatus is private)
        Trading212ReportCache cache1 = new Trading212ReportCache();
        
        // Use reflection to call private cacheReportStatus method
        Method cacheMethod = Trading212ReportCache.class.getDeclaredMethod(
            "cacheReportStatus", long.class, Trading212CsvClient.CsvReportStatus.class);
        cacheMethod.setAccessible(true);
        
        Trading212CsvClient.CsvReportStatus status1 = new Trading212CsvClient.CsvReportStatus(
            Trading212CsvClient.CsvReportStatus.ReportStatus.FINISHED,
            "https://example.com/report1.csv"
        );
        cacheMethod.invoke(cache1, 12345L, status1);
        
        Trading212CsvClient.CsvReportStatus status2 = new Trading212CsvClient.CsvReportStatus(
            Trading212CsvClient.CsvReportStatus.ReportStatus.PROCESSING,
            null
        );
        cacheMethod.invoke(cache1, 67890L, status2);
        
        // Save to disk
        Method saveMethod = Trading212ReportCache.class.getDeclaredMethod("saveCacheToDisk");
        saveMethod.setAccessible(true);
        saveMethod.invoke(cache1);
        
        // Instance 2: simulate app restart
        Trading212ReportCache cache2 = new Trading212ReportCache();
        
        // Verify cache was reloaded via reflection
        Field cacheField = Trading212ReportCache.class.getDeclaredField("reportCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Long, Object> reportCache = (Map<Long, Object>) cacheField.get(cache2);
        
        // Verify our entries survived (may have additional entries from legacy migration)
        assertTrue(reportCache.size() >= 2, "Cache should contain at least 2 entries after reload");
        assertTrue(reportCache.containsKey(12345L), "Cache should contain report 12345");
        assertTrue(reportCache.containsKey(67890L), "Cache should contain report 67890");
    }

    @Test
    void emptyCachePersists() throws Exception {
        // Instance 1: empty cache (may load from legacy migration)
        Trading212ReportCache cache1 = new Trading212ReportCache();
        
        // Clear any entries loaded from legacy migration
        Field cacheField1 = Trading212ReportCache.class.getDeclaredField("reportCache");
        cacheField1.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Long, Object> reportCache1 = (Map<Long, Object>) cacheField1.get(cache1);
        reportCache1.clear();
        
        // Save to disk (should write empty JSON)
        Method saveMethod = Trading212ReportCache.class.getDeclaredMethod("saveCacheToDisk");
        saveMethod.setAccessible(true);
        saveMethod.invoke(cache1);
        
        // Instance 2: should load empty cache without error
        Trading212ReportCache cache2 = new Trading212ReportCache();
        
        Field cacheField = Trading212ReportCache.class.getDeclaredField("reportCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Long, Object> reportCache = (Map<Long, Object>) cacheField.get(cache2);
        
        assertEquals(0, reportCache.size(), "Cache should be empty after clearing and saving");
    }
}
