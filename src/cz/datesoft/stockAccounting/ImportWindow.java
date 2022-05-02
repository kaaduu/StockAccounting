/*
 * ImportWindow.java
 *
 * Created on 8. listopad 2006, 21:57
 */

package cz.datesoft.stockAccounting;

import com.toedter.calendar.JDateChooser;
import java.awt.GridBagConstraints;
import java.awt.Dimension;
import java.io.File;
import java.util.Date;
import javax.swing.JOptionPane;
import java.util.Vector;
import javax.swing.table.DefaultTableModel;
import java.util.GregorianCalendar;

/**
 *
 * @author  lemming2
 */
public class ImportWindow extends javax.swing.JDialog
{
  // Start / end date
  JDateChooser startDate;
  JDateChooser endDate;
  
  // Transaction database
  TransactionSet transactions;  
  
  // File we are importing
  File currentFile;
  
  // Main window
  MainWindow mainWindow;
  
  /** Creates new form ImportWindow */
  public ImportWindow(java.awt.Frame parent, boolean modal)
  {
    super(parent, modal);
    initComponents();
        
    mainWindow = (MainWindow)parent;

    this.setLocationByPlatform(true);
    this.setSize(800,550);
    
    GridBagConstraints gbc;

    startDate = new JDateChooser();
    startDate.setPreferredSize(new Dimension(200,20));
    
    gbc = new GridBagConstraints();
    gbc.gridx = 1;
    gbc.gridy = 1;
    gbc.gridwidth = 1;
    gbc.weightx = 1;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.insets = new java.awt.Insets(5, 5, 5, 5);
    getContentPane().add(startDate,gbc);
    
    // Clear header for not-imported rows
//    niTable.setTableHeader(null);
//    niScrollPane.setColumnHeaderView(null);

    startDate.addPropertyChangeListener(new java.beans.PropertyChangeListener()
    {
      public void propertyChange(java.beans.PropertyChangeEvent evt) { 
        loadImport();
      }
    });    

    endDate = new JDateChooser();
    endDate.setPreferredSize(new Dimension(100,20));    
    
    gbc = new GridBagConstraints();
    gbc.gridx = 3;
    gbc.gridy = 1;
    gbc.gridwidth = 1;
    gbc.weightx = 1;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.insets = new java.awt.Insets(5, 5, 5, 5);
    getContentPane().add(endDate,gbc);

    endDate.addPropertyChangeListener(new java.beans.PropertyChangeListener()
    {
      public void propertyChange(java.beans.PropertyChangeEvent evt) { 
        loadImport();
      }
    });    
    
    getContentPane().doLayout();    
    
    transactions = new TransactionSet();    
    table.setModel(transactions);
    
    table.getColumnModel().getColumn(0).setPreferredWidth(200);
    table.getColumnModel().getColumn(0).setCellRenderer(new CZDateRenderer());
    
    table.getColumnModel().getColumn(10).setCellRenderer(new CZDateRenderer());
    
//    niTable.setTableHeader(new JTableHeader());
  }
  
  /**
   * Get "records" word
   */
  private static String getRecordsWord(int n)
  {
    if (n == 1) return "záznam";
    if ((n >= 2) && (n <= 5)) return "záznamy";
    else return "záznamů";
  }
  
  /**
   * Load import from a file
   */
  private void loadImport()
  {
    // Clear not imported rows
    DefaultTableModel model = (DefaultTableModel)niTable.getModel();
    model.setNumRows(0);
    
    try {
      Vector<String[]> notImported = new Vector<String[]>();
      
      if (currentFile == null) return; // No file
      
      // Get dates, make start 00:00:00 and end 23:59:59
      Date startD, endD;
      GregorianCalendar cal = new GregorianCalendar();
      
      startD = startDate.getDate();
      if (startD != null) {
        cal.setTime(startD);
        cal.set(GregorianCalendar.HOUR_OF_DAY,0);
        cal.set(GregorianCalendar.MINUTE,0);
        cal.set(GregorianCalendar.SECOND,0);
        cal.set(GregorianCalendar.MILLISECOND,0);
        startD = cal.getTime();
      }
      
      endD = endDate.getDate();
      if (endD != null) {
        cal.setTime(endD);
        cal.set(GregorianCalendar.HOUR_OF_DAY,23);
        cal.set(GregorianCalendar.MINUTE,59);
        cal.set(GregorianCalendar.SECOND,59);
        cal.set(GregorianCalendar.MILLISECOND,999999);
        endD = cal.getTime();
      }

      if (cbFormat.getSelectedIndex() == 0) return; // Bad format

      transactions.importFile(currentFile,startD,endD,cbFormat.getSelectedIndex(), notImported);
      
      // Set labels
      int n = transactions.getRowCount();
      lPreview.setText("Náhled ("+n+" "+getRecordsWord(n)+"):");
      int rowCount = notImported.size();
      lUnimported.setText("Neimportované řádky ("+rowCount+" "+getRecordsWord(rowCount)+"):");
      
      /* Fill in data model for not imported rows */
      
      // Get number of columns
      int colCount = 0;
      for(int i=0;i<rowCount;i++) {
        n = notImported.get(i).length;
        if (n > colCount) colCount = n;
      }
      
      if (rowCount > 0) {
        model.setRowCount(rowCount);
        model.setColumnCount(colCount);
        
        // Make columns
        for(n=0;n<colCount;n++) {
          niTable.getColumnModel().getColumn(n).setHeaderValue("Col "+n);
        }
        
        // Add data
        for(int i=0;i<rowCount;i++) {
          String a[] = notImported.get(i);
          for(n=0;n<a.length;n++) {
            model.setValueAt(a[n],i,n);
          }
          // Set nulls for not used columns
          for(;n<colCount;n++) {
            model.setValueAt(null,i,n);
          }
        }
      }
      else {
        model.setRowCount(0);
      }
    }
    catch (java.io.FileNotFoundException e) {
      JOptionPane.showMessageDialog(this, "Soubor nenalezen!");
    }
    catch (java.io.IOException e) {
      JOptionPane.showMessageDialog(this, "Chyba čtení: " +e.getLocalizedMessage());
    }
    catch (cz.datesoft.stockAccounting.imp.ImportException e) {
      JOptionPane.showMessageDialog(this, "Chyba při importu: "+e.getMessage());
    }
  }
  
