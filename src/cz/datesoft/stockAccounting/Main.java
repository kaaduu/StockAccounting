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
      javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
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
