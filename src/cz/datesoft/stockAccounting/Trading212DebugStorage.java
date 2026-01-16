/*
 * Trading212DebugStorage.java
 *
 * Stores raw API responses for debugging purposes
 */

package cz.datesoft.stockAccounting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

/**
 * Handles storage of raw API responses for debugging
 */
public class Trading212DebugStorage {

    private static final Logger logger = Logger.getLogger(Trading212DebugStorage.class.getName());
    private static final boolean DEBUG_MODE = true; // Set to false in production
    private static final Path DEBUG_DIR = Paths.get("/tmp/trading212_debug");

    /**
     * Store API response data for debugging
     */
    public static void storeApiResponse(String responseData, int year, String endpointType) {
        if (!DEBUG_MODE) return;

        try {
            // Create debug directory if it doesn't exist
            Files.createDirectories(DEBUG_DIR);

            // Generate timestamped filename
            String timestamp = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = String.format("trading212_%d_%s_%s.json",
                year, timestamp, endpointType);

            Path debugFile = DEBUG_DIR.resolve(filename);
            Files.writeString(debugFile, responseData, StandardCharsets.UTF_8);

            logger.info("Stored API response debug data: " + debugFile);

        } catch (IOException e) {
            logger.warning("Failed to store debug data: " + e.getMessage());
        }
    }

    /**
     * Get the debug directory path
     */
    public static Path getDebugDirectory() {
        return DEBUG_DIR;
    }

    /**
     * Check if debug mode is enabled
     */
    public static boolean isDebugMode() {
        return DEBUG_MODE;
    }
}