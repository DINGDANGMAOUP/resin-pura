package com.dingdangmaoup.resin.pura.ui;

import com.dingdangmaoup.resin.pura.ResinBundle;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextArea;

public class ShowResinConfDialog extends JDialog {
  private JPanel contentPane;
  private JButton buttonOK;
  private JTextArea textArea;

  public ShowResinConfDialog(String text) {
    setContentPane(contentPane);
    setModal(true);
    getRootPane().setDefaultButton(buttonOK);

    buttonOK.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        onOK();
      }
    });

    setTitle(ResinBundle.message("message.text.resin.conf.altered"));

    textArea.setText(text);
    textArea.setEditable(false);
    contentPane.setPreferredSize(new Dimension(640, 480));
  }

  private void onOK() {
    // add your code here
    dispose();
  }
}
