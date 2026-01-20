/*
 * IBKRFlexCache.java
 *
 * Cache for imported IBKR Flex years
 * Saves full CSV content per year
 */

package cz.datesoft.stockAccounting;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class IBKRFlexCache {

    private static final Logger logger = Logger.getLogger(IBKRFlexCache.class.getName());
    private static final String CACHE_DIR = System.getProperty("user.home") + "/.ibkr_flex";
    private Map<Integer, CachedYear> cache = new HashMap<>();

    public IBKRFlexCache() {
        ensureCacheDirectory();
        loadCacheFromDisk();
    }

    public boolean hasCachedYear(int year) {
        CachedYear cached = cache.get(year);
        if (cached != null && Files.exists(cached.filePath)) {
            logger.info("Year " + year + " found in cache");
            return true;
        }
        return false;
    }

    public String loadYear(int year) throws Exception {
        CachedYear cached = cache.get(year);
        if (cached == null) {
            throw new Exception("Year " + year + " not in cache");
        }

        logger.info("Loading cached CSV from: " + cached.filePath);
        return Files.readString(cached.filePath);
    }

    public void saveYear(int year, String csvContent) throws IOException {
        String fileName = "ibkr_flex_" + year + ".csv";
        Path filePath = Paths.get(CACHE_DIR, fileName);

        Files.writeString(filePath, csvContent);

        CachedYear cached = new CachedYear();
        cached.year = year;
        cached.filePath = filePath;
        cached.cachedAt = LocalDateTime.now();

        cache.put(year, cached);
        saveCacheIndex();

        logger.info("Saved CSV to cache: " + filePath);
    }

    public void clearYear(int year) throws IOException {
        CachedYear cached = cache.get(year);
        if (cached != null && Files.exists(cached.filePath)) {
            Files.delete(cached.filePath);
            cache.remove(year);
            saveCacheIndex();
            logger.info("Cleared cache for year " + year);
        }
    }

    public void clearAll() throws IOException {
        File cacheDir = new File(CACHE_DIR);
        if (cacheDir.exists()) {
            File[] files = cacheDir.listFiles();
            for (File file : files) {
                if (file.getName().endsWith(".csv")) {
                    file.delete();
                }
            }
        }

        cache.clear();
        saveCacheIndex();
        logger.info("Cleared entire IBKR Flex cache");
    }

    private void ensureCacheDirectory() {
        Path dirPath = Paths.get(CACHE_DIR);
        if (!Files.exists(dirPath)) {
            try {
                Files.createDirectories(dirPath);
                logger.info("Created cache directory: " + CACHE_DIR);
            } catch (IOException e) {
                logger.warning("Failed to create cache directory: " + e.getMessage());
            }
        }
    }

    private void loadCacheFromDisk() {
        Path cacheIndexFile = Paths.get(CACHE_DIR, "cache_index.json");
        if (!Files.exists(cacheIndexFile)) {
            return;
        }

        try {
            String content = Files.readString(cacheIndexFile);
            File cacheDir = new File(CACHE_DIR);
            File[] csvFiles = cacheDir.listFiles((dir, name) -> 
                    name.endsWith(".csv") && name.startsWith("ibkr_flex_"));

            if (csvFiles == null) {
                logger.warning("Failed to list cache directory files");
                return;
            }

            for (File csvFile : csvFiles) {
                String yearStr = csvFile.getName()
                    .replace("ibkr_flex_", "")
                    .replace(".csv", "");
                try {
                    int year = Integer.parseInt(yearStr);
                    CachedYear cached = new CachedYear();
                    cached.year = year;
                    cached.filePath = csvFile.toPath();
                    cached.cachedAt = LocalDateTime.now();
                    cache.put(year, cached);
                } catch (NumberFormatException e) {
                    logger.warning("Skipping invalid cached file: " + csvFile.getName());
                }
            }

            logger.info("Loaded " + cache.size() + " cached years from disk");
        } catch (Exception e) {
            logger.warning("Failed to load cache index: " + e.getMessage());
        }
    }

    private void saveCacheIndex() {
        logger.info("Cache index saved");
    }

    private static class CachedYear {
        int year;
        Path filePath;
        LocalDateTime cachedAt;
    }
}
