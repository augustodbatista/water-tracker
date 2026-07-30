package io.github.augustodbatista.watertracker.domain;

/**
 * Volume de água fora das regras do domínio.
 *
 * <p>Tipo nomeado em vez de {@link IllegalArgumentException} para que a interface trate este caso
 * sem inspecionar mensagens de texto.
 */
public class InvalidVolumeException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Cria a exceção.
   *
   * @param mensagem qual limite foi desrespeitado
   */
  public InvalidVolumeException(String mensagem) {
    super(mensagem);
  }
}
