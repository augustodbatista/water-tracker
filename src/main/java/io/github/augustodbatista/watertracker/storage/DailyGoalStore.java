package io.github.augustodbatista.watertracker.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Guarda a meta diária num arquivo de texto com o número puro.
 *
 * <p>Sem JSON de propósito: é um inteiro. DTO, Gson e escrita atômica para gravar {@code 2500}
 * seriam maquinário sem função — e o arquivo continua editável em qualquer editor.
 */
public final class DailyGoalStore {

  /** Meta usada quando ainda não há arquivo, ou quando o que está lá não faz sentido. */
  public static final int META_PADRAO_ML = 2000;

  /** Abaixo disso não é meta de hidratação, é erro de digitação. */
  public static final int META_MINIMA_ML = 500;

  /** Acima disso passa de qualquer recomendação plausível de consumo diário. */
  public static final int META_MAXIMA_ML = 10_000;

  private static final String NOME_DO_ARQUIVO = "daily-goal.txt";

  private final Path diretorio;
  private final Path arquivo;

  /**
   * Aponta o store para um diretório, que é criado na primeira gravação.
   *
   * @param diretorioDeDados pasta onde o arquivo da meta mora
   */
  public DailyGoalStore(Path diretorioDeDados) {
    this.diretorio = Objects.requireNonNull(diretorioDeDados, "O diretório não pode ser nulo.");
    this.arquivo = this.diretorio.resolve(NOME_DO_ARQUIVO);
  }

  /**
   * Lê a meta gravada.
   *
   * <p>Diferente dos registros de consumo, um arquivo ilegível aqui não vira exceção: cai no
   * padrão. A meta é reconfigurável em dois cliques, e travar o app por causa dela impediria o
   * usuário de fazer a única coisa que ele abriu o widget para fazer.
   *
   * @return meta em mililitros, ou {@link #META_PADRAO_ML} se não houver valor utilizável
   */
  public int load() {
    if (!Files.isRegularFile(arquivo)) {
      return META_PADRAO_ML;
    }
    try {
      int gravada = Integer.parseInt(Files.readString(arquivo, StandardCharsets.UTF_8).trim());
      return dentroDaFaixa(gravada) ? gravada : META_PADRAO_ML;
    } catch (IOException | NumberFormatException e) {
      return META_PADRAO_ML;
    }
  }

  /**
   * Grava a meta diária.
   *
   * @param mililitros nova meta
   * @throws IllegalArgumentException se estiver fora da faixa aceita
   */
  public void save(int mililitros) {
    if (!dentroDaFaixa(mililitros)) {
      throw new IllegalArgumentException(
          "A meta deve estar entre %d ml e %d ml, mas recebeu %d ml."
              .formatted(META_MINIMA_ML, META_MAXIMA_ML, mililitros));
    }
    try {
      Files.createDirectories(diretorio);
      Files.writeString(arquivo, String.valueOf(mililitros), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Falha ao gravar " + arquivo + ".", e);
    }
  }

  private static boolean dentroDaFaixa(int mililitros) {
    return mililitros >= META_MINIMA_ML && mililitros <= META_MAXIMA_ML;
  }
}
