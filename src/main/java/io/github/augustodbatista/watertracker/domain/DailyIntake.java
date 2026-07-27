package io.github.augustodbatista.watertracker.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * O consumo de água de um único dia, agregado a partir de registros individuais.
 *
 * <p>Recebe a coleção completa de registros e o dia de interesse, e se encarrega de filtrar. Quem
 * chama não precisa saber filtrar corretamente — essa responsabilidade fica de um lado só, o que
 * elimina a classe de bug em que uma tela soma o dia certo e outra esquece do filtro.
 *
 * <p>É imutável de verdade: a lista recebida é copiada na construção, de modo que alterações
 * posteriores na coleção original não conseguem mudar um {@code DailyIntake} já criado, e a lista
 * devolvida por {@link #entries()} não aceita modificação.
 *
 * @param date dia agregado
 * @param entries registros <em>daquele</em> dia, já filtrados e imutáveis
 */
public record DailyIntake(LocalDate date, List<WaterEntry> entries) {

  /**
   * Filtra os registros do dia informado e congela o resultado.
   *
   * @throws NullPointerException se {@code date}, {@code entries}, ou qualquer elemento da lista
   *     for nulo
   */
  public DailyIntake {
    Objects.requireNonNull(date, "O dia agregado não pode ser nulo.");
    Objects.requireNonNull(entries, "A lista de registros não pode ser nula.");
    // List.copyOf em vez de Stream.toList(): as duas produzem lista imutável, mas o SpotBugs só
    // reconhece a primeira e acusa EI_EXPOSE_REP na segunda. Sem custo — copyOf devolve a própria
    // instância quando ela já é imutável.
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
   * <p>O acumulador é {@code long} de propósito. Um usuário jamais registraria manualmente o
   * suficiente para estourar um {@code int}, mas o arquivo de dados pode chegar corrompido ou
   * editado à mão — e um estouro de {@code int} viraria um total negativo em silêncio, sem erro
   * algum, exibindo um número absurdo na tela.
   *
   * @return total consumido no dia, em mililitros
   */
  public long totalMilliliters() {
    return entries.stream().mapToLong(WaterEntry::milliliters).sum();
  }
}
