package cz.datesoft.stockAccounting;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Unified cache storage for all broker reports.
 *
 * Layout:
 *   ~/.stockaccounting/cache/<broker>/
 *
 * Files are named with a human-readable prefix and a short SHA-256 suffix to avoid collisions.
 */
public final class CacheManager {
  private CacheManager() {
  }

  public enum Source {
    API,
    FILE
  }

  private static final DateTimeFormatter TS = DateTimeFormatter
      .ofPattern("yyyy-MM-dd'T'HH-mm'Z'")
      .withZone(ZoneOffset.UTC);

  public static Path getBrokerDir(String brokerKey) throws IOException {
    String base = Settings.getCacheBaseDir();
    if (base == null || base.trim().isEmpty()) {
      base = System.getProperty("user.home") + "/.stockaccounting/cache";
    }
    Path dir = Paths.get(base, brokerKey);
    Files.createDirectories(dir);
    return dir;
  }

  public static Path archiveBytes(String brokerKey, Source source, String prefix, String extension, byte[] bytes)
      throws IOException {
    if (bytes == null) {
      throw new IOException("No content to cache");
    }

    String safePrefix = sanitize(prefix);
    String safeExt = sanitizeExt(extension);
    String ts = TS.format(Instant.now());
    String shortHash = sha256Short(bytes);

    Path dir = getBrokerDir(brokerKey);
    String fileName = brokerKey + "_" + source.name().toLowerCase() + "_" + safePrefix + "_" + ts + "_" + shortHash
        + safeExt;
    Path dst = dir.resolve(fileName);

    // Avoid overwriting different content (hash should prevent collision).
    if (!Files.exists(dst)) {
      Files.write(dst, bytes);
    }
    return dst;
  }

  public static Path archiveString(String brokerKey, Source source, String prefix, String extension, String content)
      throws IOException {
    if (content == null) {
      throw new IOException("No content to cache");
    }
    return archiveBytes(brokerKey, source, prefix, extension, content.getBytes(StandardCharsets.UTF_8));
  }

  public static Path archiveFile(String brokerKey, Source source, String prefix, Path src) throws IOException {
    if (src == null) {
      throw new IOException("No source file to cache");
    }
    if (!Files.exists(src)) {
      throw new IOException("Source file not found: " + src);
    }
    String ext = guessExtension(src.getFileName().toString());
    byte[] bytes = Files.readAllBytes(src);
    return archiveBytes(brokerKey, source, prefix, ext, bytes);
  }

  public static void migrateLegacyDir(Path legacyDir, String brokerKey, String reasonPrefix) {
    try {
      if (legacyDir == null || !Files.exists(legacyDir) || !Files.isDirectory(legacyDir)) {
        return;
      }

      Files.list(legacyDir).forEach(p -> {
        try {
          if (Files.isDirectory(p)) {
            return;
          }
          String name = p.getFileName().toString();
          archiveFile(brokerKey, Source.FILE, reasonPrefix + "_" + sanitize(name), p);
        } catch (Exception e) {
          // Best effort
        }
      });
    } catch (Exception e) {
      // Best effort
    }
  }

  private static String sanitize(String s) {
    if (s == null) return "report";
    String t = s.trim();
    if (t.isEmpty()) return "report";
    // Keep readable ASCII only.
    t = t.replaceAll("[^A-Za-z0-9._()\\-]+", "_");
    // collapse
    t = t.replaceAll("_+", "_");
    return t;
  }

  private static String sanitizeExt(String ext) {
    if (ext == null || ext.trim().isEmpty()) return "";
    String e = ext.trim();
    if (!e.startsWith(".")) e = "." + e;
    e = e.replaceAll("[^A-Za-z0-9.]+", "");
    return e;
  }

  private static String guessExtension(String fileName) {
    if (fileName == null) return "";
    int idx = fileName.lastIndexOf('.');
    if (idx < 0) return "";
    return fileName.substring(idx);
  }

  private static String sha256Short(byte[] bytes) throws IOException {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest;
      try (InputStream in = new java.io.ByteArrayInputStream(bytes)) {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
          md.update(buf, 0, n);
        }
      }
      digest = md.digest();
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < digest.length; i++) {
        sb.append(String.format("%02x", digest[i]));
      }
      return sb.substring(0, 8);
    } catch (Exception e) {
      throw new IOException("Failed to hash content: " + e.getMessage(), e);
    }
  }
}
