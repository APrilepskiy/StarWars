package starwars;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * "Звёздные войны" — космический аркадный шутер на Java Swing.
 * Управление: стрелки — полёт, Пробел — стрельба, Enter — рестарт.
 */
public class StarWarsGame extends JPanel implements Runnable {

    public static final int WIDTH = 800;
    public static final int HEIGHT = 600;
    private static final int FPS = 60;

    // Состояния игры
    private static final int STATE_PLAYING = 0;
    private static final int STATE_GAME_OVER = 1;

    private volatile boolean running = false;
    private int gameState = STATE_PLAYING;

    private final PlayerShip player;
    private final List<Bullet> bullets = new ArrayList<>();
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Explosion> explosions = new ArrayList<>();
    private final List<Star> stars = new ArrayList<>();

    private final Random random = new Random();
    private int enemySpawnTimer = 0;
    private long score = 0;
    private long highScore = 0;

    // Нажатые клавиши
    private boolean up, down, left, right, fire;

    private Thread gameThread;

    // Двойная буферизация
    private BufferedImage offscreen;
    private volatile BufferedImage currentFrame;

    public StarWarsGame() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_UP:    up = true; break;
                    case KeyEvent.VK_DOWN:  down = true; break;
                    case KeyEvent.VK_LEFT:  left = true; break;
                    case KeyEvent.VK_RIGHT: right = true; break;
                    case KeyEvent.VK_SPACE: fire = true; break;
                    case KeyEvent.VK_ENTER:
                        if (gameState == STATE_GAME_OVER) restart();
                        break;
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_UP:    up = false; break;
                    case KeyEvent.VK_DOWN:  down = false; break;
                    case KeyEvent.VK_LEFT:  left = false; break;
                    case KeyEvent.VK_RIGHT: right = false; break;
                    case KeyEvent.VK_SPACE: fire = false; break;
                }
            }
        });

        for (int i = 0; i < 120; i++) {
            stars.add(new Star(random.nextInt(WIDTH), random.nextInt(HEIGHT),
                    1 + random.nextInt(3), 1 + random.nextDouble() * 2.5));
        }
        player = new PlayerShip(WIDTH / 2, HEIGHT - 90);
    }

    private void restart() {
        bullets.clear();
        enemies.clear();
        explosions.clear();
        score = 0;
        enemySpawnTimer = 0;
        player.reset(WIDTH / 2, HEIGHT - 90);
        gameState = STATE_PLAYING;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        startGame();
    }

    private void startGame() {
        if (gameThread == null) {
            gameThread = new Thread(this, "StarWarsLoop");
            gameThread.start();
        }
    }

    @Override
    public void run() {
        running = true;
        final long frameTime = 1000 / FPS;
        long last = System.nanoTime();

        while (running) {
            update();
            render();

            long now = System.nanoTime();
            long sleep = frameTime - (now - last) / 1_000_000;
            last = now;
            if (sleep > 0) {
                try { Thread.sleep(sleep); } catch (InterruptedException ignored) {}
            }
        }
    }

    // ---------------- ЛОГИКА ----------------

    private void update() {
        // Звёзды всегда летят вниз (эффект полёта)
        for (Star s : stars) s.update();

        if (gameState != STATE_PLAYING) return;

        player.update(up, down, left, right);

        // Стрельба
        if (fire) player.tryFire(bullets);

        // Враги появляются всё быстрее по мере роста счёта
        enemySpawnTimer--;
        if (enemySpawnTimer <= 0) {
            enemies.add(new Enemy(random.nextInt(WIDTH - 40) + 20, -30,
                    1.5 + Math.min(4.0, score / 1500.0) + random.nextDouble() * 1.5));
            enemySpawnTimer = Math.max(15, 55 - (int) (score / 500));
        }

        // Обновление пуль
        for (Bullet b : bullets) b.update();
        bullets.removeIf(Bullet::isDead);

        // Обновление врагов
        for (Enemy e : enemies) e.update(WIDTH);
        enemies.removeIf(e -> e.getY() > HEIGHT + 40);

        // Столкновения пуль с врагами
        for (Bullet b : bullets) {
            for (Enemy e : enemies) {
                if (!b.isDead() && !e.isDead() && b.intersects(e)) {
                    b.kill();
                    e.kill();
                    score += 100;
                    explosions.add(new Explosion(e.getX(), e.getY()));
                }
            }
        }
        enemies.removeIf(Enemy::isDead);

        // Столкновение врагов с игроком
        for (Enemy e : enemies) {
            if (e.intersectsPlayer(player)) {
                explosions.add(new Explosion(player.getX(), player.getY()));
                explosions.add(new Explosion(e.getX(), e.getY()));
                gameOver();
                break;
            }
        }

        // Взрывы
        for (Explosion ex : explosions) ex.update();
        explosions.removeIf(Explosion::isFinished);

        if (score > highScore) highScore = score;
    }

    private void gameOver() {
        gameState = STATE_GAME_OVER;
    }

    // ---------------- ОТРИСОВКА ----------------

    private void render() {
        // Двойная буферизация через BufferedImage (Swing-совместимый способ)
        if (offscreen == null) {
            offscreen = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        }
        Graphics2D g2 = offscreen.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Фон
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, WIDTH, HEIGHT);

        for (Star s : stars) s.draw(g2);

        for (Bullet b : bullets) b.draw(g2);
        for (Enemy e : enemies) e.draw(g2);
        for (Explosion ex : explosions) ex.draw(g2);

        if (gameState == STATE_PLAYING) player.draw(g2);

        // HUD
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.BOLD, 16));
        g2.drawString("СЧЁТ: " + score, 12, 24);
        g2.drawString("РЕКОРД: " + highScore, WIDTH - 170, 24);

        if (gameState == STATE_GAME_OVER) {
            g2.setColor(new Color(0, 0, 0, 170));
            g2.fillRect(0, 0, WIDTH, HEIGHT);
            g2.setColor(Color.RED);
            g2.setFont(new Font("Monospaced", Font.BOLD, 48));
            String go = "GAME OVER";
            int w = g2.getFontMetrics().stringWidth(go);
            g2.drawString(go, (WIDTH - w) / 2, HEIGHT / 2 - 20);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Monospaced", Font.BOLD, 20));
            String hint = "Нажмите ENTER для рестарта";
            w = g2.getFontMetrics().stringWidth(hint);
            g2.drawString(hint, (WIDTH - w) / 2, HEIGHT / 2 + 30);
        }

        g2.dispose();
        // Вывод кадра на панель в потоке EDT
        final BufferedImage frameImg = offscreen;
        SwingUtilities.invokeLater(() -> { currentFrame = frameImg; repaint(); });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (currentFrame != null) {
            g.drawImage(currentFrame, 0, 0, this);
        }
    }

    // ---------------- СУЩНОСТИ ----------------

    /** Игрок — небольшой истребитель в стиле X-Wing. */
    static class PlayerShip {
        private double x, y;
        private static final int SIZE = 20;
        private int fireCooldown = 0;

        PlayerShip(int x, int y) { this.x = x; this.y = y; }

        void reset(int x, int y) { this.x = x; this.y = y; fireCooldown = 0; }

        int getX() { return (int) x; }
        int getY() { return (int) y; }

        void update(boolean up, boolean down, boolean left, boolean right) {
            double speed = 5.5;
            if (up)    y -= speed;
            if (down)  y += speed;
            if (left)  x -= speed;
            if (right) x += speed;
            x = Math.max(SIZE, Math.min(StarWarsGame.WIDTH - SIZE, x));
            y = Math.max(SIZE, Math.min(StarWarsGame.HEIGHT - SIZE, y));
            if (fireCooldown > 0) fireCooldown--;
        }

        void tryFire(List<Bullet> bullets) {
            if (fireCooldown == 0) {
                bullets.add(new Bullet(x, y - SIZE, true));
                fireCooldown = 10; // темп стрельбы
            }
        }

        Rectangle bounds() {
            return new Rectangle((int) x - SIZE / 2, (int) y - SIZE / 2, SIZE, SIZE);
        }

        void draw(Graphics2D g2) {
            int cx = (int) x, cy = (int) y;
            // Корпус
            g2.setColor(new Color(230, 230, 240));
            Polygon body = new Polygon();
            body.addPoint(cx, cy - SIZE);          // нос
            body.addPoint(cx + 5, cy + SIZE - 6);
            body.addPoint(cx - 5, cy + SIZE - 6);
            g2.fillPolygon(body);
            // Крылья
            g2.setColor(new Color(180, 40, 40));
            Polygon wingL = new Polygon();
            wingL.addPoint(cx - 4, cy + 2);
            wingL.addPoint(cx - SIZE, cy + SIZE - 4);
            wingL.addPoint(cx - 4, cy + SIZE - 8);
            Polygon wingR = new Polygon();
            wingR.addPoint(cx + 4, cy + 2);
            wingR.addPoint(cx + SIZE, cy + SIZE - 4);
            wingR.addPoint(cx + 4, cy + SIZE - 8);
            g2.fillPolygon(wingL);
            g2.fillPolygon(wingR);
            // Кабина
            g2.setColor(new Color(60, 140, 255));
            g2.fillOval(cx - 3, cy - 6, 6, 10);
            // Пламя двигателя
            g2.setColor(new Color(255, 170, 40));
            int flame = 6 + (int) (Math.random() * 6);
            g2.fillOval(cx - 3, cy + SIZE - 6, 6, flame);
        }
    }

    /** Пуля игрока (и потенциально врага). */
    static class Bullet {
        double x, y;
        final double vy;
        boolean dead = false;

        Bullet(double x, double y, boolean fromPlayer) {
            this.x = x;
            this.y = y;
            this.vy = fromPlayer ? -9 : 5;
        }

        void update() {
            y += vy;
            if (y < -10 || y > StarWarsGame.HEIGHT + 10) dead = true;
        }

        boolean isDead() { return dead; }
        void kill() { dead = true; }

        Rectangle bounds() {
            return new Rectangle((int) x - 2, (int) y - 8, 4, 12);
        }

        boolean intersects(Enemy e) { return bounds().intersects(e.bounds()); }

        void draw(Graphics2D g2) {
            g2.setColor(new Color(80, 255, 120));
            g2.fillRect((int) x - 2, (int) y - 8, 4, 12);
            g2.setColor(Color.WHITE);
            g2.fillRect((int) x - 1, (int) y - 8, 2, 4);
        }
    }

    /** Враг — TIE Fighter. */
    static class Enemy {
        double x, y;
        final double speed;
        final double drift;
        boolean dead = false;
        private static final int SIZE = 16;

        Enemy(int x, int y, double speed) {
            this.x = x;
            this.y = y;
            this.speed = speed;
            this.drift = (Math.random() - 0.5) * 2.0;
        }

        int getX() { return (int) x; }
        int getY() { return (int) y; }
        boolean isDead() { return dead; }
        void kill() { dead = true; }

        void update(int width) {
            y += speed;
            x += drift;
            if (x < SIZE) { x = SIZE; }
            if (x > width - SIZE) { x = width - SIZE; }
        }

        Rectangle bounds() {
            return new Rectangle((int) x - SIZE / 2, (int) y - SIZE / 2, SIZE, SIZE);
        }

        boolean intersectsPlayer(PlayerShip p) { return bounds().intersects(p.bounds()); }

        void draw(Graphics2D g2) {
            int cx = (int) x, cy = (int) y;
            // Солнечные панели (крылья)
            g2.setColor(new Color(90, 90, 110));
            g2.fillRect(cx - SIZE - 3, cy - SIZE, 5, SIZE * 2);
            g2.fillRect(cx + SIZE - 2, cy - SIZE, 5, SIZE * 2);
            g2.setColor(new Color(40, 60, 140));
            g2.fillRect(cx - SIZE - 2, cy - SIZE + 2, 3, SIZE * 2 - 4);
            g2.fillRect(cx + SIZE - 1, cy - SIZE + 2, 3, SIZE * 2 - 4);
            // Соединители
            g2.setColor(Color.GRAY);
            g2.fillRect(cx - SIZE, cy - 2, SIZE - 6, 4);
            g2.fillRect(cx + 6, cy - 2, SIZE - 6, 4);
            // Кокпит
            g2.setColor(new Color(120, 130, 140));
            g2.fillOval(cx - 7, cy - 7, 14, 14);
            g2.setColor(new Color(20, 20, 30));
            g2.fillOval(cx - 4, cy - 4, 8, 8);
        }
    }

    /** Простой взрыв из расходящихся частиц. */
    static class Explosion {
        final double x, y;
        int life = 22;
        final double[][] parts = new double[14][2];

        Explosion(double x, double y) {
            this.x = x;
            this.y = y;
            Random r = new Random();
            for (int i = 0; i < parts.length; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double spd = 1 + r.nextDouble() * 3;
                parts[i][0] = Math.cos(ang) * spd;
                parts[i][1] = Math.sin(ang) * spd;
            }
        }

        void update() { life--; }
        boolean isFinished() { return life <= 0; }

        void draw(Graphics2D g2) {
            float t = life / 22f;
            int radius = (int) ((1 - t) * 26);
            g2.setColor(new Color(255, (int) (120 * t + 60), 30, (int) (255 * t)));
            for (double[] p : parts) {
                int px = (int) (x + p[0] * (22 - life) * 1.6);
                int py = (int) (y + p[1] * (22 - life) * 1.6);
                g2.fillOval(px - 2, py - 2, 5, 5);
            }
            g2.setColor(new Color(255, 220, 120, (int) (200 * t)));
            g2.fillOval((int) x - radius / 2, (int) y - radius / 2, radius, radius);
        }
    }

    /** Звезда на фоне. */
    static class Star {
        double x, y;
        final double size, speed;

        Star(int x, int y, double size, double speed) {
            this.x = x; this.y = y; this.size = size; this.speed = speed;
        }

        void update() {
            y += speed;
            if (y > StarWarsGame.HEIGHT) {
                y = -2;
                x = Math.random() * StarWarsGame.WIDTH;
            }
        }

        void draw(Graphics2D g2) {
            int bright = (int) (120 + size * 45);
            g2.setColor(new Color(bright, bright, Math.min(255, bright + 20)));
            g2.fillRect((int) x, (int) y, (int) size, (int) size);
        }
    }

    // ---------------- ЗАПУСК ----------------

    public static void main(String[] args) {
        JFrame frame = new JFrame("Star Wars — космический шутер");
        StarWarsGame game = new StarWarsGame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(game);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setVisible(true);
    }
}
