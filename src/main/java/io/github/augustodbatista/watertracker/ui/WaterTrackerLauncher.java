package io.github.augustodbatista.watertracker.ui;

import io.github.augustodbatista.watertracker.storage.DailyGoalStore;
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
   * @param registros onde os consumos são lidos e gravados
   * @param metas onde a meta diária é lida e gravada
   */
  public static void abrir(JsonIntakeRepository registros, DailyGoalStore metas) {
    SwingUtilities.invokeLater(() -> new WaterTrackerWindow(registros, metas).setVisible(true));
  }
}
