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
 * Guarda os registros num único arquivo JSON local, em formato legível a olho nu.
 *
 * <p>A gravação é atômica: escreve num temporário e renomeia por cima com
 * {@link StandardCopyOption#ATOMIC_MOVE}. Sem isso, uma queda no meio da escrita deixaria um JSON
 * truncado e levaria junto todo o histórico.
 *
 * <p>Desserializa para {@link RegistroEmDisco}, não direto para {@link WaterEntry}: o Gson embrulha
 * exceções do construtor do <em>record</em> numa {@code RuntimeException} genérica, indistinguível
 * de um bug nosso — ver Hurdle #17.
 */
public final class JsonIntakeRepository {

  private static final String NOME_DO_ARQUIVO = "entries.json";

  private static final Type TIPO_DA_LISTA = new TypeToken<List<RegistroEmDisco>>() {}.getType();

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private final Path diretorio;
  private final Path arquivo;

  /**
   * Aponta o repositório para um diretório, que é criado na primeira gravação.
   *
   * @param diretorioDeDados pasta onde o {@code entries.json} mora
   */
  public JsonIntakeRepository(Path diretorioDeDados) {
    this.diretorio = Objects.requireNonNull(diretorioDeDados, "O diretório não pode ser nulo.");
    this.arquivo = this.diretorio.resolve(NOME_DO_ARQUIVO);
  }

  /**
   * Lê todos os registros gravados, na ordem em que foram salvos.
   *
   * @return lista imutável; vazia se o arquivo ainda não existe
   * @throws CorruptedDataException se o arquivo existe mas não pôde ser interpretado
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
    try {
      List<RegistroEmDisco> brutos = GSON.fromJson(json, TIPO_DA_LISTA);
      if (brutos == null) {
        return List.of();
      }
      return brutos.stream().map(this::converterParaDominio).toList();
    } catch (JsonParseException | DateTimeParseException | InvalidVolumeException e) {
      throw new CorruptedDataException("O arquivo " + arquivo + " não pôde ser lido.", e);
    }
  }

  /**
   * Acrescenta um registro ao histórico.
   *
   * @param registro consumo a gravar
   */
  public void save(WaterEntry registro) {
    Objects.requireNonNull(registro, "O registro não pode ser nulo.");
    List<WaterEntry> todos = new ArrayList<>(loadAll());
    todos.add(registro);
    gravarAtomicamente(todos);
  }

  private WaterEntry converterParaDominio(RegistroEmDisco bruto) {
    if (bruto == null || bruto.date() == null || bruto.milliliters() == null) {
      throw new CorruptedDataException("Registro incompleto em " + arquivo + ".", null);
    }
    return new WaterEntry(LocalDate.parse(bruto.date()), bruto.milliliters());
  }

  private void gravarAtomicamente(List<WaterEntry> todos) {
    List<RegistroEmDisco> paraGravar = todos.stream().map(RegistroEmDisco::de).toList();
    try {
      Files.createDirectories(diretorio);
      Path temporario = Files.createTempFile(diretorio, NOME_DO_ARQUIVO, ".tmp");
      Files.writeString(temporario, GSON.toJson(paraGravar, TIPO_DA_LISTA), StandardCharsets.UTF_8);
      Files.move(temporario, arquivo, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      throw new UncheckedIOException("Falha ao gravar " + arquivo + ".", e);
    }
  }

  /**
   * Contrato do arquivo em disco, separado do modelo de domínio.
   *
   * <p>Tipos frouxos de propósito: é o que um arquivo editado à mão pode conter.
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
