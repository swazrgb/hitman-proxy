package io.hitman.ui;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import io.hitman.ConfigStore;
import io.hitman.HitmanProxyApplication;
import io.hitman.RepositoryLoader;
import io.hitman.model.ConfigListener;
import io.hitman.model.Loadout;
import io.hitman.model.SelectedItem;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.Collections;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MainForm {

  private final ConfigStore configStore;

  private JPanel mainPanel;
  private JTable itemTable;
  private JTable repositoryTable;
  private JButton addButton;
  private JPanel topPanel;
  private JTextField searchText;
  private JButton clearSearchBtn;
  private JButton removeButton;
  private JComboBox<Loadout> loadoutCombo;
  private MenuButton loadoutBtn;

  private JPopupMenu itemTableMenu;
  private JPopupMenu repositoryTableMenu;

  public MainForm(RepositoryLoader repositoryLoader, ConfigStore configStore) {
    this.configStore = configStore;

    DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
    $$$setupUI$$$();
    renderer.setHorizontalAlignment(SwingConstants.LEFT);

    itemTable.getTableHeader().setReorderingAllowed(false);
    itemTable.getTableHeader().setDefaultRenderer(renderer);
    ItemTableModel itemTableModel = new ItemTableModel(repositoryLoader, configStore,
        configStore.getActiveLoadout());
    itemTable.setModel(itemTableModel);

    configStore.addConfigListener(new ConfigListener() {
      @Override
      public void onActiveLoadoutChanged(String name) {
        itemTableModel.setLoadout(configStore.getActiveLoadout());
      }
    });

    loadoutCombo.setRenderer(new DefaultListCellRenderer() {
      @Override
      public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
          boolean isSelected, boolean cellHasFocus) {
        if (value instanceof Loadout) {
          value = ((Loadout) value).getName();
        }

        return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      }
    });

    LoadoutComboModel loadoutComboModel = new LoadoutComboModel(configStore);
    configStore.addConfigListener(loadoutComboModel);
    loadoutCombo.setModel(loadoutComboModel);

    JMenuItem newMenu = new JMenuItem("New");
    newMenu.addActionListener(e -> this.newLoadout());
    loadoutBtn.getPopup().add(newMenu);

    JMenuItem copyMenu = new JMenuItem("Copy");
    copyMenu.addActionListener(e -> this.copyLoadout());
    loadoutBtn.getPopup().add(copyMenu);

    JMenuItem renameMenu = new JMenuItem("Rename");
    renameMenu.addActionListener(e -> this.renameLoadout());
    loadoutBtn.getPopup().add(renameMenu);

    JMenuItem deleteMenu = new JMenuItem("Delete");
    deleteMenu.addActionListener(e -> this.deleteLoadout());
    loadoutBtn.getPopup().add(deleteMenu);

    repositoryTable.getTableHeader().setReorderingAllowed(false);
    repositoryTable.getTableHeader().setDefaultRenderer(renderer);
    repositoryTable.setModel(new RepoTableModel(repositoryLoader));

//    Collections.list(itemTable.getColumnModel().getColumns())
//        .forEach(c -> c.setHeaderRenderer(renderer));
//    Collections.list(repositoryTable.getColumnModel().getColumns())
//        .forEach(c -> c.setHeaderRenderer(renderer));

    TableColumnAdjuster repositoryAdjuster = new TableColumnAdjuster(repositoryTable);
    repositoryTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    repositoryAdjuster.setDynamicAdjustment(true);
    repositoryAdjuster.adjustColumns();

    TableColumnAdjuster itemAdjuster = new TableColumnAdjuster(itemTable);
    itemTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    itemAdjuster.setDynamicAdjustment(true);
    itemAdjuster.adjustColumns();

    repositoryTableMenu = new JPopupMenu();
    repositoryTable.setComponentPopupMenu(repositoryTableMenu);

    JMenuItem add = new JMenuItem("Add selected");
    repositoryTableMenu.add(add);
    add.addActionListener(e -> addSelected());

    repositoryTableMenu.addSeparator();
    new JTableCopyTextEntry().add(repositoryTable, repositoryTableMenu);

    itemTableMenu = new JPopupMenu();
    itemTable.setComponentPopupMenu(itemTableMenu);

    JMenuItem delete = new JMenuItem("Remove selected");
    itemTableMenu.add(delete);
    delete.addActionListener(e -> deleteSelected());

    itemTableMenu.addSeparator();
    new JTableCopyTextEntry().add(itemTable, itemTableMenu);

    clearSearchBtn.addActionListener(e -> {
      searchText.setText("");
      searchText.requestFocus();
    });

    JInputChangeListenerCreator.addChangeListener(searchText, e -> {
      ((TableRowSorter<?>) repositoryTable.getRowSorter())
          .setRowFilter(RowFilter.regexFilter("(?i)" +
              Pattern.quote(searchText.getText()), 0, 1, 2));
    });

    addButton.addActionListener(e -> addSelected());

    removeButton.addActionListener(e -> deleteSelected());
  }

  private void newLoadout() {
    String name = (String) JOptionPane
        .showInputDialog(mainPanel, "Name?", "New loadout", JOptionPane.QUESTION_MESSAGE,
            null, null, "");
    configStore.setActiveLoadout(name);
  }

  private void deleteLoadout() {
    Loadout activeLoadout = configStore.getActiveLoadout();
    int confirmed = JOptionPane
        .showConfirmDialog(mainPanel, "Really delete loadout " + activeLoadout.getName() + "?",
            "Delete loadout", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

    if (confirmed != 0) {
      return;
    }

    configStore.removeLoadout(activeLoadout.getName());
  }

  private void renameLoadout() {
    Loadout activeLoadout = configStore.getActiveLoadout();
    String newName = (String) JOptionPane
        .showInputDialog(mainPanel, "New name?", "Rename loadout", JOptionPane.QUESTION_MESSAGE,
            null, null, activeLoadout.getName());

    if (newName == null || newName.isEmpty() || newName.equals(activeLoadout.getName())) {
      return;
    }

    Loadout newLoadout = configStore.getLoadout(newName);
    if (!newLoadout.getEntries().isEmpty()) {
      JOptionPane.showMessageDialog(mainPanel, "Loadout " + newName + " already exists.", "Error",
          JOptionPane.ERROR_MESSAGE);
      return;
    }

    for (SelectedItem entry : activeLoadout.getEntries()) {
      newLoadout.getEntries().add(new SelectedItem(entry.getId(), entry.getAmount()));
    }

    configStore.setActiveLoadout(newName);
    configStore.removeLoadout(activeLoadout.getName());
  }

  private void copyLoadout() {
    Loadout activeLoadout = configStore.getActiveLoadout();
    String newName = (String) JOptionPane
        .showInputDialog(mainPanel, "Copy " + activeLoadout.getName() + " to?", "Copy loadout",
            JOptionPane.QUESTION_MESSAGE,
            null, null, "");
    if (newName == null || newName.isEmpty()) {
      return;
    }

    Loadout newLoadout = configStore.getLoadout(newName);

    if (!newLoadout.getEntries().isEmpty()) {
      JOptionPane.showMessageDialog(mainPanel, "Loadout " + newName + " already exists.", "Error",
          JOptionPane.ERROR_MESSAGE);
      return;
    }

    for (SelectedItem entry : activeLoadout.getEntries()) {
      newLoadout.getEntries().add(new SelectedItem(entry.getId(), entry.getAmount()));
    }

    configStore.setActiveLoadout(newName);
  }

  private void deleteSelected() {
    ItemTableModel itemModel = (ItemTableModel) itemTable.getModel();
    int[] selectedRows = itemTable.getSelectedRows();
    Arrays.stream(selectedRows).boxed()
        .sorted(Collections.reverseOrder())
        .forEach(selectedRow -> itemModel.removeRow(itemTable.convertRowIndexToModel(selectedRow)));
  }

  private void addSelected() {
    RepoTableModel repoModel = (RepoTableModel) repositoryTable.getModel();
    ItemTableModel itemModel = (ItemTableModel) itemTable.getModel();
    int[] selectedRows = repositoryTable.getSelectedRows();
    for (int selectedRow : selectedRows) {
      itemModel.addItem(repoModel.getEntry(repositoryTable.convertRowIndexToModel(selectedRow)));
    }
  }

  public JPanel getMainPanel() {
    return mainPanel;
  }

  /**
   * Method generated by IntelliJ IDEA GUI Designer >>> IMPORTANT!! <<< DO NOT edit this method OR
   * call it in your code!
   *
   * @noinspection ALL
   */
  private void $$$setupUI$$$() {
    createUIComponents();
    mainPanel = new JPanel();
    mainPanel.setLayout(new GridLayoutManager(1, 1, new Insets(5, 5, 5, 5), -1, -1));
    final JSplitPane splitPane1 = new JSplitPane();
    splitPane1.setDividerLocation(327);
    splitPane1.setOrientation(0);
    mainPanel.add(splitPane1,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            new Dimension(-1, 400), new Dimension(800, 800), null, 0, false));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 5, 0), -1, -1));
    splitPane1.setLeftComponent(panel1);
    topPanel = new JPanel();
    topPanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), 5, -1));
    panel1.add(topPanel,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
            null, 0, false));
    final JLabel label1 = new JLabel();
    label1.setText("Repository search:");
    topPanel.add(label1,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
            false));
    searchText = new JTextField();
    topPanel.add(searchText, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST,
        GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW,
        GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    clearSearchBtn = new JButton();
    clearSearchBtn.setText("X");
    topPanel.add(clearSearchBtn,
        new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JScrollPane scrollPane1 = new JScrollPane();
    panel1.add(scrollPane1,
        new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
            new Dimension(400, 100), new Dimension(800, 400), null, 0, false));
    repositoryTable = new JTable();
    repositoryTable.setAutoCreateRowSorter(true);
    scrollPane1.setViewportView(repositoryTable);
    addButton = new JButton();
    addButton.setText("Add ↓");
    panel1.add(addButton, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER,
        GridConstraints.FILL_HORIZONTAL,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JPanel panel2 = new JPanel();
    panel2.setLayout(new GridLayoutManager(3, 1, new Insets(5, 0, 0, 0), -1, -1));
    splitPane1.setRightComponent(panel2);
    final JScrollPane scrollPane2 = new JScrollPane();
    panel2.add(scrollPane2,
        new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
            new Dimension(400, 100), new Dimension(800, 400), null, 0, false));
    itemTable = new JTable();
    itemTable.setAutoCreateRowSorter(true);
    scrollPane2.setViewportView(itemTable);
    removeButton = new JButton();
    removeButton.setText("Remove");
    panel2.add(removeButton, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER,
        GridConstraints.FILL_HORIZONTAL,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JPanel panel3 = new JPanel();
    panel3.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), 5, -1));
    panel2.add(panel3,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
            null, 0, false));
    final JLabel label2 = new JLabel();
    label2.setText("Extended loadout:");
    panel3.add(label2,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
            false));
    loadoutCombo = new JComboBox();
    panel3.add(loadoutCombo, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST,
        GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW,
        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    loadoutBtn.setText("...");
    panel3.add(loadoutBtn,
        new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
            null, 0, false));
  }

  /**
   * @noinspection ALL
   */
  public JComponent $$$getRootComponent$$$() {
    return mainPanel;
  }

  private void createUIComponents() {
    loadoutBtn = new MenuButton();
  }
}
