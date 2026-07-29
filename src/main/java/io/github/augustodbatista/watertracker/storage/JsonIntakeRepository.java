package io.github.augustodbatista.watertracker.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import io.github.augustodbatista.watertracker.domain.InvalidVolumeException;
import io.github.augustodbatista.watertracker.domain.WaterEntry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Guarda os registros de consumo num único arquivo JSON no disco local.
 *
 * <p>O formato é deliberadamente legível: datas em ISO-8601 e identação preservada. O arquivo é do
 * usuário, mora na pasta pessoal dele, e ele tem todo o direito de abrir e conferir sem precisar de
 * ferramenta especial.
 *
 * <p>A gravação é atômica — escreve num arquivo temporário e o renomeia por cima do original com
 * {@link StandardCopyOption#ATOMIC_MOVE}. Sem isso, uma queda de energia no meio da escrita
 * deixaria um JSON truncado, e o histórico inteiro do usuário viraria ilegível.
 *
 * <p>O JSON é desserializado para {@link RegistroEmDisco}, e não direto para {@link WaterEntry}. Um
 * arquivo editado à mão é entrada não confiável, e o Gson embrulha qualquer exceção do construtor
 * do <em>record</em> numa {@code RuntimeException} genérica — o que tornaria impossível distinguir
 * "arquivo adulterado" de um bug nosso. Com o DTO, a conversão é explícita e a validação de domínio
 * acontece em código que controlamos.
 *
 * <p><strong>Limitação conhecida:</strong> {@link #save(WaterEntry)} faz ler-alterar-gravar, o que
 * não é seguro entre duas instâncias do widget abertas ao mesmo tempo. O arquivo nunca fica
 * corrompido (a troca é atômica), mas um dos dois registros pode se perder. Tratado no próximo
 * ciclo.
 */
public final class JsonIntakeRepository {

  private static final String NOME_DO_ARQUIVO = "entries.json";

  private static final Type TIPO_DA_LISTA = new TypeToken<List<RegistroEmDisco>>() {}.getType();

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private final Path diretorio;
  private final Path arquivo;

  /**
   * Aponta o repositório para um diretório de dados.
   *
   * <p>O diretório não precisa existir ainda: ele é criado na primeira gravação.
   *
   * @param diretorioDeDados pasta onde o {@code entries.json} mora
   * @throws NullPointerException se {@code diretorioDeDados} for nulo
   */
  public JsonIntakeRepository(Path diretorioDeDados) {
    this.diretorio = Objects.requireNonNull(diretorioDeDados, "O diretório não pode ser nulo.");
    this.arquivo = this.diretorio.resolve(NOME_DO_ARQUIVO);
  }

  /**
   * Lê todos os registros já gravados, na ordem em que foram salvos.
   *
   * @return lista imutável; vazia se o arquivo ainda não existe
   * @throws CorruptedDataException se o arquivo existe mas não pôde ser interpretado
   * @throws UncheckedIOException se a leitura do disco falhar
   */
  public List<WaterEntry> loadAll() {
    if (!Files.isRegularFile(arquivo)) {
      return List.of();
    }
    String json;
    try {
      json = Files.readString(arquivo, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Falha ao ler " + arquivo + ".", e);
    }
    List<RegistroEmDisco> brutos;
    try {
      brutos = GSON.fromJson(json, TIPO_DA_LISTA);
    } catch (JsonParseException e) {
      throw new CorruptedDataException("O arquivo " + arquivo + " não é um JSON válido.", e);
    }
    if (brutos == null) {
      return List.of();
    }
    return brutos.stream().map(this::converterParaDominio).toList();
  }

  /**
   * Acrescenta um registro ao histórico.
   *
   * @param registro consumo a gravar
   * @throws NullPointerException se {@code registro} for nulo
   * @throws UncheckedIOException se a gravação no disco falhar
   */
  public void save(WaterEntry registro) {
    Objects.requireNonNull(registro, "O registro não pode ser nulo.");
    List<WaterEntry> todos = new ArrayList<>(loadAll());
    todos.add(registro);
    gravarAtomicamente(todos);
  }

  private WaterEntry converterParaDominio(RegistroEmDisco bruto) {
    if (bruto == null || bruto.date() == null || bruto.milliliters() == null) {
      throw new CorruptedDataException(
          "O arquivo " + arquivo + " tem registro incompleto: " + bruto + ".", null);
    }
    try {
      return new WaterEntry(LocalDate.parse(bruto.date()), bruto.milliliters());
    } catch (DateTimeParseException | InvalidVolumeException e) {
      throw new CorruptedDataException(
          "O arquivo " + arquivo + " tem registro inválido: " + bruto + ".", e);
    }
  }

  private void gravarAtomicamente(List<WaterEntry> todos) {
    List<RegistroEmDisco> paraGravar = todos.stream().map(RegistroEmDisco::de).toList();
    try {
      Files.createDirectories(diretorio);
      Path temporario = Files.createTempFile(diretorio, NOME_DO_ARQUIVO, ".tmp");
      Files.writeString(
          temporario, GSON.toJson(paraGravar, TIPO_DA_LISTA), StandardCharsets.UTF_8);
      Files.move(temporario, arquivo, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      throw new UncheckedIOException("Falha ao gravar " + arquivo + ".", e);
    }
  }

  /**
   * O contrato do arquivo em disco, separado do modelo de domínio.
   *
   * <p>Os tipos são frouxos de propósito — {@code String} e {@code Integer}, ambos aceitando nulo —
   * porque é exatamente isso que um arquivo editado à mão pode conter. Apertar aqui só empurraria a
   * falha para dentro do Gson, onde ela vira uma {@code RuntimeException} sem contexto.
   *
   * @param date data em ISO-8601
   * @param milliliters volume em mililitros
   */
  private record RegistroEmDisco(String date, Integer milliliters) {

    static RegistroEmDisco de(WaterEntry registro) {
      return new RegistroEmDisco(registro.date().toString(), registro.milliliters());
    }
  }
}
