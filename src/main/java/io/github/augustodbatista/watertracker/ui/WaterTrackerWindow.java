package io.github.augustodbatista.watertracker.ui;

import io.github.augustodbatista.watertracker.domain.DailyIntake;
import io.github.augustodbatista.watertracker.domain.WaterEntry;
import io.github.augustodbatista.watertracker.storage.DailyGoalStore;
import io.github.augustodbatista.watertracker.storage.JsonIntakeRepository;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

/** Janela sem borda, sempre no topo, arrastável pelo corpo. */
final class WaterTrackerWindow extends JFrame {

  private static final long serialVersionUID = 1L;

  private static final int[] PORCOES_ML = {200, 250, 500};
  private static final int PASSO_DA_META_ML = 100;

  private static final Color FUNDO = new Color(0x1B, 0x26, 0x32);
  private static final Color TEXTO = new Color(0xE8, 0xF1, 0xF8);
  private static final Color TEXTO_FRACO = new Color(0x8C, 0xA3, 0xB8);
  private static final Color AGUA = new Color(0x35, 0x9E, 0xE0);
  private static final Color TRILHO = new Color(0x2A, 0x3A, 0x4A);

  private final transient JsonIntakeRepository registros;
  private final transient DailyGoalStore metas;

  private final JLabel total = new JLabel("", SwingConstants.CENTER);
  private final JProgressBar progresso = new JProgressBar();

  WaterTrackerWindow(JsonIntakeRepository registros, DailyGoalStore metas) {
    this.registros = registros;
    this.metas = metas;

    setUndecorated(true);
    setAlwaysOnTop(true);
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    setTitle("Water Tracker");

    JPanel raiz = new JPanel(new BorderLayout(0, 10));
    raiz.setBackground(FUNDO);
    raiz.setBorder(BorderFactory.createEmptyBorder(12, 16, 14, 16));
    raiz.add(cabecalho(), BorderLayout.NORTH);
    raiz.add(centro(), BorderLayout.CENTER);
    raiz.add(botoes(), BorderLayout.SOUTH);

    permitirArrastar(raiz);
    setContentPane(raiz);
    atualizar();
    // Hurdle #18: pack() por ultimo e sem mexer no tamanho depois.
    pack();
    posicionarNoCantoInferiorDireito();
  }

  private JPanel cabecalho() {
    JPanel linha = new JPanel(new BorderLayout());
    linha.setOpaque(false);

    JLabel titulo = new JLabel("Água hoje");
    titulo.setForeground(TEXTO_FRACO);
    titulo.setFont(titulo.getFont().deriveFont(Font.PLAIN, 11f));

    JPanel acoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    acoes.setOpaque(false);
    // Corrigir fica fora da tela principal de proposito: um botao de apagar ao lado dos de
    // registrar so trocaria um misclick por outro, pior.
    acoes.add(icone("↺", "Corrigir registros de hoje", this::abrirCorrecoes));
    acoes.add(icone("⚙", "Configurar meta diária", this::abrirConfiguracoes));
    acoes.add(icone("×", "Fechar", this::dispose));

    linha.add(titulo, BorderLayout.WEST);
    linha.add(acoes, BorderLayout.EAST);
    return linha;
  }

  private JLabel icone(String simbolo, String dica, Runnable acao) {
    JLabel rotulo = new JLabel(simbolo);
    rotulo.setForeground(TEXTO_FRACO);
    rotulo.setFont(rotulo.getFont().deriveFont(Font.PLAIN, 15f));
    rotulo.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    rotulo.setToolTipText(dica);
    rotulo.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent evento) {
            acao.run();
          }

          @Override
          public void mouseEntered(MouseEvent evento) {
            rotulo.setForeground(TEXTO);
          }

