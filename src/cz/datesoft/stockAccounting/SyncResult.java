package cz.datesoft.stockAccounting;

public class SyncResult {
  private boolean success;
  private String message;
  private long timestamp;
  private ConflictInfo conflict;

  public SyncResult(boolean success, String message) {
    this.success = success;
    this.message = message;
    this.timestamp = System.currentTimeMillis();
  }

  public SyncResult(boolean success, String message, ConflictInfo conflict) {
    this(success, message);
    this.conflict = conflict;
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  public ConflictInfo getConflict() {
    return conflict;
  }

  public void setConflict(ConflictInfo conflict) {
    this.conflict = conflict;
  }

  public boolean hasConflict() {
    return conflict != null;
  }

  public static class ConflictInfo {
    private long localTimestamp;
    private long cloudTimestamp;
    private String description;
    private String cloudFilename;

    public ConflictInfo(long localTimestamp, long cloudTimestamp, String cloudFilename) {
      this.localTimestamp = localTimestamp;
      this.cloudTimestamp = cloudTimestamp;
      this.cloudFilename = cloudFilename;
      this.description = "Konflikt dat: Lokální data z "
          + formatTimestamp(localTimestamp) + ", Cloud data z " + formatTimestamp(cloudTimestamp);
    }

    public long getLocalTimestamp() {
      return localTimestamp;
    }

    public void setLocalTimestamp(long localTimestamp) {
      this.localTimestamp = localTimestamp;
    }

    public long getCloudTimestamp() {
      return cloudTimestamp;
    }

    public void setCloudTimestamp(long cloudTimestamp) {
      this.cloudTimestamp = cloudTimestamp;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public String getCloudFilename() {
      return cloudFilename;
    }

    public void setCloudFilename(String cloudFilename) {
      this.cloudFilename = cloudFilename;
    }

    private String formatTimestamp(long timestamp) {
      java.time.Instant instant = java.time.Instant.ofEpochMilli(timestamp);
      java.time.LocalDateTime dt = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
      return dt.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
    }
  }
}
