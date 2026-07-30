package io.github.augustodbatista.watertracker.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * O consumo de água de um único dia, agregado a partir de registros.
 *
 * <p>Recebe a coleção completa e filtra internamente: assim não existe o bug em que uma tela soma o
 * dia certo e outra esquece do filtro. A lista é copiada na construção e devolvida imutável.
 *
 * @param date dia agregado
 * @param entries registros daquele dia, já filtrados
 */
public record DailyIntake(LocalDate date, List<WaterEntry> entries) {

  /** Meta diária padrão, em mililitros. */
  public static final int META_DIARIA_ML = 2000;

  /** Filtra os registros do dia informado e congela o resultado. */
  public DailyIntake {
    Objects.requireNonNull(date, "O dia agregado não pode ser nulo.");
    Objects.requireNonNull(entries, "A lista de registros não pode ser nula.");
    // List.copyOf e nao Stream.toList(): as duas sao imutaveis, mas so a primeira o SpotBugs
    // reconhece - ver Hurdle #13. Custo zero: copyOf devolve a propria instancia se ja imutavel.
    entries =
        List.copyOf(
            entries.stream()
                .map(entry -> Objects.requireNonNull(entry, "A lista contém registro nulo."))
                .filter(entry -> date.equals(entry.date()))
                .toList());
  }

  /**
   * Soma o volume consumido no dia.
   *
   * <p>Acumula em {@code long} porque um estouro de {@code int} viraria total negativo em silêncio.
   *
   * @return total do dia, em mililitros
   */
  public long totalMilliliters() {
    return entries.stream().mapToLong(WaterEntry::milliliters).sum();
  }
}
