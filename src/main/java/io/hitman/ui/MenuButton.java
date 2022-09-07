package io.hitman.ui;

import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import lombok.Getter;

public class MenuButton extends JToggleButton {

  @Getter
  private JPopupMenu popup;

  public MenuButton() {
    this.popup = new JPopupMenu();

    addActionListener(ev -> {
      JToggleButton b = MenuButton.this;
      if (b.isSelected()) {
        popup.show(b, 0, b.getBounds().height);
      } else {
        popup.setVisible(false);
      }
    });

    popup.addPopupMenuListener(new PopupMenuListener() {
      @Override
      public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
      }

      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        MenuButton.this.setSelected(false);
      }

      @Override
      public void popupMenuCanceled(PopupMenuEvent e) {
      }
    });
  }
}
