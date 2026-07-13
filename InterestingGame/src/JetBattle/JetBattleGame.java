package JetBattle;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class JetBattleGame {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Jet Battle");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.add(new BattlePanel(frame));
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    private static final class BattlePanel extends JPanel {
        /*
         * Current aircraft balance
         *
         * | Aircraft   | ATK | DEF | HP    | Skill multiplier | SPD | Normal attack                  | Skill                                      | Charge efficiency |
         * |------------|-----|-----|-------|------------------|-----|--------------------------------|--------------------------------------------|-------------------|
         * | Tail Flame | 720 | 400 | 18000 | 1.40             | 188 | Missile, ATK - DEF, 700 ms     | 6 missiles, ATK / 6 * skill multiplier    | Base x1.00, boost leaves flame trails |
         * | Blue Glow  | 960 | 200 | 19000 | 1.55             | 202 | Bullet, ATK * 0.5, 400 ms      | Continuous laser, ATK * 0.7 * skill / sec | Base x1.05, hits stack ion resonance |
         * | Neutron Star | 700 | 460 | 22000 | 1.50           | 176 | Non-continuous laser orb, ATK - DEF, 600 ms | Slow singularity orb, random final ammo type | Base x1.20 + hit/deflect recovery |
         * | Venus      | 820 | 300 | 18500 | 1.30             | 192 | Nose laser bar, ATK * 0.94 - DEF, 600 ms (420 ms armored) | 6.5 s reinforced armor and piercing twin laser cannons | Base x1.00, hits generate orbiting shards |
         * | Six-Winged Angel | 680 | 260 | 18000 | 1.20       | 196 | Holy laser, ATK * 0.86 - DEF * 0.85, 600 ms | Healing circle and passive heart packs | Base x0.95 |
         *
         * Standard baseline values for future aircraft:
         * | ATK | DEF | HP    | Skill multiplier | SPD   |
         * |-----|-----|-------|------------------|-------|
         * | 820 | 300 | 18500 | 1.275            | 189.5 |
         *
         * Blue Glow burst mode:
         * | Shots per round | Interval | Round cooldown | Damage per shot                         | Initial multiplier | Drop per shot | Min multiplier | Charge efficiency |
         * |-----------------|----------|----------------|------------------------------------------|--------------------|---------------|----------------|-------------------|
         * | 4               | 140 ms   | 850 ms         | ATK * 0.68 / 4 * current burst multiplier | 1.20               | 0.05          | 0.25           | Base x0.65025     |
         */
        private static final int WIDTH = 960;
        private static final int HEIGHT = 720;
        private static final int SETUP_PANEL_WIDTH = 560;
        private static final int SETUP_PANEL_X = (WIDTH - SETUP_PANEL_WIDTH) / 2;
        private static final int START_BUTTON_WIDTH = 200;
        private static final int START_BUTTON_X = (WIDTH - START_BUTTON_WIDTH) / 2;
        private static final int HANGAR_BUTTON_WIDTH = 160;
        private static final int HANGAR_BUTTON_X = (WIDTH - HANGAR_BUTTON_WIDTH) / 2;
        private static final int SETTINGS_BUTTON_X = WIDTH - 78;
        private static final int SETTINGS_BUTTON_Y = 24;
        private static final int SETTINGS_BUTTON_SIZE = 38;
        private static final int PAUSE_BUTTON_X = WIDTH - 72;
        private static final int PAUSE_BUTTON_Y = 26;
        private static final int PAUSE_BUTTON_SIZE = 42;
        private static final int PAUSE_MENU_X = 340;
        private static final int PAUSE_MENU_Y = 230;
        private static final int PAUSE_MENU_WIDTH = 280;
        private static final int PAUSE_MENU_HEIGHT = 230;
        private static final int PAUSE_MENU_BUTTON_X = PAUSE_MENU_X + 42;
        private static final int PAUSE_MENU_CONTINUE_Y = PAUSE_MENU_Y + 92;
        private static final int PAUSE_MENU_HOME_Y = PAUSE_MENU_Y + 144;
        private static final int PAUSE_MENU_BUTTON_WIDTH = 196;
        private static final int PAUSE_MENU_BUTTON_HEIGHT = 36;
        private static final int INPUT_MOUSE_LEFT = -1;
        private static final int INPUT_MOUSE_MIDDLE = -2;
        private static final int INPUT_MOUSE_RIGHT = -3;
        private static final int MAX_CHARGE = 100;
        private static final int CHARGE_GAIN = 5;
        private static final int PLAYER_SKILL_COOLDOWN = 280;
        private static final int NORMAL_ATTACK_COOLDOWN = 600;
        private static final int TAIL_FLAME_ATTACK_COOLDOWN = 700;
        private static final int BLUE_SINGLE_SHOT_COOLDOWN = 400;
        private static final int BLUE_FIRE_INTERVAL = 140;
        private static final int BLUE_BURST_ROUND_COOLDOWN = 850;
        private static final int BLUE_BURST_SHOTS_PER_ROUND = 4;
        private static final int VENUS_ATTACK_COOLDOWN = 600;
        private static final int VENUS_ENHANCED_ATTACK_COOLDOWN = 420;
        private static final int AI_ATTACK_INTERVAL = 900;
        private static final int FIGHTER_SIZE = 40;
        private static final int ARENA_LEFT = 0;
        private static final int ARENA_TOP = 0;
        private static final int ARENA_WIDTH = WIDTH;
        private static final int ARENA_HEIGHT = HEIGHT - ARENA_TOP;
        private static final double BOOST_SPEED_MULTIPLIER = 1.8;
        private static final double BOOST_MAX_ENERGY = 2.5;
        private static final double BOOST_RECOVER_DELAY = 0.5;
        private static final double BOOST_RECOVER_PER_SECOND = 0.8;
        private static final double BEAM_PROJECTILE_SPEED = 780;
        private static final double BLUE_GLOW_PROJECTILE_SPEED = 1080;
        private static final double MISSILE_PROJECTILE_SPEED = 630;
        private static final double HOMING_PROJECTILE_SPEED = 630;
        private static final double TAIL_FLAME_SKILL_MISSILE_INITIAL_SPEED = 250;
        private static final double TAIL_FLAME_SKILL_MISSILE_MAX_SPEED = 930;
        private static final double TAIL_FLAME_SKILL_MISSILE_ACCELERATION = 610;
        private static final double TAIL_FLAME_NORMAL_HOMING_DURATION = 2.2;
        private static final double TAIL_FLAME_SKILL_HOMING_DURATION = 2.8;
        private static final double BLUE_INITIAL_BURST_MULTIPLIER = 1.2;
        private static final double BLUE_BURST_MULTIPLIER_DROP = 0.05;
        private static final double BLUE_MIN_BURST_MULTIPLIER = 0.25;
        private static final double BLUE_BURST_CHARGE_MULTIPLIER = 0.65025;
        private static final double BLUE_CHARGE_MULTIPLIER = 1.05;
        private static final double NEUTRON_CHARGE_MULTIPLIER = 1.2;
        private static final double NEUTRON_CONTINUOUS_LASER_CHARGE_RATIO = 0.25;
        private static final double BLUE_LASER_WINDUP_DURATION = 0.75;
        private static final double BLUE_LASER_DURATION = 3.0;
        private static final double BLUE_LASER_HIT_RADIUS = 22.0;
        private static final double NEUTRON_SKILL_WINDUP_DURATION = 0.55;
        private static final double NEUTRON_ORB_SPEED = 230;
        private static final double NEUTRON_ORB_RADIUS = 36;
        private static final double NEUTRON_ORB_PULL_RADIUS = 98;
        private static final double NEUTRON_ORB_PULL_DURATION = 4.0;
        private static final double NEUTRON_ORB_FLIGHT_DURATION = 10.0;
        private static final double NEUTRON_ORB_DEFLECT_RADIUS = 150;
        private static final double NEUTRON_ORB_DEFLECT_STRENGTH = 5.2;
        private static final double NEUTRON_ORB_DEFLECT_CHARGE_RATIO = 0.2;
        private static final double NEUTRON_NORMAL_ORB_RADIUS = 8;
        private static final double VENUS_GATE_DURATION = 1.2;
        private static final double VENUS_ARMOR_DURATION = 6.5;
        private static final double VENUS_ENHANCED_ATTACK_MULTIPLIER = 0.78;
        private static final double VENUS_NORMAL_ATTACK_MULTIPLIER = 0.94;
        private static final double VENUS_ATTACK_BOOST = 1.12;
        private static final double VENUS_DEFENSE_BOOST = 1.20;
        private static final double VENUS_SPEED_BOOST = 1.10;
        private static final int VENUS_ARMOR_HP = 1600;
        private static final int VENUS_MAX_SHARDS = 3;
        private static final int VENUS_SHARD_ABSORB = 180;
        private static final double TAIL_FLAME_TRAIL_INTERVAL = 0.12;
        private static final double TAIL_FLAME_TRAIL_DURATION = 3.4;
        private static final double TAIL_FLAME_TRAIL_RADIUS = 30;
        private static final double TAIL_FLAME_TRAIL_DAMAGE_PER_SECOND = 95;
        private static final double BLUE_GLOW_RESONANCE_DURATION = 2.8;
        private static final int BLUE_GLOW_RESONANCE_TRIGGER_STACKS = 3;
        private static final double BLUE_GLOW_ION_LOCK_DURATION = 0.65;
        private static final double BLUE_GLOW_ION_BOOST_DRAIN = 0.45;
        private static final double SERAPH_PROJECTILE_SPEED = 860;
        private static final double SERAPH_ATTACK_MULTIPLIER = 0.86;
        private static final double SERAPH_DEFENSE_FACTOR = 0.85;
        private static final double SERAPH_ZONE_RADIUS = 170;
        private static final double SERAPH_ZONE_DURATION = 7.0;
        private static final double SERAPH_ZONE_HEAL_PER_SECOND = 320;
        private static final double SERAPH_SPEED_BUFF_MULTIPLIER = 1.16;
        private static final double SERAPH_SPEED_DEBUFF_MULTIPLIER = 0.55;
        private static final double SERAPH_DEFENSE_DEBUFF_MULTIPLIER = 0.86;
        private static final double SERAPH_HEART_SPAWN_INTERVAL = 7.5;
        private static final double SERAPH_HEART_DURATION = 12.0;
        private static final int SERAPH_HEART_HEAL = 1200;
        private static final int SERAPH_HEART_CHARGE = 14;
        private static final int SERAPH_MAX_HEARTS_PER_SIDE = 2;
        private static final double SERAPH_HEART_PICKUP_RADIUS = 30.0;
        private static final double SHIELD_DURATION = 8.0;
        private static final double SHIELD_SPAWN_INTERVAL = 5.5;
        private static final double SHIELD_PICKUP_RADIUS = 34.0;
        private static final double FLOATING_TEXT_DURATION = 1.0;
        private static final int MAX_SHIELD_PICKUPS = 3;

        private final Fighter red = new Fighter(Aircraft.TAIL_FLAME, 220, 256);
        private final Fighter blue = new Fighter(Aircraft.BLUE_GLOW, 740, 256);
        private final List<Projectile> projectiles = new ArrayList<>();
        private final List<ShieldPickup> shieldPickups = new ArrayList<>();
        private final List<SeraphZone> seraphZones = new ArrayList<>();
        private final List<HeartPickup> heartPickups = new ArrayList<>();
        private final List<FlameTrail> flameTrails = new ArrayList<>();
        private final List<FloatingText> floatingTexts = new ArrayList<>();
        private final Set<Integer> pressedKeys = new HashSet<>();
        private final Random random = new Random();
        private final AudioEngine audio = new AudioEngine();
        private final EnumMap<Aircraft, BufferedImage> aircraftSprites = new EnumMap<>(Aircraft.class);
        private final EnumMap<BattleMap, BufferedImage> mapImages = new EnumMap<>(BattleMap.class);
        private BufferedImage seraphHeartImage;
        private final JFrame frame;
        private final GraphicsDevice graphicsDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        private final Timer gameTimer = new Timer(16, event -> updateGame());
        private final Timer aiTimer = new Timer(AI_ATTACK_INTERVAL, event -> aiTurn());
        private final Timer musicTimer = new Timer(320, event -> playBattleMusicNote());

        private long lastUpdateTime = System.nanoTime();
        private long nextPlayerAttackTime;
        private long nextPlayerNormalAttackTime;
        private long nextBlueShotTime;
        private long nextAiBlueShotTime;
        private long blueBurstRoundCooldownUntil;
        private long aiBlueBurstRoundCooldownUntil;
        private int mouseX = 480;
        private int mouseY = 256;
        private int renderOffsetX;
        private int renderOffsetY;
        private double renderScale = 1.0;
        private double aiMoveX = 0.8;
        private double aiMoveY = 1.0;
        private double aiBoostDecisionTimer;
        private double blueLaserWindupRemaining;
        private double blueLaserRemaining;
        private double blueLaserDamageCarry;
        private double playerNeutronSkillWindupRemaining;
        private double playerNeutronSkillTargetX;
        private double playerNeutronSkillTargetY;
        private double aiNeutronSkillWindupRemaining;
        private double aiLaserRemaining;
        private double aiLaserDamageCarry;
        private double blueBurstMultiplier = BLUE_INITIAL_BURST_MULTIPLIER;
        private double aiBlueBurstMultiplier = BLUE_INITIAL_BURST_MULTIPLIER;
        private double pendingBlueGlowChargeMultiplier = 1.0;
        private AmmoType pendingBlueGlowAmmoType = AmmoType.BULLET;
        private LaserType pendingBlueGlowLaserType = LaserType.NONE;
        private double shieldSpawnTimer;
        private double blueHeartSpawnTimer;
        private double redHeartSpawnTimer;
        private int blueBurstShotsRemaining;
        private int aiBlueBurstShotsRemaining;
        private int selectedDifficultyIndex = 1;
        private int selectedAircraftIndex = -1;
        private int selectedAiAircraftIndex = -1;
        private int hangarAircraftIndex = 1;
        private int selectedMapIndex;
        private int previewMapIndex;
        private int titleMapIndex;
        private int aircraftScrollIndex;
        private int hangarScrollIndex;
        private int selectedMenuSection;
        private final List<Integer> aircraftConfirmationOrder = new ArrayList<>();
        private static final int AIRCRAFT_ROLE_PLAYER = 0;
        private static final int AIRCRAFT_ROLE_AI = 1;
        private static final long AIRCRAFT_CONFIRM_EFFECT_MS = 720;
        private boolean chinese = true;
        private boolean settingsOpen;
        private int rebindingControlIndex = -1;
        private double volume = 0.55;
        private int musicStep;
        private int keyUp = KeyEvent.VK_W;
        private int keyDown = KeyEvent.VK_S;
        private int keyLeft = KeyEvent.VK_A;
        private int keyRight = KeyEvent.VK_D;
        private int keyBoost = KeyEvent.VK_SHIFT;
        private int keyPause = KeyEvent.VK_P;
        private int keyRestart = KeyEvent.VK_R;
        private int keyBlueMode = KeyEvent.VK_C;
        private int keyFullScreen = KeyEvent.VK_F11;
        private int keyAttack = INPUT_MOUSE_LEFT;
        private int keyCharge = INPUT_MOUSE_LEFT;
        private int keyInputMethodLock = KeyEvent.VK_F12;
        private String message = "WASD move. Left click fires. Right click fires charged skill.";
        private Difficulty difficulty = Difficulty.MEDIUM;
        private Aircraft playerAircraft = Aircraft.BLUE_GLOW;
        private Aircraft aiAircraft = Aircraft.TAIL_FLAME;
        private Aircraft titleLeftAircraft = Aircraft.BLUE_GLOW;
        private Aircraft titleRightAircraft = Aircraft.TAIL_FLAME;
        private boolean titleScreen = true;
        private boolean choosingDifficulty = true;
        private boolean showingHangar;
        private boolean showingMapSelection;
        private boolean aircraftMenuOpen = true;
        private boolean difficultyMenuOpen;
        private boolean playerAircraftConfirmed;
        private boolean aiAircraftConfirmed;
        private boolean paused;
        private boolean gameOver;
        private boolean playerWon;
        private boolean explosionActive;
        private boolean resultReady;
        private boolean leftMouseDown;
        private boolean draggingVolume;
        private boolean chargeInputDown;
        private boolean inputMethodLocked = true;
        private boolean blueBurstQueued;
        private boolean blueBurstMode;
        private boolean aiWantsBoost;
        private boolean aiBlueBurstMode;
        private boolean fullScreen;
        private long explosionStartedAt;
        private long playerAircraftConfirmedAt;
        private long aiAircraftConfirmedAt;
        private long titleNextBlueAttackTime;
        private long titleNextRedAttackTime;
        private long titleNextBlueSkillTime;
        private long titleNextRedSkillTime;
        private double explosionX;
        private double explosionY;
        private boolean titleDemoMode;
        private boolean titleDemoInitialized;

        BattlePanel(JFrame frame) {
            this.frame = frame;
            loadAircraftSprites();
            loadMapImages();
            loadSeraphHeartImage();
            randomizeTitleScreen();
            setPreferredSize(new Dimension(WIDTH, HEIGHT));
            setBackground(new Color(18, 20, 26));
            setFocusable(true);
            addMouseListener(new BattleMouseListener());
            addMouseMotionListener(new BattleMouseMotionListener());
            addMouseWheelListener(new BattleMouseWheelListener());
            addKeyListener(new BattleKeyListener());
            setInputMethodLocked(true);
            gameTimer.start();
            aiTimer.stop();
        }

        private void randomizeTitleScreen() {
            BattleMap[] maps = BattleMap.values();
            Aircraft[] aircraft = Aircraft.values();
            titleMapIndex = random.nextInt(maps.length);
            titleLeftAircraft = aircraft[random.nextInt(aircraft.length)];
            titleRightAircraft = aircraft[random.nextInt(aircraft.length)];
            if (aircraft.length > 1) {
                while (titleRightAircraft == titleLeftAircraft) {
                    titleRightAircraft = aircraft[random.nextInt(aircraft.length)];
                }
            }
        }

        private void loadAircraftSprites() {
            for (Aircraft aircraft : Aircraft.values()) {
                BufferedImage image = loadAssetImage(aircraft.spriteFile);
                if (image != null) {
                    aircraftSprites.put(aircraft, trimTransparentBounds(image));
                }
            }
        }

        private void loadMapImages() {
            for (BattleMap map : BattleMap.values()) {
                BufferedImage image = loadAssetImage(map.imageFile);
                if (image != null) {
                    mapImages.put(map, image);
                }
            }
        }

        private void loadSeraphHeartImage() {
            BufferedImage image = loadAssetImage("seraph-heart.png");
            if (image != null) {
                seraphHeartImage = trimTransparentBounds(image);
            }
        }

        private BufferedImage loadAssetImage(String fileName) {
            String resourcePath = "/JetBattle/assets/" + fileName;
            try (InputStream stream = JetBattleGame.class.getResourceAsStream(resourcePath)) {
                if (stream != null) {
                    return ImageIO.read(stream);
                }
            } catch (IOException ignored) {
            }

            File[] candidates = {
                    new File("src/JetBattle/assets", fileName),
                    new File("InterestingGame/src/JetBattle/assets", fileName)
            };
            for (File candidate : candidates) {
                if (candidate.isFile()) {
                    try {
                        return ImageIO.read(candidate);
                    } catch (IOException ignored) {
                    }
                }
            }
            return null;
        }

        private BufferedImage trimTransparentBounds(BufferedImage image) {
            int minX = image.getWidth();
            int minY = image.getHeight();
            int maxX = -1;
            int maxY = -1;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) >>> 24) > 20) {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            if (maxX < minX || maxY < minY) {
                return image;
            }
            return image.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
        }

        private void updateGame() {
            long now = System.nanoTime();
            double seconds = (now - lastUpdateTime) / 1_000_000_000.0;
            lastUpdateTime = now;

            if (titleScreen) {
                updateTitleDemoBattle(seconds);
                repaint();
                return;
            }

            if (explosionActive) {
                updateExplosion();
            }

            if (!choosingDifficulty && !paused && !gameOver) {
                updatePlayerLaserWindup(seconds);
                updateNeutronSkillWindups(seconds);
                updateNeutronPulls(seconds);
                updateSeraphEffects(seconds);
                updateBoostStates(seconds);
                if (blueLaserWindupRemaining <= 0) {
                    movePlayer(seconds);
                }
                moveAi(seconds);
                updatePassiveEffects(seconds);
                updatePlayerAutoFire(seconds);
                updateAiBlueBurstFire();
                updatePlayerLaser(seconds);
                updateAiLaser(seconds);
                updateProjectiles(seconds);
                updateShieldPickups(seconds);
                updateHeartPickups(seconds);
                updateActiveArmors(seconds);
                updateFloatingTexts(seconds);
            }

            repaint();
        }

        private void updateTitleDemoBattle(double seconds) {
            if (!titleDemoInitialized) {
                resetTitleDemoBattle();
            }

            long now = System.currentTimeMillis();
            mouseX = (int) Math.round(red.x);
            mouseY = (int) Math.round(red.y);
            updateTitleDemoMovement(seconds);
            mouseX = (int) Math.round(red.x);
            mouseY = (int) Math.round(red.y);
            updateNeutronSkillWindups(seconds);
            updateNeutronPulls(seconds);
            updateSeraphEffects(seconds);
            updateTitleDemoAttacks(now);
            updatePassiveEffects(seconds);
            updateAiBlueBurstFire();
            updatePlayerLaser(seconds);
            updateAiLaser(seconds);
            updateProjectiles(seconds);
            updateHeartPickups(seconds);
            updateActiveArmors(seconds);
            updateFloatingTexts(seconds);
            if (blue.hp <= 0 || red.hp <= 0 || projectiles.size() > 80) {
                resetTitleDemoBattle();
            }
        }

        private void resetTitleDemoBattle() {
            titleDemoMode = true;
            titleDemoInitialized = true;
            playerAircraft = titleLeftAircraft;
            aiAircraft = titleRightAircraft;
            blue.applyAircraft(playerAircraft);
            red.applyAircraft(aiAircraft);
            blue.reset();
            red.reset();
            blue.x = ARENA_LEFT + 210;
            blue.y = ARENA_TOP + ARENA_HEIGHT / 2.0 + 42;
            red.x = ARENA_LEFT + ARENA_WIDTH - 210;
            red.y = ARENA_TOP + ARENA_HEIGHT / 2.0 - 42;
            blue.charge = 45;
            red.charge = 45;
            projectiles.clear();
            floatingTexts.clear();
            shieldPickups.clear();
            seraphZones.clear();
            heartPickups.clear();
            flameTrails.clear();
            gameOver = false;
            explosionActive = false;
            resultReady = false;
            blueLaserWindupRemaining = 0;
            blueLaserRemaining = 0;
            aiLaserRemaining = 0;
            aiLaserDamageCarry = 0;
            blueLaserDamageCarry = 0;
            playerNeutronSkillWindupRemaining = 0;
            aiNeutronSkillWindupRemaining = 0;
            blueBurstMode = true;
            aiBlueBurstMode = true;
            blueBurstShotsRemaining = 0;
            aiBlueBurstShotsRemaining = 0;
            blueBurstMultiplier = BLUE_INITIAL_BURST_MULTIPLIER;
            aiBlueBurstMultiplier = BLUE_INITIAL_BURST_MULTIPLIER;
            blueHeartSpawnTimer = SERAPH_HEART_SPAWN_INTERVAL * 0.45;
            redHeartSpawnTimer = SERAPH_HEART_SPAWN_INTERVAL * 0.65;
            long now = System.currentTimeMillis();
            titleNextBlueAttackTime = now + 450;
            titleNextRedAttackTime = now + 780;
            titleNextBlueSkillTime = now + 2600;
            titleNextRedSkillTime = now + 3600;
        }

        private void clearTitleDemoBattle() {
            titleDemoMode = false;
            titleDemoInitialized = false;
            projectiles.clear();
            floatingTexts.clear();
            shieldPickups.clear();
            seraphZones.clear();
            heartPickups.clear();
            flameTrails.clear();
            blueLaserWindupRemaining = 0;
            blueLaserRemaining = 0;
            aiLaserRemaining = 0;
            aiLaserDamageCarry = 0;
            blueLaserDamageCarry = 0;
            playerNeutronSkillWindupRemaining = 0;
            aiNeutronSkillWindupRemaining = 0;
            blueBurstShotsRemaining = 0;
            aiBlueBurstShotsRemaining = 0;
            gameOver = false;
            explosionActive = false;
            resultReady = false;
        }

        private void updateTitleDemoMovement(double seconds) {
            double now = System.currentTimeMillis() / 1000.0;
            moveTitleDemoFighter(blue, now, seconds, 0, -1);
            moveTitleDemoFighter(red, now, seconds, Math.PI, 1);
            blue.boosting = true;
            red.boosting = true;
        }

        private void moveTitleDemoFighter(Fighter fighter, double now, double seconds, double phaseOffset, int side) {
            double centerX = ARENA_LEFT + ARENA_WIDTH / 2.0;
            double centerY = ARENA_TOP + ARENA_HEIGHT / 2.0;
            double angle = now * 0.62 + phaseOffset;
            double desiredX = centerX
                    + Math.cos(angle) * 310
                    + Math.sin(angle * 1.7 + side * 0.8) * 86;
            double desiredY = centerY
                    + Math.sin(angle * 1.23) * 124
                    + Math.cos(angle * 0.72 + side * 1.3) * 48;
            desiredX = clamp(desiredX, ARENA_LEFT + 52, ARENA_LEFT + ARENA_WIDTH - 52);
            desiredY = clamp(desiredY, ARENA_TOP + 42, ARENA_TOP + ARENA_HEIGHT - 42);
            double dx = desiredX - fighter.x;
            double dy = desiredY - fighter.y;
            double distance = Math.hypot(dx, dy);
            if (distance <= 0.01) {
                return;
            }
            double maxStep = effectiveSpeed(fighter) * 1.05 * seconds;
            double step = Math.min(distance, maxStep);
            fighter.x = clamp(fighter.x + dx / distance * step,
                    ARENA_LEFT + FIGHTER_SIZE / 2.0, ARENA_LEFT + ARENA_WIDTH - FIGHTER_SIZE / 2.0);
            fighter.y = clamp(fighter.y + dy / distance * step,
                    ARENA_TOP + FIGHTER_SIZE / 2.0, ARENA_TOP + ARENA_HEIGHT - FIGHTER_SIZE / 2.0);
        }

        private void updateTitleDemoAttacks(long now) {
            if (now >= titleNextBlueAttackTime && blueLaserRemaining <= 0 && blueLaserWindupRemaining <= 0) {
                fireTitleDemoAttack(blue, red, playerAircraft, true);
                titleNextBlueAttackTime = now + titleDemoAttackInterval(blue, playerAircraft);
            }
            if (now >= titleNextRedAttackTime && aiLaserRemaining <= 0) {
                fireTitleDemoAttack(red, blue, aiAircraft, false);
                titleNextRedAttackTime = now + titleDemoAttackInterval(red, aiAircraft);
            }
            if (now >= titleNextBlueSkillTime) {
                fireTitleDemoSkill(blue, red, playerAircraft, true);
                titleNextBlueSkillTime = now + 5200 + random.nextInt(1800);
            }
            if (now >= titleNextRedSkillTime) {
                fireTitleDemoSkill(red, blue, aiAircraft, false);
                titleNextRedSkillTime = now + 5200 + random.nextInt(1800);
            }
        }

        private int titleDemoAttackInterval(Fighter fighter, Aircraft aircraft) {
            return switch (aircraft) {
                case BLUE_GLOW -> BLUE_FIRE_INTERVAL * 3;
                case VENUS -> venusArmorActive(fighter) ? VENUS_ENHANCED_ATTACK_COOLDOWN : VENUS_ATTACK_COOLDOWN;
                case TAIL_FLAME -> TAIL_FLAME_ATTACK_COOLDOWN;
                case NEUTRON_STAR, SIX_WINGED_ANGEL -> NORMAL_ATTACK_COOLDOWN;
            };
        }

        private void fireTitleDemoAttack(Fighter attacker, Fighter target, Aircraft aircraft, boolean fromBlue) {
            switch (aircraft) {
                case BLUE_GLOW -> {
                    pendingBlueGlowChargeMultiplier = fromBlue ? BLUE_BURST_CHARGE_MULTIPLIER : 1.0;
                    pendingBlueGlowAmmoType = fromBlue ? AmmoType.LASER : AmmoType.BULLET;
                    pendingBlueGlowLaserType = fromBlue ? LaserType.NON_CONTINUOUS : LaserType.NONE;
                    int damage = blueGlowAttackDamage(attacker, fromBlue, fromBlue ? blueBurstMultiplier : aiBlueBurstMultiplier);
                    fireBlueGlowLasers(attacker, target, target.x, target.y, damage, text("蓝光", "Blue Glow"));
                }
                case NEUTRON_STAR -> fireNeutronStarShot(attacker, target, target.x, target.y, fromBlue);
                case VENUS -> fireVenusShot(attacker, target, target.x, target.y, fromBlue);
                case SIX_WINGED_ANGEL -> fireSeraphShot(attacker, target, target.x, target.y, fromBlue);
                case TAIL_FLAME -> fireTailFlameShot(attacker, target, target.x, target.y, fromBlue);
            }
        }

        private void fireTitleDemoSkill(Fighter attacker, Fighter target, Aircraft aircraft, boolean fromBlue) {
            attacker.charge = 0;
            attacker.chargeCarry = 0;
            switch (aircraft) {
                case BLUE_GLOW -> {
                    if (fromBlue) {
                        blueLaserRemaining = BLUE_LASER_DURATION;
                        blueLaserDamageCarry = 0;
                    } else {
                        aiLaserRemaining = BLUE_LASER_DURATION;
                        aiLaserDamageCarry = 0;
                    }
                }
                case NEUTRON_STAR -> fireNeutronStarSkillOrb(attacker, target, target.x, target.y);
                case VENUS -> activateVenusArmor(attacker);
                case SIX_WINGED_ANGEL -> activateSeraphZone(attacker, attacker.x * 0.65 + target.x * 0.35, attacker.y * 0.65 + target.y * 0.35);
                case TAIL_FLAME -> fireTailFlameWingMissiles(attacker, target, target.x, target.y, tailFlameMissileDamage(attacker));
            }
            attacker.charge = 35;
        }

        private void movePlayer(double seconds) {
            double dx = 0;
            double dy = 0;

            if (pressedKeys.contains(keyUp)) {
                dy--;
            }
            if (pressedKeys.contains(keyDown)) {
                dy++;
            }
            if (pressedKeys.contains(keyLeft)) {
                dx--;
            }
            if (pressedKeys.contains(keyRight)) {
                dx++;
            }

            moveFighter(blue, dx, dy, seconds);
        }

        private void updatePlayerAutoFire(double seconds) {
            if (playerAircraft == Aircraft.TAIL_FLAME) {
                return;
            }

            if (!blueBurstMode || blueLaserWindupRemaining > 0 || blueLaserRemaining > 0) {
                return;
            }

            if ((leftMouseDown || blueBurstQueued || blueBurstShotsRemaining > 0) && blueLaserWindupRemaining <= 0 && blueLaserRemaining <= 0) {
                long now = System.currentTimeMillis();
                if (blueBurstShotsRemaining == 0) {
                    if (now < blueBurstRoundCooldownUntil) {
                        return;
                    }
                    if (leftMouseDown || blueBurstQueued) {
                        blueBurstShotsRemaining = BLUE_BURST_SHOTS_PER_ROUND;
                        blueBurstQueued = false;
                        blueBurstMultiplier = BLUE_INITIAL_BURST_MULTIPLIER;
                    }
                }
                if (blueBurstShotsRemaining > 0 && now >= nextBlueShotTime) {
                    firePlayerBlueGlowLasers(true);
                    blueBurstShotsRemaining--;
                    nextBlueShotTime = now + BLUE_FIRE_INTERVAL;
                    if (blueBurstShotsRemaining == 0) {
                        blueBurstRoundCooldownUntil = now + BLUE_BURST_ROUND_COOLDOWN;
                    }
                }
            }
        }

        private void moveAi(double seconds) {
            moveFighter(red, aiMoveX, aiMoveY, seconds);

            if (red.x <= ARENA_LEFT + FIGHTER_SIZE / 2.0 || red.x >= ARENA_LEFT + ARENA_WIDTH - FIGHTER_SIZE / 2.0) {
                aiMoveX = -aiMoveX;
            }
            if (red.y <= ARENA_TOP + FIGHTER_SIZE / 2.0 || red.y >= ARENA_TOP + ARENA_HEIGHT - FIGHTER_SIZE / 2.0) {
                aiMoveY = -aiMoveY;
            }
        }

        private void moveFighter(Fighter fighter, double dx, double dy, double seconds) {
            if (dx == 0 && dy == 0) {
                return;
            }

            double length = Math.hypot(dx, dy);
            double speedMultiplier = fighter.boosting && fighter.blueGlowIonLockRemaining <= 0 ? BOOST_SPEED_MULTIPLIER : 1.0;
            double step = effectiveSpeed(fighter) * speedMultiplier * seconds;
            fighter.x = clamp(fighter.x + dx / length * step, ARENA_LEFT + FIGHTER_SIZE / 2.0, ARENA_LEFT + ARENA_WIDTH - FIGHTER_SIZE / 2.0);
            fighter.y = clamp(fighter.y + dy / length * step, ARENA_TOP + FIGHTER_SIZE / 2.0, ARENA_TOP + ARENA_HEIGHT - FIGHTER_SIZE / 2.0);
        }

        private void updateBoostStates(double seconds) {
            boolean playerMoving = pressedKeys.contains(keyUp)
                    || pressedKeys.contains(keyLeft)
                    || pressedKeys.contains(keyDown)
                    || pressedKeys.contains(keyRight);
            updateAiBoostDecision(seconds);
            updateBoost(blue, pressedKeys.contains(keyBoost) && playerMoving && blue.blueGlowIonLockRemaining <= 0, seconds);
            updateBoost(red, aiWantsBoost && red.blueGlowIonLockRemaining <= 0, seconds);
        }

        private void updateAiBoostDecision(double seconds) {
            aiBoostDecisionTimer = Math.max(0, aiBoostDecisionTimer - seconds);
            if (red.boostEnergy <= 0.12) {
                aiWantsBoost = false;
                return;
            }
            if (aiBoostDecisionTimer > 0) {
                return;
            }

            aiBoostDecisionTimer = 0.35 + random.nextDouble() * 0.75;
            double distance = Math.hypot(red.x - blue.x, red.y - blue.y);
            boolean underThreat = false;
            for (Projectile projectile : projectiles) {
                if (projectile.fromBlue && Math.hypot(projectile.x - red.x, projectile.y - red.y) < 170) {
                    underThreat = true;
                    break;
                }
            }

            double chance = 0.18;
            if (distance > 360) {
                chance += 0.22;
            }
            if (underThreat) {
                chance += 0.28;
            }
            if (red.boosting && red.boostEnergy > 0.35) {
                chance += 0.12;
            }
            if (red.boostEnergy < 0.45) {
                chance *= 0.45;
            }
            aiWantsBoost = random.nextDouble() < chance;
        }

        private void updateBoost(Fighter fighter, boolean wantsBoost, double seconds) {
            boolean wasBoosting = fighter.boosting;
            if (wantsBoost && fighter.boostEnergy > 0) {
                fighter.boosting = true;
                if (!wasBoosting) {
                    fighter.boostMaxSoundPlayed = false;
                    playBoostSound();
                }
                if (!fighter.boostMaxSoundPlayed && fighter.boostEnergy <= BOOST_MAX_ENERGY - 0.22) {
                    fighter.boostMaxSoundPlayed = true;
                    playBoostMaxSpeedSound();
                }
                fighter.boostEnergy = Math.max(0, fighter.boostEnergy - seconds);
                fighter.boostRecoverDelay = BOOST_RECOVER_DELAY;
                if (fighter.boostEnergy <= 0) {
                    fighter.boosting = false;
                }
                return;
            }

            fighter.boosting = false;
            fighter.boostMaxSoundPlayed = false;
            if (fighter.boostRecoverDelay > 0) {
                fighter.boostRecoverDelay = Math.max(0, fighter.boostRecoverDelay - seconds);
                return;
            }
            fighter.boostEnergy = Math.min(BOOST_MAX_ENERGY, fighter.boostEnergy + BOOST_RECOVER_PER_SECOND * seconds);
        }

        private double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }

        private void updateProjectiles(double seconds) {
            Iterator<Projectile> iterator = projectiles.iterator();
            while (iterator.hasNext()) {
                Projectile projectile = iterator.next();
                applyNeutronOrbDeflection(projectile, seconds);
                projectile.update(seconds);

                Fighter target = projectile.neutronDeflectOwner != null
                        ? (projectile.neutronDeflectOwner == blue ? red : blue)
                        : projectile.fromBlue ? red : blue;
                if (projectile.neutronSkillOrb) {
                    if (updateNeutronSkillOrb(projectile, target, seconds)) {
                        iterator.remove();
                        checkWinner();
                    }
                    continue;
                }

                if (isOutsideArena(projectile)) {
                    iterator.remove();
                    continue;
                }
                if (projectile.piercing && projectile.targetHit) {
                    continue;
                }

                double hitDistance = FIGHTER_SIZE / 2.0 + projectile.radius;
                if (Math.hypot(projectile.x - target.x, projectile.y - target.y) <= hitDistance) {
                    applyDamage(target, projectile.damage, projectile.ammoType, projectile.laserType);
                    playHitSound();
                    addDefenderCharge(target);
                    Fighter attacker = projectile.fromBlue ? blue : red;
                    if (projectile.neutronDeflectOwner == null) {
                        applyHitPassives(attacker, target, projectile);
                    }
                    if (!projectile.skill && projectile.neutronDeflectOwner == null) {
                        addCharge(attacker, projectile.chargeMultiplier);
                    }
                    message = projectile.hitMessage;
                    if (projectile.piercing) {
                        projectile.targetHit = true;
                    } else {
                        iterator.remove();
                    }
                    checkWinner();
                }
            }
        }

        private void addCharge(Fighter fighter, double multiplier) {
            double aircraftMultiplier = chargeMultiplierFor(aircraftFor(fighter));
            double totalGain = CHARGE_GAIN * multiplier * aircraftMultiplier + fighter.chargeCarry;
            int chargeGain = (int) totalGain;
            fighter.chargeCarry = totalGain - chargeGain;
            fighter.charge = Math.min(MAX_CHARGE, fighter.charge + chargeGain);
            if (fighter.charge >= MAX_CHARGE) {
                fighter.chargeCarry = 0;
            }
        }

        private double chargeMultiplierFor(Aircraft aircraft) {
            return switch (aircraft) {
                case BLUE_GLOW -> BLUE_CHARGE_MULTIPLIER;
                case NEUTRON_STAR -> NEUTRON_CHARGE_MULTIPLIER;
                case SIX_WINGED_ANGEL -> 0.95;
                case TAIL_FLAME, VENUS -> 1.0;
            };
        }

        private void addDefenderCharge(Fighter fighter) {
            if (aircraftFor(fighter) == Aircraft.NEUTRON_STAR) {
                addCharge(fighter, 0.5);
            }
        }

        private void addContinuousLaserDefenderCharge(Fighter fighter, double seconds) {
            if (aircraftFor(fighter) == Aircraft.NEUTRON_STAR) {
                addCharge(fighter, 0.5 * NEUTRON_CONTINUOUS_LASER_CHARGE_RATIO * seconds);
            }
        }

        private void addNeutronPullCharge(Fighter fighter, double seconds) {
            if (fighter == null) {
                return;
            }
            double totalGain = CHARGE_GAIN * 0.5 * seconds + fighter.neutronPullChargeCarry;
            int chargeGain = (int) totalGain;
            fighter.neutronPullChargeCarry = totalGain - chargeGain;
            fighter.charge = Math.min(MAX_CHARGE, fighter.charge + chargeGain);
            if (fighter.charge >= MAX_CHARGE) {
                fighter.neutronPullChargeCarry = 0;
            }
        }

        private void addNeutronDeflectCharge(Fighter fighter) {
            if (fighter == null) {
                return;
            }
            double aircraftMultiplier = chargeMultiplierFor(aircraftFor(fighter));
            double totalGain = CHARGE_GAIN * NEUTRON_ORB_DEFLECT_CHARGE_RATIO * aircraftMultiplier + fighter.chargeCarry;
            int chargeGain = (int) totalGain;
            fighter.chargeCarry = totalGain - chargeGain;
            fighter.charge = Math.min(MAX_CHARGE, fighter.charge + chargeGain);
            if (fighter.charge >= MAX_CHARGE) {
                fighter.chargeCarry = 0;
            }
        }

        private void applyNeutronOrbDeflection(Projectile projectile, double seconds) {
            if (projectile.neutronSkillOrb) {
                return;
            }
            for (Projectile orb : projectiles) {
                if (!orb.neutronSkillOrb) {
                    continue;
                }
                if (projectile.fromBlue == orb.fromBlue) {
                    continue;
                }
                double dx = orb.x - projectile.x;
                double dy = orb.y - projectile.y;
                double distance = Math.hypot(dx, dy);
                double captureRadius = projectile.neutronDeflected && projectile.neutronDeflectOwner == orb.neutronOwner
                        ? NEUTRON_ORB_DEFLECT_RADIUS * 2.2
                        : NEUTRON_ORB_DEFLECT_RADIUS;
                if (distance <= 0 || distance > captureRadius) {
                    continue;
                }
                if (!projectile.neutronDeflected) {
                    projectile.neutronDeflected = true;
                    projectile.neutronDeflectOwner = orb.neutronOwner;
                    projectile.neutronOrbitPhase = random.nextDouble() * Math.PI * 2.0;
                    projectile.neutronOrbitDirection = random.nextBoolean() ? 1.0 : -1.0;
                    projectile.neutronOrbitBias = 0.75 + random.nextDouble() * 0.65;
                    addNeutronDeflectCharge(orb.neutronOwner);
                    if (projectile.laserType != LaserType.CONTINUOUS) {
                        projectile.removeSpecialEffects();
                    }
                }
                if (projectile.laserType != LaserType.CONTINUOUS) {
                    updateCapturedProjectileOrbit(projectile, orb, distance, dx, dy, seconds);
                    continue;
                }

                double influence = (1.0 - Math.min(distance, NEUTRON_ORB_DEFLECT_RADIUS) / NEUTRON_ORB_DEFLECT_RADIUS)
                        * NEUTRON_ORB_DEFLECT_STRENGTH * seconds * 2.15;
                double speed = Math.hypot(projectile.vx, projectile.vy);
                double nx = projectile.vx / speed;
                double ny = projectile.vy / speed;
                double pullX = dx / distance;
                double pullY = dy / distance;
                double newX = nx + (pullX - nx) * influence;
                double newY = ny + (pullY - ny) * influence;
                double newLength = Math.hypot(newX, newY);
                projectile.vx = newX / newLength * speed;
                projectile.vy = newY / newLength * speed;
            }
        }

        private void updateCapturedProjectileOrbit(Projectile projectile, Projectile orb, double distance,
                                                   double dxToOrb, double dyToOrb, double seconds) {
            if (distance <= 0) {
                return;
            }
            double speed = Math.max(160, Math.hypot(projectile.vx, projectile.vy));
            double toOrbX = dxToOrb / distance;
            double toOrbY = dyToOrb / distance;
            double tangentX = -toOrbY * projectile.neutronOrbitDirection;
            double tangentY = toOrbX * projectile.neutronOrbitDirection;
            projectile.neutronOrbitPhase += seconds * (3.4 + projectile.neutronOrbitBias);
            double targetRadius = 32
                    + 42 * projectile.neutronOrbitBias
                    + 18 * Math.sin(projectile.neutronOrbitPhase)
                    + 10 * Math.sin(projectile.neutronOrbitPhase * 1.73);
            double radialError = distance - targetRadius;
            double radialStrength = clamp(radialError / 72.0, -0.95, 1.35);
            double swirl = 0.82 + 0.18 * Math.sin(projectile.neutronOrbitPhase * 0.83);
            if (distance > NEUTRON_ORB_DEFLECT_RADIUS * 0.9) {
                radialStrength = Math.max(radialStrength, 1.25);
                swirl *= 0.55;
            }
            double newX = tangentX * swirl + toOrbX * radialStrength;
            double newY = tangentY * swirl + toOrbY * radialStrength;
            double newLength = Math.hypot(newX, newY);
            if (newLength == 0) {
                return;
            }
            double adjustedSpeed = Math.max(speed, 260) * (0.92 + 0.16 * Math.sin(projectile.neutronOrbitPhase * 1.17));
            projectile.vx = newX / newLength * adjustedSpeed;
            projectile.vy = newY / newLength * adjustedSpeed;
        }

        private boolean updateNeutronSkillOrb(Projectile projectile, Fighter target, double seconds) {
            if (!projectile.neutronImpactStarted) {
                projectile.neutronFlightRemaining = Math.max(0, projectile.neutronFlightRemaining - seconds);
                bounceNeutronOrb(projectile);
                if (projectile.neutronFlightRemaining <= 0) {
                    return true;
                }
                double distance = Math.hypot(projectile.x - target.x, projectile.y - target.y);
                if (distance > NEUTRON_ORB_DEFLECT_RADIUS) {
                    return false;
                }
                projectile.startNeutronImpact(randomNeutronAmmoType(), target);
                target.neutronPullRemaining = NEUTRON_ORB_PULL_DURATION;
                target.neutronOrbitAngle = Math.atan2(target.y - projectile.y, target.x - projectile.x);
                target.neutronOrbitRadius = Math.max(30, Math.hypot(target.x - projectile.x, target.y - projectile.y));
                message = projectile.hitMessage;
                if (projectile.neutronResolvedType != AmmoType.LASER) {
                    applyDamage(target, projectile.damage, projectile.neutronResolvedType, LaserType.NONE);
                    addDefenderCharge(target);
                }
            }

            target.neutronPullX = projectile.x;
            target.neutronPullY = projectile.y;
            addNeutronPullCharge(projectile.neutronOwner, seconds);
            if (projectile.neutronResolvedType == AmmoType.LASER) {
                addContinuousLaserDefenderCharge(target, seconds);
                double damagePerSecond = projectile.neutronOwner.attack * 0.25 * projectile.neutronOwner.skillBonus;
                projectile.neutronDamageCarry += damagePerSecond * seconds;
                int damage = (int) projectile.neutronDamageCarry;
                if (damage > 0) {
                    projectile.neutronDamageCarry -= damage;
                    applyDamage(target, damage, AmmoType.LASER, LaserType.CONTINUOUS);
                }
            }

            projectile.neutronPullRemaining = Math.max(0, projectile.neutronPullRemaining - seconds);
            return projectile.neutronPullRemaining <= 0;
        }

        private void bounceNeutronOrb(Projectile projectile) {
            double minX = ARENA_LEFT + projectile.radius;
            double maxX = ARENA_LEFT + ARENA_WIDTH - projectile.radius;
            double minY = ARENA_TOP + projectile.radius;
            double maxY = ARENA_TOP + ARENA_HEIGHT - projectile.radius;
            if (projectile.x <= minX || projectile.x >= maxX) {
                projectile.x = clamp(projectile.x, minX, maxX);
                projectile.vx = -projectile.vx;
            }
            if (projectile.y <= minY || projectile.y >= maxY) {
                projectile.y = clamp(projectile.y, minY, maxY);
                projectile.vy = -projectile.vy;
            }
        }

        private AmmoType randomNeutronAmmoType() {
            int roll = random.nextInt(3);
            if (roll == 0) {
                return AmmoType.BULLET;
            }
            if (roll == 1) {
                return AmmoType.MISSILE;
            }
            return AmmoType.LASER;
        }

        private boolean isOutsideArena(Projectile projectile) {
            return projectile.x < ARENA_LEFT - 30
                    || projectile.x > ARENA_LEFT + ARENA_WIDTH + 30
                    || projectile.y < ARENA_TOP - 30
                    || projectile.y > ARENA_TOP + ARENA_HEIGHT + 30;
        }

        private void updateShieldPickups(double seconds) {
            shieldSpawnTimer -= seconds;
            if (shieldSpawnTimer <= 0 && shieldPickups.size() < MAX_SHIELD_PICKUPS) {
                spawnShieldPickup();
                shieldSpawnTimer = SHIELD_SPAWN_INTERVAL;
            }

            Iterator<ShieldPickup> iterator = shieldPickups.iterator();
            while (iterator.hasNext()) {
                ShieldPickup pickup = iterator.next();
                if (pickup.isPickedBy(red)) {
                    red.equipArmor(pickup.type, SHIELD_DURATION);
                    showFloatingText(red.x, red.y - 36, pickup.type.displayName(chinese), pickup.type.color);
                    playShieldSound();
                    iterator.remove();
                } else if (pickup.isPickedBy(blue)) {
                    blue.equipArmor(pickup.type, SHIELD_DURATION);
                    showFloatingText(blue.x, blue.y - 36, pickup.type.displayName(chinese), pickup.type.color);
                    playShieldSound();
                    iterator.remove();
                }
            }
        }

        private void spawnShieldPickup() {
            ArmorType[] types = {ArmorType.STEEL, ArmorType.LIGHT, ArmorType.COMPOSITE};
            ArmorType type = types[random.nextInt(types.length)];
            double x = ARENA_LEFT + 42 + random.nextDouble() * (ARENA_WIDTH - 84);
            double y = ARENA_TOP + 42 + random.nextDouble() * (ARENA_HEIGHT - 84);
            shieldPickups.add(new ShieldPickup(type, x, y));
        }

        private void updateSeraphEffects(double seconds) {
            blue.seraphSpeedBuffRemaining = Math.max(0, blue.seraphSpeedBuffRemaining - seconds);
            red.seraphSpeedBuffRemaining = Math.max(0, red.seraphSpeedBuffRemaining - seconds);
            blue.seraphSpeedDebuffRemaining = Math.max(0, blue.seraphSpeedDebuffRemaining - seconds);
            red.seraphSpeedDebuffRemaining = Math.max(0, red.seraphSpeedDebuffRemaining - seconds);
            blue.seraphDefenseDebuffRemaining = Math.max(0, blue.seraphDefenseDebuffRemaining - seconds);
            red.seraphDefenseDebuffRemaining = Math.max(0, red.seraphDefenseDebuffRemaining - seconds);

            Iterator<SeraphZone> iterator = seraphZones.iterator();
            while (iterator.hasNext()) {
                SeraphZone zone = iterator.next();
                zone.remaining -= seconds;
                if (zone.remaining <= 0) {
                    iterator.remove();
                    continue;
                }

                Fighter owner = zone.fromBlue ? blue : red;
                Fighter enemy = zone.fromBlue ? red : blue;
                if (zone.contains(owner)) {
                    healFighter(owner, SERAPH_ZONE_HEAL_PER_SECOND * owner.skillBonus * seconds);
                    owner.seraphSpeedBuffRemaining = 0.12;
                }
                if (zone.contains(enemy)) {
                    enemy.seraphSpeedDebuffRemaining = 0.12;
                    enemy.seraphDefenseDebuffRemaining = 0.12;
                }
            }
        }

        private void updateHeartPickups(double seconds) {
            updateHeartSpawner(blue, true, seconds);
            updateHeartSpawner(red, false, seconds);

            Iterator<HeartPickup> iterator = heartPickups.iterator();
            while (iterator.hasNext()) {
                HeartPickup pickup = iterator.next();
                pickup.remaining -= seconds;
                if (pickup.remaining <= 0) {
                    iterator.remove();
                    continue;
                }
                Fighter owner = pickup.fromBlue ? blue : red;
                if (pickup.isPickedBy(owner)) {
                    int healed = healFighter(owner, SERAPH_HEART_HEAL);
                    int oldCharge = owner.charge;
                    owner.charge = Math.min(MAX_CHARGE, owner.charge + SERAPH_HEART_CHARGE);
                    int gainedCharge = owner.charge - oldCharge;
                    if (healed > 0 || gainedCharge > 0) {
                        String value = healed > 0
                                ? "+" + healed + " HP  +" + gainedCharge + " EN"
                                : "+" + gainedCharge + " EN";
                        showFloatingText(owner.x, owner.y - 42, value, new Color(255, 124, 148));
                        playShieldSound();
                        iterator.remove();
                    }
                }
            }
        }

        private void updateHeartSpawner(Fighter fighter, boolean fromBlue, double seconds) {
            if (aircraftFor(fighter) != Aircraft.SIX_WINGED_ANGEL) {
                if (fromBlue) {
                    blueHeartSpawnTimer = SERAPH_HEART_SPAWN_INTERVAL;
                } else {
                    redHeartSpawnTimer = SERAPH_HEART_SPAWN_INTERVAL;
                }
                return;
            }
            int ownedHearts = 0;
            for (HeartPickup pickup : heartPickups) {
                if (pickup.fromBlue == fromBlue) {
                    ownedHearts++;
                }
            }
            if (ownedHearts >= SERAPH_MAX_HEARTS_PER_SIDE) {
                return;
            }

            if (fromBlue) {
                blueHeartSpawnTimer -= seconds;
                if (blueHeartSpawnTimer <= 0) {
                    spawnHeartPickup(true);
                    blueHeartSpawnTimer = SERAPH_HEART_SPAWN_INTERVAL;
                }
            } else {
                redHeartSpawnTimer -= seconds;
                if (redHeartSpawnTimer <= 0) {
                    spawnHeartPickup(false);
                    redHeartSpawnTimer = SERAPH_HEART_SPAWN_INTERVAL;
                }
            }
        }

        private void spawnHeartPickup(boolean fromBlue) {
            double x = ARENA_LEFT + 58 + random.nextDouble() * (ARENA_WIDTH - 116);
            double y = ARENA_TOP + 58 + random.nextDouble() * (ARENA_HEIGHT - 116);
            heartPickups.add(new HeartPickup(fromBlue, x, y, SERAPH_HEART_DURATION));
        }

        private int healFighter(Fighter fighter, double amount) {
            if (amount <= 0 || fighter.hp >= fighter.maxHp) {
                return 0;
            }
            double total = amount + fighter.healCarry;
            int heal = (int) total;
            fighter.healCarry = total - heal;
            if (heal <= 0) {
                return 0;
            }
            int oldHp = fighter.hp;
            fighter.hp = Math.min(fighter.maxHp, fighter.hp + heal);
            return fighter.hp - oldHp;
        }

        private void updatePassiveEffects(double seconds) {
            updateFighterPassiveTimers(blue, seconds);
            updateFighterPassiveTimers(red, seconds);
            spawnTailFlameTrailIfNeeded(blue, seconds);
            spawnTailFlameTrailIfNeeded(red, seconds);
            updateFlameTrails(seconds);
        }

        private void updateFighterPassiveTimers(Fighter fighter, double seconds) {
            fighter.blueGlowResonanceRemaining = Math.max(0, fighter.blueGlowResonanceRemaining - seconds);
            if (fighter.blueGlowResonanceRemaining <= 0) {
                fighter.blueGlowResonanceStacks = 0;
            }
            fighter.blueGlowIonLockRemaining = Math.max(0, fighter.blueGlowIonLockRemaining - seconds);
            fighter.tailFlameTrailCooldown = Math.max(0, fighter.tailFlameTrailCooldown - seconds);
        }

        private void spawnTailFlameTrailIfNeeded(Fighter fighter, double seconds) {
            if (aircraftFor(fighter) != Aircraft.TAIL_FLAME || !fighter.boosting || fighter.tailFlameTrailCooldown > 0) {
                return;
            }
            Fighter enemy = fighter == blue ? red : blue;
            double dx = enemy.x - fighter.x;
            double dy = enemy.y - fighter.y;
            double angle = Math.atan2(dy, dx);
            double trailX = fighter.x - Math.cos(angle) * 30;
            double trailY = fighter.y - Math.sin(angle) * 30;
            flameTrails.add(new FlameTrail(fighter == blue, trailX, trailY));
            fighter.tailFlameTrailCooldown = TAIL_FLAME_TRAIL_INTERVAL;
        }

        private void updateFlameTrails(double seconds) {
            Iterator<FlameTrail> iterator = flameTrails.iterator();
            while (iterator.hasNext()) {
                FlameTrail trail = iterator.next();
                trail.remaining -= seconds;
                if (trail.remaining <= 0) {
                    iterator.remove();
                    continue;
                }
                Fighter target = trail.fromBlue ? red : blue;
                if (Math.hypot(target.x - trail.x, target.y - trail.y) <= TAIL_FLAME_TRAIL_RADIUS + FIGHTER_SIZE / 2.0) {
                    double total = TAIL_FLAME_TRAIL_DAMAGE_PER_SECOND * seconds + trail.damageCarry;
                    int damage = (int) total;
                    trail.damageCarry = total - damage;
                    if (damage > 0) {
                        applyDamage(target, damage, AmmoType.LASER, LaserType.NONE);
                        checkWinner();
                    }
                }
            }
        }

        private void applyHitPassives(Fighter attacker, Fighter target, Projectile projectile) {
            Aircraft attackerAircraft = aircraftFor(attacker);
            if (attackerAircraft == Aircraft.BLUE_GLOW && !projectile.skill) {
                applyBlueGlowResonance(target);
            } else if (attackerAircraft == Aircraft.VENUS && !projectile.skill) {
                addVenusShard(attacker);
            }
        }

        private void applyBlueGlowResonance(Fighter target) {
            target.blueGlowResonanceStacks++;
            target.blueGlowResonanceRemaining = BLUE_GLOW_RESONANCE_DURATION;
            if (target.blueGlowResonanceStacks < BLUE_GLOW_RESONANCE_TRIGGER_STACKS) {
                return;
            }
            target.blueGlowResonanceStacks = 0;
            target.blueGlowResonanceRemaining = 0;
            target.blueGlowIonLockRemaining = BLUE_GLOW_ION_LOCK_DURATION;
            target.boosting = false;
            target.boostEnergy = Math.max(0, target.boostEnergy - BLUE_GLOW_ION_BOOST_DRAIN);
            showFloatingText(target.x, target.y - 48, text("离子干扰", "Ion Lock"), new Color(116, 232, 255));
        }

        private void addVenusShard(Fighter fighter) {
            if (fighter.venusShardCount >= VENUS_MAX_SHARDS) {
                return;
            }
            fighter.venusShardCount++;
            showFloatingText(fighter.x, fighter.y - 48, text("星尘护片", "Star Shard"), new Color(255, 214, 96));
        }

        private void updateActiveArmors(double seconds) {
            red.updateArmors(seconds);
            blue.updateArmors(seconds);
            boolean aiVenusArmorWasActive = venusArmorActive(red);
            red.updateVenusArmor(seconds);
            blue.updateVenusArmor(seconds);
            if (aiVenusArmorWasActive != venusArmorActive(red)) {
                configureAiAttackTimer();
            }
        }

        private void updateFloatingTexts(double seconds) {
            Iterator<FloatingText> iterator = floatingTexts.iterator();
            while (iterator.hasNext()) {
                FloatingText floatingText = iterator.next();
                floatingText.update(seconds);
                if (floatingText.remaining <= 0) {
                    iterator.remove();
                }
            }
        }

        private void firePlayerBlueGlowLasers(boolean burstShot) {
            if (!canPlayerFireNormal()) {
                return;
            }

            int damage = blueBurstDamage(burstShot);
            pendingBlueGlowChargeMultiplier = burstShot ? BLUE_BURST_CHARGE_MULTIPLIER : 1.0;
            pendingBlueGlowAmmoType = burstShot ? AmmoType.LASER : AmmoType.BULLET;
            pendingBlueGlowLaserType = burstShot ? LaserType.NON_CONTINUOUS : LaserType.NONE;
            fireBlueGlowLasers(blue, red, mouseX, mouseY, damage, text("蓝光", "Blue Glow"));
            if (burstShot) {
                message = String.format("%s burst fire. Multiplier x%.2f", fighterDisplayName(blue), blueBurstMultiplier);
                blueBurstMultiplier = Math.max(BLUE_MIN_BURST_MULTIPLIER, blueBurstMultiplier - BLUE_BURST_MULTIPLIER_DROP);
            } else {
                message = fighterDisplayName(blue) + " fired a precision twin shot.";
            }
        }

        private void fireBlueGlowLasers(Fighter attacker, Fighter target, double targetX, double targetY, int damage, String weaponName) {
            double chargeMultiplier = pendingBlueGlowChargeMultiplier;
            pendingBlueGlowChargeMultiplier = 1.0;
            AmmoType ammoType = pendingBlueGlowAmmoType;
            LaserType laserType = pendingBlueGlowLaserType;
            pendingBlueGlowAmmoType = AmmoType.BULLET;
            pendingBlueGlowLaserType = LaserType.NONE;
            playFireSound(ammoType);
            double angle = Math.atan2(targetY - attacker.y, targetX - attacker.x);
            Point2D leftWing = transformPoint(attacker.x, attacker.y, angle, -4, -22);
            Point2D rightWing = transformPoint(attacker.x, attacker.y, angle, -4, 22);
            Color aircraftColor = aircraftFor(attacker).color;
            int leftDamage = damage / 2;
            int rightDamage = damage - leftDamage;
            double projectileChargeMultiplier = chargeMultiplier / 2.0;
            fireProjectileFrom(leftWing.x, leftWing.y, attacker, targetX, targetY, leftDamage, false, false, false, 0, target, aircraftColor,
                    weaponName + " wing laser hit for " + leftDamage + " damage.", projectileChargeMultiplier, ammoType, laserType);
            fireProjectileFrom(rightWing.x, rightWing.y, attacker, targetX, targetY, rightDamage, false, false, false, 0, target, aircraftColor,
                    weaponName + " wing laser hit for " + rightDamage + " damage.", projectileChargeMultiplier, ammoType, laserType);
        }

        private boolean canPlayerFireNormal() {
            return !choosingDifficulty && !paused && !gameOver && blueLaserWindupRemaining <= 0 && blueLaserRemaining <= 0;
        }

        private void playerSkillAttack(double targetX, double targetY) {
            if (!canPlayerAct()) {
                return;
            }

            if (blue.charge < MAX_CHARGE) {
                message = "Skill is not ready. Charge: " + blue.charge + "/" + MAX_CHARGE;
                return;
            }

            blue.charge = 0;
            blue.chargeCarry = 0;
            leftMouseDown = false;
            blueBurstQueued = false;
            blueBurstShotsRemaining = 0;
            blueBurstMultiplier = BLUE_INITIAL_BURST_MULTIPLIER;
            if (playerAircraft == Aircraft.BLUE_GLOW) {
                blueLaserWindupRemaining = BLUE_LASER_WINDUP_DURATION;
                blueLaserDamageCarry = 0;
                playSkillSound();
                message = fighterDisplayName(blue) + " is charging blue laser.";
            } else if (playerAircraft == Aircraft.NEUTRON_STAR) {
                playerNeutronSkillWindupRemaining = NEUTRON_SKILL_WINDUP_DURATION;
                playerNeutronSkillTargetX = targetX;
                playerNeutronSkillTargetY = targetY;
                playSkillSound();
                message = fighterDisplayName(blue) + " is compressing a neutron orb.";
            } else if (playerAircraft == Aircraft.VENUS) {
                activateVenusArmor(blue);
            } else if (playerAircraft == Aircraft.SIX_WINGED_ANGEL) {
                activateSeraphZone(blue, targetX, targetY);
            } else {
                int damage = tailFlameMissileDamage(blue);
                fireTailFlameWingMissiles(blue, red, mouseX, mouseY, damage);
                playSkillSound();
                message = fighterDisplayName(blue) + " launched six homing missiles.";
            }
            nextPlayerAttackTime = System.currentTimeMillis() + PLAYER_SKILL_COOLDOWN;
        }

        private boolean canPlayerAct() {
            return !choosingDifficulty && !paused && !gameOver && blueLaserWindupRemaining <= 0 && blueLaserRemaining <= 0 && System.currentTimeMillis() >= nextPlayerAttackTime;
        }

        private void aiTurn() {
            if (choosingDifficulty || paused || gameOver || aiLaserRemaining > 0 || aiBlueBurstShotsRemaining > 0) {
                return;
            }
            Point2D aim = predictedAimPoint(red, blue, aiAircraft);

            if (aiAircraft == Aircraft.BLUE_GLOW) {
                if (red.charge >= MAX_CHARGE) {
                    red.charge = 0;
                    red.chargeCarry = 0;
                    aiLaserRemaining = BLUE_LASER_DURATION;
                    aiLaserDamageCarry = 0;
                    aiBlueBurstMultiplier = BLUE_INITIAL_BURST_MULTIPLIER;
                    message = fighterDisplayName(red) + " AI activated blue laser.";
                } else {
                    if (random.nextDouble() < 0.32) {
                        aiBlueBurstMode = !aiBlueBurstMode;
                        aiBlueBurstMultiplier = BLUE_INITIAL_BURST_MULTIPLIER;
                    }
                    if (aiBlueBurstMode) {
                        if (System.currentTimeMillis() >= aiBlueBurstRoundCooldownUntil) {
                            queueAiBlueBurstRound();
                            message = fighterDisplayName(red) + " AI used burst laser mode.";
                        }
                    } else {
                        pendingBlueGlowChargeMultiplier = 1.0;
                        pendingBlueGlowAmmoType = AmmoType.BULLET;
                        pendingBlueGlowLaserType = LaserType.NONE;
                        int damage = blueGlowAttackDamage(red, false, aiBlueBurstMultiplier);
                        fireBlueGlowLasers(red, blue, aim.x, aim.y, damage, text("蓝光", "Blue Glow"));
                        message = fighterDisplayName(red) + " AI fired twin wing lasers.";
                    }
                }
            } else if (aiAircraft == Aircraft.NEUTRON_STAR) {
                if (red.charge >= MAX_CHARGE) {
                    red.charge = 0;
                    red.chargeCarry = 0;
                    aiNeutronSkillWindupRemaining = NEUTRON_SKILL_WINDUP_DURATION;
                    message = fighterDisplayName(red) + " AI is compressing a neutron orb.";
                } else {
                    fireNeutronStarShot(red, blue, aim.x, aim.y, false);
                    message = fighterDisplayName(red) + " AI fired a neutron shot.";
                }
            } else if (aiAircraft == Aircraft.VENUS) {
                if (red.charge >= MAX_CHARGE) {
                    red.charge = 0;
                    red.chargeCarry = 0;
                    activateVenusArmor(red);
                } else {
                    fireVenusShot(red, blue, aim.x, aim.y, false);
                }
            } else if (aiAircraft == Aircraft.SIX_WINGED_ANGEL) {
                if (red.charge >= MAX_CHARGE) {
                    red.charge = 0;
                    red.chargeCarry = 0;
                    Point2D zoneTarget = red.hp < red.maxHp * 0.72
                            ? new Point2D(red.x, red.y)
                            : new Point2D(red.x * 0.55 + aim.x * 0.45, red.y * 0.55 + aim.y * 0.45);
                    activateSeraphZone(red, zoneTarget.x, zoneTarget.y);
                    message = fighterDisplayName(red) + " AI opened a healing field.";
                } else {
                    fireSeraphShot(red, blue, aim.x, aim.y, false);
                    message = fighterDisplayName(red) + " AI fired a holy beam.";
                }
            } else if (red.charge >= MAX_CHARGE) {
                int damage = tailFlameMissileDamage(red);
                red.charge = 0;
                red.chargeCarry = 0;
                fireTailFlameWingMissiles(red, blue, aim.x, aim.y, damage);
                message = fighterDisplayName(red) + " AI launched six homing missiles.";
            } else {
                fireTailFlameShot(red, blue, aim.x, aim.y, false);
                message = fighterDisplayName(red) + " AI fired a homing shot.";
            }
        }

        private Point2D predictedAimPoint(Fighter attacker, Fighter target, Aircraft attackerAircraft) {
            double projectileSpeed = switch (attackerAircraft) {
                case BLUE_GLOW -> BLUE_GLOW_PROJECTILE_SPEED;
                case TAIL_FLAME -> HOMING_PROJECTILE_SPEED;
                case VENUS -> venusArmorActive(attacker) ? 980 : 900;
                case SIX_WINGED_ANGEL -> SERAPH_PROJECTILE_SPEED;
                case NEUTRON_STAR -> BEAM_PROJECTILE_SPEED;
            };
            double distance = Math.hypot(target.x - attacker.x, target.y - attacker.y);
            double leadTime = clamp(distance / projectileSpeed, 0.12, 0.75);
            Point2D velocity = estimatedFighterVelocity(target);
            double accuracy = difficulty == Difficulty.HARD ? 0.84 : difficulty == Difficulty.MEDIUM ? 0.70 : 0.54;
            double predictedX = target.x + velocity.x * leadTime * accuracy;
            double predictedY = target.y + velocity.y * leadTime * accuracy;
            return new Point2D(
                    clamp(predictedX, ARENA_LEFT + FIGHTER_SIZE / 2.0, ARENA_LEFT + ARENA_WIDTH - FIGHTER_SIZE / 2.0),
                    clamp(predictedY, ARENA_TOP + FIGHTER_SIZE / 2.0, ARENA_TOP + ARENA_HEIGHT - FIGHTER_SIZE / 2.0)
            );
        }

        private Point2D estimatedFighterVelocity(Fighter fighter) {
            if (titleDemoMode) {
                Fighter target = fighter == blue ? red : blue;
                double dx = target.x - fighter.x;
                double dy = target.y - fighter.y;
                double length = Math.hypot(dx, dy);
                double speed = effectiveSpeed(fighter) * 0.65;
                return length > 0 ? new Point2D(dx / length * speed, dy / length * speed) : new Point2D(0, 0);
            }
            if (fighter != blue) {
                return new Point2D(0, 0);
            }
            double dx = 0;
            double dy = 0;
            if (pressedKeys.contains(keyUp)) {
                dy--;
            }
            if (pressedKeys.contains(keyDown)) {
                dy++;
            }
            if (pressedKeys.contains(keyLeft)) {
                dx--;
            }
            if (pressedKeys.contains(keyRight)) {
                dx++;
            }
            double length = Math.hypot(dx, dy);
            if (length == 0) {
                return new Point2D(0, 0);
            }
            double speed = effectiveSpeed(fighter) * (fighter.boosting ? BOOST_SPEED_MULTIPLIER : 1.0);
            return new Point2D(dx / length * speed, dy / length * speed);
        }

        private void fireTailFlameShot(Fighter attacker, Fighter target, double targetX, double targetY, boolean fromPlayer) {
            int damage = normalDamage(attacker, target);
            playFireSound(AmmoType.MISSILE);
            fireProjectile(attacker, targetX, targetY, damage, false, true, true, 3.8, target, aircraftFor(attacker).color,
                    fighterDisplayName(fromPlayer ? blue : red) + " homing shot hit for " + damage + " damage.");
            message = fighterDisplayName(fromPlayer ? blue : red) + " fired a homing shot.";
        }

        private void fireNeutronStarShot(Fighter attacker, Fighter target, double targetX, double targetY, boolean fromPlayer) {
            int damage = normalDamage(attacker, target);
            playFireSound(AmmoType.LASER);
            double angle = Math.atan2(targetY - attacker.y, targetX - attacker.x);
            Point2D nose = transformPoint(attacker.x, attacker.y, angle, 28, 0);
            fireProjectileFrom(nose.x, nose.y, attacker, targetX, targetY, damage, false, false, false, 0, target,
                    aircraftFor(attacker).color,
                    fighterDisplayName(fromPlayer ? blue : red) + " neutron shot hit for " + damage + " damage.",
                    1.0, AmmoType.LASER, LaserType.NON_CONTINUOUS);
            message = fighterDisplayName(fromPlayer ? blue : red) + " fired a neutron shot.";
        }

        private void activateVenusArmor(Fighter fighter) {
            fighter.venusGateRemaining = VENUS_GATE_DURATION;
            fighter.venusArmorRemaining = VENUS_ARMOR_DURATION;
            fighter.venusArmorHp = VENUS_ARMOR_HP;
            if (fighter == red) {
                configureAiAttackTimer();
            }
            playSkillSound();
            message = fighterDisplayName(fighter) + " opened a star gate and began armor assembly.";
        }

        private void fireVenusShot(Fighter attacker, Fighter target, double targetX, double targetY, boolean fromPlayer) {
            boolean enhanced = venusArmorActive(attacker);
            int totalDamage;
            if (enhanced) {
                totalDamage = Math.max(1, (int) Math.round(
                        effectiveAttack(attacker) * VENUS_ENHANCED_ATTACK_MULTIPLIER * attacker.skillBonus
                                - effectiveDefense(target) * 0.25));
            } else {
                totalDamage = Math.max(1, (int) Math.round(
                        effectiveAttack(attacker) * VENUS_NORMAL_ATTACK_MULTIPLIER - effectiveDefense(target)));
            }

            playVenusLaserSound(enhanced);
            double angle = Math.atan2(targetY - attacker.y, targetX - attacker.x);
            if (enhanced) {
                Point2D leftCannon = transformPoint(attacker.x, attacker.y, angle, 2, -25);
                Point2D rightCannon = transformPoint(attacker.x, attacker.y, angle, 2, 25);
                int leftDamage = totalDamage / 2;
                int rightDamage = totalDamage - leftDamage;
                fireVenusLaserBar(leftCannon.x, leftCannon.y, attacker, target, targetX, targetY, leftDamage, true, 0.4);
                fireVenusLaserBar(rightCannon.x, rightCannon.y, attacker, target, targetX, targetY, rightDamage, true, 0.4);
            } else {
                Point2D nose = transformPoint(attacker.x, attacker.y, angle, 31, 0);
                fireVenusLaserBar(nose.x, nose.y, attacker, target, targetX, targetY, totalDamage, false, 1.0);
            }
            message = fighterDisplayName(fromPlayer ? blue : red) + (enhanced
                    ? " fired converging twin laser cannons."
                    : " fired a non-continuous laser bar.");
        }

        private void fireVenusLaserBar(double startX, double startY, Fighter attacker, Fighter target,
                                       double targetX, double targetY, int damage, boolean enhanced, double chargeMultiplier) {
            double dx = targetX - startX;
            double dy = targetY - startY;
            double length = Math.hypot(dx, dy);
            if (length == 0) {
                dx = attacker == blue ? -1 : 1;
                dy = 0;
                length = 1;
            }
            double speed = enhanced ? 980 : 900;
            Projectile projectile = new Projectile(
                    startX,
                    startY,
                    dx / length * speed,
                    dy / length * speed,
                    speed,
                    enhanced ? 8 : 7,
                    damage,
                    enhanced,
                    false,
                    false,
                    0,
                    target,
                    attacker == blue,
                    aircraftFor(attacker).color,
                    fighterDisplayName(attacker) + " laser hit for " + damage + " damage.",
                    chargeMultiplier,
                    AmmoType.LASER,
                    LaserType.NON_CONTINUOUS
            );
            projectile.venusLaserBar = true;
            projectile.venusEnhancedLaser = enhanced;
            projectile.piercing = enhanced;
            projectiles.add(projectile);
        }

        private void fireSeraphShot(Fighter attacker, Fighter target, double targetX, double targetY, boolean fromPlayer) {
            int damage = Math.max(1, (int) Math.round(
                    effectiveAttack(attacker) * SERAPH_ATTACK_MULTIPLIER
                            - effectiveDefense(target) * SERAPH_DEFENSE_FACTOR));
            playFireSound(AmmoType.BULLET);
            double angle = Math.atan2(targetY - attacker.y, targetX - attacker.x);
            Point2D nose = transformPoint(attacker.x, attacker.y, angle, 30, 0);
            Projectile projectile = createProjectileFrom(nose.x, nose.y, attacker, targetX, targetY,
                    damage, false, false, false, 0, target,
                    aircraftFor(attacker).color,
                    fighterDisplayName(attacker) + " holy round hit for " + damage + " damage.",
                    1.0, AmmoType.BULLET, LaserType.NONE);
            projectile.seraphBullet = true;
            projectiles.add(projectile);
            message = fighterDisplayName(fromPlayer ? blue : red) + " fired a holy round.";
        }

        private void activateSeraphZone(Fighter fighter, double targetX, double targetY) {
            double x = clamp(targetX, ARENA_LEFT + SERAPH_ZONE_RADIUS, ARENA_LEFT + ARENA_WIDTH - SERAPH_ZONE_RADIUS);
            double y = clamp(targetY, ARENA_TOP + SERAPH_ZONE_RADIUS, ARENA_TOP + ARENA_HEIGHT - SERAPH_ZONE_RADIUS);
            seraphZones.add(new SeraphZone(fighter == blue, x, y, SERAPH_ZONE_RADIUS, SERAPH_ZONE_DURATION));
            playSkillSound();
            message = fighterDisplayName(fighter) + " opened a healing field.";
        }

        private void fireNeutronStarSkillOrb(Fighter attacker, Fighter target, double targetX, double targetY) {
            playSkillSound();
            double angle = Math.atan2(targetY - attacker.y, targetX - attacker.x);
            Point2D nose = transformPoint(attacker.x, attacker.y, angle, 32, 0);
            double damage = attacker.attack * attacker.skillBonus - target.defense * 0.5;
            double dx = targetX - nose.x;
            double dy = targetY - nose.y;
            double length = Math.hypot(dx, dy);
            if (length == 0) {
                dx = attacker == blue ? -1 : 1;
                dy = 0;
                length = 1;
            }

            Projectile projectile = new Projectile(
                    nose.x,
                    nose.y,
                    dx / length * NEUTRON_ORB_SPEED,
                    dy / length * NEUTRON_ORB_SPEED,
                    NEUTRON_ORB_SPEED,
                    NEUTRON_ORB_RADIUS,
                    Math.max(1, (int) Math.round(damage)),
                    true,
                    false,
                    false,
                    0,
                    target,
                    attacker == blue,
                    aircraftFor(attacker).color,
                    fighterDisplayName(attacker) + " neutron singularity locked target.",
                    0,
                    AmmoType.LASER,
                    LaserType.NON_CONTINUOUS
            );
            projectile.neutronSkillOrb = true;
            projectile.neutronOwner = attacker;
            projectile.neutronPullRemaining = NEUTRON_ORB_PULL_DURATION;
            projectile.neutronFlightRemaining = NEUTRON_ORB_FLIGHT_DURATION;
            projectiles.add(projectile);
            message = fighterDisplayName(attacker) + " launched a neutron singularity.";
        }

        private void fireTailFlameWingMissiles(Fighter attacker, Fighter target, double targetX, double targetY, int damage) {
            playFireSound(AmmoType.MISSILE);
            double angle = Math.atan2(targetY - attacker.y, targetX - attacker.x);
            Point2D leftWing = transformPoint(attacker.x, attacker.y, angle, -8, -20);
            Point2D rightWing = transformPoint(attacker.x, attacker.y, angle, -8, 20);
            double[] spreads = {-0.18, 0, 0.18};

            for (double spread : spreads) {
                double targetAngle = angle + spread;
                fireProjectileWithVectorFrom(
                        leftWing.x,
                        leftWing.y,
                        attacker,
                        Math.cos(targetAngle),
                        Math.sin(targetAngle),
                        damage,
                        true,
                        true,
                        true,
                        3.8,
                        target,
                        aircraftFor(attacker).color,
                        "Tail Flame missile hit for " + damage + " damage.");
                fireProjectileWithVectorFrom(
                        rightWing.x,
                        rightWing.y,
                        attacker,
                        Math.cos(targetAngle),
                        Math.sin(targetAngle),
                        damage,
                        true,
                        true,
                        true,
                        3.8,
                        target,
                        aircraftFor(attacker).color,
                        "Tail Flame missile hit for " + damage + " damage.");
            }
        }

        private void queueAiBlueBurstRound() {
            aiBlueBurstShotsRemaining = BLUE_BURST_SHOTS_PER_ROUND;
            aiBlueBurstMultiplier = BLUE_INITIAL_BURST_MULTIPLIER;
            nextAiBlueShotTime = 0;
        }

        private void updateAiBlueBurstFire() {
            if (aiBlueBurstShotsRemaining <= 0 || aiLaserRemaining > 0) {
                return;
            }

            long now = System.currentTimeMillis();
            if (now < nextAiBlueShotTime) {
                return;
            }

            pendingBlueGlowChargeMultiplier = BLUE_BURST_CHARGE_MULTIPLIER;
            pendingBlueGlowAmmoType = AmmoType.LASER;
            pendingBlueGlowLaserType = LaserType.NON_CONTINUOUS;
            int damage = blueGlowAttackDamage(red, true, aiBlueBurstMultiplier);
            Point2D aim = predictedAimPoint(red, blue, Aircraft.BLUE_GLOW);
            fireBlueGlowLasers(red, blue, aim.x, aim.y, damage, text("蓝光", "Blue Glow"));
            aiBlueBurstMultiplier = Math.max(BLUE_MIN_BURST_MULTIPLIER, aiBlueBurstMultiplier - BLUE_BURST_MULTIPLIER_DROP);
            aiBlueBurstShotsRemaining--;
            nextAiBlueShotTime = now + BLUE_FIRE_INTERVAL;
            if (aiBlueBurstShotsRemaining == 0) {
                aiBlueBurstRoundCooldownUntil = now + BLUE_BURST_ROUND_COOLDOWN;
            }
        }

        private void updatePlayerLaserWindup(double seconds) {
            if (blueLaserWindupRemaining <= 0) {
                return;
            }

            blueLaserWindupRemaining = Math.max(0, blueLaserWindupRemaining - seconds);
            if (blueLaserWindupRemaining == 0) {
                blueLaserRemaining = BLUE_LASER_DURATION;
                blueLaserDamageCarry = 0;
                message = fighterDisplayName(blue) + " activated blue laser for 3 seconds.";
            }
        }

        private void updateNeutronSkillWindups(double seconds) {
            if (playerNeutronSkillWindupRemaining > 0) {
                playerNeutronSkillWindupRemaining = Math.max(0, playerNeutronSkillWindupRemaining - seconds);
                if (playerNeutronSkillWindupRemaining == 0) {
                    fireNeutronStarSkillOrb(blue, red, playerNeutronSkillTargetX, playerNeutronSkillTargetY);
                }
            }
            if (aiNeutronSkillWindupRemaining > 0) {
                aiNeutronSkillWindupRemaining = Math.max(0, aiNeutronSkillWindupRemaining - seconds);
                if (aiNeutronSkillWindupRemaining == 0) {
                    Point2D aim = predictedAimPoint(red, blue, Aircraft.NEUTRON_STAR);
                    fireNeutronStarSkillOrb(red, blue, aim.x, aim.y);
                }
            }
        }

        private void updateNeutronPulls(double seconds) {
            updateNeutronPull(red, seconds);
            updateNeutronPull(blue, seconds);
        }

        private void updateNeutronPull(Fighter fighter, double seconds) {
            if (fighter.neutronPullRemaining <= 0) {
                return;
            }
            fighter.neutronPullRemaining = Math.max(0, fighter.neutronPullRemaining - seconds);
            double pullDx = fighter.neutronPullX - fighter.x;
            double pullDy = fighter.neutronPullY - fighter.y;
            double distance = Math.hypot(pullDx, pullDy);
            fighter.neutronOrbitAngle += seconds * (2.2 + Math.min(1.4, distance / 140.0));
            double desiredRadius = Math.max(34, fighter.neutronOrbitRadius - seconds * 34.0);
            fighter.neutronOrbitRadius = desiredRadius;
            double orbitX = fighter.neutronPullX + Math.cos(fighter.neutronOrbitAngle) * desiredRadius;
            double orbitY = fighter.neutronPullY + Math.sin(fighter.neutronOrbitAngle) * desiredRadius;
            double blend = Math.min(1.0, (3.4 + distance / 110.0) * seconds);
            double wobbleScale = Math.min(1.0, distance / 90.0);
            double wobbleX = Math.sin(fighter.neutronPullRemaining * 15.5) * 4.0 * wobbleScale;
            double wobbleY = Math.cos(fighter.neutronPullRemaining * 12.5) * 3.2 * wobbleScale;
            double targetX = fighter.x + (orbitX - fighter.x) * blend + wobbleX;
            double targetY = fighter.y + (orbitY - fighter.y) * blend + wobbleY;
            fighter.x = clamp(targetX, ARENA_LEFT + FIGHTER_SIZE / 2.0, ARENA_LEFT + ARENA_WIDTH - FIGHTER_SIZE / 2.0);
            fighter.y = clamp(targetY, ARENA_TOP + FIGHTER_SIZE / 2.0, ARENA_TOP + ARENA_HEIGHT - FIGHTER_SIZE / 2.0);
        }

        private void updatePlayerLaser(double seconds) {
            if (blueLaserRemaining <= 0) {
                return;
            }

            blueLaserRemaining = Math.max(0, blueLaserRemaining - seconds);
            LaserPath path = laserPath(blue, mouseX, mouseY);
            if (laserHitsTarget(path, red)) {
                addContinuousLaserDefenderCharge(red, seconds);
                double damagePerSecond = blue.attack * 0.7 * blue.skillBonus;
                blueLaserDamageCarry += damagePerSecond * seconds;
                int damage = (int) blueLaserDamageCarry;
                if (damage > 0) {
                    blueLaserDamageCarry -= damage;
                    applyDamage(red, damage, AmmoType.LASER, LaserType.CONTINUOUS);
                    message = fighterDisplayName(blue) + " laser is burning target.";
                    checkWinner();
                }
            }
        }

        private void updateAiLaser(double seconds) {
            if (aiLaserRemaining <= 0) {
                return;
            }

            aiLaserRemaining = Math.max(0, aiLaserRemaining - seconds);
            LaserPath path = laserPath(red, blue.x, blue.y);
            if (laserHitsTarget(path, blue)) {
                addContinuousLaserDefenderCharge(blue, seconds);
                double damagePerSecond = red.attack * 0.7 * red.skillBonus;
                aiLaserDamageCarry += damagePerSecond * seconds;
                int damage = (int) aiLaserDamageCarry;
                if (damage > 0) {
                    aiLaserDamageCarry -= damage;
                    applyDamage(blue, damage, AmmoType.LASER, LaserType.CONTINUOUS);
                    message = fighterDisplayName(red) + " AI laser is burning target.";
                    checkWinner();
                }
            }
        }

        private LaserPath laserPath(Fighter shooter, double aimX, double aimY) {
            double dx = aimX - shooter.x;
            double dy = aimY - shooter.y;
            double length = Math.hypot(dx, dy);
            if (length == 0) {
                dx = shooter == blue ? -1 : 1;
                dy = 0;
                length = 1;
            }

            double nx = dx / length;
            double ny = dy / length;
            double directDistance = laserDistanceToArenaEdge(shooter.x, shooter.y, nx, ny);
            double directEndX = shooter.x + nx * directDistance;
            double directEndY = shooter.y + ny * directDistance;
            for (Projectile orb : projectiles) {
                if (!orb.neutronSkillOrb || orb.fromBlue == (shooter == blue)) {
                    continue;
                }
                double orbDx = orb.x - shooter.x;
                double orbDy = orb.y - shooter.y;
                double projection = orbDx * nx + orbDy * ny;
                if (projection < 0) {
                    continue;
                }
                double closestX = shooter.x + nx * projection;
                double closestY = shooter.y + ny * projection;
                double distance = Math.hypot(orb.x - closestX, orb.y - closestY);
                if (distance > NEUTRON_ORB_DEFLECT_RADIUS || projection > directDistance) {
                    continue;
                }

                double pullLength = Math.hypot(orbDx, orbDy);
                if (pullLength == 0) {
                    continue;
                }
                double pullX = orbDx / pullLength;
                double pullY = orbDy / pullLength;
                double influence = Math.min(0.96, (1.0 - distance / NEUTRON_ORB_DEFLECT_RADIUS) * 1.05);
                double newX = nx + (pullX - nx) * influence;
                double newY = ny + (pullY - ny) * influence;
                double newLength = Math.hypot(newX, newY);
                if (newLength == 0) {
                    continue;
                }
                double deflectX = newX / newLength;
                double deflectY = newY / newLength;
                double deflectDistance = laserDistanceToArenaEdge(closestX, closestY, deflectX, deflectY);
                double endX = closestX + deflectX * deflectDistance;
                double endY = closestY + deflectY * deflectDistance;
                double sideX = -ny;
                double sideY = nx;
                double bendSign = Math.signum((orb.x - closestX) * sideX + (orb.y - closestY) * sideY);
                if (bendSign == 0) {
                    bendSign = 1;
                }
                double bend = 92 * influence * bendSign;
                double controlDistance = Math.min(240, Math.max(80, deflectDistance * 0.42));
                double controlX = closestX + (nx + deflectX) * 0.5 * controlDistance + sideX * bend;
                double controlY = closestY + (ny + deflectY) * 0.5 * controlDistance + sideY * bend;
                return new LaserPath(shooter.x, shooter.y, closestX, closestY, controlX, controlY, endX, endY, true);
            }

            return new LaserPath(shooter.x, shooter.y, directEndX, directEndY, directEndX, directEndY, directEndX, directEndY, false);
        }

        private boolean laserHitsTarget(LaserPath path, Fighter target) {
            if (distanceToSegment(target.x, target.y, path.startX, path.startY, path.pivotX, path.pivotY) <= BLUE_LASER_HIT_RADIUS) {
                return true;
            }
            if (!path.deflected) {
                return false;
            }

            double previousX = path.pivotX;
            double previousY = path.pivotY;
            for (int i = 1; i <= 30; i++) {
                double t = i / 30.0;
                double oneMinus = 1.0 - t;
                double currentX = oneMinus * oneMinus * path.pivotX + 2 * oneMinus * t * path.controlX + t * t * path.endX;
                double currentY = oneMinus * oneMinus * path.pivotY + 2 * oneMinus * t * path.controlY + t * t * path.endY;
                if (distanceToSegment(target.x, target.y, previousX, previousY, currentX, currentY) <= BLUE_LASER_HIT_RADIUS) {
                    return true;
                }
                previousX = currentX;
                previousY = currentY;
            }
            return false;
        }

        private double distanceToSegment(double px, double py, double ax, double ay, double bx, double by) {
            double dx = bx - ax;
            double dy = by - ay;
            double lengthSquared = dx * dx + dy * dy;
            if (lengthSquared == 0) {
                return Math.hypot(px - ax, py - ay);
            }
            double t = clamp(((px - ax) * dx + (py - ay) * dy) / lengthSquared, 0, 1);
            double closestX = ax + dx * t;
            double closestY = ay + dy * t;
            return Math.hypot(px - closestX, py - closestY);
        }

        private void fireProjectile(Fighter attacker, double targetX, double targetY, int damage, boolean skill, boolean missile,
                                    boolean homing, double homingStrength, Fighter homingTarget, Color color, String hitMessage) {
            fireProjectileFrom(attacker.x, attacker.y, attacker, targetX, targetY, damage, skill, missile, homing, homingStrength, homingTarget, color, hitMessage);
        }

        private void fireProjectileFrom(double startX, double startY, Fighter attacker, double targetX, double targetY, int damage, boolean skill,
                                        boolean missile, boolean homing, double homingStrength, Fighter homingTarget, Color color, String hitMessage) {
            fireProjectileFrom(startX, startY, attacker, targetX, targetY, damage, skill, missile, homing, homingStrength, homingTarget, color, hitMessage, 1.0);
        }

        private void fireProjectileFrom(double startX, double startY, Fighter attacker, double targetX, double targetY, int damage, boolean skill,
                                        boolean missile, boolean homing, double homingStrength, Fighter homingTarget, Color color, String hitMessage,
                                        double chargeMultiplier) {
            AmmoType ammoType = missile ? AmmoType.MISSILE : AmmoType.BULLET;
            fireProjectileFrom(startX, startY, attacker, targetX, targetY, damage, skill, missile, homing, homingStrength, homingTarget, color,
                    hitMessage, chargeMultiplier, ammoType, LaserType.NONE);
        }

        private void fireProjectileFrom(double startX, double startY, Fighter attacker, double targetX, double targetY, int damage, boolean skill,
                                        boolean missile, boolean homing, double homingStrength, Fighter homingTarget, Color color, String hitMessage,
                                        double chargeMultiplier, AmmoType ammoType, LaserType laserType) {
            projectiles.add(createProjectileFrom(startX, startY, attacker, targetX, targetY, damage, skill,
                    missile, homing, homingStrength, homingTarget, color, hitMessage, chargeMultiplier, ammoType, laserType));
        }

        private Projectile createProjectileFrom(double startX, double startY, Fighter attacker, double targetX, double targetY, int damage, boolean skill,
                                                boolean missile, boolean homing, double homingStrength, Fighter homingTarget, Color color, String hitMessage,
                                                double chargeMultiplier, AmmoType ammoType, LaserType laserType) {
            double dx = targetX - startX;
            double dy = targetY - startY;
            double length = Math.hypot(dx, dy);
            if (length == 0) {
                dx = attacker == blue ? -1 : 1;
                dy = 0;
                length = 1;
            }

            return createProjectileWithVectorFrom(startX, startY, attacker, dx / length, dy / length, damage, skill, missile, homing, homingStrength,
                    homingTarget, color, hitMessage, chargeMultiplier, ammoType, laserType);
        }

        private void fireProjectileWithVectorFrom(double startX, double startY, Fighter attacker, double dx, double dy, int damage, boolean skill,
                                                  boolean missile, boolean homing, double homingStrength, Fighter homingTarget, Color color, String hitMessage) {
            fireProjectileWithVectorFrom(startX, startY, attacker, dx, dy, damage, skill, missile, homing, homingStrength, homingTarget, color, hitMessage, 1.0);
        }

        private void fireProjectileWithVectorFrom(double startX, double startY, Fighter attacker, double dx, double dy, int damage, boolean skill,
                                                  boolean missile, boolean homing, double homingStrength, Fighter homingTarget, Color color,
                                                  String hitMessage, double chargeMultiplier) {
            AmmoType ammoType = missile ? AmmoType.MISSILE : AmmoType.BULLET;
            fireProjectileWithVectorFrom(startX, startY, attacker, dx, dy, damage, skill, missile, homing, homingStrength, homingTarget, color,
                    hitMessage, chargeMultiplier, ammoType, LaserType.NONE);
        }

        private void fireProjectileWithVectorFrom(double startX, double startY, Fighter attacker, double dx, double dy, int damage, boolean skill,
                                                  boolean missile, boolean homing, double homingStrength, Fighter homingTarget, Color color,
                                                  String hitMessage, double chargeMultiplier, AmmoType ammoType, LaserType laserType) {
            projectiles.add(createProjectileWithVectorFrom(startX, startY, attacker, dx, dy, damage, skill, missile, homing,
                    homingStrength, homingTarget, color, hitMessage, chargeMultiplier, ammoType, laserType));
        }

        private Projectile createProjectileWithVectorFrom(double startX, double startY, Fighter attacker, double dx, double dy, int damage, boolean skill,
                                                          boolean missile, boolean homing, double homingStrength, Fighter homingTarget, Color color,
                                                          String hitMessage, double chargeMultiplier, AmmoType ammoType, LaserType laserType) {
            double speed = homing
                    ? HOMING_PROJECTILE_SPEED
                    : (missile ? MISSILE_PROJECTILE_SPEED : beamProjectileSpeed(attacker));
            double maxSpeed = speed;
            double acceleration = 0;
            if (skill && missile) {
                speed = TAIL_FLAME_SKILL_MISSILE_INITIAL_SPEED;
                maxSpeed = TAIL_FLAME_SKILL_MISSILE_MAX_SPEED;
                acceleration = TAIL_FLAME_SKILL_MISSILE_ACCELERATION;
            }
            double radius = missile ? 12 : 7.5;
            Projectile projectile = new Projectile(
                    startX,
                    startY,
                    dx * speed,
                    dy * speed,
                    speed,
                    radius,
                    damage,
                    skill,
                    missile,
                    homing,
                    homingStrength,
                    homingTarget,
                    attacker == blue,
                    color,
                    hitMessage,
                    chargeMultiplier,
                    ammoType,
                    laserType
            );
            projectile.maxSpeed = maxSpeed;
            projectile.acceleration = acceleration;
            if (aircraftFor(attacker) == Aircraft.TAIL_FLAME && homing) {
                projectile.homingTimeout = skill ? TAIL_FLAME_SKILL_HOMING_DURATION : TAIL_FLAME_NORMAL_HOMING_DURATION;
            }
            return projectile;
        }

        private double beamProjectileSpeed(Fighter attacker) {
            Aircraft aircraft = aircraftFor(attacker);
            if (aircraft == Aircraft.BLUE_GLOW) {
                return BLUE_GLOW_PROJECTILE_SPEED;
            }
            if (aircraft == Aircraft.SIX_WINGED_ANGEL) {
                return SERAPH_PROJECTILE_SPEED;
            }
            return BEAM_PROJECTILE_SPEED;
        }

        private boolean venusArmorActive(Fighter fighter) {
            return aircraftFor(fighter) == Aircraft.VENUS
                    && fighter.venusGateRemaining <= 0
                    && fighter.venusArmorRemaining > 0;
        }

        private int effectiveAttack(Fighter fighter) {
            return venusArmorActive(fighter) ? (int) Math.round(fighter.attack * VENUS_ATTACK_BOOST) : fighter.attack;
        }

        private int effectiveDefense(Fighter fighter) {
            double value = venusArmorActive(fighter) ? fighter.defense * VENUS_DEFENSE_BOOST : fighter.defense;
            if (fighter.seraphDefenseDebuffRemaining > 0) {
                value *= SERAPH_DEFENSE_DEBUFF_MULTIPLIER;
            }
            return Math.max(1, (int) Math.round(value));
        }

        private int effectiveSpeed(Fighter fighter) {
            double value = venusArmorActive(fighter) ? fighter.speed * VENUS_SPEED_BOOST : fighter.speed;
            if (fighter.seraphSpeedBuffRemaining > 0) {
                value *= SERAPH_SPEED_BUFF_MULTIPLIER;
            }
            if (fighter.seraphSpeedDebuffRemaining > 0) {
                value *= SERAPH_SPEED_DEBUFF_MULTIPLIER;
            }
            return Math.max(1, (int) Math.round(value));
        }

        private int normalDamage(Fighter attacker, Fighter defender) {
            return Math.max(1, effectiveAttack(attacker) - effectiveDefense(defender));
        }

        private int blueBurstDamage(boolean burstShot) {
            return blueGlowAttackDamage(blue, burstShot, blueBurstMultiplier);
        }

        private int blueGlowAttackDamage(Fighter attacker, boolean burstShot, double burstMultiplier) {
            double damage = burstShot
                    ? attacker.attack * 0.68 / BLUE_BURST_SHOTS_PER_ROUND * burstMultiplier
                    : attacker.attack * 0.5 * burstMultiplier;
            return Math.max(1, (int) Math.round(damage));
        }

        private int tailFlameMissileDamage(Fighter attacker) {
            return Math.max(1, (int) Math.round(effectiveAttack(attacker) / 6.0 * attacker.skillBonus));
        }

        private Point2D transformPoint(double centerX, double centerY, double angle, double localX, double localY) {
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            return new Point2D(
                    centerX + localX * cos - localY * sin,
                    centerY + localX * sin + localY * cos
            );
        }

        private Aircraft aircraftFor(Fighter fighter) {
            return fighter == blue ? playerAircraft : aiAircraft;
        }

        private String fighterDisplayName(Fighter fighter) {
            return aircraftFor(fighter).displayName(chinese);
        }

        private void applyDamage(Fighter target, int damage, AmmoType ammoType, LaserType laserType) {
            if (damage <= 0) {
                return;
            }
            int finalDamage = applyArmorReduction(target, damage, ammoType, laserType);
            if (venusArmorActive(target) && target.venusArmorHp > 0) {
                int absorbed = Math.min(finalDamage, target.venusArmorHp);
                target.venusArmorHp -= absorbed;
                finalDamage -= absorbed;
                showVenusArmorReaction(target);
            }
            if (finalDamage > 0 && aircraftFor(target) == Aircraft.VENUS && target.venusShardCount > 0) {
                int absorbed = Math.min(finalDamage, VENUS_SHARD_ABSORB);
                finalDamage -= absorbed;
                target.venusShardCount--;
                showFloatingText(target.x, target.y - 46, text("护片 -" + absorbed, "Shard -" + absorbed), new Color(255, 214, 96));
            }
            if (finalDamage > 0) {
                target.hp = Math.max(0, target.hp - finalDamage);
            }
        }

        private void showVenusArmorReaction(Fighter target) {
            long now = System.currentTimeMillis();
            if (now < target.nextArmorTextAt) {
                return;
            }
            target.nextArmorTextAt = now + 350;
            showFloatingText(target.x, target.y - 46, text("强化装甲", "Reinforced"), new Color(255, 205, 74));
        }

        private int applyArmorReduction(Fighter target, int damage, AmmoType ammoType, LaserType laserType) {
            double multiplier = 1.0;
            ArmorType triggeredArmor = ArmorType.NONE;
            if (ammoType == AmmoType.BULLET && target.hasArmor(ArmorType.STEEL)) {
                multiplier = 0.9;
                triggeredArmor = ArmorType.STEEL;
            } else if (ammoType == AmmoType.LASER && target.hasArmor(ArmorType.LIGHT)) {
                multiplier = 0.9;
                triggeredArmor = ArmorType.LIGHT;
            } else if (ammoType == AmmoType.MISSILE && target.hasArmor(ArmorType.COMPOSITE)) {
                multiplier = 0.85;
                triggeredArmor = ArmorType.COMPOSITE;
            }
            if (triggeredArmor != ArmorType.NONE) {
                showArmorReaction(target, triggeredArmor);
            }
            return Math.max(1, (int) Math.round(damage * multiplier));
        }

        private void showArmorReaction(Fighter target, ArmorType armorType) {
            long now = System.currentTimeMillis();
            if (now < target.nextArmorTextAt) {
                return;
            }
            target.nextArmorTextAt = now + 350;
            showFloatingText(target.x, target.y - 46, armorType.reactionText(chinese), armorType.color);
        }

        private void showFloatingText(double x, double y, String value, Color color) {
            floatingTexts.add(new FloatingText(value, x, y, color));
        }

        private void playBattleMusicNote() {
            if (choosingDifficulty || paused || gameOver) {
                return;
            }
            double[] melody = {293.66, 369.99, 440.00, 392.00, 493.88, 440.00, 329.63, 392.00};
            double note = melody[musicStep % melody.length];
            audio.playTone(note, 240, 0.30, 0.28);
            if (musicStep % 2 == 0) {
                audio.playTone(note / 2.0, 300, 0.20, 0.18);
            }
            if (musicStep % 8 == 0) {
                audio.playTone(73.42, 180, 0.22, 0.65);
            }
            musicStep++;
        }

        private void playFireSound(AmmoType ammoType) {
            if (titleDemoMode) {
                return;
            }
            audio.beginImmediateSequence();
            if (ammoType == AmmoType.MISSILE) {
                audio.playSweep(190, 90, 170, 0.52, 0.55);
            } else if (ammoType == AmmoType.LASER) {
                audio.playTone(1320, 95, 0.46, 0.20);
                audio.playTone(1760, 70, 0.24, 0.10);
            } else {
                audio.playTone(760, 65, 0.42, 0.60);
            }
        }

        private void playVenusLaserSound(boolean enhanced) {
            if (titleDemoMode) {
                return;
            }
            audio.beginImmediateSequence();
            if (enhanced) {
                audio.playSweep(620, 1380, 105, 0.50, 0.32);
                audio.playTone(1840, 85, 0.34, 0.12);
                audio.playSweep(980, 520, 90, 0.24, 0.50);
            } else {
                audio.playSweep(880, 1580, 82, 0.40, 0.18);
                audio.playTone(1960, 55, 0.24, 0.08);
            }
        }

        private void playSkillSound() {
            if (titleDemoMode) {
                return;
            }
            audio.beginImmediateSequence();
            audio.playSweep(260, 920, 260, 0.48, 0.25);
            audio.playTone(1180, 170, 0.34, 0.15);
        }

        private void playHitSound() {
            if (titleDemoMode) {
                return;
            }
            audio.playSweep(160, 70, 95, 0.42, 0.75);
        }

        private void playShieldSound() {
            if (titleDemoMode) {
                return;
            }
            audio.playTone(520, 110, 0.30, 0.10);
            audio.playTone(980, 130, 0.28, 0.08);
        }

        private void playUiSound() {
            audio.playTone(900, 55, 0.22, 0.12);
        }

        private void playBoostSound() {
            if (titleDemoMode) {
                return;
            }
            audio.playSweep(180, 360, 120, 0.34, 0.70);
        }

        private void playBoostMaxSpeedSound() {
            if (titleDemoMode) {
                return;
            }
            audio.playTone(1480, 90, 0.30, 0.18);
            audio.playSweep(920, 1320, 150, 0.24, 0.12);
        }

        private void playExplosionSound() {
            audio.beginImmediateSequence();
            audio.playSweep(120, 42, 420, 0.72, 0.85);
            audio.playTone(64, 360, 0.55, 0.70);
        }

        private void checkWinner() {
            if (titleDemoMode) {
                return;
            }
            if (gameOver) {
                return;
            }
            if (red.hp <= 0 || blue.hp <= 0) {
                gameOver = true;
                playerWon = red.hp <= 0;
                explosionActive = true;
                resultReady = false;
                explosionStartedAt = System.currentTimeMillis();
                Fighter destroyed = playerWon ? red : blue;
                explosionX = destroyed.x;
                explosionY = destroyed.y;
                aiTimer.stop();
                musicTimer.stop();
                playExplosionSound();
                message = fighterDisplayName(destroyed) + " destroyed.";
            }
        }

        private void updateExplosion() {
            if (System.currentTimeMillis() - explosionStartedAt >= 1200) {
                explosionActive = false;
                resultReady = true;
                Fighter winner = playerWon ? blue : red;
                message = fighterDisplayName(winner) + " wins. Press R to return to setup.";
            }
        }

        private void startBattle(Difficulty selectedDifficulty) {
            audio.setPaused(false);
            difficulty = selectedDifficulty;
            playerAircraft = playerAircraftConfirmed ? Aircraft.values()[selectedAircraftIndex] : randomAiAircraft();
            aiAircraft = aiAircraftConfirmed ? Aircraft.values()[selectedAiAircraftIndex] : randomAiAircraft();
            blue.applyAircraft(playerAircraft);
            red.applyAircraft(aiAircraft);
            red.attack = difficulty.adjustAttack(aiAircraft.attack);
            red.speed = difficulty.adjustSpeed(aiAircraft.speed);
            red.reset();
            blue.reset();
            configureAiAttackTimer();
            projectiles.clear();
            shieldPickups.clear();
            seraphZones.clear();
            heartPickups.clear();
            flameTrails.clear();
            floatingTexts.clear();
            pressedKeys.clear();
            blueLaserWindupRemaining = 0;
            blueLaserRemaining = 0;
            blueLaserDamageCarry = 0;
            playerNeutronSkillWindupRemaining = 0;
            aiNeutronSkillWindupRemaining = 0;
            aiLaserRemaining = 0;
            aiLaserDamageCarry = 0;
            blueBurstMultiplier = BLUE_INITIAL_BURST_MULTIPLIER;
            aiBlueBurstMultiplier = BLUE_INITIAL_BURST_MULTIPLIER;
            blueBurstShotsRemaining = 0;
            aiBlueBurstShotsRemaining = 0;
            nextAiBlueShotTime = 0;
            blueBurstRoundCooldownUntil = 0;
            aiBlueBurstRoundCooldownUntil = 0;
            blueBurstMode = false;
            aiBlueBurstMode = false;
            leftMouseDown = false;
            chargeInputDown = false;
            draggingVolume = false;
            blueBurstQueued = false;
            aiWantsBoost = false;
            aiBoostDecisionTimer = 0;
            aiMoveX = 0.8;
            aiMoveY = 1.0;
            choosingDifficulty = false;
            paused = false;
            gameOver = false;
            playerWon = false;
            explosionActive = false;
            resultReady = false;
            lastUpdateTime = System.nanoTime();
            nextPlayerAttackTime = 0;
            nextPlayerNormalAttackTime = 0;
            shieldSpawnTimer = 1.4;
            blueHeartSpawnTimer = SERAPH_HEART_SPAWN_INTERVAL * 0.55;
            redHeartSpawnTimer = SERAPH_HEART_SPAWN_INTERVAL * 0.75;
            setInputMethodLocked(true);
            message = difficulty.displayName(chinese) + " mode. WASD move, aim with cursor, fire with mouse.";
            audio.setVolume(volume);
            musicStep = 0;
            aiTimer.restart();
            musicTimer.restart();
            repaint();
        }

        private void configureAiAttackTimer() {
            int baseInterval = baseAiAttackInterval();
            int aiInterval = difficulty.adjustAttackInterval(baseInterval);
            aiInterval = Math.max(250, aiInterval);
            aiTimer.setDelay(aiInterval);
            aiTimer.setInitialDelay(aiInterval);
        }

        private int baseAiAttackInterval() {
            if (aiAircraft == Aircraft.VENUS && venusArmorActive(red)) {
                return VENUS_ENHANCED_ATTACK_COOLDOWN;
            }
            if (aiAircraft == Aircraft.TAIL_FLAME) {
                return TAIL_FLAME_ATTACK_COOLDOWN;
            }
            if (aiAircraft == Aircraft.VENUS) {
                return VENUS_ATTACK_COOLDOWN;
            }
            if (aiAircraft == Aircraft.SIX_WINGED_ANGEL) {
                return NORMAL_ATTACK_COOLDOWN;
            }
            return NORMAL_ATTACK_COOLDOWN;
        }

        private Aircraft randomAiAircraft() {
            Aircraft[] aircraft = Aircraft.values();
            return aircraft[random.nextInt(aircraft.length)];
        }

        private void showDifficultySelection() {
            audio.setPaused(false);
            red.reset();
            blue.reset();
            projectiles.clear();
            shieldPickups.clear();
            seraphZones.clear();
            heartPickups.clear();
            flameTrails.clear();
            floatingTexts.clear();
            pressedKeys.clear();
            blueLaserWindupRemaining = 0;
            blueLaserRemaining = 0;
            blueLaserDamageCarry = 0;
            playerNeutronSkillWindupRemaining = 0;
            aiNeutronSkillWindupRemaining = 0;
            aiLaserRemaining = 0;
            aiLaserDamageCarry = 0;
            blueBurstMultiplier = BLUE_INITIAL_BURST_MULTIPLIER;
            aiBlueBurstMultiplier = BLUE_INITIAL_BURST_MULTIPLIER;
            blueBurstShotsRemaining = 0;
            aiBlueBurstShotsRemaining = 0;
            nextAiBlueShotTime = 0;
            blueBurstRoundCooldownUntil = 0;
            aiBlueBurstRoundCooldownUntil = 0;
            blueBurstMode = false;
            aiBlueBurstMode = false;
            leftMouseDown = false;
            chargeInputDown = false;
            draggingVolume = false;
            blueBurstQueued = false;
            blueHeartSpawnTimer = SERAPH_HEART_SPAWN_INTERVAL;
            redHeartSpawnTimer = SERAPH_HEART_SPAWN_INTERVAL;
            aiWantsBoost = false;
            aiBoostDecisionTimer = 0;
            choosingDifficulty = true;
            showingHangar = false;
            showingMapSelection = false;
            paused = false;
            gameOver = false;
            playerWon = false;
            explosionActive = false;
            resultReady = false;
            aircraftMenuOpen = true;
            difficultyMenuOpen = false;
            selectedMenuSection = 0;
            shieldSpawnTimer = 0;
            message = text("配置战机和难度。", "Configure aircraft and difficulty.");
            aiTimer.stop();
            musicTimer.stop();
            settingsOpen = false;
            rebindingControlIndex = -1;
            repaint();
        }

        private int toGameX(MouseEvent event) {
            return clampInt((int) Math.round((event.getX() - renderOffsetX) / renderScale), 0, WIDTH);
        }

        private int toGameY(MouseEvent event) {
            return clampInt((int) Math.round((event.getY() - renderOffsetY) / renderScale), 0, HEIGHT);
        }

        private int clampInt(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            renderScale = Math.max(0.1, Math.min(getWidth() / (double) WIDTH, getHeight() / (double) HEIGHT));
            int scaledWidth = (int) Math.round(WIDTH * renderScale);
            int scaledHeight = (int) Math.round(HEIGHT * renderScale);
            renderOffsetX = Math.max(0, (getWidth() - scaledWidth) / 2);
            renderOffsetY = Math.max(0, (getHeight() - scaledHeight) / 2);
            g.translate(renderOffsetX, renderOffsetY);
            g.scale(renderScale, renderScale);

            if (titleScreen) {
                drawTitleScreen(g);
                g.dispose();
                return;
            }

            if (choosingDifficulty) {
                if (showingHangar) {
                    drawHangar(g);
                } else if (showingMapSelection) {
                    drawMapSelection(g);
                } else {
                    drawDifficultySelection(g);
                }
                g.dispose();
                return;
            }

            drawArena(g);
            drawBattleMapName(g);
            drawPauseButton(g);
            drawFlameTrails(g);
            drawSeraphZones(g);
            drawShieldPickups(g);
            drawHeartPickups(g);
            drawAimLine(g);
            drawBlueLaser(g);
            drawProjectiles(g);
            if (!(gameOver && playerWon)) {
                drawFighterAvatar(g, red, aiAircraft.color);
            }
            if (!(gameOver && !playerWon)) {
                drawFighterAvatar(g, blue, playerAircraft.color);
            }
            if (explosionActive) {
                drawExplosion(g);
            }
            drawFighter(g, red, 24, 592, aiAircraft.color);
            drawFighter(g, blue, 698, 592, playerAircraft.color);
            drawFloatingTexts(g);
            drawMessage(g);
            if (paused) {
                drawPausedOverlay(g);
            }
            if (gameOver && resultReady) {
                drawResultOverlay(g);
            }

            g.dispose();
        }

        private void toggleFullScreen() {
            if (!fullScreen) {
                fullScreen = true;
                frame.dispose();
                frame.setUndecorated(true);
                frame.setResizable(false);
                setPreferredSize(null);
                graphicsDevice.setFullScreenWindow(frame);
                frame.setVisible(true);
            } else {
                fullScreen = false;
                graphicsDevice.setFullScreenWindow(null);
                frame.dispose();
                frame.setUndecorated(false);
                frame.setResizable(false);
                setPreferredSize(new Dimension(WIDTH, HEIGHT));
                setSize(new Dimension(WIDTH, HEIGHT));
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            }
            requestFocusInWindow();
            repaint();
        }

        private void togglePause() {
            paused = !paused;
            audio.setPaused(paused);
            pressedKeys.clear();
            if (paused) {
                aiTimer.stop();
                musicTimer.stop();
                leftMouseDown = false;
                chargeInputDown = false;
                blueBurstQueued = false;
                blueBurstShotsRemaining = 0;
                blueBurstMultiplier = BLUE_INITIAL_BURST_MULTIPLIER;
                message = "Paused.";
            } else {
                lastUpdateTime = System.nanoTime();
                message = difficulty.displayName(chinese) + " mode. WASD move, aim with cursor, fire with mouse.";
                aiTimer.restart();
                musicTimer.restart();
            }
            repaint();
        }

        private boolean inRect(int x, int y, int rectX, int rectY, int rectW, int rectH) {
            return x >= rectX && x <= rectX + rectW && y >= rectY && y <= rectY + rectH;
        }

        private void handlePauseClick(int x, int y) {
            if (paused) {
                if (inRect(x, y, PAUSE_MENU_BUTTON_X, PAUSE_MENU_CONTINUE_Y, PAUSE_MENU_BUTTON_WIDTH, PAUSE_MENU_BUTTON_HEIGHT)) {
                    togglePause();
                    return;
                }
                if (inRect(x, y, PAUSE_MENU_BUTTON_X, PAUSE_MENU_HOME_Y, PAUSE_MENU_BUTTON_WIDTH, PAUSE_MENU_BUTTON_HEIGHT)) {
                    showDifficultySelection();
                }
                return;
            }

            if (inRect(x, y, PAUSE_BUTTON_X, PAUSE_BUTTON_Y, PAUSE_BUTTON_SIZE, PAUSE_BUTTON_SIZE)) {
                togglePause();
            }
        }

        private void drawTitleScreen(Graphics2D g) {
            BattleMap map = BattleMap.values()[titleMapIndex];

            long now = System.currentTimeMillis();
            drawArena(g, map);
            drawFlameTrails(g);
            drawSeraphZones(g);
            drawHeartPickups(g);
            drawBlueLaser(g);
            drawProjectiles(g);
            drawFighterAvatar(g, red, aiAircraft.color);
            drawFighterAvatar(g, blue, playerAircraft.color);

            g.setFont(new Font("SansSerif", Font.BOLD, 68));
            FontMetrics titleMetrics = g.getFontMetrics();
            String title = "星际战机";
            int titleX = (WIDTH - titleMetrics.stringWidth(title)) / 2;
            int titleY = 170;
            g.setColor(new Color(79, 220, 255, 70));
            g.drawString(title, titleX - 3, titleY + 3);
            g.setColor(new Color(255, 214, 82, 80));
            g.drawString(title, titleX + 3, titleY - 2);
            g.setColor(new Color(238, 248, 255));
            g.drawString(title, titleX, titleY);
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(118, 226, 255, 190));
            g.drawLine(titleX + 10, titleY + 15, titleX + titleMetrics.stringWidth(title) - 10, titleY + 15);
            g.setColor(new Color(255, 199, 68, 190));
            g.drawLine(titleX + 58, titleY + 25, titleX + titleMetrics.stringWidth(title) - 58, titleY + 25);

            double pulse = 0.62 + 0.38 * Math.sin(now / 420.0);
            g.setFont(new Font("SansSerif", Font.BOLD, 22));
            g.setColor(new Color(238, 246, 255, (int) Math.round(155 + 80 * pulse)));
            drawCenteredText(g, text("点击任意处开始游戏", "Click anywhere to start"), 575);
            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            g.setColor(new Color(170, 188, 214));
            drawCenteredText(g, "F11 Fullscreen", 606);
        }

        private void drawDifficultySelection(Graphics2D g) {
            drawArenaBackdrop(g);
            drawSettingsButton(g);

            g.setFont(new Font("SansSerif", Font.BOLD, 36));
            g.setColor(new Color(238, 244, 255));
            drawCenteredText(g, text("战斗设置", "BATTLE SETUP"), 86);

            int panelX = SETUP_PANEL_X;
            int panelW = SETUP_PANEL_WIDTH;
            int y = 132;
            y = drawMenuHeader(g, text("选择战机", "Aircraft"), setupAircraftSummary(), panelX, y, panelW, selectedMenuSection == 0, aircraftMenuOpen);
            if (aircraftMenuOpen) {
                y = drawAircraftOptions(g, panelX, y, panelW) + 16;
            }

            y = drawMenuHeader(g, text("人机难度", "AI Difficulty"), Difficulty.values()[selectedDifficultyIndex].displayName(chinese), panelX, y, panelW, selectedMenuSection == 1, difficultyMenuOpen);
            if (difficultyMenuOpen) {
                y = drawDifficultyOptions(g, panelX, y, panelW) + 16;
            }
            y = drawMenuHeader(g, text("地图", "Map"), selectedMap().displayName(chinese), panelX, y, panelW,
                    selectedMenuSection == 2, false) + 16;

            g.setFont(new Font("SansSerif", Font.PLAIN, 17));
            g.setColor(new Color(196, 207, 224));
            drawCenteredText(g, setupSelectionPrompt(), 588);

            g.setColor(new Color(32, 130, 178));
            g.fillRoundRect(START_BUTTON_X, 604, START_BUTTON_WIDTH, 32, 10, 10);
            g.setColor(new Color(118, 226, 255));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(START_BUTTON_X, 604, START_BUTTON_WIDTH, 32, 10, 10);
            g.setFont(new Font("SansSerif", Font.BOLD, 16));
            g.setColor(Color.WHITE);
            drawCenteredTextInBox(g, text("开始战斗", "START BATTLE"), START_BUTTON_X, START_BUTTON_WIDTH, 626);

            g.setColor(new Color(56, 70, 92));
            g.fillRoundRect(HANGAR_BUTTON_X, 646, HANGAR_BUTTON_WIDTH, 30, 10, 10);
            g.setColor(new Color(142, 166, 204));
            g.drawRoundRect(HANGAR_BUTTON_X, 646, HANGAR_BUTTON_WIDTH, 30, 10, 10);
            g.setFont(new Font("SansSerif", Font.BOLD, 15));
            g.setColor(Color.WHITE);
            drawCenteredTextInBox(g, text("机库", "HANGAR"), HANGAR_BUTTON_X, HANGAR_BUTTON_WIDTH, 667);
            if (settingsOpen) {
                drawSettingsPanel(g);
            }
        }

        private BattleMap selectedMap() {
            return BattleMap.values()[selectedMapIndex];
        }

        private void drawMapSelection(Graphics2D g) {
            drawArenaBackdrop(g);
            drawSettingsButton(g);

            g.setFont(new Font("SansSerif", Font.BOLD, 34));
            g.setColor(new Color(238, 244, 255));
            drawCenteredText(g, text("地图选择", "MAP SELECT"), 72);

            BattleMap[] maps = BattleMap.values();
            int listX = 62;
            int listY = 116;
            int listW = 250;
            int rowH = 70;
            g.setFont(new Font("SansSerif", Font.BOLD, 18));
            for (int i = 0; i < maps.length; i++) {
                BattleMap map = maps[i];
                int y = listY + i * (rowH + 10);
                boolean previewed = i == previewMapIndex;
                boolean selected = i == selectedMapIndex;
                g.setColor(previewed ? new Color(34, 43, 60) : new Color(22, 27, 36));
                g.fillRoundRect(listX, y, listW, rowH, 10, 10);
                g.setColor(selected ? new Color(118, 226, 255) : previewed ? map.accent : new Color(82, 92, 112));
                g.setStroke(new BasicStroke(selected ? 3f : previewed ? 2.2f : 1.4f));
                g.drawRoundRect(listX, y, listW, rowH, 10, 10);

                drawMapSwatch(g, map, listX + 14, y + 15, 52, 40);
                g.setColor(Color.WHITE);
                g.setFont(new Font("SansSerif", Font.BOLD, 15));
                drawClippedString(g, map.displayName(chinese), listX + 78, y + 28, listW - 92);
                g.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g.setColor(new Color(190, 202, 220));
                drawClippedString(g, map.tagline(chinese), listX + 78, y + 52, listW - 92);
            }

            BattleMap preview = maps[previewMapIndex];
            g.setColor(new Color(19, 24, 34));
            g.fillRoundRect(340, 116, 520, 300, 12, 12);
            g.setColor(preview.accent);
            g.setStroke(new BasicStroke(2.4f));
            g.drawRoundRect(340, 116, 520, 300, 12, 12);
            drawBattleMap(g, preview, 360, 136, 480, 260, false);

            g.setFont(new Font("SansSerif", Font.BOLD, 24));
            g.setColor(Color.WHITE);
            g.drawString(preview.displayName(chinese), 348, 464);
            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            g.setColor(new Color(198, 211, 229));
            String[] lines = preview.details(chinese);
            for (int i = 0; i < lines.length; i++) {
                g.drawString(lines[i], 350, 496 + i * 24);
            }

            g.setColor(new Color(32, 130, 178));
            g.fillRoundRect(502, 604, 180, 34, 10, 10);
            g.setColor(new Color(118, 226, 255));
            g.drawRoundRect(502, 604, 180, 34, 10, 10);
            g.setFont(new Font("SansSerif", Font.BOLD, 15));
            g.setColor(Color.WHITE);
            drawCenteredTextInBox(g, text("使用地图", "USE MAP"), 502, 180, 626);

            g.setColor(new Color(56, 70, 92));
            g.fillRoundRect(282, 604, 180, 34, 10, 10);
            g.setColor(new Color(142, 166, 204));
            g.drawRoundRect(282, 604, 180, 34, 10, 10);
            g.setColor(Color.WHITE);
            drawCenteredTextInBox(g, text("返回", "BACK"), 282, 180, 626);

            if (settingsOpen) {
                drawSettingsPanel(g);
            }
        }

        private String setupAircraftSummary() {
            Aircraft[] aircraft = Aircraft.values();
            String playerName = playerAircraftConfirmed
                    ? aircraft[selectedAircraftIndex].displayName(chinese)
                    : text("随机", "Random");
            String aiName = aiAircraftConfirmed
                    ? aircraft[selectedAiAircraftIndex].displayName(chinese)
                    : text("随机", "Random");
            return "P1 " + playerName + " / AI " + aiName;
        }

        private String setupSelectionPrompt() {
            if (!playerAircraftConfirmed) {
                return text("选择己方战机。单击预选，再次点击或双击确认。", "Choose P1: click to preview, click again or double-click to confirm.");
            }
            if (!aiAircraftConfirmed) {
                return text("选择敌方战机。未确认则随机，右键撤销确认。", "Choose AI: unconfirmed means random. Right-click to undo.");
            }
            return text("战机已确认。右键按确认顺序撤销，Space 开始。", "Confirmed. Right-click undoes last, Space starts.");
        }

        private void drawHangar(Graphics2D g) {
            drawArenaBackdrop(g);
            drawSettingsButton(g);

            g.setFont(new Font("SansSerif", Font.BOLD, 34));
            g.setColor(new Color(238, 244, 255));
            drawCenteredText(g, text("机库", "HANGAR"), 72);

            int listX = 62;
            int listY = 116;
            int listW = 250;
            int rowH = 72;
            Aircraft[] aircraft = Aircraft.values();
            int visibleCount = hangarVisibleCount();
            hangarScrollIndex = clampScrollIndex(hangarScrollIndex, aircraft.length, visibleCount);
            g.setFont(new Font("SansSerif", Font.BOLD, 18));
            for (int slot = 0; slot < visibleCount; slot++) {
                int i = hangarScrollIndex + slot;
                Aircraft option = aircraft[i];
                int y = listY + slot * (rowH + 12);
                boolean selected = i == hangarAircraftIndex;
                g.setColor(selected ? new Color(34, 43, 60) : new Color(22, 27, 36));
                g.fillRoundRect(listX, y, listW, rowH, 10, 10);
                g.setColor(selected ? option.color : new Color(82, 92, 112));
                g.setStroke(new BasicStroke(selected ? 3f : 1.4f));
                g.drawRoundRect(listX, y, listW, rowH, 10, 10);
                drawAircraftSprite(g, option, listX + 42, y + 36, 0, 74, 56, "", false);
                g.setColor(Color.WHITE);
                g.setFont(new Font("SansSerif", Font.BOLD, 16));
                drawClippedString(g, option.displayName(chinese), listX + 84, y + 31, listW - 98);
                g.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g.setColor(new Color(190, 202, 220));
                String stats = attr("attack") + " " + option.attack + "  " + attr("defense") + " " + option.defense + "  " + attr("speed") + " " + option.speed;
                drawClippedString(g, stats, listX + 84, y + 55, listW - 98);
                g.setFont(new Font("SansSerif", Font.BOLD, 18));
            }
            if (aircraft.length > visibleCount) {
                drawScrollArrows(g, listX + listW - 32, listY + visibleCount * (rowH + 12) + 2,
                        hangarScrollIndex > 0, hangarScrollIndex + visibleCount < aircraft.length);
            }

            Aircraft selected = aircraft[hangarAircraftIndex];
            g.setColor(new Color(19, 24, 34));
            g.fillRoundRect(340, 116, 238, 430, 12, 12);
            g.setColor(selected.color);
            g.setStroke(new BasicStroke(2.4f));
            g.drawRoundRect(340, 116, 238, 430, 12, 12);
            drawLargeAircraftPreview(g, 459, 320, selected);

            g.setColor(new Color(19, 24, 34));
            g.fillRoundRect(606, 116, 292, 430, 12, 12);
            g.setColor(selected.color);
            g.drawRoundRect(606, 116, 292, 430, 12, 12);
            drawAircraftInfo(g, selected, 628, 154);

            g.setColor(new Color(56, 70, 92));
            g.fillRoundRect(390, 604, 180, 34, 10, 10);
            g.setColor(new Color(142, 166, 204));
            g.drawRoundRect(390, 604, 180, 34, 10, 10);
            g.setFont(new Font("SansSerif", Font.BOLD, 15));
            g.setColor(Color.WHITE);
            drawCenteredTextInBox(g, text("返回", "BACK"), 390, 180, 626);
            if (settingsOpen) {
                drawSettingsPanel(g);
            }
        }

        private void drawAircraftInfo(Graphics2D g, Aircraft aircraft, int x, int y) {
            g.setFont(new Font("SansSerif", Font.BOLD, 24));
            g.setColor(Color.WHITE);
            g.drawString(aircraft.displayName(chinese), x, y);

            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.setColor(aircraft.color);
            drawClippedString(g, aircraft.roleDescription(chinese), x, y + 27, 248);

            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(new Color(220, 228, 240));
            drawClippedString(g, attr("attack") + " " + aircraft.attack + "   "
                    + attr("defense") + " " + aircraft.defense + "   "
                    + attr("hp") + " " + aircraft.maxHp, x, y + 53, 248);
            drawClippedString(g, attr("speed") + " " + aircraft.speed + "   "
                    + attr("skill") + " x" + aircraft.skillBonus, x, y + 73, 248);

            int sectionY = y + 103;
            sectionY = drawAircraftInfoSection(g, text("普通攻击", "NORMAL ATTACK"), aircraft.normalDetails(chinese), x, sectionY, aircraft.color);
            sectionY = drawAircraftInfoSection(g, text("主动技能", "ACTIVE SKILL"), aircraft.skillDetails(chinese), x, sectionY + 4, aircraft.color);
            drawAircraftInfoSection(g, text("作战建议", "COMBAT TIPS"), aircraft.combatTips(chinese), x, sectionY + 4, aircraft.color);
        }

        private int drawAircraftInfoSection(Graphics2D g, String title, String[] lines, int x, int y, Color accent) {
            g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 75));
            g.fillRoundRect(x, y - 15, 248, 22, 6, 6);
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.setColor(new Color(236, 242, 252));
            g.drawString(title, x + 8, y);

            int lineY = y + 24;
            g.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g.setColor(new Color(198, 211, 229));
            for (String line : lines) {
                drawClippedString(g, line, x + 4, lineY, 240);
                lineY += 20;
            }
            return lineY;
        }

        private String attr(String key) {
            if (!chinese) {
                return switch (key) {
                    case "attack" -> "ATK";
                    case "defense" -> "DEF";
                    case "hp" -> "HP";
                    case "skill" -> "SKL";
                    case "speed" -> "SPD";
                    default -> key;
                };
            }
            return switch (key) {
                case "attack" -> "攻击";
                case "defense" -> "防御";
                case "hp" -> "血量";
                case "skill" -> "技能倍率";
                case "speed" -> "速度";
                default -> key;
            };
        }

        private void drawClippedString(Graphics2D g, String value, int x, int y, int maxWidth) {
            FontMetrics metrics = g.getFontMetrics();
            if (metrics.stringWidth(value) <= maxWidth) {
                g.drawString(value, x, y);
                return;
            }

            String ellipsis = "...";
            int end = value.length();
            while (end > 0 && metrics.stringWidth(value.substring(0, end) + ellipsis) > maxWidth) {
                end--;
            }
            g.drawString(value.substring(0, Math.max(0, end)) + ellipsis, x, y);
        }

        private void drawLargeAircraftPreview(Graphics2D g, double x, double y, Aircraft aircraft) {
            drawAircraftSprite(g, aircraft, x, y, 0, 210, 300, "", false);
        }

        private void drawLanguageToggle(Graphics2D g) {
            int x = WIDTH - 112;
            int y = 24;
            g.setColor(new Color(24, 30, 42));
            g.fillRoundRect(x, y, 76, 34, 10, 10);
            g.setColor(new Color(92, 205, 255));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(x, y, 76, 34, 10, 10);
            g.setFont(new Font("SansSerif", Font.BOLD, 15));
            g.setColor(Color.WHITE);
            g.drawString(chinese ? "中文" : "EN", x + 22, y + 23);

            if (settingsOpen) {
                g.setColor(new Color(18, 23, 32));
                g.fillRoundRect(x - 24, y + 42, 112, 72, 10, 10);
                g.setColor(new Color(92, 205, 255));
                g.drawRoundRect(x - 24, y + 42, 112, 72, 10, 10);

                drawLanguageOption(g, "中文", x - 12, y + 68, chinese);
                drawLanguageOption(g, "English", x - 12, y + 100, !chinese);
            }
        }

        private void drawLanguageOption(Graphics2D g, String label, int x, int y, boolean selected) {
            g.setFont(new Font("SansSerif", selected ? Font.BOLD : Font.PLAIN, 15));
            g.setColor(selected ? new Color(118, 226, 255) : new Color(220, 228, 240));
            g.drawString(label, x, y);
        }

        private void drawSettingsButton(Graphics2D g) {
            int x = SETTINGS_BUTTON_X;
            int y = SETTINGS_BUTTON_Y;
            g.setColor(settingsOpen ? new Color(34, 48, 66) : new Color(24, 30, 42));
            g.fillRoundRect(x, y, SETTINGS_BUTTON_SIZE, SETTINGS_BUTTON_SIZE, 10, 10);
            g.setColor(new Color(92, 205, 255));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(x, y, SETTINGS_BUTTON_SIZE, SETTINGS_BUTTON_SIZE, 10, 10);
            drawGear(g, x + SETTINGS_BUTTON_SIZE / 2, y + SETTINGS_BUTTON_SIZE / 2, 10);
        }

        private void drawGear(Graphics2D g, int cx, int cy, int radius) {
            int teeth = 8;
            int points = teeth * 2;
            int[] xs = new int[points];
            int[] ys = new int[points];
            for (int i = 0; i < points; i++) {
                double angle = -Math.PI / 2.0 + i * Math.PI / teeth;
                int currentRadius = i % 2 == 0 ? radius + 7 : radius + 2;
                xs[i] = (int) Math.round(cx + Math.cos(angle) * currentRadius);
                ys[i] = (int) Math.round(cy + Math.sin(angle) * currentRadius);
            }

            g.setColor(Color.WHITE);
            g.fillPolygon(xs, ys, points);
            g.setColor(new Color(92, 205, 255));
            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawPolygon(xs, ys, points);

            Color centerColor = settingsOpen ? new Color(34, 48, 66) : new Color(24, 30, 42);
            g.setColor(centerColor);
            g.fillOval(cx - radius + 2, cy - radius + 2, (radius - 2) * 2, (radius - 2) * 2);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawOval(cx - radius + 2, cy - radius + 2, (radius - 2) * 2, (radius - 2) * 2);
            g.fillOval(cx - 3, cy - 3, 6, 6);
        }

        private void drawSettingsPanel(Graphics2D g) {
            int x = 220;
            int y = 62;
            int w = 520;
            int h = 610;
            g.setColor(new Color(8, 12, 18, 218));
            g.fillRoundRect(x, y, w, h, 12, 12);
            g.setColor(new Color(92, 205, 255));
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(x, y, w, h, 12, 12);

            g.setFont(new Font("SansSerif", Font.BOLD, 26));
            g.setColor(Color.WHITE);
            g.drawString(text("设置", "Settings"), x + 24, y + 42);
            g.setFont(new Font("SansSerif", Font.BOLD, 18));
            g.setColor(new Color(180, 196, 220));
            g.drawString("X", x + w - 34, y + 36);

            g.setFont(new Font("SansSerif", Font.BOLD, 17));
            g.setColor(new Color(218, 228, 244));
            g.drawString(text("语言设置", "Language"), x + 28, y + 88);
            drawSettingsChoice(g, text("中文", "Chinese"), x + 142, y + 66, 96, 30, chinese);
            drawSettingsChoice(g, "English", x + 250, y + 66, 110, 30, !chinese);

            g.setColor(new Color(218, 228, 244));
            g.setFont(new Font("SansSerif", Font.BOLD, 17));
            g.drawString(text("音量", "Volume"), x + 28, y + 130);
            int barX = x + 142;
            int barY = y + 116;
            int barW = 250;
            g.setColor(new Color(38, 48, 64));
            g.fillRoundRect(barX, barY, barW, 12, 8, 8);
            g.setColor(new Color(92, 205, 255));
            g.fillRoundRect(barX, barY, (int) Math.round(barW * volume), 12, 8, 8);
            g.setColor(Color.WHITE);
            g.fillOval(barX + (int) Math.round(barW * volume) - 6, barY - 4, 20, 20);
            g.setFont(new Font("SansSerif", Font.PLAIN, 15));
            g.drawString((int) Math.round(volume * 100) + "%", x + 412, y + 130);

            g.setFont(new Font("SansSerif", Font.BOLD, 17));
            g.setColor(new Color(218, 228, 244));
            g.drawString(text("键位设置", "Key Bindings"), x + 28, y + 176);
            for (int i = 0; i < 11; i++) {
                drawControlRow(g, i, x + 28, y + 194 + i * 34, w - 56, 28);
            }

            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            g.setColor(new Color(166, 182, 204));
            g.drawString(text("点击任意键位后，按下新的按键完成修改。", "Click a binding, then press a new key."), x + 28, y + h - 24);
        }

        private void drawSettingsChoice(Graphics2D g, String label, int x, int y, int w, int h, boolean selected) {
            g.setColor(selected ? new Color(32, 130, 178) : new Color(30, 38, 52));
            g.fillRoundRect(x, y, w, h, 8, 8);
            g.setColor(selected ? new Color(118, 226, 255) : new Color(104, 120, 146));
            g.drawRoundRect(x, y, w, h, 8, 8);
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            g.setColor(Color.WHITE);
            drawCenteredTextInBox(g, label, x, w, y + 20);
        }

        private void drawControlRow(Graphics2D g, int index, int x, int y, int w, int h) {
            boolean rebinding = rebindingControlIndex == index;
            g.setColor(rebinding ? new Color(42, 60, 82) : new Color(22, 28, 39));
            g.fillRoundRect(x, y, w, h, 8, 8);
            g.setColor(rebinding ? new Color(118, 226, 255) : new Color(78, 92, 116));
            g.drawRoundRect(x, y, w, h, 8, 8);
            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            g.setColor(new Color(220, 228, 240));
            g.drawString(controlName(index), x + 12, y + 19);
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            g.setColor(rebinding ? new Color(118, 226, 255) : Color.WHITE);
            String value = rebinding ? text("按下新按键...", "Press a key...") : keyText(controlKey(index));
            drawClippedString(g, value, x + w - 170, y + 19, 150);
        }

        private String controlName(int index) {
            if (index == 7) {
                return text("攻击模式切换", "Attack Mode");
            }
            if (index == 8) {
                return text("攻击", "Attack");
            }
            if (index == 9) {
                return text("蓄力", "Charge");
            }
            if (index == 10) {
                return text("输入法锁定", "Input Method Lock");
            }
            return switch (index) {
                case 0 -> text("向上移动", "Move Up");
                case 1 -> text("向下移动", "Move Down");
                case 2 -> text("向左移动", "Move Left");
                case 3 -> text("向右移动", "Move Right");
                case 4 -> text("加速", "Boost");
                case 5 -> text("暂停", "Pause");
                case 6 -> text("返回设置", "Return Setup");
                case 7 -> text("蓝光模式切换", "Blue Mode");
                default -> "";
            };
        }

        private int controlKey(int index) {
            return switch (index) {
                case 0 -> keyUp;
                case 1 -> keyDown;
                case 2 -> keyLeft;
                case 3 -> keyRight;
                case 4 -> keyBoost;
                case 5 -> keyPause;
                case 6 -> keyRestart;
                case 7 -> keyBlueMode;
                case 8 -> keyAttack;
                case 9 -> keyCharge;
                case 10 -> keyInputMethodLock;
                default -> 0;
            };
        }

        private void setControlKey(int index, int key) {
            switch (index) {
                case 0 -> keyUp = key;
                case 1 -> keyDown = key;
                case 2 -> keyLeft = key;
                case 3 -> keyRight = key;
                case 4 -> keyBoost = key;
                case 5 -> keyPause = key;
                case 6 -> keyRestart = key;
                case 7 -> keyBlueMode = key;
                case 8 -> keyAttack = key;
                case 9 -> keyCharge = key;
                case 10 -> keyInputMethodLock = key;
                default -> {
                }
            }
        }

        private String keyText(int key) {
            if (key == INPUT_MOUSE_LEFT) {
                return text("鼠标左键", "Mouse Left");
            }
            if (key == INPUT_MOUSE_MIDDLE) {
                return text("鼠标中键", "Mouse Middle");
            }
            if (key == INPUT_MOUSE_RIGHT) {
                return text("鼠标右键", "Mouse Right");
            }
            String text = KeyEvent.getKeyText(key);
            return text == null || text.isBlank() ? "Key " + key : text;
        }

        private int drawMenuHeader(Graphics2D g, String title, String value, int x, int y, int width, boolean selected, boolean open) {
            Color accent = selected ? new Color(92, 205, 255) : new Color(86, 97, 116);
            g.setColor(new Color(24, 30, 42));
            g.fillRoundRect(x, y, width, 54, 10, 10);
            g.setColor(accent);
            g.setStroke(new BasicStroke(selected ? 2.8f : 1.4f));
            g.drawRoundRect(x, y, width, 54, 10, 10);

            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            g.setColor(Color.WHITE);
            g.drawString((open ? "v " : "> ") + title, x + 22, y + 34);

            g.setFont(new Font("SansSerif", Font.PLAIN, 18));
            g.setColor(new Color(202, 214, 232));
            drawClippedString(g, value, x + width - 260, y + 34, 238);
            return y + 66;
        }

        private int drawAircraftOptions(Graphics2D g, int x, int y, int width) {
            Aircraft[] aircraft = Aircraft.values();
            int visibleCount = Math.min(3, aircraft.length);
            aircraftScrollIndex = clampScrollIndex(aircraftScrollIndex, aircraft.length, visibleCount);
            int gap = 14;
            int arrowSpace = aircraft.length > visibleCount ? 34 : 0;
            int contentWidth = width - arrowSpace * 2;
            int cardW = (contentWidth - gap * (visibleCount - 1)) / visibleCount;
            int startX = x + arrowSpace;
            for (int slot = 0; slot < visibleCount; slot++) {
                int i = aircraftScrollIndex + slot;
                Aircraft option = aircraft[i];
                int cardX = startX + slot * (cardW + gap);
                boolean playerSelected = i == selectedAircraftIndex;
                boolean aiSelected = i == selectedAiAircraftIndex;
                g.setColor(new Color(22, 27, 36));
                g.fillRoundRect(cardX, y, cardW, 110, 10, 10);
                g.setColor(playerSelected || aiSelected ? option.color : new Color(80, 88, 104));
                g.setStroke(new BasicStroke(playerSelected || aiSelected ? 3f : 1.4f));
                g.drawRoundRect(cardX, y, cardW, 110, 10, 10);
                if (playerSelected) {
                    drawRoleBadge(g, "P1", cardX + 10, y + 10, new Color(92, 205, 255), playerAircraftConfirmed);
                }
                if (aiSelected) {
                    drawRoleBadge(g, "AI", cardX + (playerSelected ? 50 : 10), y + 10, new Color(255, 169, 74), aiAircraftConfirmed);
                }
                if (playerSelected && playerAircraftConfirmed) {
                    drawAircraftConfirmEffect(g, cardX, y, cardW, playerAircraftConfirmedAt, new Color(92, 205, 255));
                }
                if (aiSelected && aiAircraftConfirmed) {
                    drawAircraftConfirmEffect(g, cardX, y, cardW, aiAircraftConfirmedAt, new Color(255, 169, 74));
                }
                drawAircraftSprite(g, option, cardX + cardW / 2.0, y + 43, 0, cardW - 14, 62, "", false);
                g.setFont(new Font("SansSerif", Font.BOLD, 14));
                g.setColor(Color.WHITE);
                drawCenteredTextInBox(g, option.displayName(chinese), cardX, cardW, y + 82);
                g.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g.setColor(new Color(180, 192, 210));
                String roleText = playerSelected && aiSelected ? aircraftRoleText("P1", playerAircraftConfirmed) + " / " + aircraftRoleText("AI", aiAircraftConfirmed)
                        : playerSelected ? aircraftRoleText("P1", playerAircraftConfirmed)
                        : aiSelected ? aircraftRoleText("AI", aiAircraftConfirmed)
                        : text("可选", "Available");
                drawCenteredTextInBox(g, roleText, cardX, cardW, y + 103);
            }
            if (aircraft.length > visibleCount) {
                drawHorizontalScrollArrow(g, x + 5, y + 40, false, aircraftScrollIndex > 0);
                drawHorizontalScrollArrow(g, x + width - 29, y + 40, true,
                        aircraftScrollIndex + visibleCount < aircraft.length);
            }
            return y + 120;
        }

        private int clampScrollIndex(int index, int itemCount, int visibleCount) {
            return Math.max(0, Math.min(index, Math.max(0, itemCount - visibleCount)));
        }

        private int hangarVisibleCount() {
            return Math.min(5, Aircraft.values().length);
        }

        private void drawHorizontalScrollArrow(Graphics2D g, int x, int y, boolean right, boolean enabled) {
            g.setColor(enabled ? new Color(42, 55, 74) : new Color(28, 33, 42));
            g.fillRoundRect(x, y, 24, 32, 7, 7);
            g.setColor(enabled ? new Color(210, 224, 244) : new Color(82, 91, 105));
            int direction = right ? 1 : -1;
            int centerX = x + 12;
            int[] xs = {centerX - direction * 4, centerX + direction * 4, centerX - direction * 4};
            int[] ys = {y + 9, y + 16, y + 23};
            g.fillPolygon(xs, ys, 3);
        }

        private void drawScrollArrows(Graphics2D g, int x, int y, boolean upEnabled, boolean downEnabled) {
            drawVerticalScrollArrow(g, x - 28, y, false, upEnabled);
            drawVerticalScrollArrow(g, x, y, true, downEnabled);
        }

        private void drawVerticalScrollArrow(Graphics2D g, int x, int y, boolean down, boolean enabled) {
            g.setColor(enabled ? new Color(42, 55, 74) : new Color(28, 33, 42));
            g.fillRoundRect(x, y, 24, 24, 7, 7);
            g.setColor(enabled ? new Color(210, 224, 244) : new Color(82, 91, 105));
            int direction = down ? 1 : -1;
            int centerY = y + 12;
            int[] xs = {x + 6, x + 12, x + 18};
            int[] ys = {centerY - direction * 3, centerY + direction * 4, centerY - direction * 3};
            g.fillPolygon(xs, ys, 3);
        }

        private String aircraftRoleText(String role, boolean confirmed) {
            return confirmed ? role : role + "?";
        }

        private void drawRoleBadge(Graphics2D g, String label, int x, int y, Color color, boolean confirmed) {
            int alpha = confirmed ? 225 : 110;
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
            g.fillRoundRect(x, y, 34, 22, 8, 8);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            drawCenteredTextInBox(g, label, x, 34, y + 15);
        }

        private void drawAircraftConfirmEffect(Graphics2D g, int x, int y, int width, long confirmedAt, Color color) {
            long elapsed = System.currentTimeMillis() - confirmedAt;
            if (elapsed < 0 || elapsed > AIRCRAFT_CONFIRM_EFFECT_MS) {
                return;
            }
            double progress = elapsed / (double) AIRCRAFT_CONFIRM_EFFECT_MS;
            int expand = 2 + (int) Math.round(progress * 10);
            int alpha = (int) Math.round(210 * (1.0 - progress));
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, alpha)));
            g.setStroke(new BasicStroke(2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawRoundRect(x - expand, y - expand, width + expand * 2, 110 + expand * 2, 12 + expand, 12 + expand);
        }

        private int drawDifficultyOptions(Graphics2D g, int x, int y, int width) {
            Difficulty[] difficulties = Difficulty.values();
            Aircraft previewAircraft = selectedAiAircraftIndex >= 0 ? Aircraft.values()[selectedAiAircraftIndex] : null;
            int cardW = 160;
            int gap = 18;
            int startX = x + (width - cardW * difficulties.length - gap * 2) / 2;
            for (int i = 0; i < difficulties.length; i++) {
                Difficulty option = difficulties[i];
                int cardX = startX + i * (cardW + gap);
                boolean selected = i == selectedDifficultyIndex;
                Color accent = selected ? new Color(92, 205, 255) : new Color(86, 97, 116);
                g.setColor(new Color(22, 27, 36));
                g.fillRoundRect(cardX, y, cardW, 130, 10, 10);
                g.setColor(accent);
                g.setStroke(new BasicStroke(selected ? 3f : 1.4f));
                g.drawRoundRect(cardX, y, cardW, 130, 10, 10);
                g.setFont(new Font("SansSerif", Font.BOLD, 20));
                g.setColor(Color.WHITE);
                drawCenteredTextInBox(g, option.displayName(chinese), cardX, cardW, y + 34);
                g.setFont(new Font("SansSerif", Font.PLAIN, 13));
                g.setColor(new Color(177, 188, 205));
                if (previewAircraft == null) {
                    g.drawString("ATK x" + option.formatMultiplier(option.attackMultiplier), cardX + 22, y + 66);
                    g.drawString("SPD x" + option.formatMultiplier(option.speedMultiplier), cardX + 22, y + 90);
                    g.drawString("Fire x" + option.formatMultiplier(option.intervalMultiplier), cardX + 22, y + 114);
                } else {
                    int baseInterval = previewAircraft == Aircraft.TAIL_FLAME ? TAIL_FLAME_ATTACK_COOLDOWN
                            : previewAircraft == Aircraft.VENUS ? VENUS_ATTACK_COOLDOWN : NORMAL_ATTACK_COOLDOWN;
                    g.drawString("AI ATK " + option.adjustAttack(previewAircraft.attack), cardX + 22, y + 66);
                    g.drawString("AI SPD " + option.adjustSpeed(previewAircraft.speed), cardX + 22, y + 90);
                    g.drawString("Fire " + option.adjustAttackInterval(baseInterval) + " ms", cardX + 22, y + 114);
                }
            }
            return y + 140;
        }

        private void drawMapSwatch(Graphics2D g, BattleMap map, int x, int y, int width, int height) {
            Shape oldClip = g.getClip();
            g.setClip(x, y, width, height);
            drawBattleMap(g, map, x, y, width, height, false);
            g.setClip(oldClip);
            g.setColor(new Color(220, 230, 245, 150));
            g.setStroke(new BasicStroke(1.2f));
            g.drawRoundRect(x, y, width, height, 8, 8);
        }

        private void drawBattleMap(Graphics2D g, BattleMap map, int x, int y, int width, int height, boolean gameplay) {
            Shape oldClip = g.getClip();
            g.setClip(x, y, width, height);
            g.setColor(map.background);
            g.fillRect(x, y, width, height);
            BufferedImage image = mapImages.get(map);
            if (image != null) {
                drawCoverImage(g, image, x, y, width, height);
                if (gameplay) {
                    g.setColor(new Color(0, 0, 0, 74));
                    g.fillRect(x, y, width, height);
                } else {
                    g.setColor(new Color(255, 255, 255, 22));
                    g.fillRect(x, y, width, height);
                }
            } else {
                switch (map) {
                    case ORBITAL_DOCK -> drawOrbitalDockMap(g, x, y, width, height);
                    case NEBULA_RIFT -> drawNebulaRiftMap(g, x, y, width, height);
                    case CRYSTAL_FIELD -> drawCrystalFieldMap(g, x, y, width, height);
                    case SOLAR_RELIC -> drawSolarRelicMap(g, x, y, width, height);
                }
            }

            g.setClip(oldClip);
        }

        private void drawCoverImage(Graphics2D g, BufferedImage image, int x, int y, int width, int height) {
            double scale = Math.max(width / (double) image.getWidth(), height / (double) image.getHeight());
            int drawW = (int) Math.round(image.getWidth() * scale);
            int drawH = (int) Math.round(image.getHeight() * scale);
            int drawX = x + (width - drawW) / 2;
            int drawY = y + (height - drawH) / 2;
            g.drawImage(image, drawX, drawY, drawW, drawH, null);
        }

        private void drawOrbitalDockMap(Graphics2D g, int x, int y, int width, int height) {
            g.setColor(new Color(42, 58, 77));
            g.setStroke(new BasicStroke(2f));
            for (int gx = x - 30; gx < x + width + 60; gx += 58) {
                g.drawLine(gx, y, gx + 96, y + height);
            }
            for (int gy = y + 24; gy < y + height; gy += 54) {
                g.drawLine(x, gy, x + width, gy);
            }
            g.setColor(new Color(91, 124, 154, 100));
            g.fillRoundRect(x + width / 2 - 80, y + 28, 160, height - 56, 18, 18);
            g.setColor(new Color(118, 226, 255, 125));
            g.drawRoundRect(x + width / 2 - 80, y + 28, 160, height - 56, 18, 18);
        }

        private void drawNebulaRiftMap(Graphics2D g, int x, int y, int width, int height) {
            g.setPaint(new RadialGradientPaint(x + width * 0.62f, y + height * 0.45f, width * 0.7f,
                    new float[]{0f, 0.55f, 1f},
                    new Color[]{new Color(91, 44, 128, 165), new Color(31, 52, 90, 120), new Color(13, 18, 30, 0)}));
            g.fillRect(x, y, width, height);
            g.setColor(new Color(226, 127, 255, 120));
            g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawArc(x + width / 2 - 170, y + height / 2 - 85, 340, 170, 205, 145);
            g.setColor(new Color(110, 211, 255, 85));
            for (int i = 0; i < 24; i++) {
                int sx = x + (i * 67) % Math.max(1, width);
                int sy = y + (i * 41) % Math.max(1, height);
                g.fillOval(sx, sy, 2 + i % 3, 2 + i % 3);
            }
        }

        private void drawCrystalFieldMap(Graphics2D g, int x, int y, int width, int height) {
            g.setColor(new Color(65, 156, 180, 70));
            for (int i = 0; i < 9; i++) {
                int cx = x + 28 + (i * 73) % Math.max(1, width - 56);
                int cy = y + 22 + (i * 47) % Math.max(1, height - 44);
                int size = 18 + i % 4 * 8;
                int[] xs = {cx, cx + size / 2, cx, cx - size / 2};
                int[] ys = {cy - size, cy, cy + size, cy};
                g.fillPolygon(xs, ys, 4);
                g.setColor(new Color(150, 242, 255, 130));
                g.drawPolygon(xs, ys, 4);
                g.setColor(new Color(65, 156, 180, 70));
            }
        }

        private void drawSolarRelicMap(Graphics2D g, int x, int y, int width, int height) {
            g.setPaint(new RadialGradientPaint(x + width * 0.82f, y + height * 0.22f, width * 0.58f,
                    new float[]{0f, 0.45f, 1f},
                    new Color[]{new Color(255, 178, 58, 180), new Color(88, 48, 28, 80), new Color(18, 20, 26, 0)}));
            g.fillRect(x, y, width, height);
            g.setColor(new Color(255, 196, 77, 110));
            g.setStroke(new BasicStroke(2f));
            int cx = x + width / 2;
            int cy = y + height / 2;
            for (int r = 38; r < Math.min(width, height); r += 46) {
                g.drawOval(cx - r, cy - r, r * 2, r * 2);
            }
            g.setColor(new Color(120, 86, 54, 155));
            g.fillRoundRect(x + width / 2 - 100, y + height / 2 - 18, 200, 36, 12, 12);
        }

        private void drawArenaBackdrop(Graphics2D g) {
            g.setColor(new Color(18, 20, 26));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setColor(new Color(32, 43, 58));
            g.setStroke(new BasicStroke(1));
            for (int x = 40; x < WIDTH; x += 48) {
                g.drawLine(x, 0, x, HEIGHT);
            }
            for (int y = 36; y < HEIGHT; y += 48) {
                g.drawLine(0, y, WIDTH, y);
            }
        }

        private void drawTitle(Graphics2D g) {
            g.setFont(new Font("SansSerif", Font.BOLD, 26));
            g.setColor(new Color(238, 240, 245));
            drawCenteredText(g, fighterDisplayName(red) + " AI vs " + fighterDisplayName(blue) + " Player", 48);

            g.setFont(new Font("SansSerif", Font.PLAIN, 15));
            g.setColor(new Color(174, 183, 197));
            drawCenteredText(g, "Player: WASD move, aim with cursor, mouse fire, P or Space pause, R restart, F11 fullscreen", 76);
        }

        private void drawPauseButton(Graphics2D g) {
            g.setColor(new Color(28, 34, 46, 230));
            g.fillRoundRect(PAUSE_BUTTON_X, PAUSE_BUTTON_Y, PAUSE_BUTTON_SIZE, PAUSE_BUTTON_SIZE, 12, 12);
            g.setColor(new Color(118, 226, 255));
            g.setStroke(new BasicStroke(1.8f));
            g.drawRoundRect(PAUSE_BUTTON_X, PAUSE_BUTTON_Y, PAUSE_BUTTON_SIZE, PAUSE_BUTTON_SIZE, 12, 12);
            g.setColor(Color.WHITE);
            g.fillRoundRect(PAUSE_BUTTON_X + 13, PAUSE_BUTTON_Y + 10, 6, 22, 3, 3);
            g.fillRoundRect(PAUSE_BUTTON_X + 24, PAUSE_BUTTON_Y + 10, 6, 22, 3, 3);
        }

        private void drawBattleMapName(Graphics2D g) {
            String name = selectedMap().displayName(chinese);
            g.setFont(new Font("SansSerif", Font.BOLD, 16));
            FontMetrics metrics = g.getFontMetrics();
            int boxW = metrics.stringWidth(name) + 44;
            int x = (WIDTH - boxW) / 2;
            g.setColor(new Color(8, 12, 18, 132));
            g.fillRoundRect(x, 22, boxW, 30, 10, 10);
            g.setColor(new Color(224, 236, 252));
            drawCenteredTextInBox(g, name, x, boxW, 42);
        }

        private void drawArena(Graphics2D g) {
            drawArena(g, selectedMap());
        }

        private void drawArena(Graphics2D g, BattleMap map) {
            drawBattleMap(g, map, ARENA_LEFT, ARENA_TOP, ARENA_WIDTH, ARENA_HEIGHT, true);
        }

        private void drawAimLine(Graphics2D g) {
            if (gameOver || blueLaserWindupRemaining > 0 || blueLaserRemaining > 0) {
                return;
            }

            g.setColor(new Color(82, 155, 255, 95));
            g.setStroke(new BasicStroke(1.5f));
            g.drawLine((int) blue.x, (int) blue.y, mouseX, mouseY);
        }

        private void drawBlueLaser(Graphics2D g) {
            if (blueLaserWindupRemaining > 0 && !gameOver) {
                drawBlueLaserWindup(g);
            }
            if (blueLaserRemaining > 0 && !gameOver) {
                drawLaser(g, blue, laserPath(blue, mouseX, mouseY), blueLaserRemaining);
            }
            if (aiLaserRemaining > 0 && !gameOver) {
                drawLaser(g, red, laserPath(red, blue.x, blue.y), aiLaserRemaining);
            }
        }

        private void drawBlueLaserWindup(Graphics2D g) {
            double progress = 1.0 - blueLaserWindupRemaining / BLUE_LASER_WINDUP_DURATION;
            int pulseRadius = 24 + (int) Math.round(progress * 22);
            int alpha = 170 - (int) Math.round(progress * 70);

            g.setColor(new Color(65, 180, 255, 45));
            g.fillOval((int) blue.x - pulseRadius, (int) blue.y - pulseRadius, pulseRadius * 2, pulseRadius * 2);

            g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(112, 224, 255, alpha));
            g.drawOval((int) blue.x - pulseRadius, (int) blue.y - pulseRadius, pulseRadius * 2, pulseRadius * 2);

            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(235, 252, 255, 210));
            g.drawArc((int) blue.x - 34, (int) blue.y - 34, 68, 68, 90, (int) Math.round(-360 * progress));
        }

        private void drawLaser(Graphics2D g, Fighter shooter, LaserPath path, double remaining) {
            g.setColor(new Color(45, 168, 255, 70));
            g.setStroke(new BasicStroke(16f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            drawLaserPath(g, path);

            g.setColor(new Color(95, 215, 255, 150));
            g.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            drawLaserPath(g, path);

            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            drawLaserPath(g, path);

            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            g.setColor(new Color(195, 235, 255));
            g.drawString(String.format("LASER %.1fs", remaining), (int) shooter.x + 18, (int) shooter.y - 22);
        }

        private void drawLaserPath(Graphics2D g, LaserPath path) {
            g.drawLine((int) Math.round(path.startX), (int) Math.round(path.startY), (int) Math.round(path.pivotX), (int) Math.round(path.pivotY));
            if (path.deflected) {
                QuadCurve2D curve = new QuadCurve2D.Double(path.pivotX, path.pivotY, path.controlX, path.controlY, path.endX, path.endY);
                g.draw(curve);
            }
        }

        private double laserDistanceToArenaEdge(Fighter shooter, double nx, double ny) {
            return laserDistanceToArenaEdge(shooter.x, shooter.y, nx, ny);
        }

        private double laserDistanceToArenaEdge(double startX, double startY, double nx, double ny) {
            double maxDistance = Double.POSITIVE_INFINITY;
            if (nx > 0) {
                maxDistance = Math.min(maxDistance, (ARENA_LEFT + ARENA_WIDTH - startX) / nx);
            } else if (nx < 0) {
                maxDistance = Math.min(maxDistance, (ARENA_LEFT - startX) / nx);
            }

            if (ny > 0) {
                maxDistance = Math.min(maxDistance, (ARENA_TOP + ARENA_HEIGHT - startY) / ny);
            } else if (ny < 0) {
                maxDistance = Math.min(maxDistance, (ARENA_TOP - startY) / ny);
            }

            return Double.isFinite(maxDistance) ? Math.max(0, maxDistance) : ARENA_WIDTH;
        }

        private void drawProjectiles(Graphics2D g) {
            for (Projectile projectile : projectiles) {
                projectile.draw(g);
            }
        }

        private void drawFlameTrails(Graphics2D g) {
            for (FlameTrail trail : flameTrails) {
                trail.draw(g);
            }
        }

        private void drawShieldPickups(Graphics2D g) {
            for (ShieldPickup pickup : shieldPickups) {
                pickup.draw(g);
            }
        }

        private void drawSeraphZones(Graphics2D g) {
            long now = System.currentTimeMillis();
            for (SeraphZone zone : seraphZones) {
                double lifeRatio = Math.max(0, zone.remaining / SERAPH_ZONE_DURATION);
                double pulse = 0.5 + 0.5 * Math.sin(now / 180.0 + zone.x * 0.01);
                int radius = (int) Math.round(zone.radius);
                int alpha = (int) Math.round(34 + 18 * pulse);
                Color ownerColor = zone.fromBlue ? new Color(118, 226, 255) : new Color(255, 118, 136);
                g.setColor(new Color(255, 240, 246, alpha));
                g.fillOval((int) Math.round(zone.x - radius), (int) Math.round(zone.y - radius), radius * 2, radius * 2);
                g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(ownerColor.getRed(), ownerColor.getGreen(), ownerColor.getBlue(), (int) Math.round(95 + 70 * pulse)));
                g.drawOval((int) Math.round(zone.x - radius), (int) Math.round(zone.y - radius), radius * 2, radius * 2);
                g.setStroke(new BasicStroke(1.2f));
                int inner = (int) Math.round(radius * (0.55 + 0.07 * pulse));
                g.setColor(new Color(255, 255, 255, 72));
                g.drawOval((int) Math.round(zone.x - inner), (int) Math.round(zone.y - inner), inner * 2, inner * 2);
                drawSeraphHeartIcon(g, zone.x, zone.y, 52, 42, (int) Math.round(150 + 80 * lifeRatio));
            }
        }

        private void drawHeartPickups(Graphics2D g) {
            for (HeartPickup pickup : heartPickups) {
                pickup.draw(g, seraphHeartImage);
            }
        }

        private void drawSeraphHeartIcon(Graphics2D g, double x, double y, int maxWidth, int maxHeight, int alpha) {
            if (seraphHeartImage == null) {
                drawWingedHeartIcon(g, x, y, Math.min(maxWidth / 45.0, maxHeight / 28.0), alpha);
                return;
            }
            double scale = Math.min(maxWidth / (double) seraphHeartImage.getWidth(), maxHeight / (double) seraphHeartImage.getHeight());
            int drawWidth = Math.max(1, (int) Math.round(seraphHeartImage.getWidth() * scale));
            int drawHeight = Math.max(1, (int) Math.round(seraphHeartImage.getHeight() * scale));
            Graphics2D icon = (Graphics2D) g.create();
            icon.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, Math.max(0, Math.min(255, alpha)) / 255f));
            icon.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            icon.drawImage(seraphHeartImage, (int) Math.round(x - drawWidth / 2.0), (int) Math.round(y - drawHeight / 2.0), drawWidth, drawHeight, null);
            icon.dispose();
        }

        private static void drawWingedHeartIcon(Graphics2D g, double x, double y, double scale, int alpha) {
            Graphics2D icon = (Graphics2D) g.create();
            icon.translate(x, y);
            icon.scale(scale, scale);
            int safeAlpha = Math.max(0, Math.min(255, alpha));
            icon.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            icon.setColor(new Color(255, 255, 255, Math.min(210, safeAlpha)));
            icon.drawArc(-31, -9, 30, 20, 20, 135);
            icon.drawArc(1, -9, 30, 20, 25, 135);
            icon.drawLine(-13, -3, -25, 4);
            icon.drawLine(13, -3, 25, 4);
            icon.setColor(new Color(255, 94, 126, Math.min(230, safeAlpha)));
            icon.fillOval(-11, -10, 13, 13);
            icon.fillOval(-2, -10, 13, 13);
            int[] heartX = {-11, 11, 0};
            int[] heartY = {-3, -3, 13};
            icon.fillPolygon(heartX, heartY, 3);
            icon.setColor(new Color(255, 238, 244, Math.min(245, safeAlpha)));
            icon.drawOval(-11, -10, 13, 13);
            icon.drawOval(-2, -10, 13, 13);
            icon.drawLine(-11, -2, 0, 13);
            icon.drawLine(11, -2, 0, 13);
            icon.dispose();
        }

        private void drawFloatingTexts(Graphics2D g) {
            for (FloatingText floatingText : floatingTexts) {
                floatingText.draw(g);
            }
        }

        private void drawFighterAvatar(Graphics2D g, Fighter fighter, Color mainColor) {
            double targetX = titleDemoMode ? (fighter == blue ? red.x : blue.x) : (fighter == blue ? mouseX : blue.x);
            double targetY = titleDemoMode ? (fighter == blue ? red.y : blue.y) : (fighter == blue ? mouseY : blue.y);
            double angle = Math.atan2(targetY - fighter.y, targetX - fighter.x);
            drawActiveArmorEffects(g, fighter);
            if (aircraftFor(fighter) == Aircraft.VENUS && fighter.venusGateRemaining > 0) {
                drawVenusStarGate(g, fighter, angle);
            }
            drawVenusShards(g, fighter);
            drawAircraftSprite(g, aircraftFor(fighter), fighter.x, fighter.y, angle, 98, 76,
                    fighter == blue ? "P" : "AI", fighter.boosting);
            if (aircraftFor(fighter) == Aircraft.VENUS && fighter.venusGateRemaining > 0) {
                drawVenusArmorAssembly(g, fighter, angle);
            } else if (venusArmorActive(fighter)) {
                drawVenusReinforcedArmor(g, fighter, angle);
            }
        }

        private void drawVenusShards(Graphics2D g, Fighter fighter) {
            if (aircraftFor(fighter) != Aircraft.VENUS || fighter.venusShardCount <= 0) {
                return;
            }
            long now = System.currentTimeMillis();
            Graphics2D shard = (Graphics2D) g.create();
            shard.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < fighter.venusShardCount; i++) {
                double angle = now / 420.0 + i * Math.PI * 2.0 / VENUS_MAX_SHARDS;
                int sx = (int) Math.round(fighter.x + Math.cos(angle) * 34);
                int sy = (int) Math.round(fighter.y + Math.sin(angle) * 28);
                int[] xs = {sx, sx + 6, sx, sx - 6};
                int[] ys = {sy - 10, sy, sy + 10, sy};
                shard.setColor(new Color(255, 195, 58, 90));
                shard.fillOval(sx - 14, sy - 14, 28, 28);
                shard.setColor(new Color(255, 219, 102, 230));
                shard.fillPolygon(xs, ys, 4);
                shard.setColor(Color.WHITE);
                shard.drawPolygon(xs, ys, 4);
            }
            shard.dispose();
        }

        private void drawVenusStarGate(Graphics2D g, Fighter fighter, double angle) {
            double progress = 1.0 - fighter.venusGateRemaining / VENUS_GATE_DURATION;
            double gateX = fighter.x - Math.cos(angle) * 48;
            double gateY = fighter.y - Math.sin(angle) * 48;
            int radius = 20 + (int) Math.round(Math.sin(progress * Math.PI) * 18);
            Graphics2D gate = (Graphics2D) g.create();
            gate.translate(gateX, gateY);
            gate.rotate(angle + progress * Math.PI * 2.0);
            gate.setColor(new Color(255, 205, 74, 60));
            gate.fillOval(-radius, -radius, radius * 2, radius * 2);
            gate.setColor(new Color(255, 226, 126, 225));
            gate.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            gate.drawArc(-radius, -radius, radius * 2, radius * 2, 10, 125);
            gate.drawArc(-radius, -radius, radius * 2, radius * 2, 190, 125);
            gate.setColor(Color.WHITE);
            gate.setStroke(new BasicStroke(1.4f));
            gate.drawOval(-radius / 2, -radius / 2, radius, radius);
            gate.dispose();
        }

        private void drawVenusReinforcedArmor(Graphics2D g, Fighter fighter, double angle) {
            Graphics2D armor = (Graphics2D) g.create();
            armor.translate(fighter.x, fighter.y);
            armor.rotate(angle);
            armor.setColor(new Color(255, 199, 57, 54));
            armor.fillOval(-42, -38, 84, 76);
            drawVenusArmorPiece(armor, 5, -25, 0, 1.0, 235);
            drawVenusArmorPiece(armor, 1, -9, -18, 1.0, 238);
            drawVenusArmorPiece(armor, 2, -9, 18, 1.0, 238);
            drawVenusArmorPiece(armor, 0, 1, 0, 1.0, 245);
            drawVenusArmorPiece(armor, 3, 4, -25, 1.0, 245);
            drawVenusArmorPiece(armor, 4, 4, 25, 1.0, 245);
            drawVenusArmorPiece(armor, 6, 22, 0, 1.0, 220);
            armor.dispose();
        }

        private void drawVenusArmorAssembly(Graphics2D g, Fighter fighter, double angle) {
            double progress = 1.0 - fighter.venusGateRemaining / VENUS_GATE_DURATION;
            Graphics2D armor = (Graphics2D) g.create();
            armor.translate(fighter.x, fighter.y);
            armor.rotate(angle);
            double[][] pieces = {
                    {5, -25, 0, 0.00},
                    {1, -9, -18, 0.10},
                    {2, -9, 18, 0.10},
                    {0, 1, 0, 0.22},
                    {3, 4, -25, 0.38},
                    {4, 4, 25, 0.38},
                    {6, 22, 0, 0.55}
            };
            for (double[] piece : pieces) {
                int type = (int) piece[0];
                double targetX = piece[1];
                double targetY = piece[2];
                double local = clamp((progress - piece[3]) / 0.45, 0, 1);
                double eased = local * local * (3.0 - 2.0 * local);
                double startX = -82 - type * 3.0;
                double startY = targetY * 1.22;
                double px = startX + (targetX - startX) * eased;
                double py = startY + (targetY - startY) * eased;
                int alpha = (int) Math.round(80 + 165 * local);
                armor.setColor(new Color(255, 210, 74, Math.max(0, Math.min(160, alpha - 40))));
                armor.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                armor.drawLine((int) Math.round(startX), (int) Math.round(startY), (int) Math.round(px), (int) Math.round(py));
                drawVenusArmorPiece(armor, type, px, py, 0.65 + 0.35 * local, alpha);
            }
            armor.dispose();
        }

        private void drawVenusArmorPiece(Graphics2D g, int type, double x, double y, double scale, int alpha) {
            Graphics2D piece = (Graphics2D) g.create();
            piece.translate(x, y);
            piece.scale(scale, scale);
            Color white = new Color(246, 239, 218, alpha);
            Color gold = new Color(255, 188, 45, Math.min(255, alpha + 8));
            Color dark = new Color(22, 24, 26, Math.min(230, alpha));
            Color glow = new Color(255, 144, 24, Math.min(220, alpha));
            Color outline = new Color(255, 246, 202, Math.min(255, alpha));
            piece.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (type) {
                case 0 -> {
                    piece.setColor(dark);
                    piece.fillRoundRect(-17, -9, 42, 18, 9, 9);
                    piece.setColor(white);
                    piece.fillRoundRect(-13, -7, 34, 14, 8, 8);
                    piece.setColor(gold);
                    piece.fillRoundRect(-5, -5, 19, 10, 6, 6);
                    piece.setColor(glow);
                    piece.drawLine(1, 0, 18, 0);
                    piece.setColor(outline);
                    piece.drawRoundRect(-17, -9, 42, 18, 9, 9);
                }
                case 1, 2 -> {
                    int mirror = type == 1 ? -1 : 1;
                    int[] outerX = {-18, -5, 23, 15, -8};
                    int[] outerY = {2 * mirror, -18 * mirror, -14 * mirror, -4 * mirror, 7 * mirror};
                    piece.setColor(dark);
                    piece.fillPolygon(outerX, outerY, outerX.length);
                    int[] plateX = {-13, -2, 16, 10, -5};
                    int[] plateY = {1 * mirror, -13 * mirror, -10 * mirror, -3 * mirror, 5 * mirror};
                    piece.setColor(white);
                    piece.fillPolygon(plateX, plateY, plateX.length);
                    int[] goldX = {-6, 2, 12, 7, -2};
                    int[] goldY = {0, -9 * mirror, -7 * mirror, -2 * mirror, 3 * mirror};
                    piece.setColor(gold);
                    piece.fillPolygon(goldX, goldY, goldX.length);
                    piece.setColor(outline);
                    piece.drawPolygon(outerX, outerY, outerX.length);
                }
                case 3, 4 -> {
                    piece.setColor(dark);
                    piece.fillRoundRect(-13, -5, 31, 10, 6, 6);
                    piece.setColor(white);
                    piece.fillRoundRect(-8, -4, 20, 8, 5, 5);
                    piece.setColor(gold);
                    piece.fillRoundRect(8, -3, 17, 6, 4, 4);
                    piece.setColor(glow);
                    piece.fillOval(24, -4, 8, 8);
                    piece.setColor(outline);
                    piece.drawRoundRect(-13, -5, 31, 10, 6, 6);
                }
                case 5 -> {
                    int[] outerX = {-16, -5, 12, 11, -4};
                    int[] outerY = {0, -12, -7, 7, 12};
                    piece.setColor(dark);
                    piece.fillPolygon(outerX, outerY, outerX.length);
                    int[] plateX = {-10, -2, 7, 7, -2};
                    int[] plateY = {0, -8, -4, 4, 8};
                    piece.setColor(white);
                    piece.fillPolygon(plateX, plateY, plateX.length);
                    piece.setColor(glow);
                    piece.drawLine(-1, -6, 7, 0);
                    piece.drawLine(-1, 6, 7, 0);
                    piece.setColor(outline);
                    piece.drawPolygon(outerX, outerY, outerX.length);
                }
                case 6 -> {
                    int[] outerX = {-9, 15, 27, 12, -6};
                    int[] outerY = {-8, -5, 0, 5, 8};
                    piece.setColor(white);
                    piece.fillPolygon(outerX, outerY, outerX.length);
                    piece.setColor(gold);
                    int[] innerX = {1, 16, 22, 13, 2};
                    int[] innerY = {-4, -3, 0, 3, 4};
                    piece.fillPolygon(innerX, innerY, innerX.length);
                    piece.setColor(outline);
                    piece.drawPolygon(outerX, outerY, outerX.length);
                }
                default -> {
                }
            }
            piece.dispose();
        }

        private void drawActiveArmorEffects(Graphics2D g, Fighter fighter) {
            int layer = 0;
            for (ArmorType armorType : fighter.activeArmors.keySet()) {
                int radius = 28 + layer * 6;
                int x = (int) Math.round(fighter.x) - radius;
                int y = (int) Math.round(fighter.y) - radius;
                int size = radius * 2;
                g.setColor(new Color(armorType.color.getRed(), armorType.color.getGreen(), armorType.color.getBlue(), 42));
                g.fillOval(x, y, size, size);
                g.setColor(new Color(armorType.color.getRed(), armorType.color.getGreen(), armorType.color.getBlue(), 150));
                g.setStroke(new BasicStroke(2.2f + layer * 0.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                if (armorType == ArmorType.LIGHT) {
                    g.drawArc(x, y, size, size, 25, 115);
                    g.drawArc(x, y, size, size, 205, 115);
                } else if (armorType == ArmorType.COMPOSITE) {
                    g.drawArc(x, y, size, size, 0, 65);
                    g.drawArc(x, y, size, size, 120, 65);
                    g.drawArc(x, y, size, size, 240, 65);
                } else {
                    g.drawOval(x, y, size, size);
                }
                layer++;
            }
        }

        private void drawFutureJet(Graphics2D g, double x, double y, double angle, Color mainColor, String label) {
            drawFutureJet(g, x, y, angle, mainColor, label, false);
        }

        private void drawAircraftSprite(Graphics2D g, Aircraft aircraft, double x, double y, double angle,
                                        int maxWidth, int maxHeight, String label, boolean boosting) {
            BufferedImage image = aircraftSprites.get(aircraft);
            if (image == null) {
                if (aircraft == Aircraft.SIX_WINGED_ANGEL) {
                    drawSixWingedAngelJet(g, x, y, angle, label, boosting);
                    return;
                }
                drawFutureJet(g, x, y, angle, aircraft.color, label, boosting);
                return;
            }

            boolean noseUpSource = aircraft == Aircraft.SIX_WINGED_ANGEL;
            double scale = noseUpSource
                    ? Math.min(maxWidth / (double) image.getHeight(), maxHeight / (double) image.getWidth())
                    : Math.min(maxWidth / (double) image.getWidth(), maxHeight / (double) image.getHeight());
            int drawWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
            int drawHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
            Graphics2D jet = (Graphics2D) g.create();
            jet.translate(x, y);
            jet.rotate(noseUpSource ? angle + Math.PI / 2.0 : angle);
            jet.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

            if (boosting) {
                if (noseUpSource) {
                    drawSeraphImageBoostFlames(jet, drawWidth, drawHeight);
                } else {
                    drawAircraftBoostFlames(jet, aircraft, drawWidth, drawHeight);
                }
            }

            jet.drawImage(image, -drawWidth / 2, -drawHeight / 2, drawWidth, drawHeight, null);
            jet.dispose();

            if (!label.isEmpty()) {
                g.setFont(new Font("SansSerif", Font.BOLD, 12));
                g.setColor(Color.WHITE);
                FontMetrics metrics = g.getFontMetrics();
                g.drawString(label, (int) (x - metrics.stringWidth(label) / 2.0),
                        (int) y + maxHeight / 2 + 14);
            }
        }

        private void drawAircraftBoostFlames(Graphics2D jet, Aircraft aircraft, int drawWidth, int drawHeight) {
            int rearX = -drawWidth / 2 + Math.max(3, drawWidth / 16);
            switch (aircraft) {
                case TAIL_FLAME -> {
                    drawThrusterFlame(jet, rearX, 0, drawWidth / 2.4, drawHeight / 4.4,
                            new Color(255, 105, 34, 150), new Color(255, 244, 196, 235));
                    drawThrusterFlame(jet, rearX + drawWidth / 12, -drawHeight * 0.22, drawWidth / 3.6, drawHeight / 9.5,
                            new Color(255, 169, 74, 115), new Color(255, 244, 210, 190));
                    drawThrusterFlame(jet, rearX + drawWidth / 12, drawHeight * 0.22, drawWidth / 3.6, drawHeight / 9.5,
                            new Color(255, 169, 74, 115), new Color(255, 244, 210, 190));
                }
                case BLUE_GLOW -> {
                    drawThrusterFlame(jet, rearX + drawWidth / 12, -drawHeight * 0.19, drawWidth / 3.8, drawHeight / 10.5,
                            new Color(62, 176, 255, 145), new Color(225, 250, 255, 225));
                    drawThrusterFlame(jet, rearX + drawWidth / 12, drawHeight * 0.19, drawWidth / 3.8, drawHeight / 10.5,
                            new Color(62, 176, 255, 145), new Color(225, 250, 255, 225));
                }
                case NEUTRON_STAR -> {
                    drawThrusterFlame(jet, rearX + drawWidth / 18, 0, drawWidth / 3.2, drawHeight / 8.8,
                            new Color(166, 94, 255, 128), new Color(244, 226, 255, 210));
                    jet.setColor(new Color(196, 150, 255, 95));
                    int ringR = Math.max(8, drawHeight / 8);
                    jet.drawOval(rearX - ringR, -ringR, ringR * 2, ringR * 2);
                }
                case VENUS -> {
                    drawThrusterFlame(jet, rearX + drawWidth / 10, -drawHeight * 0.16, drawWidth / 3.6, drawHeight / 11.0,
                            new Color(255, 190, 58, 138), new Color(255, 249, 203, 220));
                    drawThrusterFlame(jet, rearX + drawWidth / 10, drawHeight * 0.16, drawWidth / 3.6, drawHeight / 11.0,
                            new Color(255, 190, 58, 138), new Color(255, 249, 203, 220));
                    drawThrusterFlame(jet, rearX, 0, drawWidth / 4.4, drawHeight / 12.0,
                            new Color(255, 142, 38, 110), new Color(255, 244, 196, 185));
                }
                case SIX_WINGED_ANGEL -> {
                    drawThrusterFlame(jet, rearX + drawWidth / 9, -drawHeight * 0.18, drawWidth / 4.0, drawHeight / 12.0,
                            new Color(255, 136, 164, 112), new Color(255, 255, 255, 220));
                    drawThrusterFlame(jet, rearX + drawWidth / 9, drawHeight * 0.18, drawWidth / 4.0, drawHeight / 12.0,
                            new Color(255, 136, 164, 112), new Color(255, 255, 255, 220));
                    drawThrusterFlame(jet, rearX, 0, drawWidth / 4.8, drawHeight / 14.0,
                            new Color(255, 214, 224, 92), new Color(255, 255, 255, 185));
                }
            }
        }

        private void drawSeraphImageBoostFlames(Graphics2D jet, int drawWidth, int drawHeight) {
            double rearY = drawHeight / 2.0 - Math.max(4, drawHeight / 18.0);
            drawVerticalThrusterFlame(jet, -drawWidth * 0.12, rearY, drawHeight / 3.2, drawWidth / 28.0,
                    new Color(255, 136, 190, 118), new Color(255, 255, 255, 215));
            drawVerticalThrusterFlame(jet, drawWidth * 0.12, rearY, drawHeight / 3.2, drawWidth / 28.0,
                    new Color(255, 136, 190, 118), new Color(255, 255, 255, 215));
            drawVerticalThrusterFlame(jet, 0, rearY + drawHeight * 0.02, drawHeight / 2.8, drawWidth / 24.0,
                    new Color(255, 172, 210, 98), new Color(255, 255, 255, 185));
        }

        private void drawVerticalThrusterFlame(Graphics2D jet, double x, double y, double length, double halfWidth, Color outer, Color core) {
            int[] flameX = {(int) Math.round(x - halfWidth), (int) Math.round(x), (int) Math.round(x + halfWidth)};
            int[] flameY = {(int) Math.round(y), (int) Math.round(y + length), (int) Math.round(y)};
            jet.setColor(outer);
            jet.fillPolygon(flameX, flameY, 3);
            int coreHalfWidth = Math.max(2, (int) Math.round(halfWidth * 0.45));
            int[] coreX = {(int) Math.round(x - coreHalfWidth), (int) Math.round(x), (int) Math.round(x + coreHalfWidth)};
            int[] coreY = {(int) Math.round(y), (int) Math.round(y + length * 0.62), (int) Math.round(y)};
            jet.setColor(core);
            jet.fillPolygon(coreX, coreY, 3);
        }

        private void drawThrusterFlame(Graphics2D jet, double x, double y, double length, double halfHeight, Color outer, Color core) {
            int[] flameX = {(int) Math.round(x), (int) Math.round(x - length), (int) Math.round(x)};
            int[] flameY = {(int) Math.round(y - halfHeight), (int) Math.round(y), (int) Math.round(y + halfHeight)};
            jet.setColor(outer);
            jet.fillPolygon(flameX, flameY, 3);
            int[] coreX = {(int) Math.round(x), (int) Math.round(x - length * 0.62), (int) Math.round(x)};
            int coreHalfHeight = Math.max(2, (int) Math.round(halfHeight * 0.42));
            int[] coreY = {(int) Math.round(y - coreHalfHeight), (int) Math.round(y), (int) Math.round(y + coreHalfHeight)};
            jet.setColor(core);
            jet.fillPolygon(coreX, coreY, 3);
        }

        private void drawSixWingedAngelJet(Graphics2D g, double x, double y, double angle, String label, boolean boosting) {
            Graphics2D jet = (Graphics2D) g.create();
            jet.translate(x, y);
            jet.rotate(angle);
            if (boosting) {
                drawThrusterFlame(jet, -30, -13, 28, 5, new Color(255, 137, 165, 120), new Color(255, 255, 255, 215));
                drawThrusterFlame(jet, -30, 13, 28, 5, new Color(255, 137, 165, 120), new Color(255, 255, 255, 215));
                drawThrusterFlame(jet, -34, 0, 22, 4, new Color(255, 218, 228, 90), new Color(255, 255, 255, 180));
            }

            jet.setColor(new Color(255, 255, 255, 44));
            jet.fillOval(-43, -36, 86, 72);
            drawAngelWing(jet, -3, -8, -1);
            drawAngelWing(jet, -3, 8, 1);
            drawAngelWing(jet, -17, -16, -1);
            drawAngelWing(jet, -17, 16, 1);
            drawAngelWing(jet, -30, -22, -1);
            drawAngelWing(jet, -30, 22, 1);

            int[] hullX = {32, 13, -15, -35, -18, -35, -15, 13};
            int[] hullY = {0, -8, -10, -5, 0, 5, 10, 8};
            jet.setColor(new Color(246, 250, 255, 235));
            jet.fillPolygon(hullX, hullY, hullX.length);
            jet.setColor(new Color(255, 104, 130, 218));
            jet.fillRoundRect(-4, -4, 14, 8, 6, 6);
            jet.fillOval(13, -3, 8, 6);
            jet.setColor(new Color(145, 38, 58, 180));
            jet.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            jet.drawLine(-16, 0, 24, 0);
            jet.setColor(new Color(255, 255, 255, 230));
            jet.drawPolygon(hullX, hullY, hullX.length);
            jet.dispose();

            if (!label.isEmpty()) {
                g.setFont(new Font("SansSerif", Font.BOLD, 12));
                g.setColor(Color.WHITE);
                FontMetrics metrics = g.getFontMetrics();
                g.drawString(label, (int) (x - metrics.stringWidth(label) / 2.0), (int) y + 28);
            }
        }

        private void drawAngelWing(Graphics2D jet, int x, int y, int side) {
            int[] wingX = {x + 21, x - 3, x - 25, x - 7};
            int[] wingY = {y, y + side * 14, y + side * 26, y + side * 5};
            jet.setColor(new Color(247, 252, 255, 178));
            jet.fillPolygon(wingX, wingY, wingX.length);
            jet.setColor(new Color(255, 119, 145, 135));
            jet.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            jet.drawLine(x + 2, y + side * 9, x - 17, y + side * 21);
            jet.setColor(new Color(255, 255, 255, 218));
            jet.drawPolygon(wingX, wingY, wingX.length);
        }

        private void drawFutureJet(Graphics2D g, double x, double y, double angle, Color mainColor, String label, boolean boosting) {
            Graphics2D jet = (Graphics2D) g.create();
            jet.translate(x, y);
            jet.rotate(angle);

            jet.setColor(new Color(mainColor.getRed(), mainColor.getGreen(), mainColor.getBlue(), 45));
            jet.fillOval(-36, -30, 72, 60);

            int[] leftWingX = {4, -18, -30, -8};
            int[] leftWingY = {-7, -15, -30, -21};
            int[] rightWingX = {4, -18, -30, -8};
            int[] rightWingY = {7, 15, 30, 21};
            jet.setColor(new Color(mainColor.getRed(), mainColor.getGreen(), mainColor.getBlue(), 185));
            jet.fillPolygon(leftWingX, leftWingY, leftWingX.length);
            jet.fillPolygon(rightWingX, rightWingY, rightWingX.length);

            int[] hullX = {28, 10, -15, -26, -12, -26, -15, 10};
            int[] hullY = {0, -9, -8, -18, -5, 5, 8, 9};
            jet.setColor(new Color(mainColor.getRed(), mainColor.getGreen(), mainColor.getBlue(), 220));
            jet.fillPolygon(hullX, hullY, hullX.length);
            jet.setColor(Color.WHITE);
            jet.setStroke(new BasicStroke(1.6f));
            jet.drawPolygon(leftWingX, leftWingY, leftWingX.length);
            jet.drawPolygon(rightWingX, rightWingY, rightWingX.length);
            jet.drawPolygon(hullX, hullY, hullX.length);

            jet.setColor(new Color(20, 26, 36));
            jet.fillRoundRect(-6, -5, 16, 10, 8, 8);
            jet.setColor(new Color(168, 235, 255));
            jet.drawLine(2, 0, 18, 0);

            jet.setColor(new Color(mainColor.getRed(), mainColor.getGreen(), mainColor.getBlue(), 130));
            jet.fillOval(-27, -5, 13, 10);
            jet.fillRoundRect(-11, -23, 10, 5, 4, 4);
            jet.fillRoundRect(-11, 18, 10, 5, 4, 4);
            jet.setColor(new Color(255, 255, 255, 170));
            jet.fillOval(-30, -3, 8, 6);

            if (boosting) {
                int[] flameX = {-27, -58, -31};
                int[] flameY = {-8, 0, 8};
                jet.setColor(new Color(mainColor.getRed(), mainColor.getGreen(), mainColor.getBlue(), 120));
                jet.fillPolygon(flameX, flameY, flameX.length);
                int[] coreX = {-28, -46, -31};
                int[] coreY = {-4, 0, 4};
                jet.setColor(new Color(255, 245, 230, 205));
                jet.fillPolygon(coreX, coreY, coreX.length);
            }

            jet.dispose();

            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.setColor(Color.WHITE);
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(label, (int) (x - metrics.stringWidth(label) / 2.0), (int) y + 28);
        }

        private void drawFighter(Graphics2D g, Fighter fighter, int x, int y, Color mainColor) {
            int panelW = 238;
            g.setColor(new Color(8, 12, 18, 150));
            g.fillRoundRect(x, y, panelW, 104, 10, 10);

            g.setFont(new Font("SansSerif", Font.BOLD, 16));
            g.setColor(Color.WHITE);
            drawClippedString(g, fighterDisplayName(fighter), x + 12, y + 24, 132);
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            g.setColor(mainColor);
            String controller = titleDemoMode ? "AI" : fighter == blue ? text("玩家", "Player") : text("人机", "AI");
            drawClippedString(g, controller, x + 152, y + 24, 72);

            drawBar(g, "HP", fighter.hp, fighter.maxHp, x + 12, y + 43, 214, new Color(71, 196, 117));
            drawBar(g, "Skill", fighter.charge, MAX_CHARGE, x + 12, y + 74, 98, new Color(244, 196, 77));
            drawBar(g, "Boost", (int) Math.round(fighter.boostEnergy * 10), (int) Math.round(BOOST_MAX_ENERGY * 10),
                    x + 128, y + 74, 98, new Color(86, 202, 255));

            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(new Color(223, 228, 237));
            g.drawString("ATK " + effectiveAttack(fighter), x + 12, y + 100);
            g.drawString("DEF " + effectiveDefense(fighter), x + 70, y + 100);
            g.drawString("SPD " + effectiveSpeed(fighter), x + 128, y + 100);
            g.drawString("SKL " + fighter.skillBonus, x + 184, y + 100);
            if (fighter == blue && playerAircraft == Aircraft.BLUE_GLOW) {
                g.setFont(new Font("SansSerif", Font.PLAIN, 10));
                g.setColor(new Color(190, 207, 226));
                g.drawString("Mode " + (blueBurstMode ? "Burst" : "Single") + " (C)", x + 128, y + 55);
            } else if (aircraftFor(fighter) == Aircraft.VENUS && fighter.venusGateRemaining > 0) {
                g.setFont(new Font("SansSerif", Font.PLAIN, 10));
                g.setColor(new Color(255, 214, 96));
                g.drawString(String.format("Assembling %.1fs", fighter.venusGateRemaining), x + 128, y + 55);
            } else if (venusArmorActive(fighter)) {
                g.setFont(new Font("SansSerif", Font.PLAIN, 10));
                g.setColor(new Color(255, 214, 96));
                g.drawString(String.format("Armor %d  %.1fs", fighter.venusArmorHp, fighter.venusArmorRemaining), x + 128, y + 55);
            } else if (fighter.seraphSpeedBuffRemaining > 0) {
                g.setFont(new Font("SansSerif", Font.PLAIN, 10));
                g.setColor(new Color(255, 232, 240));
                g.drawString("Seraph field +SPD", x + 128, y + 55);
            } else if (fighter.seraphSpeedDebuffRemaining > 0 || fighter.seraphDefenseDebuffRemaining > 0) {
                g.setFont(new Font("SansSerif", Font.PLAIN, 10));
                g.setColor(new Color(255, 154, 174));
                g.drawString("Seraph field -DEF/SPD", x + 128, y + 55);
            }
        }

        private void drawBar(Graphics2D g, String label, int value, int maxValue, int x, int y, int width, Color color) {
            int height = 12;
            double ratio = maxValue == 0 ? 0 : (double) value / maxValue;
            int filledWidth = (int) Math.round(width * ratio);

            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            g.setColor(new Color(210, 216, 226));
            g.drawString(label + " " + value + "/" + maxValue, x, y - 4);

            g.setColor(new Color(17, 23, 32, 175));
            g.fillRoundRect(x, y, width, height, 5, 5);
            g.setColor(color);
            g.fillRoundRect(x, y, filledWidth, height, 5, 5);
        }

        private void drawMessage(Graphics2D g) {
            g.setColor(new Color(8, 12, 18, 135));
            g.fillRoundRect(300, 676, 360, 28, 8, 8);
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.setColor(new Color(238, 240, 245));
            drawCenteredTextInBox(g, message, 300, 360, 695);
        }

        private void drawPausedOverlay(Graphics2D g) {
            g.setColor(new Color(0, 0, 0, 145));
            g.fillRect(0, 0, WIDTH, HEIGHT);

            g.setColor(new Color(18, 24, 34, 238));
            g.fillRoundRect(PAUSE_MENU_X, PAUSE_MENU_Y, PAUSE_MENU_WIDTH, PAUSE_MENU_HEIGHT, 16, 16);
            g.setColor(new Color(90, 210, 255));
            g.setStroke(new BasicStroke(2.4f));
            g.drawRoundRect(PAUSE_MENU_X, PAUSE_MENU_Y, PAUSE_MENU_WIDTH, PAUSE_MENU_HEIGHT, 16, 16);

            g.setFont(new Font("SansSerif", Font.BOLD, 30));
            g.setColor(Color.WHITE);
            drawCenteredTextInBox(g, text("暂停", "PAUSED"), PAUSE_MENU_X, PAUSE_MENU_WIDTH, PAUSE_MENU_Y + 52);

            drawPauseMenuButton(g, PAUSE_MENU_CONTINUE_Y, text("继续游戏", "CONTINUE"));
            drawPauseMenuButton(g, PAUSE_MENU_HOME_Y, text("返回主界面", "MAIN MENU"));
        }

        private void drawPauseMenuButton(Graphics2D g, int y, String label) {
            g.setColor(new Color(35, 48, 65));
            g.fillRoundRect(PAUSE_MENU_BUTTON_X, y, PAUSE_MENU_BUTTON_WIDTH, PAUSE_MENU_BUTTON_HEIGHT, 10, 10);
            g.setColor(new Color(118, 226, 255));
            g.setStroke(new BasicStroke(1.6f));
            g.drawRoundRect(PAUSE_MENU_BUTTON_X, y, PAUSE_MENU_BUTTON_WIDTH, PAUSE_MENU_BUTTON_HEIGHT, 10, 10);
            g.setFont(new Font("SansSerif", Font.BOLD, 15));
            g.setColor(Color.WHITE);
            drawCenteredTextInBox(g, label, PAUSE_MENU_BUTTON_X, PAUSE_MENU_BUTTON_WIDTH, y + 24);
        }

        private void drawExplosion(Graphics2D g) {
            double age = System.currentTimeMillis() - explosionStartedAt;
            double progress = Math.min(1.0, age / 1200.0);
            int outer = (int) (28 + progress * 120);
            int inner = (int) (16 + progress * 52);
            int alpha = (int) (220 * (1.0 - progress));

            g.setColor(new Color(255, 92, 42, Math.max(0, alpha)));
            g.fillOval((int) explosionX - outer / 2, (int) explosionY - outer / 2, outer, outer);
            g.setColor(new Color(255, 216, 92, Math.max(0, alpha)));
            g.fillOval((int) explosionX - inner / 2, (int) explosionY - inner / 2, inner, inner);

            g.setColor(new Color(255, 255, 255, Math.max(0, alpha)));
            g.setStroke(new BasicStroke(2f));
            for (int i = 0; i < 12; i++) {
                double angle = i * Math.PI * 2 / 12.0;
                int x1 = (int) Math.round(explosionX + Math.cos(angle) * inner * 0.6);
                int y1 = (int) Math.round(explosionY + Math.sin(angle) * inner * 0.6);
                int x2 = (int) Math.round(explosionX + Math.cos(angle) * outer * 0.7);
                int y2 = (int) Math.round(explosionY + Math.sin(angle) * outer * 0.7);
                g.drawLine(x1, y1, x2, y2);
            }
        }

        private void drawResultOverlay(Graphics2D g) {
            Color accent = playerWon ? new Color(80, 220, 255) : new Color(255, 86, 82);
            String title = playerWon ? "VICTORY" : "DEFEAT";
            String subtitle = playerWon ? "TARGET NEUTRALIZED" : "SYSTEM FAILURE";

            g.setColor(new Color(0, 0, 0, 175));
            g.fillRect(0, 0, WIDTH, HEIGHT);

            int centerX = WIDTH / 2;
            int centerY = 274;
            int[] xs = {centerX, centerX + 140, centerX + 176, centerX + 140, centerX, centerX - 140, centerX - 176, centerX - 140};
            int[] ys = {centerY - 92, centerY - 60, centerY, centerY + 60, centerY + 92, centerY + 60, centerY, centerY - 60};

            g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 35));
            g.fillPolygon(xs, ys, xs.length);
            g.setColor(accent);
            g.setStroke(new BasicStroke(3f));
            g.drawPolygon(xs, ys, xs.length);
            g.setStroke(new BasicStroke(1.4f));
            g.drawOval(centerX - 112, centerY - 112, 224, 224);
            g.drawOval(centerX - 78, centerY - 78, 156, 156);
            g.drawLine(centerX - 210, centerY, centerX - 132, centerY);
            g.drawLine(centerX + 132, centerY, centerX + 210, centerY);

            g.setFont(new Font("SansSerif", Font.BOLD, 50));
            g.setColor(Color.WHITE);
            drawCenteredText(g, title, centerY + 8);

            g.setFont(new Font("SansSerif", Font.BOLD, 18));
            g.setColor(accent);
            drawCenteredText(g, subtitle, centerY + 42);

            g.setFont(new Font("SansSerif", Font.PLAIN, 18));
            g.setColor(new Color(221, 231, 245));
            drawCenteredText(g, "Press R to return to battle setup", 492);
        }

        private void drawCenteredText(Graphics2D g, String text, int y) {
            FontMetrics metrics = g.getFontMetrics();
            int x = (WIDTH - metrics.stringWidth(text)) / 2;
            g.drawString(text, x, y);
        }

        private void drawCenteredTextInBox(Graphics2D g, String text, int x, int width, int y) {
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(text, x + (width - metrics.stringWidth(text)) / 2, y);
        }

        private String text(String zh, String en) {
            return chinese ? zh : en;
        }

        private void setInputMethodLocked(boolean locked) {
            inputMethodLocked = locked;
            if (locked) {
                switchToEnglishInputMethod();
            }
            enableInputMethods(!locked);
        }

        private void switchToEnglishInputMethod() {
            if (getInputContext() != null) {
                getInputContext().selectInputMethod(Locale.ENGLISH);
            }
        }

        private void toggleInputMethodLock() {
            setInputMethodLocked(!inputMethodLocked);
            message = inputMethodLocked
                    ? text("输入法已锁定。", "Input method locked.")
                    : text("输入法已解锁。", "Input method unlocked.");
            playUiSound();
            requestFocusInWindow();
            repaint();
        }

        private void performAttackInput(double targetX, double targetY) {
            if (playerAircraft == Aircraft.BLUE_GLOW) {
                if (blueBurstMode) {
                    if (canPlayerFireNormal()) {
                        leftMouseDown = true;
                        blueBurstQueued = true;
                    }
                } else {
                    long now = System.currentTimeMillis();
                    if (now >= nextPlayerNormalAttackTime && canPlayerFireNormal()) {
                        blueBurstMultiplier = BLUE_INITIAL_BURST_MULTIPLIER;
                        firePlayerBlueGlowLasers(false);
                        nextPlayerNormalAttackTime = now + BLUE_SINGLE_SHOT_COOLDOWN;
                    }
                }
            } else if (playerAircraft == Aircraft.NEUTRON_STAR) {
                leftMouseDown = false;
                long now = System.currentTimeMillis();
                if (now >= nextPlayerNormalAttackTime) {
                    fireNeutronStarShot(blue, red, targetX, targetY, true);
                    nextPlayerNormalAttackTime = now + NORMAL_ATTACK_COOLDOWN;
                }
            } else if (playerAircraft == Aircraft.VENUS) {
                leftMouseDown = false;
                long now = System.currentTimeMillis();
                if (now >= nextPlayerNormalAttackTime) {
                    boolean enhanced = venusArmorActive(blue);
                    fireVenusShot(blue, red, targetX, targetY, true);
                    nextPlayerNormalAttackTime = now + (enhanced ? VENUS_ENHANCED_ATTACK_COOLDOWN : VENUS_ATTACK_COOLDOWN);
                }
            } else if (playerAircraft == Aircraft.SIX_WINGED_ANGEL) {
                leftMouseDown = false;
                long now = System.currentTimeMillis();
                if (now >= nextPlayerNormalAttackTime) {
                    fireSeraphShot(blue, red, targetX, targetY, true);
                    nextPlayerNormalAttackTime = now + NORMAL_ATTACK_COOLDOWN;
                }
            } else {
                leftMouseDown = false;
                long now = System.currentTimeMillis();
                if (now >= nextPlayerNormalAttackTime) {
                    fireTailFlameShot(blue, red, targetX, targetY, true);
                    nextPlayerNormalAttackTime = now + TAIL_FLAME_ATTACK_COOLDOWN;
                }
            }
        }

        private void releaseAttackInput() {
            leftMouseDown = false;
            if (!blueBurstMode) {
                blueBurstMultiplier = BLUE_INITIAL_BURST_MULTIPLIER;
            }
        }

        private int mouseInputCode(MouseEvent event) {
            return switch (event.getButton()) {
                case MouseEvent.BUTTON1 -> INPUT_MOUSE_LEFT;
                case MouseEvent.BUTTON2 -> INPUT_MOUSE_MIDDLE;
                case MouseEvent.BUTTON3 -> INPUT_MOUSE_RIGHT;
                default -> 0;
            };
        }

        private boolean inputMatchesMouse(int input, MouseEvent event) {
            return input < 0 && input == mouseInputCode(event);
        }

        private void leaveTitleScreen() {
            clearTitleDemoBattle();
            titleScreen = false;
            choosingDifficulty = true;
            showingHangar = false;
            showingMapSelection = false;
            settingsOpen = false;
            aircraftMenuOpen = true;
            difficultyMenuOpen = false;
            selectedMenuSection = 0;
            playUiSound();
            repaint();
        }

        private final class BattleMouseListener extends MouseAdapter {
            @Override
            public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
                if (titleScreen) {
                    leaveTitleScreen();
                    return;
                }
                if (settingsOpen && rebindingControlIndex >= 0) {
                    int input = mouseInputCode(event);
                    if (input != 0) {
                        setControlKey(rebindingControlIndex, input);
                        playUiSound();
                    }
                    rebindingControlIndex = -1;
                    repaint();
                    return;
                }
                if (choosingDifficulty) {
                    handleSetupClick(toGameX(event), toGameY(event), event.getClickCount(), SwingUtilities.isRightMouseButton(event));
                    return;
                }

                if (gameOver) {
                    return;
                }
                mouseX = toGameX(event);
                mouseY = toGameY(event);
                if (paused || inRect(mouseX, mouseY, PAUSE_BUTTON_X, PAUSE_BUTTON_Y, PAUSE_BUTTON_SIZE, PAUSE_BUTTON_SIZE)) {
                    handlePauseClick(mouseX, mouseY);
                    return;
                }

                boolean consumed = false;
                if (inputMatchesMouse(keyCharge, event)) {
                    chargeInputDown = true;
                    consumed = true;
                }
                if (inputMatchesMouse(keyAttack, event)) {
                    performAttackInput(mouseX, mouseY);
                    consumed = true;
                }
                if (!consumed && SwingUtilities.isRightMouseButton(event)) {
                    playerSkillAttack(mouseX, mouseY);
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                draggingVolume = false;
                if (inputMatchesMouse(keyCharge, event)) {
                    chargeInputDown = false;
                }
                if (inputMatchesMouse(keyAttack, event)) {
                    releaseAttackInput();
                }
            }
        }

        private void handleSettingsClick(int mouseX, int mouseY) {
            int x = 220;
            int y = 62;
            int w = 520;
            int h = 610;
            if (mouseX >= x + w - 44 && mouseX <= x + w - 18 && mouseY >= y + 16 && mouseY <= y + 44) {
                settingsOpen = false;
                rebindingControlIndex = -1;
                return;
            }

            if (mouseY >= y + 66 && mouseY <= y + 96) {
                if (mouseX >= x + 142 && mouseX <= x + 238) {
                    chinese = true;
                    return;
                }
                if (mouseX >= x + 250 && mouseX <= x + 360) {
                    chinese = false;
                    return;
                }
            }

            int barX = x + 142;
            int barY = y + 110;
            int barW = 250;
            if (mouseX >= barX && mouseX <= barX + barW && mouseY >= barY && mouseY <= barY + 28) {
                setVolumeFromMouse(mouseX, barX, barW);
                draggingVolume = true;
                playUiSound();
                return;
            }

            int rowX = x + 28;
            int rowY = y + 194;
            int rowW = w - 56;
            for (int i = 0; i < 11; i++) {
                int currentY = rowY + i * 34;
                if (mouseX >= rowX && mouseX <= rowX + rowW && mouseY >= currentY && mouseY <= currentY + 28) {
                    rebindingControlIndex = i;
                    return;
                }
            }

            if (mouseX < x || mouseX > x + w || mouseY < y || mouseY > y + h) {
                rebindingControlIndex = -1;
            }
        }

        private void setVolumeFromMouse(int mouseX, int barX, int barW) {
            volume = clamp((mouseX - barX) / (double) barW, 0, 1);
            audio.setVolume(volume);
        }

        private void handleSetupClick(int x, int y, int clickCount, boolean rightClick) {
            if (rightClick) {
                undoLastAircraftConfirmation();
                repaint();
                return;
            }

            if (x >= SETTINGS_BUTTON_X && x <= SETTINGS_BUTTON_X + SETTINGS_BUTTON_SIZE
                    && y >= SETTINGS_BUTTON_Y && y <= SETTINGS_BUTTON_Y + SETTINGS_BUTTON_SIZE) {
                settingsOpen = !settingsOpen;
                rebindingControlIndex = -1;
                repaint();
                return;
            }

            if (settingsOpen) {
                handleSettingsClick(x, y);
                repaint();
                return;
            }

            if (showingHangar) {
                handleHangarClick(x, y);
                return;
            }

            if (showingMapSelection) {
                handleMapSelectionClick(x, y);
                return;
            }

            if (x >= SETUP_PANEL_X && x <= SETUP_PANEL_X + SETUP_PANEL_WIDTH && y >= 132 && y <= 186) {
                selectedMenuSection = 0;
                aircraftMenuOpen = !aircraftMenuOpen;
                difficultyMenuOpen = false;
                repaint();
                return;
            }

            int difficultyHeaderY = 132 + 66 + (aircraftMenuOpen ? 136 : 0);
            if (x >= SETUP_PANEL_X && x <= SETUP_PANEL_X + SETUP_PANEL_WIDTH && y >= difficultyHeaderY && y <= difficultyHeaderY + 54) {
                selectedMenuSection = 1;
                difficultyMenuOpen = !difficultyMenuOpen;
                aircraftMenuOpen = false;
                repaint();
                return;
            }

            int mapHeaderY = difficultyHeaderY + 66 + (difficultyMenuOpen ? 156 : 0);
            if (x >= SETUP_PANEL_X && x <= SETUP_PANEL_X + SETUP_PANEL_WIDTH && y >= mapHeaderY && y <= mapHeaderY + 54) {
                selectedMenuSection = 2;
                aircraftMenuOpen = false;
                difficultyMenuOpen = false;
                showingMapSelection = true;
                previewMapIndex = selectedMapIndex;
                repaint();
                return;
            }

            if (aircraftMenuOpen && y >= 198 && y <= 308) {
                Aircraft[] aircraft = Aircraft.values();
                int visibleCount = Math.min(3, aircraft.length);
                int gap = 14;
                int arrowSpace = aircraft.length > visibleCount ? 34 : 0;
                int contentWidth = SETUP_PANEL_WIDTH - arrowSpace * 2;
                int cardW = (contentWidth - gap * (visibleCount - 1)) / visibleCount;
                int startX = SETUP_PANEL_X + arrowSpace;
                if (aircraft.length > visibleCount && x >= SETUP_PANEL_X + 5 && x <= SETUP_PANEL_X + 29) {
                    aircraftScrollIndex = clampScrollIndex(aircraftScrollIndex - 1, aircraft.length, visibleCount);
                    repaint();
                    return;
                }
                if (aircraft.length > visibleCount && x >= SETUP_PANEL_X + SETUP_PANEL_WIDTH - 29
                        && x <= SETUP_PANEL_X + SETUP_PANEL_WIDTH - 5) {
                    aircraftScrollIndex = clampScrollIndex(aircraftScrollIndex + 1, aircraft.length, visibleCount);
                    repaint();
                    return;
                }
                for (int slot = 0; slot < visibleCount; slot++) {
                    int i = aircraftScrollIndex + slot;
                    int cardX = startX + slot * (cardW + gap);
                    if (x >= cardX && x <= cardX + cardW) {
                        selectSetupAircraft(i, clickCount >= 2);
                        selectedMenuSection = 0;
                        repaint();
                        return;
                    }
                }
            }

            int difficultyCardsY = difficultyHeaderY + 66;
            if (difficultyMenuOpen && y >= difficultyCardsY && y <= difficultyCardsY + 130) {
                Difficulty[] difficulties = Difficulty.values();
                int cardW = 160;
                int gap = 18;
                int startX = SETUP_PANEL_X + (SETUP_PANEL_WIDTH - cardW * difficulties.length - gap * 2) / 2;
                for (int i = 0; i < difficulties.length; i++) {
                    int cardX = startX + i * (cardW + gap);
                    if (x >= cardX && x <= cardX + cardW) {
                        selectedDifficultyIndex = i;
                        selectedMenuSection = 1;
                        repaint();
                        return;
                    }
                }
            }

            if (x >= START_BUTTON_X && x <= START_BUTTON_X + START_BUTTON_WIDTH && y >= 604 && y <= 636) {
                startBattle(Difficulty.values()[selectedDifficultyIndex]);
                return;
            }

            if (x >= HANGAR_BUTTON_X && x <= HANGAR_BUTTON_X + HANGAR_BUTTON_WIDTH && y >= 646 && y <= 676) {
                showingHangar = true;
                showingMapSelection = false;
                hangarAircraftIndex = selectedAircraftIndex >= 0 ? selectedAircraftIndex : 0;
                ensureHangarAircraftVisible();
                repaint();
                return;
            }

        }

        private void handleMapSelectionClick(int x, int y) {
            if (x >= 282 && x <= 462 && y >= 604 && y <= 638) {
                showingMapSelection = false;
                repaint();
                return;
            }
            if (x >= 502 && x <= 682 && y >= 604 && y <= 638) {
                selectedMapIndex = previewMapIndex;
                showingMapSelection = false;
                playUiSound();
                repaint();
                return;
            }

            BattleMap[] maps = BattleMap.values();
            int listX = 62;
            int listY = 116;
            int listW = 250;
            int rowH = 70;
            for (int i = 0; i < maps.length; i++) {
                int rowY = listY + i * (rowH + 10);
                if (x >= listX && x <= listX + listW && y >= rowY && y <= rowY + rowH) {
                    previewMapIndex = i;
                    repaint();
                    return;
                }
            }
        }

        private void selectSetupAircraft(int aircraftIndex, boolean doubleClick) {
            if (!playerAircraftConfirmed) {
                boolean confirm = doubleClick || selectedAircraftIndex == aircraftIndex;
                selectedAircraftIndex = aircraftIndex;
                if (confirm) {
                    confirmSetupAircraft(AIRCRAFT_ROLE_PLAYER);
                }
                return;
            }

            if (!aiAircraftConfirmed) {
                boolean confirm = doubleClick || selectedAiAircraftIndex == aircraftIndex;
                selectedAiAircraftIndex = aircraftIndex;
                if (confirm) {
                    confirmSetupAircraft(AIRCRAFT_ROLE_AI);
                }
            }
        }

        private void ensureAircraftVisible(int aircraftIndex) {
            int visibleCount = Math.min(3, Aircraft.values().length);
            if (aircraftIndex < aircraftScrollIndex) {
                aircraftScrollIndex = aircraftIndex;
            } else if (aircraftIndex >= aircraftScrollIndex + visibleCount) {
                aircraftScrollIndex = aircraftIndex - visibleCount + 1;
            }
            aircraftScrollIndex = clampScrollIndex(aircraftScrollIndex, Aircraft.values().length, visibleCount);
        }

        private void ensureHangarAircraftVisible() {
            int visibleCount = hangarVisibleCount();
            if (hangarAircraftIndex < hangarScrollIndex) {
                hangarScrollIndex = hangarAircraftIndex;
            } else if (hangarAircraftIndex >= hangarScrollIndex + visibleCount) {
                hangarScrollIndex = hangarAircraftIndex - visibleCount + 1;
            }
            hangarScrollIndex = clampScrollIndex(hangarScrollIndex, Aircraft.values().length, visibleCount);
        }

        private void confirmSetupAircraft(int role) {
            if (role == AIRCRAFT_ROLE_PLAYER && selectedAircraftIndex >= 0 && !playerAircraftConfirmed) {
                playerAircraftConfirmed = true;
                playerAircraftConfirmedAt = System.currentTimeMillis();
                aircraftConfirmationOrder.add(AIRCRAFT_ROLE_PLAYER);
                playUiSound();
            } else if (role == AIRCRAFT_ROLE_AI && selectedAiAircraftIndex >= 0 && !aiAircraftConfirmed) {
                aiAircraftConfirmed = true;
                aiAircraftConfirmedAt = System.currentTimeMillis();
                aircraftConfirmationOrder.add(AIRCRAFT_ROLE_AI);
                playUiSound();
            }
        }

        private void undoLastAircraftConfirmation() {
            if (aircraftConfirmationOrder.isEmpty()) {
                return;
            }

            int role = aircraftConfirmationOrder.remove(aircraftConfirmationOrder.size() - 1);
            if (role == AIRCRAFT_ROLE_AI) {
                aiAircraftConfirmed = false;
                aiAircraftConfirmedAt = 0;
            } else {
                playerAircraftConfirmed = false;
                playerAircraftConfirmedAt = 0;
            }
            playUiSound();
        }

        private void handleHangarClick(int x, int y) {
            if (x >= 390 && x <= 570 && y >= 604 && y <= 638) {
                showingHangar = false;
                repaint();
                return;
            }

            Aircraft[] aircraft = Aircraft.values();
            int listX = 62;
            int listY = 116;
            int listW = 250;
            int rowH = 72;
            int visibleCount = hangarVisibleCount();
            int arrowY = listY + visibleCount * (rowH + 12) + 2;
            if (aircraft.length > visibleCount && y >= arrowY && y <= arrowY + 24) {
                if (x >= listX + listW - 60 && x <= listX + listW - 36) {
                    hangarScrollIndex = clampScrollIndex(hangarScrollIndex - 1, aircraft.length, visibleCount);
                } else if (x >= listX + listW - 32 && x <= listX + listW - 8) {
                    hangarScrollIndex = clampScrollIndex(hangarScrollIndex + 1, aircraft.length, visibleCount);
                }
                repaint();
                return;
            }
            for (int slot = 0; slot < visibleCount; slot++) {
                int i = hangarScrollIndex + slot;
                int rowY = listY + slot * (rowH + 12);
                if (x >= listX && x <= listX + listW && y >= rowY && y <= rowY + rowH) {
                    hangarAircraftIndex = i;
                    repaint();
                    return;
                }
            }
        }

        private final class BattleMouseMotionListener extends MouseMotionAdapter {
            @Override
            public void mouseMoved(MouseEvent event) {
                mouseX = toGameX(event);
                mouseY = toGameY(event);
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                mouseX = toGameX(event);
                mouseY = toGameY(event);
                if (settingsOpen && draggingVolume) {
                    setVolumeFromMouse(mouseX, 220 + 142, 250);
                    repaint();
                }
            }
        }

        private final class BattleMouseWheelListener implements MouseWheelListener {
            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
                if (!choosingDifficulty || settingsOpen) {
                    return;
                }
                int direction = Integer.signum(event.getWheelRotation());
                if (showingMapSelection) {
                    BattleMap[] maps = BattleMap.values();
                    previewMapIndex = (previewMapIndex + direction + maps.length) % maps.length;
                } else if (showingHangar) {
                    hangarScrollIndex = clampScrollIndex(hangarScrollIndex + direction,
                            Aircraft.values().length, hangarVisibleCount());
                } else if (aircraftMenuOpen) {
                    aircraftScrollIndex = clampScrollIndex(aircraftScrollIndex + direction,
                            Aircraft.values().length, Math.min(3, Aircraft.values().length));
                } else {
                    return;
                }
                repaint();
            }
        }

        private final class BattleKeyListener extends KeyAdapter {
            @Override
            public void keyPressed(KeyEvent event) {
                int key = event.getKeyCode();
                if (key == keyFullScreen) {
                    toggleFullScreen();
                    return;
                }
                if (key == keyInputMethodLock && rebindingControlIndex < 0) {
                    toggleInputMethodLock();
                    return;
                }

                if (titleScreen) {
                    leaveTitleScreen();
                    return;
                }

                if (settingsOpen) {
                    if (rebindingControlIndex >= 0) {
                        if (key != KeyEvent.VK_ESCAPE) {
                            setControlKey(rebindingControlIndex, key);
                            playUiSound();
                        }
                        rebindingControlIndex = -1;
                    } else if (key == KeyEvent.VK_ESCAPE) {
                        settingsOpen = false;
                    }
                    repaint();
                    return;
                }

                if (choosingDifficulty) {
                    Difficulty[] difficulties = Difficulty.values();
                    Aircraft[] aircraft = Aircraft.values();
                    if (showingMapSelection) {
                        BattleMap[] maps = BattleMap.values();
                        if (key == KeyEvent.VK_ESCAPE) {
                            showingMapSelection = false;
                        } else if (key == KeyEvent.VK_ENTER || key == KeyEvent.VK_SPACE) {
                            selectedMapIndex = previewMapIndex;
                            showingMapSelection = false;
                            playUiSound();
                        } else if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W || key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) {
                            previewMapIndex = (previewMapIndex - 1 + maps.length) % maps.length;
                        } else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S || key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) {
                            previewMapIndex = (previewMapIndex + 1) % maps.length;
                        }
                        repaint();
                        return;
                    }
                    if (showingHangar) {
                        if (key == KeyEvent.VK_ESCAPE || key == KeyEvent.VK_ENTER) {
                            showingHangar = false;
                        } else if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W || key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) {
                            hangarAircraftIndex = (hangarAircraftIndex - 1 + aircraft.length) % aircraft.length;
                            ensureHangarAircraftVisible();
                        } else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S || key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) {
                            hangarAircraftIndex = (hangarAircraftIndex + 1) % aircraft.length;
                            ensureHangarAircraftVisible();
                        }
                        repaint();
                        return;
                    }
                    if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) {
                        selectedMenuSection = (selectedMenuSection + 2) % 3;
                        aircraftMenuOpen = selectedMenuSection == 0;
                        difficultyMenuOpen = selectedMenuSection == 1;
                        repaint();
                    } else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) {
                        selectedMenuSection = (selectedMenuSection + 1) % 3;
                        aircraftMenuOpen = selectedMenuSection == 0;
                        difficultyMenuOpen = selectedMenuSection == 1;
                        repaint();
                    } else if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) {
                        if (selectedMenuSection == 0) {
                            selectedAircraftIndex = selectedAircraftIndex < 0 ? 0 : (selectedAircraftIndex - 1 + aircraft.length) % aircraft.length;
                            ensureAircraftVisible(selectedAircraftIndex);
                        } else if (selectedMenuSection == 1) {
                            selectedDifficultyIndex = (selectedDifficultyIndex - 1 + difficulties.length) % difficulties.length;
                        } else {
                            selectedMapIndex = (selectedMapIndex - 1 + BattleMap.values().length) % BattleMap.values().length;
                        }
                        repaint();
                    } else if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) {
                        if (selectedMenuSection == 0) {
                            selectedAircraftIndex = selectedAircraftIndex < 0 ? 0 : (selectedAircraftIndex + 1) % aircraft.length;
                            ensureAircraftVisible(selectedAircraftIndex);
                        } else if (selectedMenuSection == 1) {
                            selectedDifficultyIndex = (selectedDifficultyIndex + 1) % difficulties.length;
                        } else {
                            selectedMapIndex = (selectedMapIndex + 1) % BattleMap.values().length;
                        }
                        repaint();
                    } else if (key == KeyEvent.VK_1) {
                        if (selectedMenuSection == 0) {
                            selectedAircraftIndex = 0;
                            ensureAircraftVisible(selectedAircraftIndex);
                        } else if (selectedMenuSection == 1) {
                            selectedDifficultyIndex = 0;
                        } else {
                            selectedMapIndex = 0;
                        }
                        repaint();
                    } else if (key == KeyEvent.VK_2) {
                        if (selectedMenuSection == 0) {
                            selectedAircraftIndex = 1;
                            ensureAircraftVisible(selectedAircraftIndex);
                        } else if (selectedMenuSection == 1) {
                            selectedDifficultyIndex = 1;
                        } else if (BattleMap.values().length > 1) {
                            selectedMapIndex = 1;
                        }
                        repaint();
                    } else if (key == KeyEvent.VK_3) {
                        if (selectedMenuSection == 0 && aircraft.length > 2) {
                            selectedAircraftIndex = 2;
                            ensureAircraftVisible(selectedAircraftIndex);
                        } else if (selectedMenuSection == 1) {
                            selectedDifficultyIndex = 2;
                        } else if (selectedMenuSection == 2 && BattleMap.values().length > 2) {
                            selectedMapIndex = 2;
                        }
                        repaint();
                    } else if (key == KeyEvent.VK_4 && selectedMenuSection == 0 && aircraft.length > 3) {
                        selectedAircraftIndex = 3;
                        ensureAircraftVisible(selectedAircraftIndex);
                        repaint();
                    } else if (key == KeyEvent.VK_4 && selectedMenuSection == 2 && BattleMap.values().length > 3) {
                        selectedMapIndex = 3;
                        repaint();
                    } else if (key == KeyEvent.VK_ENTER) {
                        if (selectedMenuSection == 0) {
                            aircraftMenuOpen = !aircraftMenuOpen;
                        } else if (selectedMenuSection == 1) {
                            difficultyMenuOpen = !difficultyMenuOpen;
                        } else {
                            showingMapSelection = true;
                            previewMapIndex = selectedMapIndex;
                        }
                        repaint();
                    } else if (key == KeyEvent.VK_ENTER || key == KeyEvent.VK_SPACE) {
                        startBattle(difficulties[selectedDifficultyIndex]);
                    }
                    return;
                }

                if (key == keyRestart) {
                    showDifficultySelection();
                    return;
                }

                if (key == keyBlueMode && playerAircraft == Aircraft.BLUE_GLOW && !paused) {
                    blueBurstMode = !blueBurstMode;
                    leftMouseDown = false;
                    blueBurstQueued = false;
                    blueBurstShotsRemaining = 0;
                    blueBurstMultiplier = BLUE_INITIAL_BURST_MULTIPLIER;
                    message = fighterDisplayName(blue) + (blueBurstMode ? " mode: burst fire." : " mode: single shot.");
                    repaint();
                    return;
                }

                if (key == keyPause || key == KeyEvent.VK_SPACE) {
                    togglePause();
                    return;
                }

                if (paused) {
                    return;
                }

                if (key == keyCharge) {
                    chargeInputDown = true;
                }

                if (key == keyAttack) {
                    performAttackInput(mouseX, mouseY);
                    return;
                }

                if (key == keyUp || key == keyLeft || key == keyDown || key == keyRight || key == keyBoost) {
                    pressedKeys.add(key);
                }
            }

            @Override
            public void keyReleased(KeyEvent event) {
                int key = event.getKeyCode();
                if (key == keyCharge) {
                    chargeInputDown = false;
                }
                if (key == keyAttack) {
                    releaseAttackInput();
                }
                pressedKeys.remove(key);
            }
        }
    }

    private static final class AudioEngine {
        private static final float SAMPLE_RATE = 22050f;
        private static final Tone STOP = new Tone(0, 0, 0, 0, 0, -1);
        private final LinkedBlockingQueue<Tone> queue = new LinkedBlockingQueue<>(96);
        private final AtomicLong sequence = new AtomicLong();
        private volatile double volume = 0.55;
        private volatile boolean paused;

        private AudioEngine() {
            Thread thread = new Thread(this::runAudioLoop, "JetBattle-Audio");
            thread.setDaemon(true);
            thread.start();
        }

        private void setVolume(double volume) {
            this.volume = Math.max(0, Math.min(1, volume));
        }

        private void setPaused(boolean paused) {
            this.paused = paused;
            sequence.incrementAndGet();
            queue.clear();
        }

        private void beginImmediateSequence() {
            sequence.incrementAndGet();
            queue.clear();
        }

        private void playTone(double frequency, int durationMs, double gain, double harmonicMix) {
            playSweep(frequency, frequency, durationMs, gain, harmonicMix);
        }

        private void playSweep(double startFrequency, double endFrequency, int durationMs, double gain, double harmonicMix) {
            if (paused || volume <= 0 || durationMs <= 0 || startFrequency <= 0 || endFrequency <= 0) {
                return;
            }
            Tone tone = new Tone(startFrequency, endFrequency, durationMs, gain, harmonicMix, sequence.get());
            if (!queue.offer(tone)) {
                queue.poll();
                queue.offer(tone);
            }
        }

        private void runAudioLoop() {
            try {
                AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
                SourceDataLine line = AudioSystem.getSourceDataLine(format);
                line.open(format, (int) SAMPLE_RATE / 2);
                line.start();
                while (true) {
                    if (paused) {
                        writeSilence(line, 16);
                        continue;
                    }
                    Tone tone = queue.poll(120, TimeUnit.MILLISECONDS);
                    if (tone == STOP) {
                        break;
                    }
                    if (tone == null) {
                        writeSilence(line, 16);
                    } else {
                        renderTone(line, tone);
                    }
                }
                line.drain();
                line.close();
            } catch (Exception ignored) {
                // Audio is optional; gameplay should continue if no output line is available.
            }
        }

        private void renderTone(SourceDataLine line, Tone tone) {
            int samples = Math.max(1, (int) (SAMPLE_RATE * tone.durationMs / 1000.0));
            byte[] data = new byte[samples * 2];
            double currentVolume = volume;
            double phase = 0;
            double harmonicPhase = 0;
            for (int i = 0; i < samples; i++) {
                double progress = i / (double) samples;
                double frequency = tone.startFrequency + (tone.endFrequency - tone.startFrequency) * progress;
                phase += 2.0 * Math.PI * frequency / SAMPLE_RATE;
                harmonicPhase += 2.0 * Math.PI * frequency * 2.0 / SAMPLE_RATE;
                double fadeIn = Math.min(1.0, i / (SAMPLE_RATE * 0.006));
                double fadeOut = Math.min(1.0, (samples - i) / (SAMPLE_RATE * 0.04));
                double envelope = Math.max(0, Math.min(fadeIn, fadeOut));
                double wave = Math.sin(phase);
                wave = wave * (1.0 - tone.harmonicMix) + Math.sin(harmonicPhase) * tone.harmonicMix;
                short sample = (short) (wave * envelope * tone.gain * currentVolume * Short.MAX_VALUE);
                data[i * 2] = (byte) (sample & 0xff);
                data[i * 2 + 1] = (byte) ((sample >> 8) & 0xff);
            }
            int offset = 0;
            int chunkBytes = 512;
            while (offset < data.length) {
                if (paused || tone.sequence != sequence.get()) {
                    line.flush();
                    return;
                }
                int length = Math.min(chunkBytes, data.length - offset);
                line.write(data, offset, length);
                offset += length;
            }
        }

        private void writeSilence(SourceDataLine line, int durationMs) {
            int samples = Math.max(1, (int) (SAMPLE_RATE * durationMs / 1000.0));
            line.write(new byte[samples * 2], 0, samples * 2);
        }

        private static final class Tone {
            private final double startFrequency;
            private final double endFrequency;
            private final int durationMs;
            private final double gain;
            private final double harmonicMix;
            private final long sequence;

            private Tone(double startFrequency, double endFrequency, int durationMs, double gain, double harmonicMix, long sequence) {
                this.startFrequency = startFrequency;
                this.endFrequency = endFrequency;
                this.durationMs = durationMs;
                this.gain = gain;
                this.harmonicMix = harmonicMix;
                this.sequence = sequence;
            }
        }
    }

    private enum Difficulty {
        EASY("Easy", "简单", 0.85, 0.88, 1.25),
        MEDIUM("Medium", "中等", 1.0, 1.0, 1.0),
        HARD("Hard", "困难", 1.15, 1.12, 0.90);

        private final String name;
        private final String zhName;
        private final double attackMultiplier;
        private final double speedMultiplier;
        private final double intervalMultiplier;

        Difficulty(String name, String zhName, double attackMultiplier, double speedMultiplier, double intervalMultiplier) {
            this.name = name;
            this.zhName = zhName;
            this.attackMultiplier = attackMultiplier;
            this.speedMultiplier = speedMultiplier;
            this.intervalMultiplier = intervalMultiplier;
        }

        private String displayName(boolean chinese) {
            return chinese ? zhName : name;
        }

        private int adjustAttack(int baseAttack) {
            return Math.max(1, (int) Math.round(baseAttack * attackMultiplier));
        }

        private int adjustSpeed(int baseSpeed) {
            return Math.max(1, (int) Math.round(baseSpeed * speedMultiplier));
        }

        private int adjustAttackInterval(int baseInterval) {
            return Math.max(1, (int) Math.round(baseInterval * intervalMultiplier));
        }

        private String formatMultiplier(double multiplier) {
            return String.format(Locale.US, "%.2f", multiplier);
        }
    }

    private enum BattleMap {
        ORBITAL_DOCK("Orbital Dock", "轨道船坞", "map-orbital-dock.png",
                "Balanced open arena",
                "开放式标准战场",
                new Color(25, 29, 37), new Color(118, 226, 255),
                new String[]{
                        "Standard training dock with clear sight lines.",
                        "Best for testing aircraft damage and movement."
                },
                new String[]{
                        "标准训练船坞，视野清晰，适合正面对抗。",
                        "用于测试战机伤害、速度和技能手感。"
                }),
        NEBULA_RIFT("Nebula Rift", "星云裂隙", "map-nebula-rift.png",
                "High contrast deep-space field",
                "高对比度深空战场",
                new Color(17, 19, 35), new Color(226, 127, 255),
                new String[]{
                        "A violet rift cuts across the center lane.",
                        "Projectiles remain easy to read against the dark field."
                },
                new String[]{
                        "紫色裂隙横贯中心航道，画面更有速度感。",
                        "深色背景能让弹药和激光轨迹更清晰。"
                }),
        CRYSTAL_FIELD("Crystal Field", "晶体星域", "map-crystal-field.png",
                "Cold blue tactical space",
                "冷色调战术星域",
                new Color(14, 31, 39), new Color(150, 242, 255),
                new String[]{
                        "Scattered crystal structures create a sharp sci-fi tone.",
                        "The arena remains mechanically identical to other maps."
                },
                new String[]{
                        "散布的晶体结构强化科幻感和空间层次。",
                        "地图只改变视觉表现，不改变碰撞和数值。"
                }),
        SOLAR_RELIC("Solar Relic", "恒星遗迹", "map-solar-relic.png",
                "Warm relic battlefield",
                "暖色恒星遗迹",
                new Color(29, 24, 22), new Color(255, 196, 77),
                new String[]{
                        "Ancient orbital rings glow under a nearby star.",
                        "Warm lighting gives gold and red aircraft stronger presence."
                },
                new String[]{
                        "古代轨道环在恒星光照下发亮，氛围更厚重。",
                        "暖色光照会让金色与红色战机更突出。"
                });

        private final String name;
        private final String zhName;
        private final String imageFile;
        private final String tagline;
        private final String zhTagline;
        private final Color background;
        private final Color accent;
        private final String[] details;
        private final String[] zhDetails;

        BattleMap(String name, String zhName, String imageFile, String tagline, String zhTagline,
                  Color background, Color accent, String[] details, String[] zhDetails) {
            this.name = name;
            this.zhName = zhName;
            this.imageFile = imageFile;
            this.tagline = tagline;
            this.zhTagline = zhTagline;
            this.background = background;
            this.accent = accent;
            this.details = details;
            this.zhDetails = zhDetails;
        }

        private String displayName(boolean chinese) {
            return chinese ? zhName : name;
        }

        private String tagline(boolean chinese) {
            return chinese ? zhTagline : tagline;
        }

        private String[] details(boolean chinese) {
            return chinese ? zhDetails : details;
        }
    }

    private enum Aircraft {
        TAIL_FLAME("Tail Flame", "尾焰", "tail-flame.png", new Color(210, 65, 62), 720, 400, 18000, 1.4, 188),
        BLUE_GLOW("Blue Glow", "蓝光", "blue-glow.png", new Color(70, 133, 232), 960, 200, 19000, 1.55, 202),
        NEUTRON_STAR("Neutron Star", "中子星", "neutron-star.png", new Color(150, 85, 225), 700, 460, 22000, 1.5, 176),
        VENUS("Venus", "金星", "venus.png", new Color(232, 181, 52), 820, 300, 18500, 1.3, 192),
        SIX_WINGED_ANGEL("Six-Winged Angel", "六翼天使", "six-winged-angel.png", new Color(255, 136, 190), 680, 260, 18000, 1.2, 196);

        private final String name;
        private final String zhName;
        private final String spriteFile;
        private final Color color;
        private final int attack;
        private final int defense;
        private final int maxHp;
        private final double skillBonus;
        private final int speed;

        Aircraft(String name, String zhName, String spriteFile, Color color, int attack, int defense, int maxHp, double skillBonus, int speed) {
            this.name = name;
            this.zhName = zhName;
            this.spriteFile = spriteFile;
            this.color = color;
            this.attack = attack;
            this.defense = defense;
            this.maxHp = maxHp;
            this.skillBonus = skillBonus;
            this.speed = speed;
        }

        private String displayName(boolean chinese) {
            return chinese ? zhName : name;
        }

        private String normalDescription(boolean chinese) {
            return switch (this) {
                case TAIL_FLAME -> chinese ? "追踪导弹" : "Homing missile";
                case BLUE_GLOW -> chinese ? "双翼弹药" : "Twin wing shots";
                case NEUTRON_STAR -> chinese ? "紫色非连续激光光球" : "Purple non-continuous laser orb";
                case VENUS -> chinese ? "机头长条激光" : "Nose laser bar";
                case SIX_WINGED_ANGEL -> chinese ? "圣辉子弹" : "Holy rounds";
            };
        }

        private String skillDescription(boolean chinese) {
            return switch (this) {
                case TAIL_FLAME -> chinese ? "六枚追踪导弹" : "Six homing missiles";
                case BLUE_GLOW -> chinese ? "连续型蓝色激光" : "Continuous blue laser";
                case NEUTRON_STAR -> chinese ? "慢速奇点光球" : "Slow singularity orb";
                case VENUS -> chinese ? "星门强化装甲" : "Star-gate reinforced armor";
                case SIX_WINGED_ANGEL -> chinese ? "治疗与压制光环" : "Healing and suppression field";
            };
        }

        private String roleDescription(boolean chinese) {
            return switch (this) {
                case TAIL_FLAME -> chinese ? "定位：高防御追踪导弹突击机" : "ROLE: Armored homing-missile striker";
                case BLUE_GLOW -> chinese ? "定位：高速持续火力压制机" : "ROLE: High-speed sustained-fire fighter";
                case NEUTRON_STAR -> chinese ? "定位：重装奇点控制机" : "ROLE: Armored singularity controller";
                case VENUS -> chinese ? "定位：均衡型装甲强化战机" : "ROLE: Balanced armor-enhancement fighter";
                case SIX_WINGED_ANGEL -> chinese ? "定位：续航辅助与区域压制战机" : "ROLE: Sustain support and area-control fighter";
            };
        }

        private String[] normalDetails(boolean chinese) {
            return switch (this) {
                case TAIL_FLAME -> chinese
                        ? new String[]{"每 700ms 发射一枚追踪导弹", "加速时会在身后留下短时火痕", "敌机穿过火痕会受到持续灼烧"}
                        : new String[]{"Homing missile every 700 ms", "Boosting leaves short-lived flame trails", "Enemies crossing trails take burn damage"};
                case BLUE_GLOW -> chinese
                        ? new String[]{"双翼齐射，单点间隔 400ms", "命中会叠加离子共振", "三层共振会短暂干扰敌方推进"}
                        : new String[]{"Twin-wing volley every 400 ms", "Hits stack ion resonance", "Three stacks briefly disrupt enemy boost"};
                case NEUTRON_STAR -> chinese
                        ? new String[]{"每 600ms 发射非连续激光球", "偏转弹药回复基础回能的 20%"}
                        : new String[]{"Laser orb every 600 ms", "Deflections restore 20% base hit charge"};
                case VENUS -> chinese
                        ? new String[]{"机头高速激光伤害小幅降低", "普通命中生成环绕星尘护片", "护片可抵消下一次部分伤害"}
                        : new String[]{"Faster nose laser with slightly less damage", "Normal hits create orbiting star shards", "Shards absorb part of the next hit"};
                case SIX_WINGED_ANGEL -> chinese
                        ? new String[]{"每 600ms 从机头发射圣辉子弹", "属于子弹类弹药，基础伤害偏低但飞行速度较快"}
                        : new String[]{"Holy bullet round every 600 ms", "Bullet-type ammo with lower damage but fast travel"};
            };
        }

        private String[] skillDetails(boolean chinese) {
            return switch (this) {
                case TAIL_FLAME -> chinese
                        ? new String[]{"满充能从双翼发射六枚导弹", "初速较低，持续加速至更高极速"}
                        : new String[]{"Launches six missiles from both wings", "Low initial speed, accelerating to high max"};
                case BLUE_GLOW -> chinese
                        ? new String[]{"短暂蓄力后持续照射 3 秒", "连续光束按秒造成高额伤害"}
                        : new String[]{"Brief windup, then a 3-second beam", "Continuous beam deals damage over time"};
                case NEUTRON_STAR -> chinese
                        ? new String[]{"发射慢速奇点光球牵引目标", "可偏转弹药，并弧形偏转光束"}
                        : new String[]{"Slow singularity orb pulls targets", "Deflects projectiles and curves beams"};
                case VENUS -> chinese
                        ? new String[]{"星门缓慢组装强化装甲 6.5 秒", "提升攻防速度并获得 1600 装甲"}
                        : new String[]{"Star gate assembles armor for 6.5 seconds", "Boosts ATK/DEF/SPD and grants 1600 armor"};
                case SIX_WINGED_ANGEL -> chinese
                        ? new String[]{"在光标位置展开 7 秒治疗圈", "己方在圈内回血并加速，敌方在圈内大幅减速并降低防御", "被动血包可恢复生命和能量，12 秒未拾取会消失"}
                        : new String[]{"Creates a 7-second healing field at cursor", "Friendly fighter heals and accelerates; enemy is heavily slowed and loses DEF", "Passive heart packs restore HP and energy, expiring after 12 seconds"};
            };
        }

        private String[] combatTips(boolean chinese) {
            return switch (this) {
                case TAIL_FLAME -> chinese
                        ? new String[]{"用加速转向把火痕铺在敌方路线", "导弹逼走位，火痕惩罚追击"}
                        : new String[]{"Lay flame trails across enemy routes", "Missiles force movement; trails punish pursuit"};
                case BLUE_GLOW -> chinese
                        ? new String[]{"持续命中比单发爆发更重要", "共振触发后抓住敌方无法加速的窗口"}
                        : new String[]{"Sustained hits matter more than burst", "Use ion-lock windows to chase or reposition"};
                case NEUTRON_STAR -> chinese
                        ? new String[]{"预判敌方路线释放奇点光球", "控制战场中心并干扰敌方火力"}
                        : new String[]{"Lead targets with the singularity orb", "Control center and disrupt enemy fire"};
                case VENUS -> chinese
                        ? new String[]{"普通命中积累护片后再主动换血", "强化期间用穿透双炮保持压制"}
                        : new String[]{"Build shards with normal hits before trading", "Use piercing cannons to press during armor"};
                case SIX_WINGED_ANGEL -> chinese
                        ? new String[]{"把治疗圈放在自己移动路线或敌我交战点", "及时回收血包，不要让它自然消失"}
                        : new String[]{"Place fields on your route or contested space", "Collect hearts before they expire"};
            };
        }
    }

    private record Point2D(double x, double y) {
    }

    private record LaserPath(double startX, double startY, double pivotX, double pivotY,
                             double controlX, double controlY, double endX, double endY, boolean deflected) {
    }

    private enum AmmoType {
        BULLET,
        MISSILE,
        LASER
    }

    private enum LaserType {
        NONE,
        NON_CONTINUOUS,
        CONTINUOUS
    }

    private enum ArmorType {
        NONE("", "", "", "", Color.WHITE),
        STEEL("钢甲", "Steel Armor", "格挡", "Blocked", new Color(190, 205, 216)),
        LIGHT("光甲", "Light Armor", "散射", "Scattered", new Color(90, 220, 255)),
        COMPOSITE("复合甲", "Composite Armor", "拦截", "Intercepted", new Color(255, 169, 74));

        private final String zhName;
        private final String enName;
        private final String zhReaction;
        private final String enReaction;
        private final Color color;

        ArmorType(String zhName, String enName, String zhReaction, String enReaction, Color color) {
            this.zhName = zhName;
            this.enName = enName;
            this.zhReaction = zhReaction;
            this.enReaction = enReaction;
            this.color = color;
        }

        private String displayName(boolean chinese) {
            return chinese ? zhName : enName;
        }

        private String reactionText(boolean chinese) {
            return chinese ? zhReaction : enReaction;
        }
    }

    private static final class Fighter {
        private int attack;
        private int defense;
        private int maxHp;
        private double skillBonus;
        private int speed;
        private final double startX;
        private final double startY;
        private int hp;
        private int charge;
        private double chargeCarry;
        private final EnumMap<ArmorType, Double> activeArmors = new EnumMap<>(ArmorType.class);
        private long nextArmorTextAt;
        private double neutronPullRemaining;
        private double neutronPullX;
        private double neutronPullY;
        private double neutronOrbitAngle;
        private double neutronOrbitRadius;
        private double neutronPullChargeCarry;
        private double venusGateRemaining;
        private double venusArmorRemaining;
        private int venusArmorHp;
        private int venusShardCount;
        private int blueGlowResonanceStacks;
        private double blueGlowResonanceRemaining;
        private double blueGlowIonLockRemaining;
        private double tailFlameTrailCooldown;
        private double seraphSpeedBuffRemaining;
        private double seraphSpeedDebuffRemaining;
        private double seraphDefenseDebuffRemaining;
        private double healCarry;
        private double boostEnergy;
        private double boostRecoverDelay;
        private boolean boosting;
        private boolean boostMaxSoundPlayed;
        private double x;
        private double y;

        Fighter(Aircraft aircraft, double startX, double startY) {
            this.startX = startX;
            this.startY = startY;
            applyAircraft(aircraft);
            reset();
        }

        private void applyAircraft(Aircraft aircraft) {
            attack = aircraft.attack;
            defense = aircraft.defense;
            maxHp = aircraft.maxHp;
            skillBonus = aircraft.skillBonus;
            speed = aircraft.speed;
        }

        private void reset() {
            hp = maxHp;
            charge = 0;
            chargeCarry = 0;
            activeArmors.clear();
            nextArmorTextAt = 0;
            neutronPullRemaining = 0;
            neutronPullX = 0;
            neutronPullY = 0;
            neutronOrbitAngle = 0;
            neutronOrbitRadius = 0;
            neutronPullChargeCarry = 0;
            venusGateRemaining = 0;
            venusArmorRemaining = 0;
            venusArmorHp = 0;
            venusShardCount = 0;
            blueGlowResonanceStacks = 0;
            blueGlowResonanceRemaining = 0;
            blueGlowIonLockRemaining = 0;
            tailFlameTrailCooldown = 0;
            seraphSpeedBuffRemaining = 0;
            seraphSpeedDebuffRemaining = 0;
            seraphDefenseDebuffRemaining = 0;
            healCarry = 0;
            boostEnergy = BattlePanel.BOOST_MAX_ENERGY;
            boostRecoverDelay = 0;
            boosting = false;
            boostMaxSoundPlayed = false;
            x = startX;
            y = startY;
        }

        private void equipArmor(ArmorType armorType, double duration) {
            if (armorType != ArmorType.NONE) {
                activeArmors.put(armorType, duration);
            }
        }

        private boolean hasArmor(ArmorType armorType) {
            return activeArmors.getOrDefault(armorType, 0.0) > 0;
        }

        private void updateArmors(double seconds) {
            Iterator<ArmorType> iterator = activeArmors.keySet().iterator();
            while (iterator.hasNext()) {
                ArmorType armorType = iterator.next();
                double remaining = activeArmors.get(armorType) - seconds;
                if (remaining <= 0) {
                    iterator.remove();
                } else {
                    activeArmors.put(armorType, remaining);
                }
            }
        }

        private void updateVenusArmor(double seconds) {
            if (venusGateRemaining > 0) {
                venusGateRemaining = Math.max(0, venusGateRemaining - seconds);
                return;
            }
            venusArmorRemaining = Math.max(0, venusArmorRemaining - seconds);
            if (venusArmorRemaining <= 0) {
                venusArmorHp = 0;
            }
        }
    }

    private static final class ShieldPickup {
        private final ArmorType type;
        private final double x;
        private final double y;

        ShieldPickup(ArmorType type, double x, double y) {
            this.type = type;
            this.x = x;
            this.y = y;
        }

        private boolean isPickedBy(Fighter fighter) {
            return Math.hypot(fighter.x - x, fighter.y - y) <= BattlePanel.SHIELD_PICKUP_RADIUS;
        }

        private void draw(Graphics2D g) {
            switch (type) {
                case STEEL -> drawSteel(g);
                case LIGHT -> drawLight(g);
                case COMPOSITE -> drawComposite(g);
                case NONE -> {
                }
            }
        }

        private void drawSteel(Graphics2D g) {
            int cx = (int) Math.round(x);
            int cy = (int) Math.round(y);
            g.setColor(new Color(190, 205, 216, 48));
            g.fillOval(cx - 24, cy - 24, 48, 48);
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(210, 222, 232));
            g.drawRoundRect(cx - 16, cy - 18, 32, 36, 8, 8);
            g.setColor(new Color(122, 137, 150));
            g.drawLine(cx - 8, cy - 10, cx + 8, cy - 10);
            g.drawLine(cx - 10, cy, cx + 10, cy);
            g.drawLine(cx - 8, cy + 10, cx + 8, cy + 10);
        }

        private void drawLight(Graphics2D g) {
            int cx = (int) Math.round(x);
            int cy = (int) Math.round(y);
            g.setColor(new Color(90, 220, 255, 54));
            g.fillOval(cx - 27, cy - 27, 54, 54);
            g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(116, 232, 255));
            int[] xs = {cx, cx + 20, cx, cx - 20};
            int[] ys = {cy - 24, cy, cy + 24, cy};
            g.drawPolygon(xs, ys, 4);
            g.setColor(Color.WHITE);
            g.drawLine(cx - 12, cy, cx + 12, cy);
            g.drawLine(cx, cy - 12, cx, cy + 12);
        }

        private void drawComposite(Graphics2D g) {
            int cx = (int) Math.round(x);
            int cy = (int) Math.round(y);
            g.setColor(new Color(255, 169, 74, 48));
            g.fillOval(cx - 26, cy - 26, 52, 52);
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(255, 187, 94));
            g.drawArc(cx - 22, cy - 22, 44, 44, 18, 72);
            g.drawArc(cx - 22, cy - 22, 44, 44, 138, 72);
            g.drawArc(cx - 22, cy - 22, 44, 44, 258, 72);
            g.setColor(new Color(255, 231, 184));
            g.fillOval(cx - 6, cy - 6, 12, 12);
        }
    }

    private static final class SeraphZone {
        private final boolean fromBlue;
        private final double x;
        private final double y;
        private final double radius;
        private double remaining;

        SeraphZone(boolean fromBlue, double x, double y, double radius, double duration) {
            this.fromBlue = fromBlue;
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.remaining = duration;
        }

        private boolean contains(Fighter fighter) {
            return Math.hypot(fighter.x - x, fighter.y - y) <= radius;
        }
    }

    private static final class FlameTrail {
        private final boolean fromBlue;
        private final double x;
        private final double y;
        private double remaining = BattlePanel.TAIL_FLAME_TRAIL_DURATION;
        private double damageCarry;

        FlameTrail(boolean fromBlue, double x, double y) {
            this.fromBlue = fromBlue;
            this.x = x;
            this.y = y;
        }

        private void draw(Graphics2D g) {
            double ratio = Math.max(0, remaining / BattlePanel.TAIL_FLAME_TRAIL_DURATION);
            int radius = (int) Math.round(BattlePanel.TAIL_FLAME_TRAIL_RADIUS * (0.65 + 0.35 * ratio));
            int alpha = (int) Math.round(170 * ratio);
            int cx = (int) Math.round(x);
            int cy = (int) Math.round(y);
            long now = System.currentTimeMillis();
            RadialGradientPaint heat = new RadialGradientPaint(
                    (float) x,
                    (float) y,
                    radius,
                    new float[]{0f, 0.48f, 1f},
                    new Color[]{
                            new Color(255, 241, 184, Math.max(0, alpha)),
                            new Color(255, 116, 38, Math.max(0, (int) (alpha * 0.68))),
                            new Color(112, 24, 12, 0)
                    }
            );
            g.setPaint(heat);
            g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
            g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < 4; i++) {
                double wave = Math.sin(now / 120.0 + x * 0.03 + i * 1.7);
                int w = (int) Math.round(radius * (0.72 + i * 0.18));
                int h = (int) Math.round(radius * (0.42 + i * 0.10));
                int ox = (int) Math.round(Math.cos(i * 2.1 + now / 260.0) * 5);
                int oy = (int) Math.round(Math.sin(i * 1.5 + now / 230.0) * 4);
                g.setColor(new Color(255, i % 2 == 0 ? 188 : 86, 34, Math.max(0, (int) (alpha * (0.58 - i * 0.08)))));
                g.drawArc(cx - w / 2 + ox, cy - h / 2 + oy, w, h, (int) (wave * 45 + i * 72), 95);
            }
            g.setColor(new Color(255, 244, 204, Math.max(0, (int) (alpha * 0.72))));
            g.fillOval(cx - radius / 5, cy - radius / 5, Math.max(3, radius / 3), Math.max(3, radius / 3));
        }
    }

    private static final class HeartPickup {
        private final boolean fromBlue;
        private final double x;
        private final double y;
        private final double duration;
        private double remaining;

        HeartPickup(boolean fromBlue, double x, double y, double duration) {
            this.fromBlue = fromBlue;
            this.x = x;
            this.y = y;
            this.duration = duration;
            this.remaining = duration;
        }

        private boolean isPickedBy(Fighter fighter) {
            return Math.hypot(fighter.x - x, fighter.y - y) <= BattlePanel.SERAPH_HEART_PICKUP_RADIUS;
        }

        private void draw(Graphics2D g, BufferedImage image) {
            double ratio = Math.max(0, remaining / duration);
            int alpha = (int) Math.round(230 * ratio);
            int cx = (int) Math.round(x);
            int cy = (int) Math.round(y);
            g.setColor(new Color(255, 128, 154, Math.max(0, (int) Math.round(46 * ratio))));
            g.fillOval(cx - 25, cy - 25, 50, 50);
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(fromBlue
                    ? new Color(124, 225, 255, Math.max(0, (int) Math.round(135 * ratio)))
                    : new Color(255, 140, 155, Math.max(0, (int) Math.round(135 * ratio))));
            g.drawOval(cx - 23, cy - 23, 46, 46);
            if (image == null) {
                BattlePanel.drawWingedHeartIcon(g, x, y, 0.82, alpha);
                return;
            }
            double scale = Math.min(42.0 / image.getWidth(), 28.0 / image.getHeight());
            int drawWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
            int drawHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
            Graphics2D icon = (Graphics2D) g.create();
            icon.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, Math.max(0, Math.min(255, alpha)) / 255f));
            icon.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            icon.drawImage(image, cx - drawWidth / 2, cy - drawHeight / 2, drawWidth, drawHeight, null);
            icon.dispose();
        }
    }

    private static final class FloatingText {
        private final String value;
        private final double x;
        private final double y;
        private final Color color;
        private double remaining = BattlePanel.FLOATING_TEXT_DURATION;

        FloatingText(String value, double x, double y, Color color) {
            this.value = value;
            this.x = x;
            this.y = y;
            this.color = color;
        }

        private void update(double seconds) {
            remaining -= seconds;
        }

        private void draw(Graphics2D g) {
            double progress = Math.max(0, remaining / BattlePanel.FLOATING_TEXT_DURATION);
            int alpha = (int) Math.round(255 * progress);
            int drawY = (int) Math.round(y - (1 - progress) * 22);
            g.setFont(new Font("SansSerif", Font.BOLD, 18));
            FontMetrics metrics = g.getFontMetrics();
            int drawX = (int) Math.round(x - metrics.stringWidth(value) / 2.0);
            g.setColor(new Color(10, 14, 20, Math.min(180, alpha)));
            g.drawString(value, drawX + 1, drawY + 1);
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
            g.drawString(value, drawX, drawY);
        }
    }

        private static final class Projectile {
        private double vx;
        private double vy;
        private double speed;
        private double maxSpeed;
        private double acceleration;
        private final double radius;
        private final int damage;
        private boolean skill;
        private final boolean missile;
        private boolean homing;
        private double homingStrength;
        private Fighter homingTarget;
        private double homingTimeout;
        private final boolean fromBlue;
        private final Color color;
        private final String hitMessage;
        private double chargeMultiplier;
        private final AmmoType ammoType;
        private final LaserType laserType;
        private boolean neutronSkillOrb;
        private boolean neutronDeflected;
        private boolean neutronImpactStarted;
        private boolean venusLaserBar;
        private boolean venusEnhancedLaser;
        private boolean seraphBullet;
        private boolean piercing;
        private boolean targetHit;
        private Fighter neutronOwner;
        private Fighter neutronDeflectOwner;
        private AmmoType neutronResolvedType;
        private double neutronFlightRemaining;
        private double neutronPullRemaining;
        private double neutronDamageCarry;
        private double neutronOrbitPhase;
        private double neutronOrbitDirection = 1;
        private double neutronOrbitBias;
        private double x;
        private double y;

        Projectile(double x, double y, double vx, double vy, double speed, double radius, int damage, boolean skill, boolean missile,
                   boolean homing, double homingStrength, Fighter homingTarget, boolean fromBlue, Color color, String hitMessage,
                   double chargeMultiplier, AmmoType ammoType, LaserType laserType) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.speed = speed;
            this.maxSpeed = speed;
            this.acceleration = 0;
            this.radius = radius;
            this.damage = damage;
            this.skill = skill;
            this.missile = missile;
            this.homing = homing;
            this.homingStrength = homingStrength;
            this.homingTarget = homingTarget;
            this.fromBlue = fromBlue;
            this.color = color;
            this.hitMessage = hitMessage;
            this.chargeMultiplier = chargeMultiplier;
            this.ammoType = ammoType;
            this.laserType = laserType;
        }

        private void update(double seconds) {
            if (acceleration > 0 && speed < maxSpeed) {
                double currentLength = Math.hypot(vx, vy);
                if (currentLength > 0) {
                    speed = Math.min(maxSpeed, speed + acceleration * seconds);
                    vx = vx / currentLength * speed;
                    vy = vy / currentLength * speed;
                }
            }

            if (homingTimeout > 0) {
                homingTimeout = Math.max(0, homingTimeout - seconds);
                if (homingTimeout == 0) {
                    homing = false;
                    homingTarget = null;
                    homingStrength = 0;
                }
            }

            if (homing && homingTarget != null) {
                double targetDx = homingTarget.x - x;
                double targetDy = homingTarget.y - y;
                double targetLength = Math.hypot(targetDx, targetDy);
                double currentLength = Math.hypot(vx, vy);
                if (targetLength > 0 && currentLength > 0) {
                    double currentX = vx / currentLength;
                    double currentY = vy / currentLength;
                    double targetX = targetDx / targetLength;
                    double targetY = targetDy / targetLength;
                    double turn = Math.min(1, homingStrength * seconds);
                    double newX = currentX + (targetX - currentX) * turn;
                    double newY = currentY + (targetY - currentY) * turn;
                    double newLength = Math.hypot(newX, newY);
                    vx = newX / newLength * speed;
                    vy = newY / newLength * speed;
                }
            }

            x += vx * seconds;
            y += vy * seconds;
        }

        private void draw(Graphics2D g) {
            if (neutronSkillOrb) {
                drawNeutronSkillOrb(g);
            } else if (venusLaserBar) {
                drawVenusLaserBar(g);
            } else if (seraphBullet) {
                drawSeraphBullet(g);
            } else if (ammoType == AmmoType.LASER && laserType == LaserType.NON_CONTINUOUS && !missile) {
                drawEnergyOrb(g);
            } else if (missile) {
                drawMissile(g);
            } else {
                drawBeam(g);
            }
        }

        private void drawVenusLaserBar(Graphics2D g) {
            double length = Math.hypot(vx, vy);
            if (length == 0) {
                return;
            }
            double nx = vx / length;
            double ny = vy / length;
            double beamLength = venusEnhancedLaser ? 58 : 44;
            double tailX = x - nx * beamLength;
            double tailY = y - ny * beamLength;
            int glowWidth = venusEnhancedLaser ? 13 : 9;
            int coreWidth = venusEnhancedLaser ? 5 : 3;

            g.setColor(new Color(255, 186, 38, venusEnhancedLaser ? 95 : 70));
            g.setStroke(new BasicStroke(glowWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine((int) Math.round(tailX), (int) Math.round(tailY), (int) Math.round(x), (int) Math.round(y));
            g.setColor(new Color(255, 222, 105, 220));
            g.setStroke(new BasicStroke(coreWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine((int) Math.round(tailX), (int) Math.round(tailY), (int) Math.round(x), (int) Math.round(y));
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine((int) Math.round(x - nx * beamLength * 0.58), (int) Math.round(y - ny * beamLength * 0.58),
                    (int) Math.round(x), (int) Math.round(y));
        }

        private void startNeutronImpact(AmmoType resolvedType, Fighter target) {
            neutronImpactStarted = true;
            neutronResolvedType = resolvedType;
            vx = 0;
            vy = 0;
        }

        private void removeSpecialEffects() {
            skill = false;
            homing = false;
            homingStrength = 0;
            homingTarget = null;
            chargeMultiplier = 1.0;
        }

        private void drawEnergyOrb(Graphics2D g) {
            int r = (int) Math.round(radius + 7);
            RadialGradientPaint paint = new RadialGradientPaint(
                    (float) x,
                    (float) y,
                    r,
                    new float[]{0f, 0.45f, 1f},
                    new Color[]{
                            Color.WHITE,
                            brighten(color, 55, 220),
                            new Color(color.getRed(), color.getGreen(), color.getBlue(), 0)
                    }
            );
            g.setPaint(paint);
            g.fillOval((int) x - r, (int) y - r, r * 2, r * 2);
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 160));
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawOval((int) x - r / 2, (int) y - r / 2, r, r);
        }

        private void drawNeutronSkillOrb(Graphics2D g) {
            int influenceRadius = (int) Math.round(BattlePanel.NEUTRON_ORB_DEFLECT_RADIUS);
            long now = System.currentTimeMillis();
            double pulse = (Math.sin(now / 260.0) + 1.0) * 0.5;
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 26));
            g.fillOval((int) Math.round(x - influenceRadius), (int) Math.round(y - influenceRadius),
                    influenceRadius * 2, influenceRadius * 2);
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < 3; i++) {
                int ring = (int) Math.round(influenceRadius * (0.42 + i * 0.22 + pulse * 0.045));
                g.setColor(new Color(210, 174, 255, 44 - i * 8));
                g.drawOval((int) Math.round(x - ring), (int) Math.round(y - ring), ring * 2, ring * 2);
            }
            g.setColor(new Color(238, 218, 255, 82));
            for (int i = 0; i < 4; i++) {
                int arcRadius = (int) Math.round(influenceRadius * (0.52 + i * 0.1));
                int start = (int) Math.round(now / 18.0 + i * 73);
                g.drawArc((int) Math.round(x - arcRadius), (int) Math.round(y - arcRadius),
                        arcRadius * 2, arcRadius * 2, start, 58);
            }

            int r = (int) Math.round(neutronImpactStarted ? radius + 26 : radius + 16);
            RadialGradientPaint paint = new RadialGradientPaint(
                    (float) x,
                    (float) y,
                    r,
                    new float[]{0f, 0.35f, 0.72f, 1f},
                    new Color[]{
                            Color.WHITE,
                            brighten(color, 70, 245),
                            new Color(color.getRed(), color.getGreen(), color.getBlue(), 185),
                            new Color(color.getRed(), color.getGreen(), color.getBlue(), 0)
                    }
            );
            g.setPaint(paint);
            g.fillOval((int) x - r, (int) y - r, r * 2, r * 2);
            g.setColor(new Color(238, 218, 255, 210));
            g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawArc((int) x - r, (int) y - r, r * 2, r * 2, 25, 115);
            g.drawArc((int) x - r, (int) y - r, r * 2, r * 2, 205, 115);
        }

        private void drawSeraphBullet(Graphics2D g) {
            double length = Math.hypot(vx, vy);
            if (length == 0) {
                return;
            }
            double nx = vx / length;
            double ny = vy / length;
            double sideX = -ny;
            double sideY = nx;
            double tailX = x - nx * 26;
            double tailY = y - ny * 26;

            g.setColor(new Color(255, 118, 146, 80));
            g.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine((int) Math.round(tailX), (int) Math.round(tailY), (int) Math.round(x), (int) Math.round(y));
            g.setColor(new Color(255, 245, 250, 205));
            g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine((int) Math.round(x - nx * 16), (int) Math.round(y - ny * 16), (int) Math.round(x), (int) Math.round(y));

            int[] xs = {
                    (int) Math.round(x + nx * 11),
                    (int) Math.round(x - nx * 8 + sideX * 7),
                    (int) Math.round(x - nx * 3),
                    (int) Math.round(x - nx * 8 - sideX * 7)
            };
            int[] ys = {
                    (int) Math.round(y + ny * 11),
                    (int) Math.round(y - ny * 8 + sideY * 7),
                    (int) Math.round(y - ny * 3),
                    (int) Math.round(y - ny * 8 - sideY * 7)
            };
            g.setColor(new Color(255, 255, 255, 235));
            g.fillPolygon(xs, ys, xs.length);
            g.setColor(new Color(255, 82, 118, 220));
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawPolygon(xs, ys, xs.length);
            g.setColor(new Color(255, 112, 142, 180));
            g.fillOval((int) Math.round(x - nx * 7 - 3), (int) Math.round(y - ny * 7 - 3), 6, 6);
        }

        private void drawBeam(Graphics2D g) {
            double length = Math.hypot(vx, vy);
            double nx = vx / length;
            double ny = vy / length;
            boolean coolColor = color.getBlue() >= color.getRed();
            double tailLength = coolColor ? 34 : 22;
            double tailX = x - nx * tailLength;
            double tailY = y - ny * tailLength;

            if (coolColor) {
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 70));
                g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine((int) tailX, (int) tailY, (int) x, (int) y);

                g.setColor(brighten(color, 70, 185));
                g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine((int) tailX, (int) tailY, (int) x, (int) y);

                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine((int) (x - nx * 14), (int) (y - ny * 14), (int) x, (int) y);
            } else {
                double sideX = -ny;
                double sideY = nx;
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 75));
                g.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine((int) tailX, (int) tailY, (int) x, (int) y);

                g.setColor(brighten(color, 45, 180));
                g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine((int) tailX, (int) tailY, (int) x, (int) y);

                int[] xs = {
                        (int) Math.round(x + nx * 8),
                        (int) Math.round(x - nx * 8 + sideX * 5),
                        (int) Math.round(x - nx * 8 - sideX * 5)
                };
                int[] ys = {
                        (int) Math.round(y + ny * 8),
                        (int) Math.round(y - ny * 8 + sideY * 5),
                        (int) Math.round(y - ny * 8 - sideY * 5)
                };
                g.setColor(new Color(255, 226, 198));
                g.fillPolygon(xs, ys, 3);
            }
        }

        private void drawMissile(Graphics2D g) {
            double length = Math.hypot(vx, vy);
            double nx = vx / length;
            double ny = vy / length;
            double tailX = x - nx * 20;
            double tailY = y - ny * 20;
            double sideX = -ny;
            double sideY = nx;

            boolean coolColor = color.getBlue() >= color.getRed();
            Color trailColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), coolColor ? 80 : 95);
            Color coreColor = coolColor ? brighten(color, 95, 255) : new Color(255, 219, 188);

            g.setColor(trailColor);
            g.setStroke(new BasicStroke(coolColor ? 6f : 8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine((int) tailX, (int) tailY, (int) x, (int) y);

            RadialGradientPaint paint = new RadialGradientPaint(
                    (float) x,
                    (float) y,
                    18,
                    new float[]{0f, 0.55f, 1f},
                    new Color[]{
                            Color.WHITE,
                            color,
                            new Color(color.getRed(), color.getGreen(), color.getBlue(), 0)
                    }
            );
            g.setPaint(paint);
            g.fillOval((int) x - 18, (int) y - 18, 36, 36);

            g.setColor(coreColor);
            int[] xs = {
                    (int) Math.round(x + nx * (coolColor ? 9 : 12)),
                    (int) Math.round(x - nx * 9 + sideX * (coolColor ? 4 : 6)),
                    (int) Math.round(x - nx * 9 - sideX * (coolColor ? 4 : 6))
            };
            int[] ys = {
                    (int) Math.round(y + ny * (coolColor ? 9 : 12)),
                    (int) Math.round(y - ny * 9 + sideY * (coolColor ? 4 : 6)),
                    (int) Math.round(y - ny * 9 - sideY * (coolColor ? 4 : 6))
            };
            g.fillPolygon(xs, ys, 3);

            if (!coolColor) {
                g.setColor(brighten(color, 35, 190));
                g.fillOval((int) Math.round(x - nx * 13 - 3), (int) Math.round(y - ny * 13 - 3), 6, 6);
            }
        }

        private Color brighten(Color source, int amount, int alpha) {
            return new Color(
                    Math.min(255, source.getRed() + amount),
                    Math.min(255, source.getGreen() + amount),
                    Math.min(255, source.getBlue() + amount),
                    alpha
            );
        }
    }
}
