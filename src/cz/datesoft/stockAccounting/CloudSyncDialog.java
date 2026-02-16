package cz.datesoft.stockAccounting;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CloudSyncDialog extends JDialog {
  private JPasswordField pfPassword;
  private JButton btnOK;
  private JButton btnCancel;
  private boolean confirmed = false;
  private char[] password;
  private JLabel lblStatus;
  private JProgressBar progressBar;
  private Timer progressTimer;

  public CloudSyncDialog(java.awt.Window parent, String title, String message) {
    super(parent, title, Dialog.ModalityType.APPLICATION_MODAL);
    initComponents(message);
    setLocationRelativeTo(parent);
  }

  private void initComponents(String message) {
    setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    setLayout(new BorderLayout(10, 10));

    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(5, 5, 5, 5);
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.HORIZONTAL;

    JLabel lblMessage = new JLabel(message);
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 2;
    panel.add(lblMessage, gbc);

    JLabel lblPassword = new JLabel("Heslo pro šifrování:");
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.gridwidth = 1;
    panel.add(lblPassword, gbc);

    pfPassword = new JPasswordField(20);
    gbc.gridx = 1;
    gbc.gridy = 1;
    panel.add(pfPassword, gbc);

    lblStatus = new JLabel(" ");
    lblStatus.setForeground(new Color(0, 0, 200));
    gbc.gridx = 0;
    gbc.gridy = 2;
    gbc.gridwidth = 2;
    panel.add(lblStatus, gbc);

    progressBar = new JProgressBar();
    progressBar.setIndeterminate(true);
    progressBar.setVisible(false);
    gbc.gridx = 0;
    gbc.gridy = 3;
    gbc.gridwidth = 2;
    panel.add(progressBar, gbc);

    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    btnOK = new JButton("OK");
    btnCancel = new JButton("Storno");
    buttonPanel.add(btnOK);
    buttonPanel.add(btnCancel);

    gbc.gridx = 0;
    gbc.gridy = 4;
    gbc.gridwidth = 2;
    panel.add(buttonPanel, gbc);

    add(panel, BorderLayout.CENTER);
    pack();

    btnOK.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        password = pfPassword.getPassword();
        confirmed = true;
        dispose();
      }
    });

    btnCancel.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        password = null;
        confirmed = false;
        dispose();
      }
    });

    pfPassword.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        btnOK.doClick();
      }
    });
  }

  public static char[] showPasswordDialog(java.awt.Window parent) {
    CloudSyncDialog dialog = new CloudSyncDialog(parent, "Zálohování/Obnova", "Zadejte heslo pro šifrování:");
    dialog.setVisible(true);
    return dialog.confirmed ? dialog.password : null;
  }

  public static void showOperationInProgress(java.awt.Window parent, String message) {
    CloudSyncDialog dialog = new CloudSyncDialog(parent, "Pracuji...", message);
    dialog.setButtonsEnabled(false);
    dialog.showProgress();
    dialog.setVisible(true);
  }

  public static boolean showConflictDialog(java.awt.Window parent, SyncResult.ConflictInfo conflict) {
    String[] options = {"Místní data", "Data z cloudu", "Zrušit"};
    String message = "Konflikt synchronizace:\n\n" +
        "Místní: " + conflict.getLocalTimestamp() + "\n" +
        "Cloud: " + conflict.getCloudTimestamp() + "\n\n" +
        "Kterou verzi chcete použít?";

    int choice = JOptionPane.showOptionDialog(
        parent,
        message,
        "Konflikt synchronizace",
        JOptionPane.YES_NO_CANCEL_OPTION,
        JOptionPane.QUESTION_MESSAGE,
        null,
        options,
        options[0]);

    return choice == 1;
  }

  public static void showResult(java.awt.Window parent, SyncResult result) {
    int messageType = result.isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE;
    JOptionPane.showMessageDialog(parent, result.getMessage(), "Výsledek synchronizace", messageType);
  }

  private void setButtonsEnabled(boolean enabled) {
    btnOK.setEnabled(enabled);
    btnCancel.setEnabled(enabled);
    pfPassword.setEnabled(enabled);
  }

  private void showProgress() {
    progressBar.setVisible(true);
    lblStatus.setVisible(true);
  }

  public void setStatus(String status) {
    lblStatus.setText(status);
  }

  public void setProgress(boolean visible) {
    progressBar.setVisible(visible);
  }

  public static char[] showBackupPasswordDialog(java.awt.Window parent) {
    CloudSyncDialog dialog = new CloudSyncDialog(parent, "Zálohování do cloudu",
        "Zadejte heslo pro šifrování zálohy (pamatujte si ho, nebude možné ho obnovit!):");
    dialog.setVisible(true);
    return dialog.confirmed ? dialog.password : null;
  }

  public static char[] showRestorePasswordDialog(java.awt.Window parent) {
    CloudSyncDialog dialog = new CloudSyncDialog(parent, "Obnova z cloudu",
        "Zadejte heslo pro dešifrování zálohy:");
    dialog.setVisible(true);
    return dialog.confirmed ? dialog.password : null;
  }
}
