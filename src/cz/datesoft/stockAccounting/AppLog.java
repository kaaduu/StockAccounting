package cz.datesoft.stockAccounting;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Simple in-memory application log for UI display (Nápověda → Logy).
 *
 * Stores short summaries with optional details (stack traces).
 */
public final class AppLog {
  public enum Level {
    INFO,
    WARN,
    ERROR
  }

  public static final class Entry {
    public final long timestampMs;
    public final Level level;
    public final String summary;
    public final String details;

    private Entry(long timestampMs, Level level, String summary, String details) {
      this.timestampMs = timestampMs;
      this.level = level;
      this.summary = summary;
      this.details = details;
    }
  }

  private static final int MAX_ENTRIES = 2000;
  private static final List<Entry> entries = Collections.synchronizedList(new ArrayList<Entry>());
  private static final SimpleDateFormat TS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  private AppLog() {
  }

  public static void info(String summary) {
    add(Level.INFO, summary, null);
  }

  public static void warn(String summary) {
    add(Level.WARN, summary, null);
  }

  public static void error(String summary, Throwable t) {
    add(Level.ERROR, summary, t);
  }

  public static void add(Level level, String summary, Throwable t) {
    if (summary == null || summary.trim().isEmpty())
      return;

    String details = null;
    if (t != null) {
      details = toStackTrace(t);
    }

    synchronized (entries) {
      entries.add(new Entry(System.currentTimeMillis(), level, summary.trim(), details));
      // Cap memory.
      int overflow = entries.size() - MAX_ENTRIES;
      if (overflow > 0) {
        for (int i = 0; i < overflow; i++) {
          entries.remove(0);
        }
      }
    }
  }

  public static List<Entry> snapshot() {
    synchronized (entries) {
      return new ArrayList<Entry>(entries);
    }
  }

  public static void clear() {
    synchronized (entries) {
      entries.clear();
    }
  }

  public static String formatSummary(Entry e) {
    if (e == null)
      return "";
    String ts = TS.format(new Date(e.timestampMs));
    String lvl = e.level == null ? "INFO" : e.level.name();
    return "[" + ts + "] " + lvl + " " + (e.summary == null ? "" : e.summary);
  }

  private static String toStackTrace(Throwable t) {
    try {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      t.printStackTrace(pw);
      pw.flush();
      return sw.toString();
    } catch (Exception e) {
      return String.valueOf(t);
    }
  }
}
