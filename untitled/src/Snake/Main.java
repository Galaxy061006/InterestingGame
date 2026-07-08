package Snake;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Snake");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.add(new SnakeGame());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    private static final class SnakeGame extends JPanel {
        private static final int TILE_SIZE = 24;
        private static final int TILE_COUNT = 25;
        private static final int BOARD_SIZE = TILE_SIZE * TILE_COUNT;
        private static final int DELAY_MS = 110;
        private static final SnakeColor[] SNAKE_COLORS = {
                new SnakeColor("Green", new Color(99, 214, 131), new Color(58, 181, 102)),
                new SnakeColor("Blue", new Color(91, 157, 255), new Color(54, 111, 218)),
                new SnakeColor("Yellow", new Color(245, 204, 82), new Color(215, 158, 45)),
                new SnakeColor("Pink", new Color(238, 116, 183), new Color(202, 75, 145)),
                new SnakeColor("White", new Color(235, 239, 244), new Color(171, 181, 190))
        };

        private final Random random = new Random();
        private final List<Point> snake = new ArrayList<>();
        private final Timer timer = new Timer(DELAY_MS, event -> tick());

        private Point food;
        private Direction direction = Direction.RIGHT;
        private Direction nextDirection = Direction.RIGHT;
        private boolean gameOver;
        private boolean paused;
        private boolean choosingColor;
        private int selectedColorIndex;
        private int score;

        SnakeGame() {
            setPreferredSize(new Dimension(BOARD_SIZE, BOARD_SIZE));
            setBackground(new Color(20, 23, 28));
            setFocusable(true);
            addKeyListener(new SnakeKeyListener());
            showColorSelection();
        }

        private void showColorSelection() {
            timer.stop();
            snake.clear();
            snake.add(new Point(12, 12));
            snake.add(new Point(11, 12));
            snake.add(new Point(10, 12));
            direction = Direction.RIGHT;
            nextDirection = Direction.RIGHT;
            score = 0;
            gameOver = false;
            paused = false;
            choosingColor = true;
            food = null;
            repaint();
        }

        private void startGame() {
            snake.clear();
            snake.add(new Point(8, 12));
            snake.add(new Point(7, 12));
            snake.add(new Point(6, 12));
            direction = Direction.RIGHT;
            nextDirection = Direction.RIGHT;
            score = 0;
            gameOver = false;
            paused = false;
            choosingColor = false;
            placeFood();
            timer.start();
            repaint();
        }

        private void tick() {
            if (choosingColor || gameOver || paused) {
                return;
            }

            direction = nextDirection;
            Point head = snake.get(0);
            Point nextHead = new Point(head.x + direction.dx, head.y + direction.dy);

            if (hitsWall(nextHead) || hitsSnake(nextHead)) {
                gameOver = true;
                timer.stop();
                repaint();
                return;
            }

            snake.add(0, nextHead);
            if (nextHead.equals(food)) {
                score++;
                placeFood();
            } else {
                snake.remove(snake.size() - 1);
            }

            repaint();
        }

        private boolean hitsWall(Point point) {
            return point.x < 0 || point.y < 0 || point.x >= TILE_COUNT || point.y >= TILE_COUNT;
        }

        private boolean hitsSnake(Point point) {
            return snake.contains(point);
        }

        private void placeFood() {
            do {
                food = new Point(random.nextInt(TILE_COUNT), random.nextInt(TILE_COUNT));
            } while (snake.contains(food));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            drawGrid(g);
            if (food != null) {
                drawFood(g);
            }
            drawSnake(g);
            if (!choosingColor) {
                drawScore(g);
            }

            if (choosingColor) {
                drawColorSelection(g);
            } else if (gameOver) {
                drawGameOver(g);
            } else if (paused) {
                drawPaused(g);
            }

            g.dispose();
        }

        private void drawGrid(Graphics2D g) {
            g.setColor(new Color(31, 36, 43));
            for (int i = 0; i <= TILE_COUNT; i++) {
                int position = i * TILE_SIZE;
                g.drawLine(position, 0, position, BOARD_SIZE);
                g.drawLine(0, position, BOARD_SIZE, position);
            }
        }

        private void drawFood(Graphics2D g) {
            g.setColor(new Color(238, 79, 74));
            int margin = 4;
            g.fillOval(
                    food.x * TILE_SIZE + margin,
                    food.y * TILE_SIZE + margin,
                    TILE_SIZE - margin * 2,
                    TILE_SIZE - margin * 2
            );
        }

        private void drawSnake(Graphics2D g) {
            SnakeColor snakeColor = SNAKE_COLORS[selectedColorIndex];
            for (int i = 0; i < snake.size(); i++) {
                Point part = snake.get(i);
                g.setColor(i == 0 ? snakeColor.head : snakeColor.body);
                g.fillRoundRect(
                        part.x * TILE_SIZE + 2,
                        part.y * TILE_SIZE + 2,
                        TILE_SIZE - 4,
                        TILE_SIZE - 4,
                        8,
                        8
                );
            }
        }

        private void drawScore(Graphics2D g) {
            g.setFont(new Font("SansSerif", Font.BOLD, 18));
            g.setColor(new Color(229, 233, 240));
            g.drawString("Score: " + score, 14, 26);
        }

        private void drawGameOver(Graphics2D g) {
            g.setColor(new Color(0, 0, 0, 170));
            g.fillRect(0, 0, BOARD_SIZE, BOARD_SIZE);

            g.setFont(new Font("SansSerif", Font.BOLD, 42));
            g.setColor(Color.WHITE);
            drawCenteredText(g, "Game Over", BOARD_SIZE / 2 - 18);

            g.setFont(new Font("SansSerif", Font.PLAIN, 20));
            drawCenteredText(g, "Press Space to choose color", BOARD_SIZE / 2 + 24);
        }

        private void drawPaused(Graphics2D g) {
            g.setColor(new Color(0, 0, 0, 130));
            g.fillRect(0, 0, BOARD_SIZE, BOARD_SIZE);

            g.setFont(new Font("SansSerif", Font.BOLD, 42));
            g.setColor(Color.WHITE);
            drawCenteredText(g, "Paused", BOARD_SIZE / 2 - 18);

            g.setFont(new Font("SansSerif", Font.PLAIN, 20));
            drawCenteredText(g, "Press P or Space to continue", BOARD_SIZE / 2 + 24);
        }

        private void drawColorSelection(Graphics2D g) {
            g.setColor(new Color(0, 0, 0, 145));
            g.fillRect(0, 0, BOARD_SIZE, BOARD_SIZE);

            g.setFont(new Font("SansSerif", Font.BOLD, 36));
            g.setColor(Color.WHITE);
            drawCenteredText(g, "Choose Snake Color", 170);

            int swatchSize = 52;
            int gap = 18;
            int totalWidth = SNAKE_COLORS.length * swatchSize + (SNAKE_COLORS.length - 1) * gap;
            int startX = (BOARD_SIZE - totalWidth) / 2;
            int y = 245;

            for (int i = 0; i < SNAKE_COLORS.length; i++) {
                int x = startX + i * (swatchSize + gap);
                SnakeColor snakeColor = SNAKE_COLORS[i];
                g.setColor(i == selectedColorIndex ? Color.WHITE : new Color(125, 134, 145));
                g.drawRoundRect(x - 5, y - 5, swatchSize + 10, swatchSize + 10, 12, 12);
                g.setColor(snakeColor.body);
                g.fillRoundRect(x, y, swatchSize, swatchSize, 10, 10);
                g.setColor(snakeColor.head);
                g.fillOval(x + 14, y + 10, 24, 24);
            }

            SnakeColor selected = SNAKE_COLORS[selectedColorIndex];
            g.setFont(new Font("SansSerif", Font.BOLD, 22));
            g.setColor(Color.WHITE);
            drawCenteredText(g, selected.name, 342);

            g.setFont(new Font("SansSerif", Font.PLAIN, 18));
            drawCenteredText(g, "Use Left/Right or A/D, then press Space", 388);
        }

        private void drawCenteredText(Graphics2D g, String text, int y) {
            FontMetrics metrics = g.getFontMetrics();
            int x = (BOARD_SIZE - metrics.stringWidth(text)) / 2;
            g.drawString(text, x, y);
        }

        private final class SnakeKeyListener extends KeyAdapter {
            @Override
            public void keyPressed(KeyEvent event) {
                int key = event.getKeyCode();

                if (choosingColor) {
                    if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) {
                        selectedColorIndex = (selectedColorIndex - 1 + SNAKE_COLORS.length) % SNAKE_COLORS.length;
                        repaint();
                    } else if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) {
                        selectedColorIndex = (selectedColorIndex + 1) % SNAKE_COLORS.length;
                        repaint();
                    } else if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) {
                        startGame();
                    }
                    return;
                }

                if (key == KeyEvent.VK_SPACE && gameOver) {
                    showColorSelection();
                    return;
                }

                if ((key == KeyEvent.VK_P || key == KeyEvent.VK_SPACE) && !gameOver) {
                    paused = !paused;
                    repaint();
                    return;
                }

                if (paused) {
                    return;
                }

                Direction requested = switch (key) {
                    case KeyEvent.VK_UP, KeyEvent.VK_W -> Direction.UP;
                    case KeyEvent.VK_DOWN, KeyEvent.VK_S -> Direction.DOWN;
                    case KeyEvent.VK_LEFT, KeyEvent.VK_A -> Direction.LEFT;
                    case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> Direction.RIGHT;
                    default -> null;
                };

                if (requested != null && !requested.isOpposite(direction)) {
                    nextDirection = requested;
                }
            }
        }
    }

    private enum Direction {
        UP(0, -1),
        DOWN(0, 1),
        LEFT(-1, 0),
        RIGHT(1, 0);

        private final int dx;
        private final int dy;

        Direction(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }

        private boolean isOpposite(Direction other) {
            return dx + other.dx == 0 && dy + other.dy == 0;
        }
    }

    private record Point(int x, int y) {
    }

    private record SnakeColor(String name, Color head, Color body) {
    }
}
