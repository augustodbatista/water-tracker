package io.github.augustodbatista.watertracker.ui;

import io.github.augustodbatista.watertracker.domain.DailyIntake;
import io.github.augustodbatista.watertracker.domain.WaterEntry;
import io.github.augustodbatista.watertracker.storage.JsonIntakeRepository;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;

/** Janela sem borda, sempre no topo, arrastável pelo corpo. */
final class WaterTrackerWindow extends JFrame {

  private static final long serialVersionUID = 1L;

  private static final int[] PORCOES_ML = {200, 250, 500};

  private static final Color FUNDO = new Color(0x1B, 0x26, 0x32);
  private static final Color TEXTO = new Color(0xE8, 0xF1, 0xF8);
  private static final Color TEXTO_FRACO = new Color(0x8C, 0xA3, 0xB8);
  private static final Color AGUA = new Color(0x35, 0x9E, 0xE0);

  private final transient JsonIntakeRepository repositorio;
  private final JLabel total = new JLabel("", SwingConstants.CENTER);
  private final JProgressBar progresso = new JProgressBar(0, DailyIntake.META_DIARIA_ML);

  WaterTrackerWindow(JsonIntakeRepository repositorio) {
    this.repositorio = repositorio;

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
    // pack() por ultimo e sem mexer no tamanho depois: redimensionar apos o pack deixa o layout
    // desatualizado, e o texto do total passa por cima dos botoes.
    pack();
    posicionarNoCantoInferiorDireito();
  }

  private JPanel cabecalho() {
    JPanel linha = new JPanel(new BorderLayout());
    linha.setOpaque(false);

    JLabel titulo = new JLabel("Água hoje");
    titulo.setForeground(TEXTO_FRACO);
    titulo.setFont(titulo.getFont().deriveFont(Font.PLAIN, 11f));

    JLabel fechar = new JLabel("×");
    fechar.setForeground(TEXTO_FRACO);
    fechar.setFont(fechar.getFont().deriveFont(Font.PLAIN, 16f));
    fechar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    fechar.setToolTipText("Fechar");
    fechar.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent evento) {
            dispose();
          }
        });

    linha.add(titulo, BorderLayout.WEST);
    linha.add(fechar, BorderLayout.EAST);
    return linha;
  }

  private JPanel centro() {
    JPanel coluna = new JPanel();
    coluna.setLayout(new BoxLayout(coluna, BoxLayout.Y_AXIS));
    coluna.setOpaque(false);

    total.setForeground(TEXTO);
    total.setFont(total.getFont().deriveFont(Font.BOLD, 22f));
    total.setAlignmentX(CENTER_ALIGNMENT);

    progresso.setForeground(AGUA);
    progresso.setBackground(new Color(0x2A, 0x3A, 0x4A));
    progresso.setBorderPainted(false);
    // Largura real, nao zero: com preferredSize de largura 0 o pack() encolhe a coluna inteira.
    progresso.setPreferredSize(new Dimension(220, 8));
    progresso.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
    progresso.setAlignmentX(CENTER_ALIGNMENT);

    coluna.add(total);
    coluna.add(Box.createVerticalStrut(10));
    coluna.add(progresso);
    return coluna;
  }

  private JPanel botoes() {
    JPanel linha = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
    linha.setOpaque(false);
    for (int porcao : PORCOES_ML) {
      linha.add(botaoDePorcao(porcao));
    }
    return linha;
  }

  private JButton botaoDePorcao(int mililitros) {
    JButton botao = new JButton("+" + mililitros);
    botao.setFocusPainted(false);
    botao.setBackground(AGUA);
    botao.setForeground(Color.WHITE);
    botao.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
    botao.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    botao.setToolTipText("Registrar " + mililitros + " ml");
    botao.addActionListener(evento -> registrar(mililitros));
    return botao;
  }

  private void registrar(int mililitros) {
    try {
      repositorio.save(new WaterEntry(LocalDate.now(), mililitros));
      atualizar();
    } catch (RuntimeException e) {
      JOptionPane.showMessageDialog(
          this, e.getMessage(), "Não deu para registrar", JOptionPane.ERROR_MESSAGE);
    }
  }

  private void atualizar() {
    long consumido;
    try {
      consumido = new DailyIntake(LocalDate.now(), repositorio.loadAll()).totalMilliliters();
    } catch (RuntimeException e) {
      total.setText("erro ao ler");
      total.setToolTipText(e.getMessage());
      return;
    }
    total.setText(consumido + " / " + DailyIntake.META_DIARIA_ML + " ml");
    progresso.setValue((int) Math.min(consumido, DailyIntake.META_DIARIA_ML));
  }

  /** Sem borda não há barra de título, então a janela inteira vira a alça de arraste. */
  private void permitirArrastar(JPanel raiz) {
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
    var area = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
    setLocation(area.x + area.width - getWidth() - 24, area.y + area.height - getHeight() - 24);
  }
}