          @Override
          public void mouseExited(MouseEvent evento) {
            rotulo.setForeground(TEXTO_FRACO);
          }
        });
    return rotulo;
  }

  private JPanel centro() {
    JPanel coluna = new JPanel();
    coluna.setLayout(new BoxLayout(coluna, BoxLayout.Y_AXIS));
    coluna.setOpaque(false);

    total.setForeground(TEXTO);
    total.setFont(total.getFont().deriveFont(Font.BOLD, 22f));
    total.setAlignmentX(CENTER_ALIGNMENT);

    progresso.setForeground(AGUA);
    progresso.setBackground(TRILHO);
    progresso.setBorderPainted(false);
    // Hurdle #18: largura real, nao zero, senao o pack() encolhe a coluna inteira.
    progresso.setPreferredSize(new Dimension(230, 8));
    progresso.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
    progresso.setAlignmentX(CENTER_ALIGNMENT);

    coluna.add(total);
    coluna.add(Box.createVerticalStrut(10));
    coluna.add(progresso);
    return coluna;
  }

  private JPanel botoes() {
    JPanel linha = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
    linha.setOpaque(false);
    for (int porcao : PORCOES_ML) {
      linha.add(botao("+" + porcao, "Registrar " + porcao + " ml", () -> registrar(porcao)));
    }
    linha.add(botao("⋯", "Digitar outra quantidade", this::perguntarQuantidade));
    return linha;
  }

  private JButton botao(String texto, String dica, Runnable acao) {
    JButton botao = new JButton(texto);
    botao.setFocusPainted(false);
    botao.setBackground(AGUA);
    botao.setForeground(Color.WHITE);
    botao.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
    botao.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    botao.setToolTipText(dica);
    botao.addActionListener(evento -> acao.run());
    return botao;
  }

  private void perguntarQuantidade() {
    String digitado =
        JOptionPane.showInputDialog(
            this, "Quantos mililitros?", "Registrar água", JOptionPane.PLAIN_MESSAGE);
    if (digitado == null || digitado.isBlank()) {
      return;
    }
    try {
      registrar(Integer.parseInt(digitado.trim()));
    } catch (NumberFormatException e) {
      avisar("Digite apenas o número em mililitros. Por exemplo: 350");
    }
  }

  private void abrirConfiguracoes() {
    JSpinner campo =
        new JSpinner(
            new SpinnerNumberModel(
                metas.load(),
                DailyGoalStore.META_MINIMA_ML,
                DailyGoalStore.META_MAXIMA_ML,
                PASSO_DA_META_ML));

    JPanel painel = new JPanel(new GridLayout(0, 1, 0, 8));
    painel.add(new JLabel("Meta diária, em mililitros:"));
    painel.add(campo);

    int escolha =
        JOptionPane.showConfirmDialog(
            this, painel, "Configurações", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
    if (escolha != JOptionPane.OK_OPTION) {
      return;
    }
    try {
      metas.save((Integer) campo.getValue());
      atualizar();
    } catch (RuntimeException e) {
      avisar(e.getMessage());
    }
  }

  private void abrirCorrecoes() {
    boolean primeiraVolta = true;
    while (true) {
      List<WaterEntry> doDia;
      try {
        doDia = new DailyIntake(LocalDate.now(), registros.loadAll()).entries();
      } catch (RuntimeException e) {
        avisar(e.getMessage());
        return;
      }
      if (doDia.isEmpty()) {
        // Só avisa se já abriu vazio. Reabrir para dizer "acabou" depois de remover o último
        // registro seria um clique a mais para informar o óbvio.
        if (primeiraVolta) {
          avisar("Não há registros hoje para corrigir.");
        }
        return;
      }
      primeiraVolta = false;
      if (!corrigirUmaVez(doDia)) {
        return;
      }
    }
  }

  /**
   * Mostra os registros do dia e aplica uma correção.
   *
   * @return {@code true} se o diálogo deve reabrir para outra correção
   */
  private boolean corrigirUmaVez(List<WaterEntry> doDia) {
    JList<String> lista =
        new JList<>(doDia.stream().map(r -> r.milliliters() + " ml").toArray(String[]::new));
    lista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    // O ultimo e o candidato obvio: quem errou o clique acabou de errar.
    lista.setSelectedIndex(doDia.size() - 1);

    JScrollPane rolagem = new JScrollPane(lista);
    rolagem.setPreferredSize(new Dimension(210, 108));

    Object[] opcoes = {"Remover selecionado", "Zerar o dia", "Fechar"};
    int escolha =
        JOptionPane.showOptionDialog(
            this,
            rolagem,
            "Corrigir registros de hoje",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.PLAIN_MESSAGE,
            null,
            opcoes,
            opcoes[2]);

    if (escolha == 0 && lista.getSelectedIndex() >= 0) {
      registros.remove(doDia.get(lista.getSelectedIndex()));
      atualizar();
      return true;
    }
    if (escolha == 1 && confirmarZerar(doDia.size())) {
      registros.clearDay(LocalDate.now());
      atualizar();
    }
    return false;
  }

  private boolean confirmarZerar(int quantidade) {
    int resposta =
        JOptionPane.showConfirmDialog(
            this,
            "Apagar os " + quantidade + " registros de hoje? Isso não tem desfazer.",
            "Zerar o dia",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
    return resposta == JOptionPane.YES_OPTION;
  }

  private void registrar(int mililitros) {
    try {
      registros.save(new WaterEntry(LocalDate.now(), mililitros));
      atualizar();
    } catch (RuntimeException e) {
      avisar(e.getMessage());
    }
  }

  private void atualizar() {
    int meta = metas.load();
    long consumido;
    try {
      consumido = new DailyIntake(LocalDate.now(), registros.loadAll()).totalMilliliters();
    } catch (RuntimeException e) {
      total.setText("erro ao ler");
      total.setToolTipText(e.getMessage());
      return;
    }
    total.setText(consumido + " / " + meta + " ml");
    total.setToolTipText(null);
    progresso.setMaximum(meta);
    progresso.setValue((int) Math.min(consumido, meta));
  }

  private void avisar(String mensagem) {
    JOptionPane.showMessageDialog(this, mensagem, "Water Tracker", JOptionPane.WARNING_MESSAGE);
  }

  /** Sem borda não há barra de título, então a janela inteira vira a alça de arraste. */
  private void permitirArrastar(JComponent raiz) {
    Point[] agarrou = new Point[1];
    MouseAdapter arraste =
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent evento) {
            agarrou[0] = evento.getPoint();
          }

          @Override
          public void mouseDragged(MouseEvent evento) {
            if (agarrou[0] != null) {
              setLocation(
                  getX() + evento.getX() - agarrou[0].x, getY() + evento.getY() - agarrou[0].y);
            }
          }
        };
    raiz.addMouseListener(arraste);
    raiz.addMouseMotionListener(arraste);
  }

  private void posicionarNoCantoInferiorDireito() {
    var area = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
    setLocation(area.x + area.width - getWidth() - 24, area.y + area.height - getHeight() - 24);
  }
}
