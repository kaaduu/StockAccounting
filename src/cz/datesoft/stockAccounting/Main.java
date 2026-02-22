/*
 * Main.java
 *
 * Created on 5. rijen 2006, 23:31
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package cz.datesoft.stockAccounting;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author lemming2
 */
public class Main {

  private static final Logger logger = Logger.getLogger(Main.class.getName());

  /**
   * Main window
   */
  private static MainWindow mainWindow;
    
  /**
   * @param args the command line arguments
   */
  public static void main(String[] args)
  {
    // Load persisted settings first (theme selection is stored there).
    Settings.load();

    // Apply Look&Feel before any Swing components are created.
    try {
      UiTheme.applyFromSettings();
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Failed to apply UI theme", e);
    }
    
    mainWindow = new MainWindow();
    
    mainWindow.refreshCurrenciesCombo();
    
    mainWindow.setVisible(true);
  }
  
  /**
   * Main window getter
   */
  public static MainWindow getMainWindow()
  {
    return mainWindow;
  }
    
}
