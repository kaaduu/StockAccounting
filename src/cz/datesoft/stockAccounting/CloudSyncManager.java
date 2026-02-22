package cz.datesoft.stockAccounting;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CloudSyncManager {
  private static CloudSyncManager instance;
  private GoogleDriveClient googleDriveClient;
  private static final String BACKUP_FILENAME_PREFIX = "StockAccounting-Backup-";
  private static final String BACKUP_FILENAME_SUFFIX = ".enc";

  private CloudSyncManager() {
  }

  public static synchronized CloudSyncManager getInstance() {
    if (instance == null) {
      instance = new CloudSyncManager();
    }
    return instance;
  }

  public void initialize() throws IOException, Exception {
    if (googleDriveClient == null) {
      googleDriveClient = new GoogleDriveClient();
      googleDriveClient.initialize();
    }
  }

  public boolean isAuthenticated() {
    return googleDriveClient != null && googleDriveClient.isAuthenticated();
  }

  public void revokeAuthentication() throws IOException {
    if (googleDriveClient != null) {
      googleDriveClient.revokeAuthentication();
      googleDriveClient = null;
    }
  }

  public SyncResult backupToCloud(char[] password) {
    try {
      if (!isAuthenticated()) {
        initialize();
      }

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      Settings.exportToEncryptedBackup(baos, password);

      byte[] encryptedData = baos.toByteArray();
      String filename = generateBackupFilename();

      String existingFileId = googleDriveClient.findFileByName(filename);
      String fileId;

      if (existingFileId != null) {
        googleDriveClient.updateFile(existingFileId, encryptedData, "application/octet-stream");
        fileId = existingFileId;
      } else {
        fileId = googleDriveClient.uploadFile(filename, encryptedData, "application/octet-stream");
      }

      Settings.setLastCloudSyncTimestamp(System.currentTimeMillis());
      return new SyncResult(true, "Záloha úspěšně odeslána do Google Drive (" + filename + ")");

    } catch (Exception e) {
      AppLog.error("Chyba při záloze do cloudu: " + e.getMessage(), e);
      return new SyncResult(false, "Chyba při záloze: " + e.getMessage());
    }
  }

  public SyncResult restoreFromCloud(char[] password) {
    try {
      if (!isAuthenticated()) {
        initialize();
      }

      String latestFilename = findLatestBackupFilename();
      if (latestFilename == null) {
        return new SyncResult(false, "Žádná záloha v cloudu nebyla nalezena");
      }

      String fileId = googleDriveClient.findFileByName(latestFilename);
      if (fileId == null) {
        return new SyncResult(false, "Záloha '" + latestFilename + "' nebyla nalezena");
      }

      byte[] encryptedData = googleDriveClient.downloadFile(fileId);
      ByteArrayInputStream bais = new ByteArrayInputStream(encryptedData);
      Settings.importFromEncryptedBackup(bais, password);

      Settings.setLastCloudSyncTimestamp(System.currentTimeMillis());
      return new SyncResult(true, "Obnova úspěšně dokončena z " + latestFilename);

    } catch (Exception e) {
      AppLog.error("Chyba při obnově z cloudu: " + e.getMessage(), e);
      return new SyncResult(false, "Chyba při obnově: " + e.getMessage());
    }
  }

  public SyncResult backupTransactionFile(String filePath, char[] password) {
    try {
      if (!isAuthenticated()) {
        initialize();
      }

      Path path = Paths.get(filePath);
      if (!Files.exists(path)) {
        return new SyncResult(false, "Soubor neexistuje: " + filePath);
      }

      byte[] fileData = Files.readAllBytes(path);
      byte[] encryptedData = EncryptionUtils.encryptData(fileData, password);

      String filename = BACKUP_FILENAME_PREFIX + path.getFileName().toString() + "-" + generateTimestamp() + BACKUP_FILENAME_SUFFIX;

      String existingFileId = googleDriveClient.findFileByName(filename);
      if (existingFileId != null) {
        googleDriveClient.updateFile(existingFileId, encryptedData, "application/octet-stream");
      } else {
        googleDriveClient.uploadFile(filename, encryptedData, "application/octet-stream");
      }

      Settings.setLastCloudSyncTimestamp(System.currentTimeMillis());
      return new SyncResult(true, "Soubor úspěšně odeslán do Google Drive (" + filename + ")");

    } catch (Exception e) {
      AppLog.error("Chyba při záloze souboru: " + e.getMessage(), e);
      return new SyncResult(false, "Chyba při záloze souboru: " + e.getMessage());
    }
  }

  public SyncResult checkSyncStatus() {
    try {
      if (!isAuthenticated()) {
        return new SyncResult(false, "Není připojeno k Google Drive");
      }

      String latestCloudFilename = findLatestBackupFilename();
      if (latestCloudFilename == null) {
        return new SyncResult(true, "V cloudu nejsou žádné zálohy");
      }

      String fileId = googleDriveClient.findFileByName(latestCloudFilename);
      if (fileId == null) {
        return new SyncResult(false, "Nebyla nalezena záloha v cloudu");
      }

      Drive drive = googleDriveClient.getDriveService();
      File file = drive.files().get(fileId).setFields("modifiedTime").execute();
      long cloudTimestamp = file.getModifiedTime().getValue();
      long localTimestamp = Settings.getLastCloudSyncTimestamp();

      if (localTimestamp == 0) {
        return new SyncResult(true, "Místní synchronizace ještě nebyla provedena. Cloud: " + formatTimestamp(cloudTimestamp));
      }

      long diff = Math.abs(cloudTimestamp - localTimestamp);
      if (diff > 60000) {
        SyncResult.ConflictInfo conflict = new SyncResult.ConflictInfo(localTimestamp, cloudTimestamp, latestCloudFilename);
        return new SyncResult(true, "Detekován rozdíl v datech", conflict);
      }

      return new SyncResult(true, "Data jsou synchronizována");

    } catch (Exception e) {
      AppLog.error("Chyba při kontrole stavu synchronizace: " + e.getMessage(), e);
      return new SyncResult(false, "Chyba při kontrole stavu: " + e.getMessage());
    }
  }

  public boolean canAutoRestore() {
    if (!isAuthenticated()) {
      return false;
    }
    try {
      String latestFilename = findLatestBackupFilename();
      return latestFilename != null;
    } catch (Exception e) {
      return false;
    }
  }

  private String generateBackupFilename() {
    return BACKUP_FILENAME_PREFIX + "Settings-" + generateTimestamp() + BACKUP_FILENAME_SUFFIX;
  }

  private String generateTimestamp() {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
    return sdf.format(new Date());
  }

  private String findLatestBackupFilename() throws IOException {
    String query = "name contains '" + BACKUP_FILENAME_PREFIX + "' and trashed = false";
    com.google.api.services.drive.Drive.Files.List request = googleDriveClient.getDriveService().files()
        .list()
        .setQ(query)
        .setSpaces("appDataFolder")
        .setFields("files(id, name, modifiedTime)")
        .setOrderBy("modifiedTime desc");

    com.google.api.services.drive.model.FileList result = request.execute();
    java.util.List<File> files = result.getFiles();

    if (files == null || files.isEmpty()) {
      return null;
    }

    return files.get(0).getName();
  }

  private String formatTimestamp(long timestamp) {
    Date date = new Date(timestamp);
    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
    return sdf.format(date);
  }

  public void autoBackup(char[] password) {
    try {
      backupToCloud(password);
    } catch (Exception e) {
      AppLog.warn("Automatická záloha se nepodařila: " + e.getMessage());
    }
  }

  public void autoRestore(char[] password) {
    try {
      if (canAutoRestore()) {
        restoreFromCloud(password);
      }
    } catch (Exception e) {
      AppLog.warn("Automatická obnova se nepodařila: " + e.getMessage());
    }
  }
}
