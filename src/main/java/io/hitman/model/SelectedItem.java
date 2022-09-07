package io.hitman.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SelectedItem {

  private String id;
  private int amount = 1;

  public SelectedItem(String id) {
    this.id = id;
  }
}
