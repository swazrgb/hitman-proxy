package io.hitman.ui;

import io.hitman.ConfigStore;
import io.hitman.model.Loadout;
import io.hitman.model.RepositoryEntry;
import io.hitman.RepositoryLoader;
import io.hitman.model.SelectedItem;
import java.util.List;
import javax.swing.table.AbstractTableModel;

public class ItemTableModel extends AbstractTableModel {

  private final RepositoryLoader repositoryLoader;
  private ConfigStore configStore;
  private Loadout loadout;

  public ItemTableModel(RepositoryLoader repositoryLoader, ConfigStore configStore,
      Loadout loadout) {
    this.repositoryLoader = repositoryLoader;
    this.configStore = configStore;
    this.loadout = loadout;
    fireTableStructureChanged();
  }

  public void setLoadout(Loadout loadout) {
    this.loadout = loadout;
    fireTableStructureChanged();
  }

  public void addItem(RepositoryEntry repositoryEntry) {
    List<SelectedItem> entries = loadout.getEntries();
    for (int i = 0; i < entries.size(); i++) {
      SelectedItem entry = entries.get(i);
      if (entry.getId().equals(repositoryEntry.getId())) {
        entry.setAmount(entry.getAmount() + 1);
        fireTableRowsUpdated(i, i);
        return;
      }
    }

    int idx = entries.size();
    entries.add(idx, new SelectedItem(repositoryEntry.getId()));
    fireTableRowsInserted(idx, idx);
    configStore.persist();
  }

  public void removeRow(int idx) {
    List<SelectedItem> entries = loadout.getEntries();
    entries.remove(idx);
    fireTableRowsDeleted(idx, idx);
    configStore.persist();
  }

  @Override
  public int getRowCount() {
    return loadout.getEntries().size();
  }

  @Override
  public int getColumnCount() {
    return 5;
  }

  @Override
  public String getColumnName(int columnIndex) {
    switch (columnIndex) {
      case 0:
        return "Amount";
      case 1:
        return "ID";
      case 2:
        return "CommonName";
      case 3:
        return "Name";
      case 4:
        return "Description";
    }

    return null;
  }

  @Override
  public Class<?> getColumnClass(int columnIndex) {
    switch (columnIndex) {
      case 0:
        return Integer.class;
      case 1:
      case 2:
      case 3:
      case 4:
        return String.class;
    }

    return null;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    List<SelectedItem> entries = loadout.getEntries();
    SelectedItem item = entries.get(rowIndex);
    RepositoryEntry entry = repositoryLoader.getRepositoryEntryMap().get(item.getId());
    switch (columnIndex) {
      case 0:
        return item.getAmount();
      case 1:
        return entry.getId();
      case 2:
        return entry.getCommonName();
      case 3:
        return firstNonNull(entry.getNameLocEn(), entry.getCommonName());
      case 4:
        return firstNonNull(entry.getDescriptionEn(), entry.getDescription());
    }

    return null;
  }

  private static <T> T firstNonNull(T... entries) {
    for (T entry : entries) {
      if (entry != null) {
        return entry;
      }
    }

    return null;
  }

  @Override
  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    if (columnIndex == 0 && aValue != null) {
      List<SelectedItem> entries = loadout.getEntries();
      SelectedItem item = entries.get(rowIndex);

      try {
        int parsed = Integer.parseInt(aValue.toString());
        item.setAmount(parsed);
        configStore.persist();
      } catch (NumberFormatException ex) {
        // Ignore...
      }
    }
  }

  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return columnIndex == 0;
  }
}
