package io.github.augustodbatista.watertracker.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class DailyIntakeTest {

  private static final LocalDate HOJE = LocalDate.of(2026, 7, 27);
  private static final LocalDate ONTEM = HOJE.minusDays(1);

  @Test
  void deveRetornarZeroQuandoNaoHaRegistros() {
    assertEquals(0L, new DailyIntake(HOJE, List.of()).totalMilliliters());
  }

  @Test
  void deveSomarRegistrosDoMesmoDia() {
    List<WaterEntry> registros =
        List.of(new WaterEntry(HOJE, 250), new WaterEntry(HOJE, 500), new WaterEntry(HOJE, 100));

    assertEquals(850L, new DailyIntake(HOJE, registros).totalMilliliters());
  }

  @Test
  void deveIgnorarRegistrosDeOutrosDias() {
    List<WaterEntry> registros =
        List.of(new WaterEntry(HOJE, 250), new WaterEntry(ONTEM, 5000), new WaterEntry(HOJE, 150));

    assertEquals(400L, new DailyIntake(HOJE, registros).totalMilliliters());
  }

  @Test
  void deveExporSomenteOsRegistrosDoDia() {
    List<WaterEntry> registros = List.of(new WaterEntry(HOJE, 250), new WaterEntry(ONTEM, 300));

    assertEquals(List.of(new WaterEntry(HOJE, 250)), new DailyIntake(HOJE, registros).entries());
  }

  @Test
  void naoDeveSerAfetadoPorMudancaNaListaOriginal() {
    List<WaterEntry> mutavel = new ArrayList<>(List.of(new WaterEntry(HOJE, 250)));
    DailyIntake consumo = new DailyIntake(HOJE, mutavel);

    mutavel.add(new WaterEntry(HOJE, 999));

    assertEquals(250L, consumo.totalMilliliters());
  }

  @Test
  void naoDevePermitirModificacaoDaListaExposta() {
    DailyIntake consumo = new DailyIntake(HOJE, List.of(new WaterEntry(HOJE, 250)));

    assertThrows(
        UnsupportedOperationException.class, () -> consumo.entries().add(new WaterEntry(HOJE, 10)));
  }

  @Test
  void deveRejeitarDataNula() {
    assertThrows(NullPointerException.class, () -> new DailyIntake(null, List.of()));
  }

  @Test
  void deveRejeitarListaNula() {
    assertThrows(NullPointerException.class, () -> new DailyIntake(HOJE, null));
  }

  @Test
  void deveRejeitarRegistroNuloDentroDaLista() {
    List<WaterEntry> comNulo = Arrays.asList(new WaterEntry(HOJE, 250), null);

    assertThrows(NullPointerException.class, () -> new DailyIntake(HOJE, comNulo));
  }
}
