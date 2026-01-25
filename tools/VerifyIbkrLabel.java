package cz.datesoft.stockAccounting;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Small helper to prove what the compiled ImportWindow contains.
 * Not used by the application.
 */
public final class VerifyIbkrLabel {
  public static void main(String[] args) throws Exception {
    String classesDir = args.length > 0 ? args[0] : "manual_build";
    Path classesPath = Paths.get(classesDir);
    Path importWindowClass = classesPath.resolve("cz/datesoft/stockAccounting/ImportWindow.class");

    byte[] bytes = Files.readAllBytes(importWindowClass);
    String haystack = new String(bytes, StandardCharsets.ISO_8859_1);
    String needle = "IBKR Flex API/soubor";

    System.out.println("ImportWindow.class: " + importWindowClass.toAbsolutePath());
    System.out.println("Contains '" + needle + "': " + haystack.contains(needle));
  }
}
