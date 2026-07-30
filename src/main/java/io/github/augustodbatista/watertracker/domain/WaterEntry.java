package io.github.augustodbatista.watertracker.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Um único registro de consumo: quanto foi bebido e em que dia.
 *
 * <p>Valida no construtor compacto, então uma instância inválida nunca chega a existir e nenhum
 * consumidor precisa checar de novo.
 *
 * @param date dia em que o consumo ocorreu
 * @param milliliters volume consumido, em mililitros
 */
public record WaterEntry(LocalDate date, int milliliters) {

  /** Barreira contra erro de digitação: {@code 25000} no lugar de {@code 250} envenena o dia. */
  public static final int VOLUME_MAXIMO_ML = 5000;

  /** Registrar "zero" não é um consumo. */
  public static final int VOLUME_MINIMO_ML = 1;

  /** Valida as invariantes do registro. */
  public WaterEntry {
    Objects.requireNonNull(date, "A data do registro não pode ser nula.");
    if (milliliters < VOLUME_MINIMO_ML || milliliters > VOLUME_MAXIMO_ML) {
      throw new InvalidVolumeException(
          "Volume deve estar entre %d ml e %d ml, mas recebeu %d ml."
              .formatted(VOLUME_MINIMO_ML, VOLUME_MAXIMO_ML, milliliters));
    }
  }
}
