package io.github.augustodbatista.watertracker.ui;

import io.github.augustodbatista.watertracker.storage.JsonIntakeRepository;
import javax.swing.SwingUtilities;

/** Abre a janela do widget na thread correta. */
public final class WaterTrackerLauncher {

  private WaterTrackerLauncher() {}

  /**
   * Cria e exibe a janela dentro da EDT.
   *
   * <p>Construir componentes Swing fora da Event Dispatch Thread trava ou faz a janela piscar —
   * Hurdle #7.
   *
   * @param repositorio onde os registros são lidos e gravados
   */
  public static void abrir(JsonIntakeRepository repositorio) {
    SwingUtilities.invokeLater(() -> new WaterTrackerWindow(repositorio).setVisible(true));
  }
}
