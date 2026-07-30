package io.github.augustodbatista.watertracker.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DailyGoalStoreTest {

  @TempDir private Path diretorio;

  @Test
  void deveUsarAMetaPadraoQuandoNaoHaArquivo() {
    assertEquals(DailyGoalStore.META_PADRAO_ML, new DailyGoalStore(diretorio).load());
  }

  @Test
  void deveRecuperarAMetaSalva() {
    DailyGoalStore store = new DailyGoalStore(diretorio);

    store.save(3000);

    assertEquals(3000, new DailyGoalStore(diretorio).load());
  }

  /**
   * A meta não é dado precioso: se o arquivo estiver ilegível, seguir com o padrão é melhor do que
   * impedir o usuário de registrar água.
   */
  @Test
  void deveVoltarAoPadraoQuandoOArquivoNaoEhNumero() throws IOException {
    Files.writeString(diretorio.resolve("daily-goal.txt"), "dois litros", StandardCharsets.UTF_8);

    assertEquals(DailyGoalStore.META_PADRAO_ML, new DailyGoalStore(diretorio).load());
  }

  @Test
  void deveVoltarAoPadraoQuandoAMetaGravadaEstaForaDaFaixa() throws IOException {
    Files.writeString(diretorio.resolve("daily-goal.txt"), "999999", StandardCharsets.UTF_8);

    assertEquals(DailyGoalStore.META_PADRAO_ML, new DailyGoalStore(diretorio).load());
  }

  @Test
  void deveIgnorarEspacosEQuebrasDeLinhaNoArquivo() throws IOException {
    Files.writeString(diretorio.resolve("daily-goal.txt"), "  2500 \n", StandardCharsets.UTF_8);

    assertEquals(2500, new DailyGoalStore(diretorio).load());
  }

  @Test
  void deveRejeitarMetaAbaixoDoMinimo() {
    DailyGoalStore store = new DailyGoalStore(diretorio);

    assertThrows(
        IllegalArgumentException.class, () -> store.save(DailyGoalStore.META_MINIMA_ML - 1));
  }

  @Test
  void deveRejeitarMetaAcimaDoMaximo() {
    DailyGoalStore store = new DailyGoalStore(diretorio);

    assertThrows(
        IllegalArgumentException.class, () -> store.save(DailyGoalStore.META_MAXIMA_ML + 1));
  }

  @Test
  void deveRejeitarDiretorioNulo() {
    assertThrows(NullPointerException.class, () -> new DailyGoalStore(null));
  }
}
