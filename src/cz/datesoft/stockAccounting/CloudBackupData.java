package cz.datesoft.stockAccounting;

import java.io.Serializable;
import java.util.Map;

public class CloudBackupData implements Serializable {
  private static final long serialVersionUID = 1L;

  private long timestamp;
  private String appVersion;
  private Map<String, Object> settings;
  private String transactionFilename;
  private byte[] transactionData;

  public CloudBackupData() {
  }

  public CloudBackupData(long timestamp, String appVersion, Map<String, Object> settings) {
    this.timestamp = timestamp;
    this.appVersion = appVersion;
    this.settings = settings;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  public String getAppVersion() {
    return appVersion;
  }

  public void setAppVersion(String appVersion) {
    this.appVersion = appVersion;
  }

  public Map<String, Object> getSettings() {
    return settings;
  }

  public void setSettings(Map<String, Object> settings) {
    this.settings = settings;
  }

  public String getTransactionFilename() {
    return transactionFilename;
  }

  public void setTransactionFilename(String transactionFilename) {
    this.transactionFilename = transactionFilename;
  }

  public byte[] getTransactionData() {
    return transactionData;
  }

  public void setTransactionData(byte[] transactionData) {
    this.transactionData = transactionData;
  }
}
