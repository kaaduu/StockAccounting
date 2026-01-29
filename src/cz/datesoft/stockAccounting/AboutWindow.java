/*
 * AboutDialog.java
 *
 * Created on 11. listopad 2006, 21:22
 */

package cz.datesoft.stockAccounting;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

/**
 *
 * @author  lemming2
 */
public class AboutWindow extends javax.swing.JDialog
{
  
   /** Creates new form AboutDialog */
   public AboutWindow(java.awt.Frame parent, boolean modal)
   {
     super(parent, modal);
     initComponents();

      setLocationByPlatform(true);
      // Prefer pack-based sizing; allow user resize.
      setResizable(true);
    
     // Set icon
     iconButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("images/dolar.png")));

     // Generate dynamic content with version and system information
     String version = getVersionFromGit();
     String javaInfo = getJavaInfo();
     String systemInfo = getSystemInfo();

       String htmlContent = "<html>" +
           "<body style='font-family:sans-serif;'>" +
           "<div style='margin:0 0 10px 0;'>" +
           "  <div style='font-size:16px;font-weight:700;margin:0 0 2px 0;'>Akciové účetnictví</div>" +
           "  <div style='font-size:12px;color:#666;margin:0 0 8px 0;'>verze <b>" + version + "</b></div>" +
           "</div>" +
           "<div style='margin:0 0 10px 0;'>" +
           "  Zdrojové kódy: <a href='https://github.com/kaaduu/StockAccounting'>GitHub</a><br/>" +
           "  Licence: <a href='https://www.gnu.org/licenses/gpl-3.0.html'>GPL-3.0</a>" +
           "</div>" +
           "<div style='margin:0 0 10px 0;'>" +
           "  <b>Java Runtime:</b> " + javaInfo + "<br/>" +
           "  <b>Operační systém:</b> " + systemInfo + "" +
           "</div>" +
           "</body></html>";

      jEditorPane1.setText(htmlContent);
      pack();
    
    //Enable link handler (open link in browser)
    jEditorPane1.addHyperlinkListener(new HyperlinkListener() {
    public void hyperlinkUpdate(HyperlinkEvent e) {
        if(e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
           if(Desktop.isDesktopSupported()) {
               try {
                   Desktop.getDesktop().browse(e.getURL().toURI());
               } catch (URISyntaxException ex) {
                   Logger.getLogger(AboutWindow.class.getName()).log(Level.SEVERE, null, ex);
               } catch (IOException ex) {
                   Logger.getLogger(AboutWindow.class.getName()).log(Level.SEVERE, null, ex);
               }
            }
        }
    }
});
    //Enable link handler (open link in browser)
        jEditorPane2.addHyperlinkListener(new HyperlinkListener() {
    public void hyperlinkUpdate(HyperlinkEvent e) {
        if(e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
           if(Desktop.isDesktopSupported()) {
               try {
                   Desktop.getDesktop().browse(e.getURL().toURI());
               } catch (URISyntaxException ex) {
                   Logger.getLogger(AboutWindow.class.getName()).log(Level.SEVERE, null, ex);
               } catch (IOException ex) {
                   Logger.getLogger(AboutWindow.class.getName()).log(Level.SEVERE, null, ex);
               }
            }
        }
    }
});
    
    
   }

   /**
    * Get version information from git with fallback chain
    */
   private String getVersionFromGit() {
       // Solution 1: Try git describe (preferred)
       String version = tryGitDescribe();
       if (version != null) return version;

       // Solution 2: Try reading version file (build-time injection)
       version = tryReadVersionFile();
       if (version != null) return version;

       // Solution 3: Ultimate fallback
       return "dev-build";
   }

   private String tryGitDescribe() {
       try {
           Process process = Runtime.getRuntime().exec("git describe --tags --always");
           BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
           String version = reader.readLine();
           int exitCode = process.waitFor();
           if (exitCode == 0 && version != null && !version.trim().isEmpty()) {
               return version.trim();
           }
       } catch (Exception e) {
           // Silently handle git not available
       }
       return null;
   }

   private String tryReadVersionFile() {
       try {
           // Read version.properties from classpath
           Properties props = new Properties();
           props.load(getClass().getResourceAsStream("/version.properties"));
           String version = props.getProperty("version");
           if (version != null && !version.trim().isEmpty()) {
               return version.trim();
           }
       } catch (Exception e) {
           // Silently handle missing version file
       }
       return null;
   }

   private String getJavaInfo() {
       String version = System.getProperty("java.version", "Unknown");
       String vendor = System.getProperty("java.vendor", "Unknown");
       return String.format("%s (%s)", version, vendor);
   }

   private String getSystemInfo() {
       String osName = System.getProperty("os.name", "Unknown");
       String osVersion = System.getProperty("os.version", "Unknown");
       return String.format("%s %s", osName, osVersion);
   }

   /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        iconButton = new javax.swing.JButton();
        jEditorPane1 = new javax.swing.JEditorPane();
        jPanel1 = new javax.swing.JPanel();
        bClose = new javax.swing.JButton();
        jEditorPane2 = new javax.swing.JEditorPane();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("O aplikaci");

        iconButton.setAlignmentX(0.5F);
        iconButton.setBorderPainted(false);
        iconButton.setFocusPainted(false);
        iconButton.setFocusable(false);

        jEditorPane1.setEditable(false);
        jEditorPane1.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        jEditorPane1.setContentType("text/html"); // NOI18N
        jEditorPane1.setFont(jEditorPane1.getFont().deriveFont(java.awt.Font.BOLD));
        jEditorPane1.setText("<html>\n  <head>\n\n  </head>\n  <body>\n    <p style=\"margin-top: 0\">\n      Akciové účetnictví verze 2022-05 rev 1 (<a href=\"http://lemming.ucw.cz/ucetnictvi/\">vychazi z puvodni verze 1.2.7</a> - Michal Kára)<br> \n     Zdrojove kody na <a href=\"https://github.com/kaaduu/StockAccounting\">githubu</a> - vydano pod licencí GPL\n    </p>\n  </body>\n</html>\n");
        jEditorPane1.setToolTipText("");
        jEditorPane1.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        jEditorPane1.setOpaque(false);

        jPanel1.setLayout(new java.awt.GridBagLayout());

        bClose.setText("Zavřít");
        bClose.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bCloseActionPerformed(evt);
            }
        });
        jPanel1.add(bClose, new java.awt.GridBagConstraints());

        jEditorPane2.setEditable(false);
        jEditorPane2.setBorder(null);
        jEditorPane2.setContentType("text/html"); // NOI18N
        jEditorPane2.setFont(jEditorPane2.getFont().deriveFont(java.awt.Font.PLAIN, 12f));
        jEditorPane2.setText("<html>\n  <head>\n    <style type=\"text/css\">\n     .style1 { color: #000000; font-family: Verdana, Arial, Helvetica, sans-serif; font-size: 8px;  }\n     .style2 { font-family: Verdana, Arial, Helvetica, sans-serif;\tcolor: red;\t font-size:0.9em; }\n  </style>  \n  </head>\n  <body>\n<div class=\"style2\">\nUpozornění: Použití tohoto programu je pouze na vlastní nebezpečí!\nAutor neručí za jeho metodickou ani výpočetní správnost!<br>\n</div>\n<div class=\"style1\">\nPlná historie změn v <a href=\"https://github.com/kaaduu/StockAccounting/blob/master/CHANGES.md\">CHANGES.md na GitHubu</a><br>\n<b>Podpora importu pro:<b><br>\n<ul>\n<li>Interactive Brokers - <a href=\"https://github.com/kaaduu/StockAccounting/wiki/Interactive-Brokers-tradelog\">TradeLog</a>  (tlg |)\n<li>Interactive Brokers - <a href=\"https://github.com/kaaduu/StockAccounting/wiki/Interactive-Brokers---FlexiQuery\">FlexQuery</a> (csv ,)\n<li>FIO - <a href=\"http://lemming.ucw.cz/ucetnictvi/importWindow.html\">csv obchodu</a> (csv ;) \n<li>Trading 212 Invest - <a href=\"https://github.com/kaaduu/StockAccounting/wiki/Trading-212-Invest-csv\">export csv</a>\n<li>Revolut - <a href=\"https://github.com/kaaduu/StockAccounting/wiki/Revolut-csv\">export csv</a>\n</ul>\n\n</div>\n</body>\n</html>\n");
        jEditorPane2.setOpaque(false);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(iconButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 53, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jEditorPane1))
                    .add(jEditorPane2)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 679, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(iconButton, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(jEditorPane1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 58, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jEditorPane2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 166, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 77, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        pack();
        setMinimumSize(new java.awt.Dimension(720, 360));
    }// </editor-fold>//GEN-END:initComponents

  private void bCloseActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_bCloseActionPerformed
  {//GEN-HEADEREND:event_bCloseActionPerformed
    setVisible(false);
  }//GEN-LAST:event_bCloseActionPerformed
  
  /**
   * @param args the command line arguments
   */
  public static void main(String args[])
  {
    java.awt.EventQueue.invokeLater(new Runnable()
    {
      public void run()
      {
        new AboutWindow(new javax.swing.JFrame(), true).setVisible(true);
      }
    });
  }
  
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bClose;
    private javax.swing.JButton iconButton;
    private javax.swing.JEditorPane jEditorPane1;
    private javax.swing.JEditorPane jEditorPane2;
    private javax.swing.JPanel jPanel1;
    // End of variables declaration//GEN-END:variables
  
}
