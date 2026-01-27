package cz.datesoft.stockAccounting;

import java.awt.Font;

import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

/**
 * Centralized UI font configuration.
 */
public final class UiFonts {
  private UiFonts() {
  }

  public static void applyFromSettings() {
    FontUIResource ui = getUiFontOverride();
    if (ui == null) {
      return;
    }

    // FlatLaf supports a single defaultFont key.
    UIManager.put("defaultFont", ui);

    // Other Look&Feels need per-key override.
    java.util.Enumeration<Object> keys = UIManager.getDefaults().keys();
    while (keys.hasMoreElements()) {
      Object key = keys.nextElement();
      Object v = UIManager.get(key);
      if (v instanceof FontUIResource) {
        UIManager.put(key, ui);
      }
    }
  }

  public static Font monospaceFont() {
    String family = Settings.getMonospaceFontFamily();
    int size = Settings.getMonospaceFontSize();

    if (family == null || family.trim().isEmpty()) {
      family = "Monospaced";
    }
    if (size <= 0) {
      size = 12;
    }

    return new Font(family, Font.PLAIN, size);
  }

  private static FontUIResource getUiFontOverride() {
    String family = Settings.getUiFontFamily();
    int size = Settings.getUiFontSize();

    if (family == null || family.trim().isEmpty()) {
      return null;
    }
    if (size <= 0) {
      return null;
    }

    return new FontUIResource(new Font(family, Font.PLAIN, size));
  }
}