  /**
   * Start import - show ourselves and do first initial import if format was already set
   */
  public void startImport(File file, Date startDateValue)
  {
    currentFile = null;
    
    startDate.setDate(startDateValue);
    endDate.setDate(null);
    
    currentFile = file;

    if (cbFormat.getSelectedIndex() != 0) loadImport();
    
    setVisible(true);    
  }
  
  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        cbFormat = new javax.swing.JComboBox();
        bRefresh = new javax.swing.JButton();
        bImport = new javax.swing.JButton();
        jPanel2 = new javax.swing.JPanel();
        bCancel = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        table = new javax.swing.JTable();
        lPreview = new javax.swing.JLabel();
        lUnimported = new javax.swing.JLabel();
        niScrollPane = new javax.swing.JScrollPane();
        niTable = new javax.swing.JTable();

        setTitle("Import souboru");
        setMaximumSize(new java.awt.Dimension(1024, 1024));
        setModal(true);
        getContentPane().setLayout(new java.awt.GridBagLayout());

        jLabel1.setText("Importovat od:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        getContentPane().add(jLabel1, gridBagConstraints);

        jLabel2.setText("do:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        getContentPane().add(jLabel2, gridBagConstraints);

        jLabel4.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        jLabel4.setText("Formát:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        getContentPane().add(jLabel4, gridBagConstraints);

        cbFormat.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "<vyberte formát>", "Fio - obchody export", "BrokerJet - HTML export (legacy)", "IB - TradeLog", "IB - FlexQuery Trades only CSV", "T212 Invest  - csv  mena: USD", "T212 Invest  - csv  mena: CZK", "Revolut - csv" }));
        cbFormat.setMinimumSize(new java.awt.Dimension(100, 20));
        cbFormat.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cbFormatActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        getContentPane().add(cbFormat, gridBagConstraints);

        bRefresh.setText("Aktualizovat náhled");
        bRefresh.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bRefreshActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        getContentPane().add(bRefresh, gridBagConstraints);

        bImport.setText("Importovat");
        bImport.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bImportActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        getContentPane().add(bImport, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        getContentPane().add(jPanel2, gridBagConstraints);

        bCancel.setText("Storno");
        bCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bCancelActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        getContentPane().add(bCancel, gridBagConstraints);

        table.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null},
                {null},
                {null},
                {null}
            },
            new String [] {
                "Columns..."
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        table.setEnabled(false);
        jScrollPane1.setViewportView(table);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 2.0;
        getContentPane().add(jScrollPane1, gridBagConstraints);

        lPreview.setText("Náhled (0 záznamů):");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        getContentPane().add(lPreview, gridBagConstraints);

        lUnimported.setText("Neimportované řádky (0 záznamů):");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        getContentPane().add(lUnimported, gridBagConstraints);

        niTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null},
                {null},
                {null},
                {null}
            },
            new String [] {
                "Columns..."
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        niTable.setEnabled(false);
        niScrollPane.setViewportView(niTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        getContentPane().add(niScrollPane, gridBagConstraints);

        pack();
    }// </editor-fold>//GEN-END:initComponents

  private void bImportActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_bImportActionPerformed
  {//GEN-HEADEREND:event_bImportActionPerformed
    // Really do import
    try {
      transactions.mergeTo(mainWindow.getTransactionDatabase());
    }
    catch(Exception e) {
      e.printStackTrace();
      JOptionPane.showMessageDialog(this, "Při importu došlo k chybě: "+e+"\nByla importována jen číst záznamů.");
    }
    
    setVisible(false);
  }//GEN-LAST:event_bImportActionPerformed

  private void bRefreshActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_bRefreshActionPerformed
  {//GEN-HEADEREND:event_bRefreshActionPerformed
    loadImport();
  }//GEN-LAST:event_bRefreshActionPerformed

  private void bCancelActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_bCancelActionPerformed
  {//GEN-HEADEREND:event_bCancelActionPerformed
    setVisible(false);
  }//GEN-LAST:event_bCancelActionPerformed

  private void cbFormatActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cbFormatActionPerformed
    
    if (cbFormat.getSelectedIndex() != 0) loadImport();
    
  }//GEN-LAST:event_cbFormatActionPerformed
  
  /**
   * @param args the command line arguments
   */
  public static void main(String args[])
  {
    java.awt.EventQueue.invokeLater(new Runnable()
    {
      public void run()
      {
        new ImportWindow(new javax.swing.JFrame(), true).setVisible(true);
      }
    });
  }
  
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bCancel;
    private javax.swing.JButton bImport;
    private javax.swing.JButton bRefresh;
    private javax.swing.JComboBox cbFormat;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel lPreview;
    private javax.swing.JLabel lUnimported;
    private javax.swing.JScrollPane niScrollPane;
    private javax.swing.JTable niTable;
    private javax.swing.JTable table;
    // End of variables declaration//GEN-END:variables
  
}
