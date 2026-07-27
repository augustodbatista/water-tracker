package io.github.augustodbatista.watertracker.domain;

/**
 * Sinaliza que um volume de água informado viola as regras do domínio.
 *
 * <p>É uma exceção de domínio nomeada, e não uma {@link IllegalArgumentException} crua: o tipo
 * comunica <em>qual</em> regra foi violada, permitindo que a camada de interface trate este caso
 * especificamente sem inspecionar mensagens de texto.
 */
public class InvalidVolumeException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Cria a exceção com a explicação da regra violada.
   *
   * @param mensagem descrição legível de qual limite foi desrespeitado
   */
  public InvalidVolumeException(String mensagem) {
    super(mensagem);
  }
}
