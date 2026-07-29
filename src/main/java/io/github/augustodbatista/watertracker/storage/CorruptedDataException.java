package io.github.augustodbatista.watertracker.storage;

/**
 * Sinaliza que o arquivo de registros existe, mas não pôde ser interpretado.
 *
 * <p>Cobre tanto JSON sintaticamente quebrado quanto conteúdo sintaticamente válido que viola uma
 * regra do domínio — um volume zero, por exemplo. O arquivo fica no disco do usuário e nada impede
 * que ele o edite à mão, então "veio de arquivo" não é sinônimo de "confiável".
 *
 * <p>Existe para que a camada de interface distinga <em>arquivo adulterado</em> de <em>falha de
 * I/O</em>: o primeiro caso pede que se mostre o problema ao usuário, o segundo é transitório.
 */
public class CorruptedDataException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Cria a exceção descrevendo o que impediu a leitura.
   *
   * @param mensagem explicação de qual parte do arquivo não pôde ser lida
   * @param causa exceção original de parse ou de validação de domínio
   */
  public CorruptedDataException(String mensagem, Throwable causa) {
    super(mensagem, causa);
  }
}
