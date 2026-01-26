/*
 * Main.java
 *
 * Created on 5. rijen 2006, 23:31
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package cz.datesoft.stockAccounting;

/**
 *
 * @author lemming2
 */
public class Main {

  /**
   * Main window
   */
  private static MainWindow mainWindow;
    
  /**
   * @param args the command line arguments
   */
  public static void main(String[] args)
  {
    try {
      // Prefer modern cross-platform Look&Feel.
      com.formdev.flatlaf.FlatLightLaf.setup();
      javax.swing.UIManager.put("Component.arc", 12);
      javax.swing.UIManager.put("Button.arc", 12);
      javax.swing.UIManager.put("TextComponent.arc", 10);
      javax.swing.UIManager.put("ScrollBar.thumbArc", 999);
      javax.swing.UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2, 2, 2));
      javax.swing.UIManager.put("ScrollBar.trackArc", 999);
    } catch (Exception e) { }
      
    Settings.load();
    
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
