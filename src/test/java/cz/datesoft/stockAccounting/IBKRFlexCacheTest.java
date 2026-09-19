package cz.datesoft.stockAccounting;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that IBKRFlexCache persists its year index to disk
 * and rehydrates it on the next instance creation.
 */
class IBKRFlexCacheTest {

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
    void saveYearThenReloadFromNewInstance() throws Exception {
        String csvContent = "ACCOUNT_INFORMATION\nsome,data,here\n";

        // Instance 1: save a year
        IBKRFlexCache cache1 = new IBKRFlexCache();
        assertFalse(cache1.hasCachedYear(2024), "Year 2024 should not be cached initially");

        cache1.saveYear(2024, csvContent);
        assertTrue(cache1.hasCachedYear(2024), "Year 2024 should be cached after save");
        assertEquals(csvContent, cache1.loadYear(2024));

        // Instance 2: simulate app restart — new cache must rehydrate from disk
        IBKRFlexCache cache2 = new IBKRFlexCache();
        assertTrue(cache2.hasCachedYear(2024),
            "Year 2024 should survive app restart (cache index must be persisted to disk)");
        assertEquals(csvContent, cache2.loadYear(2024),
            "Loaded CSV content must match what was saved");
    }

    @Test
    void saveMultipleYearsThenReload() throws Exception {
        IBKRFlexCache cache1 = new IBKRFlexCache();
        cache1.saveYear(2022, "data2022");
        cache1.saveYear(2023, "data2023");
        cache1.saveYear(2024, "data2024");

        IBKRFlexCache cache2 = new IBKRFlexCache();
        assertTrue(cache2.hasCachedYear(2022));
        assertTrue(cache2.hasCachedYear(2023));
        assertTrue(cache2.hasCachedYear(2024));
        assertEquals("data2022", cache2.loadYear(2022));
        assertEquals("data2023", cache2.loadYear(2023));
        assertEquals("data2024", cache2.loadYear(2024));
    }

    @Test
    void clearYearThenReload() throws Exception {
        IBKRFlexCache cache1 = new IBKRFlexCache();
        cache1.saveYear(2024, "data2024");
        cache1.saveYear(2023, "data2023");

        cache1.clearYear(2024);

        IBKRFlexCache cache2 = new IBKRFlexCache();
        assertFalse(cache2.hasCachedYear(2024),
            "Cleared year 2024 should not survive reload");
        assertTrue(cache2.hasCachedYear(2023),
            "Year 2023 should still be cached");
    }

    @Test
    void clearAllThenReload() throws Exception {
        IBKRFlexCache cache1 = new IBKRFlexCache();
        cache1.saveYear(2023, "data2023");
        cache1.saveYear(2024, "data2024");

        cache1.clearAll();

        IBKRFlexCache cache2 = new IBKRFlexCache();
        assertFalse(cache2.hasCachedYear(2023));
        assertFalse(cache2.hasCachedYear(2024));
    }
}
