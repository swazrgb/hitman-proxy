package io.hitman.ui;

import io.hitman.model.RepositoryEntry;
import io.hitman.RepositoryLoader;
import javax.swing.table.AbstractTableModel;

public class RepoTableModel extends AbstractTableModel {

  private RepositoryLoader repositoryLoader;

  public RepoTableModel(RepositoryLoader repositoryLoader) {
    this.repositoryLoader = repositoryLoader;
    fireTableStructureChanged();
  }

  public RepositoryEntry getEntry(int idx) {
    return repositoryLoader.getRepository().get(idx);
  }

  @Override
  public int getRowCount() {
    return repositoryLoader.getRepository().size();
  }

  @Override
  public int getColumnCount() {
    return 4;
  }

  @Override
  public String getColumnName(int columnIndex) {
    switch (columnIndex) {
      case 0:
        return "ID";
      case 1:
        return "CommonName";
      case 2:
        return "Name";
      case 3:
        return "Description";
    }

    return null;
  }

  @Override
  public Class<?> getColumnClass(int columnIndex) {
    switch (columnIndex) {
      case 0:
      case 1:
      case 2:
      case 3:
        return String.class;
    }

    return null;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    RepositoryEntry entry = repositoryLoader.getRepository().get(rowIndex);
    switch (columnIndex) {
      case 0:
        return entry.getId();
      case 1:
        return entry.getCommonName();
      case 2:
        return firstNonNull(entry.getNameLocEn(), entry.getCommonName());
      case 3:
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
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return false;
  }
}
