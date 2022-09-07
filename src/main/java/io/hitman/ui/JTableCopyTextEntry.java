package io.hitman.ui;

import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JTableCopyTextEntry {

  private int row, col;

  public void add(JTable table, JPopupMenu menu) {
    menu.addPopupMenuListener(new PopupMenuListener() {
      @Override
      public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        row = -1;
        col = -1;

        SwingUtilities.invokeLater(() -> {
          Point point = SwingUtilities.convertPoint(menu, new Point(0, 0), table);
          row = table.rowAtPoint(point);
          col = table.columnAtPoint(point);
        });
      }

      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
      }

      @Override
      public void popupMenuCanceled(PopupMenuEvent e) {
      }
    });

    JMenuItem item = new JMenuItem("Copy text");
    menu.add(item);
    item.addActionListener(e -> {
      if (row == -1 || col == -1) {
        return;
      }

      Object valueAt = table.getValueAt(row, col);
      if (valueAt == null) {
        return;
      }

      log.info("Set clipboard to: {}", valueAt);
      StringSelection selection = new StringSelection(valueAt.toString());
      Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
      clipboard.setContents(selection, selection);
    });
  }
}
