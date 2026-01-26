package cz.datesoft.stockAccounting;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * Standard dialogs with optional details.
 */
public final class UiDialogs {
  private UiDialogs() {
  }

  public static void info(java.awt.Component parent, String message, String title) {
    JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
    AppLog.info(strip(message));
  }

  public static void warn(java.awt.Component parent, String message, String title) {
    JOptionPane.showMessageDialog(parent, message, title, JOptionPane.WARNING_MESSAGE);
    AppLog.warn(strip(message));
  }

  public static void error(java.awt.Component parent, String message, String title, Throwable t) {
    showErrorWithDetails(parent, message, title, t);
    AppLog.error(strip(message), t);
  }

  public static void showErrorWithDetails(java.awt.Component parent, String message, String title, Throwable t) {
    if (t == null) {
      JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE);
      return;
    }

    Object[] options = new Object[] { "OK", "Detaily..." };
    int res = JOptionPane.showOptionDialog(parent, message, title,
        JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE,
        null, options, options[0]);

    if (res != 1)
      return;

    JTextArea ta = new JTextArea(stackTrace(t));
    ta.setEditable(false);
    ta.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
    ta.setCaretPosition(0);

    JScrollPane sp = new JScrollPane(ta);
    sp.setPreferredSize(new Dimension(860, 420));

    JPanel panel = new JPanel(new BorderLayout(8, 8));
    panel.add(sp, BorderLayout.CENTER);

    JOptionPane pane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE);
    JDialog dialog = pane.createDialog(parent, title + " - detaily");
    dialog.setResizable(true);
    dialog.setVisible(true);
  }

  private static String stackTrace(Throwable t) {
    try {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      t.printStackTrace(pw);
      pw.flush();
      return sw.toString();
    } catch (Exception e) {
      return String.valueOf(t);
    }
  }

  private static String strip(String s) {
    if (s == null)
      return "";
    return s.replaceAll("\\s+", " ").trim();
  }
}
