package io.github.augustodbatista.watertracker.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.augustodbatista.watertracker.domain.WaterEntry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonIntakeRepositoryTest {

  private static final LocalDate HOJE = LocalDate.of(2026, 7, 27);

  @TempDir private Path diretorio;

  @Test
  void deveDevolverListaVaziaQuandoOArquivoAindaNaoExiste() {
    assertEquals(List.of(), new JsonIntakeRepository(diretorio).loadAll());
  }

  @Test
  void deveRecuperarORegistroQueFoiSalvo() {
    JsonIntakeRepository repositorio = new JsonIntakeRepository(diretorio);

    repositorio.save(new WaterEntry(HOJE, 250));

    assertEquals(List.of(new WaterEntry(HOJE, 250)), repositorio.loadAll());
  }

  @Test
  void devePreservarAOrdemDeVariosRegistros() {
    JsonIntakeRepository repositorio = new JsonIntakeRepository(diretorio);

    repositorio.save(new WaterEntry(HOJE, 250));
    repositorio.save(new WaterEntry(HOJE, 500));
    repositorio.save(new WaterEntry(HOJE.minusDays(1), 100));

    assertEquals(
        List.of(
            new WaterEntry(HOJE, 250),
            new WaterEntry(HOJE, 500),
            new WaterEntry(HOJE.minusDays(1), 100)),
        repositorio.loadAll());
  }

  @Test
  void deveCriarODiretorioDeDadosQuandoEleAindaNaoExiste() {
    Path inexistente = diretorio.resolve("nivel-um").resolve("nivel-dois");

    new JsonIntakeRepository(inexistente).save(new WaterEntry(HOJE, 250));

    assertTrue(Files.isDirectory(inexistente));
  }

  @Test
  void naoDeveDeixarArquivoTemporarioParaTras() throws IOException {
    new JsonIntakeRepository(diretorio).save(new WaterEntry(HOJE, 250));

    try (var conteudo = Files.list(diretorio)) {
      assertEquals(
          List.of("entries.json"), conteudo.map(p -> p.getFileName().toString()).sorted().toList());
    }
  }

  /** O arquivo é do usuário: ele precisa conseguir abrir e entender sem ferramenta especial. */
  @Test
  void deveGravarADataEmFormatoIsoLegivel() throws IOException {
    new JsonIntakeRepository(diretorio).save(new WaterEntry(HOJE, 250));

    String json = Files.readString(diretorio.resolve("entries.json"), StandardCharsets.UTF_8);

    assertTrue(json.contains("2026-07-27"), () -> "esperava data ISO no arquivo, veio: " + json);
    assertTrue(json.contains("250"), () -> "esperava o volume no arquivo, veio: " + json);
  }

  @Test
  void deveSobrescreverOArquivoAnteriorEmVezDeConcatenar() {
    new JsonIntakeRepository(diretorio).save(new WaterEntry(HOJE, 250));
    new JsonIntakeRepository(diretorio).save(new WaterEntry(HOJE, 300));

    assertEquals(2, new JsonIntakeRepository(diretorio).loadAll().size());
  }

  @Test
  void deveRecusarArquivoComJsonMalformado() throws IOException {
    Files.writeString(
        diretorio.resolve("entries.json"), "{isto nao e json", StandardCharsets.UTF_8);

    assertThrows(CorruptedDataException.class, () -> new JsonIntakeRepository(diretorio).loadAll());
  }

  /** Desfazer um misclick tira <em>um</em> registro, não todos os iguais a ele. */
  @Test
  void deveRemoverApenasUmaOcorrenciaQuandoHaRegistrosIguais() {
    JsonIntakeRepository repositorio = new JsonIntakeRepository(diretorio);
    repositorio.save(new WaterEntry(HOJE, 250));
    repositorio.save(new WaterEntry(HOJE, 250));

    repositorio.remove(new WaterEntry(HOJE, 250));

    assertEquals(List.of(new WaterEntry(HOJE, 250)), repositorio.loadAll());
  }

  @Test
  void deveManterOsDemaisRegistrosAoRemoverUm() {
    JsonIntakeRepository repositorio = new JsonIntakeRepository(diretorio);
    repositorio.save(new WaterEntry(HOJE, 250));
    repositorio.save(new WaterEntry(HOJE, 500));

    repositorio.remove(new WaterEntry(HOJE, 250));

    assertEquals(List.of(new WaterEntry(HOJE, 500)), repositorio.loadAll());
  }

  @Test
  void naoDeveAlterarNadaAoRemoverRegistroInexistente() {
    JsonIntakeRepository repositorio = new JsonIntakeRepository(diretorio);
    repositorio.save(new WaterEntry(HOJE, 250));

    repositorio.remove(new WaterEntry(HOJE, 999));

    assertEquals(List.of(new WaterEntry(HOJE, 250)), repositorio.loadAll());
  }

  @Test
  void deveZerarSomenteODiaInformado() {
    JsonIntakeRepository repositorio = new JsonIntakeRepository(diretorio);
    repositorio.save(new WaterEntry(HOJE, 250));
    repositorio.save(new WaterEntry(HOJE, 500));
    repositorio.save(new WaterEntry(HOJE.minusDays(1), 700));

    repositorio.clearDay(HOJE);

    assertEquals(List.of(new WaterEntry(HOJE.minusDays(1), 700)), repositorio.loadAll());
  }

  @Test
  void deveRejeitarRegistroNuloAoRemover() {
    JsonIntakeRepository repositorio = new JsonIntakeRepository(diretorio);

    assertThrows(NullPointerException.class, () -> repositorio.remove(null));
  }

  @Test
  void deveRejeitarDiaNuloAoZerar() {
    JsonIntakeRepository repositorio = new JsonIntakeRepository(diretorio);

    assertThrows(NullPointerException.class, () -> repositorio.clearDay(null));
  }

  @Test
  void deveRejeitarDiretorioNulo() {
    assertThrows(NullPointerException.class, () -> new JsonIntakeRepository(null));
  }

  @Test
  void deveRejeitarRegistroNuloAoSalvar() {
    JsonIntakeRepository repositorio = new JsonIntakeRepository(diretorio);

    assertThrows(NullPointerException.class, () -> repositorio.save(null));
  }
}
