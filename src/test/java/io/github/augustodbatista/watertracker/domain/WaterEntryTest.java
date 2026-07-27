package io.github.augustodbatista.watertracker.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class WaterEntryTest {

  private static final LocalDate HOJE = LocalDate.of(2026, 7, 27);

  @Test
  void deveArmazenarDataEVolumeQuandoValidos() {
    WaterEntry entrada = new WaterEntry(HOJE, 250);

    assertEquals(HOJE, entrada.date());
    assertEquals(250, entrada.milliliters());
  }

  @Test
  void deveRejeitarVolumeZero() {
    assertThrows(InvalidVolumeException.class, () -> new WaterEntry(HOJE, 0));
  }

  @Test
  void deveRejeitarVolumeNegativo() {
    assertThrows(InvalidVolumeException.class, () -> new WaterEntry(HOJE, -250));
  }

  @Test
  void deveAceitarVolumeMinimoDeUmMililitro() {
    assertEquals(1, new WaterEntry(HOJE, 1).milliliters());
  }

  @Test
  void deveAceitarVolumeExatamenteNoLimiteSuperior() {
    WaterEntry entrada = new WaterEntry(HOJE, WaterEntry.VOLUME_MAXIMO_ML);

    assertEquals(WaterEntry.VOLUME_MAXIMO_ML, entrada.milliliters());
  }

  @Test
  void deveRejeitarVolumeAcimaDoLimiteSuperior() {
    assertThrows(
        InvalidVolumeException.class, () -> new WaterEntry(HOJE, WaterEntry.VOLUME_MAXIMO_ML + 1));
  }

  @Test
  void deveRejeitarDataNula() {
    assertThrows(NullPointerException.class, () -> new WaterEntry(null, 250));
  }
}
