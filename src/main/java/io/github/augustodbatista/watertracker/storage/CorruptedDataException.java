package io.github.augustodbatista.watertracker.storage;

/**
 * O arquivo de registros existe, mas não pôde ser interpretado.
 *
 * <p>Separado de falha de I/O porque os dois pedem respostas diferentes na interface: um é
 * permanente e precisa ser mostrado, o outro é transitório.
 */
public class CorruptedDataException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Cria a exceção.
   *
   * @param mensagem qual parte do arquivo não pôde ser lida
   * @param causa exceção original de parse ou de validação
   */
  public CorruptedDataException(String mensagem, Throwable causa) {
    super(mensagem, causa);
  }
}
