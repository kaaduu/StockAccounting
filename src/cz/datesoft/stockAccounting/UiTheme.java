package cz.datesoft.stockAccounting;

/**
 * UI theme/Look&Feel configuration.
 */
public final class UiTheme {
  private UiTheme() {
  }

  public static void refreshAllWindows() {
    try {
      java.awt.Window[] windows = java.awt.Window.getWindows();
      if (windows == null)
        return;
      for (java.awt.Window w : windows) {
        if (w == null)
          continue;
        javax.swing.SwingUtilities.updateComponentTreeUI(w);
        w.invalidate();
        w.validate();
        w.repaint();
      }
    } catch (Exception e) {
      // ignore
    }
  }

  public static void applyFromSettings() {
    apply(Settings.getUiTheme());
  }

  public static void apply(int theme) {
    try {
      boolean isFlatLaf = (theme != Settings.THEME_SYSTEM);
      if (isFlatLaf) {
        // FlatLaf defaults (rounded corners etc.)
        javax.swing.UIManager.put("Component.arc", 12);
        javax.swing.UIManager.put("Button.arc", 12);
        javax.swing.UIManager.put("TextComponent.arc", 10);
        javax.swing.UIManager.put("ScrollBar.thumbArc", 999);
        javax.swing.UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2, 2, 2));
        javax.swing.UIManager.put("ScrollBar.trackArc", 999);

        // Table defaults (readability)
        javax.swing.UIManager.put("Table.rowHeight", Integer.valueOf(24));
        javax.swing.UIManager.put("Table.showHorizontalLines", Boolean.TRUE);
        javax.swing.UIManager.put("Table.showVerticalLines", Boolean.FALSE);
        javax.swing.UIManager.put("Table.intercellSpacing", new java.awt.Dimension(0, 1));

        // Dialog/button defaults
        javax.swing.UIManager.put("OptionPane.buttonPadding", Integer.valueOf(10));
      }

      switch (theme) {
        case Settings.THEME_SYSTEM:
          javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
          break;
        case Settings.THEME_FLAT_DARK:
          com.formdev.flatlaf.FlatDarkLaf.setup();
          break;
        case Settings.THEME_FLAT_INTELLIJ:
          com.formdev.flatlaf.FlatIntelliJLaf.setup();
          break;
        case Settings.THEME_FLAT_DARCULA:
          com.formdev.flatlaf.FlatDarculaLaf.setup();
          break;
        case Settings.THEME_FLAT_LIGHT:
        default:
          com.formdev.flatlaf.FlatLightLaf.setup();
          break;
      }

      // Apply UI font overrides after Look&Feel is installed.
      UiFonts.applyFromSettings();
    } catch (Exception e) {
      // Best effort fallback
      try {
        com.formdev.flatlaf.FlatLightLaf.setup();
        UiFonts.applyFromSettings();
      } catch (Exception ignored) {
      }
    }
  }
}
