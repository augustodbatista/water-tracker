package io.github.augustodbatista.watertracker;

import io.github.augustodbatista.watertracker.storage.JsonIntakeRepository;
import io.github.augustodbatista.watertracker.ui.WaterTrackerLauncher;
import java.nio.file.Path;

/** Ponto de entrada do widget. */
public final class App {

  private App() {}

  /**
   * Abre a janela apontando para {@code ~/.water-tracker}.
   *
   * @param args ignorados
   */
  public static void main(String[] args) {
    Path dados = Path.of(System.getProperty("user.home"), ".water-tracker");
    WaterTrackerLauncher.abrir(new JsonIntakeRepository(dados));
  }
}
