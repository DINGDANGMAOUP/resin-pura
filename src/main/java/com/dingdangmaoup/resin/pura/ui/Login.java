package com.dingdangmaoup.resin.pura.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class Login {

  private JPanel LoginContent;
  private JTextField username;
  private JButton show;
  private JButton hide;
  private JLabel readeText;



  public Login() {
    show.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        username.setVisible(true);
        readeText.setVisible(false);
        readeText.setText("");
      }
    });
    hide.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        readeText.setText(username.getText());
        username.setVisible(false);
        readeText.setVisible(true);
      }
    });
  }

  public JPanel getLoginContent() {
    return LoginContent;
  }
}
