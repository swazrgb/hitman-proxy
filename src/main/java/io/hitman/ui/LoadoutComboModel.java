package io.hitman.ui;

import io.hitman.ConfigStore;
import io.hitman.model.ConfigListener;
import io.hitman.model.Loadout;
import java.util.HashSet;
import java.util.Set;
import javax.swing.ComboBoxModel;
import javax.swing.JOptionPane;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

public class LoadoutComboModel implements ComboBoxModel<Loadout>, ConfigListener {

  private final ConfigStore configStore;
  private Set<ListDataListener> listeners = new HashSet<>();

  private final Loadout newLoadout = new Loadout("New...");

  public LoadoutComboModel(ConfigStore configStore) {
    this.configStore = configStore;
  }

  @Override
  public void setSelectedItem(Object loadout) {
//    if (loadout == newLoadout) {
//      String name = (String) JOptionPane
//          .showInputDialog(null, "Name?", "New loadout", JOptionPane.QUESTION_MESSAGE,
//              null, null, "");
//      configStore.setActiveLoadout(name);
//      return;
//    }

    if (loadout instanceof Loadout) {
      configStore.setActiveLoadout(((Loadout) loadout).getName());
    } else {
      configStore.setActiveLoadout(loadout.toString());
    }
  }

  @Override
  public Object getSelectedItem() {
    return configStore.getActiveLoadout();
  }

  @Override
  public int getSize() {
    return configStore.getLoadouts().size();
  }

  @Override
  public Loadout getElementAt(int index) {
//    if (index == getSize() - 1) {
//      return newLoadout;
//    }

    return configStore.getLoadouts().get(index);
  }

  private void onChange() {
    for (ListDataListener listener : listeners) {
      listener
          .contentsChanged(new ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED, 0, getSize()));
    }
  }

  @Override
  public void onActiveLoadoutChanged(String name) {
    onChange();
  }

  @Override
  public void onLoadoutCreated(String name) {
    onChange();
  }

  @Override
  public void addListDataListener(ListDataListener l) {
    listeners.add(l);
  }

  @Override
  public void removeListDataListener(ListDataListener l) {
    listeners.remove(l);
  }
}
