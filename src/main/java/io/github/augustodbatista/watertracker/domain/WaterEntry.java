package io.github.augustodbatista.watertracker.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Um único registro de consumo de água: quanto foi bebido e em que dia.
 *
 * <p>É um <em>value object</em> imutável. A validação mora no construtor compacto, de modo que uma
 * instância inválida de {@code WaterEntry} nunca chega a existir — nenhum código consumidor precisa
 * checar de novo. Este é o encapsulamento que interessa: a classe protege a sua própria invariante,
 * em vez de expor o estado por um par getter/setter que só finge encapsular.
 *
 * @param date dia em que o consumo ocorreu
 * @param milliliters volume consumido, em mililitros
 */
public record WaterEntry(LocalDate date, int milliliters) {

  /**
   * Teto de sanidade por registro, em mililitros.
   *
   * <p>Não é um limite fisiológico, e sim uma barreira contra erro de digitação: sem ele, um
   * {@code 25000} digitado por engano no lugar de {@code 250} envenenaria silenciosamente o total
   * do dia e a leitura da meta.
   */
  public static final int VOLUME_MAXIMO_ML = 5000;

  /** Volume mínimo aceito, em mililitros. Registrar "zero" não é um consumo. */
  public static final int VOLUME_MINIMO_ML = 1;

  /**
   * Valida as invariantes do registro.
   *
   * @throws NullPointerException se {@code date} for nulo
   * @throws InvalidVolumeException se {@code milliliters} estiver fora da faixa aceita
   */
  public WaterEntry {
    Objects.requireNonNull(date, "A data do registro não pode ser nula.");
    if (milliliters < VOLUME_MINIMO_ML || milliliters > VOLUME_MAXIMO_ML) {
      throw new InvalidVolumeException(
          "Volume deve estar entre %d ml e %d ml, mas recebeu %d ml."
              .formatted(VOLUME_MINIMO_ML, VOLUME_MAXIMO_ML, milliliters));
    }
  }
}
