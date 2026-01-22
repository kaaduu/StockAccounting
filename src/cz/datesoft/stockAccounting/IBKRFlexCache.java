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
    private static final String LEGACY_CACHE_DIR = System.getProperty("user.home") + "/.ibkr_flex";
    private Map<Integer, CachedYear> cache = new HashMap<>();

    private static Path getUnifiedDir() {
        // Keep everything for Interactive Brokers under a single broker folder.
        return Paths.get(Settings.getCacheBaseDir(), "ib");
    }

    private static Path getUnifiedLegacyDir() {
        // Previously used broker folder name.
        return Paths.get(Settings.getCacheBaseDir(), "ibkr");
    }

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
        Path filePath = getUnifiedDir().resolve(fileName);

        Files.createDirectories(filePath.getParent());
        
        Files.writeString(filePath, csvContent);

        // Also archive the raw payload for debugging
        try {
            CacheManager.archiveString("ib", CacheManager.Source.API, "flex_single_" + year, ".csv", csvContent);
        } catch (Exception e) {
            // Best effort
        }

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
        File cacheDir = getUnifiedDir().toFile();
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
        Path dirPath = getUnifiedDir();
        if (!Files.exists(dirPath)) {
            try {
                Files.createDirectories(dirPath);
                logger.info("Created cache directory: " + dirPath);
            } catch (IOException e) {
                logger.warning("Failed to create cache directory: " + e.getMessage());
            }
        }
    }

    private void loadCacheFromDisk() {
        // Migrate legacy cache directories if present
        try {
            Path legacy = Paths.get(LEGACY_CACHE_DIR);
            if (Files.exists(legacy) && Files.isDirectory(legacy)) {
                Files.createDirectories(getUnifiedDir());
                // Copy csv files + cache index
                Files.list(legacy).forEach(p -> {
                    try {
                        if (Files.isDirectory(p)) return;
                        Path dst = getUnifiedDir().resolve(p.getFileName().toString());
                        if (!Files.exists(dst)) {
                            Files.copy(p, dst);
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                });
            }

            // Also migrate from old unified folder name (cacheBaseDir/ibkr -> cacheBaseDir/ib)
            Path legacyUnified = getUnifiedLegacyDir();
            if (Files.exists(legacyUnified) && Files.isDirectory(legacyUnified)) {
                Files.createDirectories(getUnifiedDir());
                Files.list(legacyUnified).forEach(p -> {
                    try {
                        if (Files.isDirectory(p)) return;
                        Path dst = getUnifiedDir().resolve(p.getFileName().toString());
                        if (!Files.exists(dst)) {
                            Files.copy(p, dst);
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                });
            }
        } catch (Exception e) {
            // ignore
        }

        Path cacheIndexFile = getUnifiedDir().resolve("cache_index.json");
        if (!Files.exists(cacheIndexFile)) {
            return;
        }

        try {
            String content = Files.readString(cacheIndexFile);
            File cacheDir = getUnifiedDir().toFile();
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
