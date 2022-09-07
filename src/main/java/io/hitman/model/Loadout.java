package io.hitman.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Loadout {

  private String name;
  private List<SelectedItem> entries = new ArrayList<>();

  public Loadout(String name) {
    this.name = name;
  }
}
