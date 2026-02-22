package cz.datesoft.stockAccounting;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.FileList;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

public class GoogleDriveClient {
  private static final String APPLICATION_NAME = "StockAccounting Cloud Sync";
  private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
  private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE_APPDATA);
  private static final String TOKENS_DIRECTORY_PATH = "tokens";
  private static final String APPDATA_FOLDER = "appDataFolder";

  private Drive driveService;
  private Credential credential;

  public GoogleDriveClient() {
  }

  public void initialize() throws IOException, GeneralSecurityException {
    final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    this.credential = authorize(httpTransport);
    this.driveService = new Drive.Builder(httpTransport, JSON_FACTORY, credential)
        .setApplicationName(APPLICATION_NAME)
        .build();
  }

  private Credential authorize(final NetHttpTransport httpTransport) throws IOException {
    InputStream in = GoogleDriveClient.class.getResourceAsStream("/client_secrets.json");
    if (in == null) {
      throw new FileNotFoundException("Client secrets file not found in resources/client_secrets.json");
    }
    GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

    Path tokensPath = Path.of(System.getProperty("user.home"), ".stockaccounting", "tokens");
    FileDataStoreFactory dataStoreFactory = new FileDataStoreFactory(tokensPath.toFile());

    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
        httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
        .setDataStoreFactory(dataStoreFactory)
        .setAccessType("offline")
        .build();

    LocalServerReceiver receiver = new LocalServerReceiver.Builder()
        .setPort(8888)
        .build();

    return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
  }

  public String uploadFile(String filename, byte[] content, String mimeType) throws IOException {
    com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
    fileMetadata.setName(filename);
    fileMetadata.setParents(Collections.singletonList(APPDATA_FOLDER));

    ByteArrayContent mediaContent = new ByteArrayContent(mimeType, content);

    com.google.api.services.drive.model.File file = driveService.files().create(fileMetadata, mediaContent)
        .setFields("id")
        .execute();

    return file.getId();
  }

  public byte[] downloadFile(String fileId) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    driveService.files().get(fileId)
        .executeMediaAndDownloadTo(outputStream);
    return outputStream.toByteArray();
  }

  public String findFileByName(String filename) throws IOException {
    String query = "name = '" + filename + "' and '" + APPDATA_FOLDER + "' in parents and trashed = false";
    FileList result = driveService.files().list()
        .setQ(query)
        .setSpaces(APPDATA_FOLDER)
        .setFields("files(id, name, modifiedTime)")
        .execute();

    List<com.google.api.services.drive.model.File> files = result.getFiles();
    if (files == null || files.isEmpty()) {
      return null;
    }
    return files.get(0).getId();
  }

  public void updateFile(String fileId, byte[] content, String mimeType) throws IOException {
    ByteArrayContent mediaContent = new ByteArrayContent(mimeType, content);

    driveService.files().update(fileId, null, mediaContent)
        .execute();
  }

  public void deleteFile(String fileId) throws IOException {
    driveService.files().delete(fileId).execute();
  }

  public boolean isAuthenticated() {
    return credential != null && credential.getAccessToken() != null;
  }

  public void revokeAuthentication() throws IOException {
    if (credential != null) {
      credential = null;
      driveService = null;
    }
  }

  public Drive getDriveService() {
    return driveService;
  }
}
