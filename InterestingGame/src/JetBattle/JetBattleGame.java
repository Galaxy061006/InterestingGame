package JetBattle;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
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

public class JetBattleGame {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Jet Battle");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.add(new BattlePanel());
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
         * | Tail Flame | 720 | 400 | 18000 | 1.40             | 145 | Missile, ATK - DEF, 700 ms     | 6 missiles, ATK / 6 * skill multiplier    | Base x1.00        |
         * | Blue Glow  | 960 | 320 | 19000 | 1.55             | 150 | Bullet, ATK * 0.5, 350 ms      | Continuous laser, ATK * 0.7 * skill / sec | Base x1.05        |
         * | Neutron Star | 700 | 460 | 22000 | 1.50           | 140 | Non-continuous laser orb, ATK - DEF, 600 ms | Slow singularity orb, random final ammo type | Base x1.20 + hit recovery |
         *
         * Standard baseline values for future aircraft:
         * | ATK | DEF | HP    | Skill multiplier | SPD   |
         * |-----|-----|-------|------------------|-------|
         * | 820 | 300 | 18500 | 1.275            | 147.5 |
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
        private static final int INPUT_MOUSE_LEFT = -1;
        private static final int INPUT_MOUSE_MIDDLE = -2;
        private static final int INPUT_MOUSE_RIGHT = -3;
        private static final int MAX_CHARGE = 100;
        private static final int CHARGE_GAIN = 5;
        private static final int PLAYER_SKILL_COOLDOWN = 280;
        private static final int NORMAL_ATTACK_COOLDOWN = 600;
        private static final int TAIL_FLAME_ATTACK_COOLDOWN = 700;
        private static final int BLUE_SINGLE_SHOT_COOLDOWN = 350;
        private static final int BLUE_FIRE_INTERVAL = 140;
        private static final int BLUE_BURST_ROUND_COOLDOWN = 850;
        private static final int BLUE_BURST_SHOTS_PER_ROUND = 4;
        private static final int AI_ATTACK_INTERVAL = 900;
        private static final int FIGHTER_SIZE = 34;
        private static final int ARENA_LEFT = 70;
        private static final int ARENA_TOP = 92;
        private static final int ARENA_WIDTH = 820;
        private static final int ARENA_HEIGHT = 330;
        private static final double BOOST_SPEED_MULTIPLIER = 1.8;
        private static final double BOOST_MAX_ENERGY = 2.5;
        private static final double BOOST_RECOVER_DELAY = 0.5;
        private static final double BOOST_RECOVER_PER_SECOND = 0.8;
        private static final double BEAM_PROJECTILE_SPEED = 540;
        private static final double BLUE_GLOW_PROJECTILE_SPEED = 760;
        private static final double MISSILE_PROJECTILE_SPEED = 430;
        private static final double HOMING_PROJECTILE_SPEED = 430;
        private static final double TAIL_FLAME_SKILL_MISSILE_INITIAL_SPEED = 240;
        private static final double TAIL_FLAME_SKILL_MISSILE_MAX_SPEED = 560;
        private static final double TAIL_FLAME_SKILL_MISSILE_ACCELERATION = 420;
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
        private static final double NEUTRON_ORB_RADIUS = 30;
        private static final double NEUTRON_ORB_PULL_RADIUS = 98;
        private static final double NEUTRON_ORB_PULL_DURATION = 4.0;
        private static final double NEUTRON_ORB_FLIGHT_DURATION = 10.0;
        private static final double NEUTRON_ORB_DEFLECT_RADIUS = 150;
        private static final double NEUTRON_ORB_DEFLECT_STRENGTH = 5.2;
        private static final double NEUTRON_NORMAL_ORB_RADIUS = 8;
        private static final double SHIELD_DURATION = 8.0;
        private static final double SHIELD_SPAWN_INTERVAL = 5.5;
        private static final double SHIELD_PICKUP_RADIUS = 34.0;
        private static final double FLOATING_TEXT_DURATION = 1.0;
        private static final int MAX_SHIELD_PICKUPS = 3;

        private final Fighter red = new Fighter(Aircraft.TAIL_FLAME, 220, 256);
        private final Fighter blue = new Fighter(Aircraft.BLUE_GLOW, 740, 256);
        private final List<Projectile> projectiles = new ArrayList<>();
        private final List<ShieldPickup> shieldPickups = new ArrayList<>();
        private final List<FloatingText> floatingTexts = new ArrayList<>();
        private final Set<Integer> pressedKeys = new HashSet<>();
        private final Random random = new Random();
        private final AudioEngine audio = new AudioEngine();
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
        private int blueBurstShotsRemaining;
        private int aiBlueBurstShotsRemaining;
        private int selectedDifficultyIndex = 1;
        private int selectedAircraftIndex = -1;
        private int selectedAiAircraftIndex = -1;
        private int hangarAircraftIndex = 1;
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
        private int keyAttack = INPUT_MOUSE_LEFT;
        private int keyCharge = INPUT_MOUSE_LEFT;
        private int keyInputMethodLock = KeyEvent.VK_F12;
        private String message = "WASD move. Left click fires. Right click fires charged skill.";
        private Difficulty difficulty = Difficulty.MEDIUM;
        private Aircraft playerAircraft = Aircraft.BLUE_GLOW;
        private Aircraft aiAircraft = Aircraft.TAIL_FLAME;
        private boolean choosingDifficulty = true;
        private boolean showingHangar;
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
        private long explosionStartedAt;
        private long playerAircraftConfirmedAt;
        private long aiAircraftConfirmedAt;
        private double explosionX;
        private double explosionY;

        BattlePanel() {
            setPreferredSize(new Dimension(WIDTH, HEIGHT));
            setBackground(new Color(18, 20, 26));
            setFocusable(true);
            addMouseListener(new BattleMouseListener());
            addMouseMotionListener(new BattleMouseMotionListener());
            addKeyListener(new BattleKeyListener());
            setInputMethodLocked(true);
            gameTimer.start();
            aiTimer.stop();
        }

        private void updateGame() {
            long now = System.nanoTime();
            double seconds = (now - lastUpdateTime) / 1_000_000_000.0;
            lastUpdateTime = now;

            if (explosionActive) {
                updateExplosion();
            }

            if (!choosingDifficulty && !paused && !gameOver) {
                updatePlayerLaserWindup(seconds);
                updateNeutronSkillWindups(seconds);
                updateNeutronPulls(seconds);
                updateBoostStates(seconds);
                if (blueLaserWindupRemaining <= 0) {
                    movePlayer(seconds);
                }
                moveAi(seconds);
                updatePlayerAutoFire(seconds);
                updateAiBlueBurstFire();
                updatePlayerLaser(seconds);
                updateAiLaser(seconds);
                updateProjectiles(seconds);
                updateShieldPickups(seconds);
                updateActiveArmors(seconds);
                updateFloatingTexts(seconds);
            }

            repaint();
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
            double speedMultiplier = fighter.boosting ? BOOST_SPEED_MULTIPLIER : 1.0;
            double step = fighter.speed * speedMultiplier * seconds;
            fighter.x = clamp(fighter.x + dx / length * step, ARENA_LEFT + FIGHTER_SIZE / 2.0, ARENA_LEFT + ARENA_WIDTH - FIGHTER_SIZE / 2.0);
            fighter.y = clamp(fighter.y + dy / length * step, ARENA_TOP + FIGHTER_SIZE / 2.0, ARENA_TOP + ARENA_HEIGHT - FIGHTER_SIZE / 2.0);
        }

        private void updateBoostStates(double seconds) {
            boolean playerMoving = pressedKeys.contains(keyUp)
                    || pressedKeys.contains(keyLeft)
                    || pressedKeys.contains(keyDown)
                    || pressedKeys.contains(keyRight);
            updateAiBoostDecision(seconds);
            updateBoost(blue, pressedKeys.contains(keyBoost) && playerMoving, seconds);
            updateBoost(red, aiWantsBoost, seconds);
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

                Fighter target = projectile.fromBlue ? red : blue;
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

                double hitDistance = FIGHTER_SIZE / 2.0 + projectile.radius;
                if (Math.hypot(projectile.x - target.x, projectile.y - target.y) <= hitDistance) {
                    applyDamage(target, projectile.damage, projectile.ammoType, projectile.laserType);
                    playHitSound();
                    addDefenderCharge(target);
                    if (!projectile.skill) {
                        Fighter attacker = projectile.fromBlue ? blue : red;
                        addCharge(attacker, projectile.chargeMultiplier);
                    }
                    message = projectile.hitMessage;
                    iterator.remove();
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
                case TAIL_FLAME -> 1.0;
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
                if (distance <= 0 || distance > NEUTRON_ORB_DEFLECT_RADIUS) {
                    continue;
                }
                if (!projectile.neutronDeflected) {
                    projectile.neutronDeflected = true;
                    if (projectile.ammoType != AmmoType.LASER) {
                        projectile.removeSpecialEffects();
                    }
                }
                double influence = (1.0 - distance / NEUTRON_ORB_DEFLECT_RADIUS) * NEUTRON_ORB_DEFLECT_STRENGTH * seconds;
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

        private boolean updateNeutronSkillOrb(Projectile projectile, Fighter target, double seconds) {
            if (!projectile.neutronImpactStarted) {
                projectile.neutronFlightRemaining = Math.max(0, projectile.neutronFlightRemaining - seconds);
                bounceNeutronOrb(projectile);
                if (projectile.neutronFlightRemaining <= 0) {
                    return true;
                }
                double distance = Math.hypot(projectile.x - target.x, projectile.y - target.y);
                if (distance > FIGHTER_SIZE / 2.0 + projectile.radius) {
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

        private void updateActiveArmors(double seconds) {
            red.updateArmors(seconds);
            blue.updateArmors(seconds);
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
                        fireBlueGlowLasers(red, blue, blue.x, blue.y, damage, text("蓝光", "Blue Glow"));
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
                    fireNeutronStarShot(red, blue, blue.x, blue.y, false);
                    message = fighterDisplayName(red) + " AI fired a neutron shot.";
                }
            } else if (red.charge >= MAX_CHARGE) {
                int damage = tailFlameMissileDamage(red);
                red.charge = 0;
                red.chargeCarry = 0;
                fireTailFlameWingMissiles(red, blue, blue.x, blue.y, damage);
                message = fighterDisplayName(red) + " AI launched six homing missiles.";
            } else {
                fireTailFlameShot(red, blue, blue.x, blue.y, false);
                message = fighterDisplayName(red) + " AI fired a homing shot.";
            }
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
            fireBlueGlowLasers(red, blue, blue.x, blue.y, damage, text("蓝光", "Blue Glow"));
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
                    fireNeutronStarSkillOrb(red, blue, blue.x, blue.y);
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
            fighter.neutronOrbitAngle += (2.8 + Math.sin(fighter.neutronPullRemaining * 7.0) * 0.65) * seconds;
            fighter.neutronOrbitRadius = Math.max(24, fighter.neutronOrbitRadius - 9.0 * seconds + Math.sin(fighter.neutronPullRemaining * 11.0) * 0.9);
            double wobbleX = Math.sin(fighter.neutronPullRemaining * 13.0) * 8.0;
            double wobbleY = Math.cos(fighter.neutronPullRemaining * 9.0) * 6.0;
            double targetX = fighter.neutronPullX + Math.cos(fighter.neutronOrbitAngle) * fighter.neutronOrbitRadius + wobbleX;
            double targetY = fighter.neutronPullY + Math.sin(fighter.neutronOrbitAngle) * fighter.neutronOrbitRadius + wobbleY;
            fighter.x = clamp(targetX, ARENA_LEFT + FIGHTER_SIZE / 2.0, ARENA_LEFT + ARENA_WIDTH - FIGHTER_SIZE / 2.0);
            fighter.y = clamp(targetY, ARENA_TOP + FIGHTER_SIZE / 2.0, ARENA_TOP + ARENA_HEIGHT - FIGHTER_SIZE / 2.0);
        }

        private void updatePlayerLaser(double seconds) {
            if (blueLaserRemaining <= 0) {
                return;
            }

            blueLaserRemaining = Math.max(0, blueLaserRemaining - seconds);
            Point2D aim = neutronDeflectedLaserAim(blue, mouseX, mouseY);
            if (laserHitsTarget(blue, red, aim.x, aim.y)) {
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
            Point2D aim = neutronDeflectedLaserAim(red, blue.x, blue.y);
            if (laserHitsTarget(red, blue, aim.x, aim.y)) {
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

        private Point2D neutronDeflectedLaserAim(Fighter shooter, double aimX, double aimY) {
            double dx = aimX - shooter.x;
            double dy = aimY - shooter.y;
            double length = Math.hypot(dx, dy);
            if (length == 0) {
                return new Point2D(aimX, aimY);
            }

            double nx = dx / length;
            double ny = dy / length;
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
                if (distance > NEUTRON_ORB_DEFLECT_RADIUS) {
                    continue;
                }

                double pullLength = Math.hypot(orbDx, orbDy);
                double pullX = orbDx / pullLength;
                double pullY = orbDy / pullLength;
                double influence = (1.0 - distance / NEUTRON_ORB_DEFLECT_RADIUS) * 0.62;
                double newX = nx + (pullX - nx) * influence;
                double newY = ny + (pullY - ny) * influence;
                double newLength = Math.hypot(newX, newY);
                return new Point2D(shooter.x + newX / newLength * ARENA_WIDTH, shooter.y + newY / newLength * ARENA_WIDTH);
            }

            return new Point2D(aimX, aimY);
        }

        private boolean laserHitsTarget(Fighter shooter, Fighter target, double aimX, double aimY) {
            double dx = aimX - shooter.x;
            double dy = aimY - shooter.y;
            double length = Math.hypot(dx, dy);
            if (length == 0) {
                return false;
            }

            double nx = dx / length;
            double ny = dy / length;
            double targetDx = target.x - shooter.x;
            double targetDy = target.y - shooter.y;
            double projection = targetDx * nx + targetDy * ny;
            if (projection < 0) {
                return false;
            }

            double closestX = shooter.x + nx * projection;
            double closestY = shooter.y + ny * projection;
            return Math.hypot(target.x - closestX, target.y - closestY) <= BLUE_LASER_HIT_RADIUS;
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
            double dx = targetX - startX;
            double dy = targetY - startY;
            double length = Math.hypot(dx, dy);
            if (length == 0) {
                dx = attacker == blue ? -1 : 1;
                dy = 0;
                length = 1;
            }

            fireProjectileWithVectorFrom(startX, startY, attacker, dx / length, dy / length, damage, skill, missile, homing, homingStrength,
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
            double radius = missile ? 10 : 6;
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
            projectiles.add(projectile);
        }

        private double beamProjectileSpeed(Fighter attacker) {
            return aircraftFor(attacker) == Aircraft.BLUE_GLOW ? BLUE_GLOW_PROJECTILE_SPEED : BEAM_PROJECTILE_SPEED;
        }

        private int normalDamage(Fighter attacker, Fighter defender) {
            return Math.max(1, attacker.attack - defender.defense);
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
            return Math.max(1, (int) Math.round(attacker.attack / 6.0 * attacker.skillBonus));
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
            target.hp = Math.max(0, target.hp - finalDamage);
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
            if (ammoType == AmmoType.MISSILE) {
                audio.playSweep(190, 90, 170, 0.52, 0.55);
            } else if (ammoType == AmmoType.LASER) {
                audio.playTone(1320, 95, 0.46, 0.20);
                audio.playTone(1760, 70, 0.24, 0.10);
            } else {
                audio.playTone(760, 65, 0.42, 0.60);
            }
        }

        private void playSkillSound() {
            audio.playSweep(260, 920, 260, 0.48, 0.25);
            audio.playTone(1180, 170, 0.34, 0.15);
        }

        private void playHitSound() {
            audio.playSweep(160, 70, 95, 0.42, 0.75);
        }

        private void playShieldSound() {
            audio.playTone(520, 110, 0.30, 0.10);
            audio.playTone(980, 130, 0.28, 0.08);
        }

        private void playUiSound() {
            audio.playTone(900, 55, 0.22, 0.12);
        }

        private void playBoostSound() {
            audio.playSweep(180, 360, 120, 0.34, 0.70);
        }

        private void playBoostMaxSpeedSound() {
            audio.playTone(1480, 90, 0.30, 0.18);
            audio.playSweep(920, 1320, 150, 0.24, 0.12);
        }

        private void playExplosionSound() {
            audio.playSweep(120, 42, 420, 0.72, 0.85);
            audio.playTone(64, 360, 0.55, 0.70);
        }

        private void checkWinner() {
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
            difficulty = selectedDifficulty;
            playerAircraft = playerAircraftConfirmed ? Aircraft.values()[selectedAircraftIndex] : randomAiAircraft();
            aiAircraft = aiAircraftConfirmed ? Aircraft.values()[selectedAiAircraftIndex] : randomAiAircraft();
            blue.applyAircraft(playerAircraft);
            red.applyAircraft(aiAircraft);
            red.attack = difficulty.aiAttack;
            red.speed = difficulty.aiSpeed;
            int minimumAiInterval = aiAircraft == Aircraft.TAIL_FLAME ? TAIL_FLAME_ATTACK_COOLDOWN : NORMAL_ATTACK_COOLDOWN;
            int aiInterval = Math.max(minimumAiInterval, difficulty.aiAttackInterval);
            aiTimer.setDelay(aiInterval);
            aiTimer.setInitialDelay(aiInterval);
            red.reset();
            blue.reset();
            projectiles.clear();
            shieldPickups.clear();
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
            setInputMethodLocked(true);
            message = difficulty.displayName(chinese) + " mode. WASD move, aim with cursor, fire with mouse.";
            audio.setVolume(volume);
            musicStep = 0;
            aiTimer.restart();
            musicTimer.restart();
            repaint();
        }

        private Aircraft randomAiAircraft() {
            Aircraft[] aircraft = Aircraft.values();
            return aircraft[random.nextInt(aircraft.length)];
        }

        private void showDifficultySelection() {
            red.reset();
            blue.reset();
            projectiles.clear();
            shieldPickups.clear();
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
            choosingDifficulty = true;
            showingHangar = false;
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

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (choosingDifficulty) {
                if (showingHangar) {
                    drawHangar(g);
                } else {
                    drawDifficultySelection(g);
                }
                g.dispose();
                return;
            }

            drawTitle(g);
            drawArena(g);
            drawShieldPickups(g);
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
            drawFighter(g, red, 90, 460, aiAircraft.color);
            drawFighter(g, blue, 600, 460, playerAircraft.color);
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
            g.setFont(new Font("SansSerif", Font.BOLD, 18));
            for (int i = 0; i < aircraft.length; i++) {
                Aircraft option = aircraft[i];
                int y = listY + i * (rowH + 12);
                boolean selected = i == hangarAircraftIndex;
                g.setColor(selected ? new Color(34, 43, 60) : new Color(22, 27, 36));
                g.fillRoundRect(listX, y, listW, rowH, 10, 10);
                g.setColor(selected ? option.color : new Color(82, 92, 112));
                g.setStroke(new BasicStroke(selected ? 3f : 1.4f));
                g.drawRoundRect(listX, y, listW, rowH, 10, 10);
                drawFutureJet(g, listX + 42, y + 36, 0, option.color, "");
                g.setColor(Color.WHITE);
                g.setFont(new Font("SansSerif", Font.BOLD, 16));
                drawClippedString(g, option.displayName(chinese), listX + 84, y + 31, listW - 98);
                g.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g.setColor(new Color(190, 202, 220));
                String stats = attr("attack") + " " + option.attack + "  " + attr("defense") + " " + option.defense + "  " + attr("speed") + " " + option.speed;
                drawClippedString(g, stats, listX + 84, y + 55, listW - 98);
                g.setFont(new Font("SansSerif", Font.BOLD, 18));
            }

            Aircraft selected = aircraft[hangarAircraftIndex];
            g.setColor(new Color(19, 24, 34));
            g.fillRoundRect(340, 116, 238, 430, 12, 12);
            g.setColor(selected.color);
            g.setStroke(new BasicStroke(2.4f));
            g.drawRoundRect(340, 116, 238, 430, 12, 12);
            drawLargeAircraftPreview(g, 459, 320, selected.color);

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

            g.setFont(new Font("SansSerif", Font.PLAIN, 15));
            g.setColor(new Color(220, 228, 240));
            int lineY = y + 42;
            String[] lines = {
                    attr("attack") + " " + aircraft.attack,
                    attr("defense") + " " + aircraft.defense,
                    attr("hp") + " " + aircraft.maxHp,
                    attr("skill") + " x" + aircraft.skillBonus,
                    attr("speed") + " " + aircraft.speed,
                    text("普攻: ", "Normal: ") + aircraft.normalDescription(chinese),
                    text("技能: ", "Skill: ") + aircraft.skillDescription(chinese)
            };
            for (String line : lines) {
                drawClippedString(g, line, x, lineY, 248);
                lineY += 32;
            }
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

        private void drawLargeAircraftPreview(Graphics2D g, double x, double y, Color color) {
            Graphics2D preview = (Graphics2D) g.create();
            preview.translate(x, y);
            preview.scale(2.4, 2.4);
            drawFutureJet(preview, 0, 0, 0, color, "", false);
            preview.dispose();
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
            int gap = aircraft.length > 2 ? 14 : 28;
            int cardW = aircraft.length > 2 ? (width - gap * (aircraft.length - 1)) / aircraft.length : 238;
            int startX = x + (width - cardW * aircraft.length - gap * (aircraft.length - 1)) / 2;
            for (int i = 0; i < aircraft.length; i++) {
                Aircraft option = aircraft[i];
                int cardX = startX + i * (cardW + gap);
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
                drawFutureJet(g, cardX + 42, y + 55, 0, option.color, "");
                g.setFont(new Font("SansSerif", Font.BOLD, aircraft.length > 2 ? 16 : 20));
                g.setColor(Color.WHITE);
                g.drawString(option.displayName(chinese), cardX + 78, y + 48);
                g.setFont(new Font("SansSerif", Font.PLAIN, 14));
                g.setColor(new Color(180, 192, 210));
                String roleText = playerSelected && aiSelected ? aircraftRoleText("P1", playerAircraftConfirmed) + " / " + aircraftRoleText("AI", aiAircraftConfirmed)
                        : playerSelected ? aircraftRoleText("P1", playerAircraftConfirmed)
                        : aiSelected ? aircraftRoleText("AI", aiAircraftConfirmed)
                        : text("可选", "Available");
                g.drawString(roleText, cardX + 78, y + 76);
            }
            return y + 120;
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
                g.drawString("AI ATK " + option.aiAttack, cardX + 22, y + 66);
                g.drawString("AI SPD " + option.aiSpeed, cardX + 22, y + 90);
                g.drawString("Fire " + option.aiAttackInterval + " ms", cardX + 22, y + 114);
            }
            return y + 140;
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
            drawCenteredText(g, "Player: WASD move, aim with cursor, mouse fire, P or Space pause, R restart", 76);
        }

        private void drawArena(Graphics2D g) {
            g.setColor(new Color(25, 29, 37));
            g.fillRoundRect(ARENA_LEFT, ARENA_TOP, ARENA_WIDTH, ARENA_HEIGHT, 12, 12);
            g.setColor(new Color(42, 58, 77));
            g.setStroke(new BasicStroke(1));
            for (int x = ARENA_LEFT + 24; x < ARENA_LEFT + ARENA_WIDTH; x += 48) {
                g.drawLine(x, ARENA_TOP, x, ARENA_TOP + ARENA_HEIGHT);
            }
            for (int y = ARENA_TOP + 24; y < ARENA_TOP + ARENA_HEIGHT; y += 48) {
                g.drawLine(ARENA_LEFT, y, ARENA_LEFT + ARENA_WIDTH, y);
            }
            g.setColor(new Color(68, 75, 90));
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(ARENA_LEFT, ARENA_TOP, ARENA_WIDTH, ARENA_HEIGHT, 12, 12);
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
                Point2D aim = neutronDeflectedLaserAim(blue, mouseX, mouseY);
                drawLaser(g, blue, aim.x, aim.y, blueLaserRemaining);
            }
            if (aiLaserRemaining > 0 && !gameOver) {
                Point2D aim = neutronDeflectedLaserAim(red, blue.x, blue.y);
                drawLaser(g, red, aim.x, aim.y, aiLaserRemaining);
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

        private void drawLaser(Graphics2D g, Fighter shooter, double aimX, double aimY, double remaining) {
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
            double distance = laserDistanceToArenaEdge(shooter, nx, ny);
            int endX = (int) Math.round(shooter.x + nx * distance);
            int endY = (int) Math.round(shooter.y + ny * distance);

            g.setColor(new Color(45, 168, 255, 70));
            g.setStroke(new BasicStroke(16f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine((int) shooter.x, (int) shooter.y, endX, endY);

            g.setColor(new Color(95, 215, 255, 150));
            g.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine((int) shooter.x, (int) shooter.y, endX, endY);

            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine((int) shooter.x, (int) shooter.y, endX, endY);

            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            g.setColor(new Color(195, 235, 255));
            g.drawString(String.format("LASER %.1fs", remaining), (int) shooter.x + 18, (int) shooter.y - 22);
        }

        private double laserDistanceToArenaEdge(Fighter shooter, double nx, double ny) {
            double maxDistance = Double.POSITIVE_INFINITY;
            if (nx > 0) {
                maxDistance = Math.min(maxDistance, (ARENA_LEFT + ARENA_WIDTH - shooter.x) / nx);
            } else if (nx < 0) {
                maxDistance = Math.min(maxDistance, (ARENA_LEFT - shooter.x) / nx);
            }

            if (ny > 0) {
                maxDistance = Math.min(maxDistance, (ARENA_TOP + ARENA_HEIGHT - shooter.y) / ny);
            } else if (ny < 0) {
                maxDistance = Math.min(maxDistance, (ARENA_TOP - shooter.y) / ny);
            }

            return Double.isFinite(maxDistance) ? Math.max(0, maxDistance) : ARENA_WIDTH;
        }

        private void drawProjectiles(Graphics2D g) {
            for (Projectile projectile : projectiles) {
                projectile.draw(g);
            }
        }

        private void drawShieldPickups(Graphics2D g) {
            for (ShieldPickup pickup : shieldPickups) {
                pickup.draw(g);
            }
        }

        private void drawFloatingTexts(Graphics2D g) {
            for (FloatingText floatingText : floatingTexts) {
                floatingText.draw(g);
            }
        }

        private void drawFighterAvatar(Graphics2D g, Fighter fighter, Color mainColor) {
            double targetX = fighter == blue ? mouseX : blue.x;
            double targetY = fighter == blue ? mouseY : blue.y;
            double angle = Math.atan2(targetY - fighter.y, targetX - fighter.x);
            drawActiveArmorEffects(g, fighter);
            drawFutureJet(g, fighter.x, fighter.y, angle, mainColor, fighter == blue ? "P" : "AI", fighter.boosting);
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
            g.setColor(new Color(28, 32, 40));
            g.fillRoundRect(x, y, 270, 184, 12, 12);
            g.setColor(mainColor);
            g.setStroke(new BasicStroke(3));
            g.drawRoundRect(x, y, 270, 184, 12, 12);

            g.setFont(new Font("SansSerif", Font.BOLD, 22));
            g.setColor(Color.WHITE);
            g.drawString(fighterDisplayName(fighter), x + 18, y + 34);

            g.setFont(new Font("SansSerif", Font.BOLD, 18));
            g.setColor(mainColor);
            g.drawString(fighter == blue ? text("玩家", "Player") : text("人机", "AI"), x + 18, y + 60);

            drawBar(g, "HP", fighter.hp, fighter.maxHp, x + 18, y + 82, 234, new Color(71, 196, 117));
            drawBar(g, "Skill", fighter.charge, MAX_CHARGE, x + 18, y + 118, 108, new Color(244, 196, 77));
            drawBar(g, "Boost", (int) Math.round(fighter.boostEnergy * 10), (int) Math.round(BOOST_MAX_ENERGY * 10),
                    x + 144, y + 118, 108, new Color(86, 202, 255));

            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            g.setColor(new Color(223, 228, 237));
            g.drawString("ATK " + fighter.attack, x + 18, y + 154);
            g.drawString("DEF " + fighter.defense, x + 86, y + 154);
            g.drawString("SPD " + fighter.speed, x + 154, y + 154);
            g.drawString("SKL " + fighter.skillBonus, x + 210, y + 154);
            if (fighter == blue && playerAircraft == Aircraft.BLUE_GLOW) {
                g.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g.setColor(new Color(190, 207, 226));
                g.drawString("Mode " + (blueBurstMode ? "Burst" : "Single") + " (C)", x + 18, y + 176);
            }
        }

        private void drawBar(Graphics2D g, String label, int value, int maxValue, int x, int y, int width, Color color) {
            int height = 22;
            double ratio = maxValue == 0 ? 0 : (double) value / maxValue;
            int filledWidth = (int) Math.round(width * ratio);

            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            g.setColor(new Color(210, 216, 226));
            g.drawString(label + ": " + value + "/" + maxValue, x, y - 8);

            g.setColor(new Color(47, 53, 64));
            g.fillRoundRect(x, y, width, height, 8, 8);
            g.setColor(color);
            g.fillRoundRect(x, y, filledWidth, height, 8, 8);
        }

        private void drawMessage(Graphics2D g) {
            g.setColor(new Color(28, 32, 40));
            g.fillRoundRect(90, 644, 780, 48, 10, 10);
            g.setFont(new Font("SansSerif", Font.BOLD, 17));
            g.setColor(new Color(238, 240, 245));
            drawCenteredText(g, message, 675);
        }

        private void drawPausedOverlay(Graphics2D g) {
            g.setColor(new Color(0, 0, 0, 145));
            g.fillRect(0, 0, WIDTH, HEIGHT);

            int centerX = WIDTH / 2;
            int centerY = 244;
            g.setColor(new Color(90, 210, 255, 45));
            g.fillOval(centerX - 124, centerY - 124, 248, 248);
            g.setColor(new Color(90, 210, 255));
            g.setStroke(new BasicStroke(3f));
            g.drawOval(centerX - 96, centerY - 96, 192, 192);
            g.drawLine(centerX - 150, centerY, centerX - 108, centerY);
            g.drawLine(centerX + 108, centerY, centerX + 150, centerY);

            g.fillRoundRect(centerX - 34, centerY - 52, 22, 92, 6, 6);
            g.fillRoundRect(centerX + 12, centerY - 52, 22, 92, 6, 6);

            g.setFont(new Font("SansSerif", Font.BOLD, 40));
            g.setColor(Color.WHITE);
            drawCenteredText(g, "PAUSED", centerY + 104);

            g.setFont(new Font("SansSerif", Font.PLAIN, 18));
            g.setColor(new Color(221, 231, 245));
            drawCenteredText(g, "Press P or Space to continue", centerY + 136);
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

        private final class BattleMouseListener extends MouseAdapter {
            @Override
            public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
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
                    handleSetupClick(event.getX(), event.getY(), event.getClickCount(), SwingUtilities.isRightMouseButton(event));
                    return;
                }

                if (gameOver) {
                    return;
                }
                mouseX = event.getX();
                mouseY = event.getY();

                boolean consumed = false;
                if (inputMatchesMouse(keyCharge, event)) {
                    chargeInputDown = true;
                    consumed = true;
                }
                if (inputMatchesMouse(keyAttack, event)) {
                    performAttackInput(event.getX(), event.getY());
                    consumed = true;
                }
                if (!consumed && SwingUtilities.isRightMouseButton(event)) {
                    playerSkillAttack(event.getX(), event.getY());
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

            if (x >= SETUP_PANEL_X && x <= SETUP_PANEL_X + SETUP_PANEL_WIDTH && y >= 132 && y <= 186) {
                selectedMenuSection = 0;
                aircraftMenuOpen = !aircraftMenuOpen;
                difficultyMenuOpen = false;
                repaint();
                return;
            }

            int difficultyHeaderY = aircraftMenuOpen ? 314 : 198;
            if (x >= SETUP_PANEL_X && x <= SETUP_PANEL_X + SETUP_PANEL_WIDTH && y >= difficultyHeaderY && y <= difficultyHeaderY + 54) {
                selectedMenuSection = 1;
                difficultyMenuOpen = !difficultyMenuOpen;
                aircraftMenuOpen = false;
                repaint();
                return;
            }

            if (aircraftMenuOpen && y >= 198 && y <= 308) {
                Aircraft[] aircraft = Aircraft.values();
                int gap = aircraft.length > 2 ? 14 : 28;
                int cardW = aircraft.length > 2 ? (SETUP_PANEL_WIDTH - gap * (aircraft.length - 1)) / aircraft.length : 238;
                int startX = SETUP_PANEL_X + (SETUP_PANEL_WIDTH - cardW * aircraft.length - gap * (aircraft.length - 1)) / 2;
                for (int i = 0; i < aircraft.length; i++) {
                    int cardX = startX + i * (cardW + gap);
                    if (x >= cardX && x <= cardX + cardW) {
                        selectSetupAircraft(i, clickCount >= 2);
                        selectedMenuSection = 0;
                        repaint();
                        return;
                    }
                }
            }

            int difficultyCardsY = aircraftMenuOpen ? 380 : 264;
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
                hangarAircraftIndex = selectedAircraftIndex >= 0 ? selectedAircraftIndex : 0;
                repaint();
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
            for (int i = 0; i < aircraft.length; i++) {
                int rowY = listY + i * (rowH + 12);
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
                mouseX = event.getX();
                mouseY = event.getY();
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                mouseX = event.getX();
                mouseY = event.getY();
                if (settingsOpen && draggingVolume) {
                    setVolumeFromMouse(event.getX(), 220 + 142, 250);
                    repaint();
                }
            }
        }

        private final class BattleKeyListener extends KeyAdapter {
            @Override
            public void keyPressed(KeyEvent event) {
                int key = event.getKeyCode();
                if (key == keyInputMethodLock && rebindingControlIndex < 0) {
                    toggleInputMethodLock();
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
                    if (showingHangar) {
                        if (key == KeyEvent.VK_ESCAPE || key == KeyEvent.VK_ENTER) {
                            showingHangar = false;
                        } else if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W || key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) {
                            hangarAircraftIndex = (hangarAircraftIndex - 1 + aircraft.length) % aircraft.length;
                        } else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S || key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) {
                            hangarAircraftIndex = (hangarAircraftIndex + 1) % aircraft.length;
                        }
                        repaint();
                        return;
                    }
                    if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) {
                        selectedMenuSection = (selectedMenuSection + 1) % 2;
                        aircraftMenuOpen = selectedMenuSection == 0;
                        difficultyMenuOpen = selectedMenuSection == 1;
                        repaint();
                    } else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) {
                        selectedMenuSection = (selectedMenuSection + 1) % 2;
                        aircraftMenuOpen = selectedMenuSection == 0;
                        difficultyMenuOpen = selectedMenuSection == 1;
                        repaint();
                    } else if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) {
                        if (selectedMenuSection == 0) {
                            selectedAircraftIndex = selectedAircraftIndex < 0 ? 0 : (selectedAircraftIndex - 1 + aircraft.length) % aircraft.length;
                        } else {
                            selectedDifficultyIndex = (selectedDifficultyIndex - 1 + difficulties.length) % difficulties.length;
                        }
                        repaint();
                    } else if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) {
                        if (selectedMenuSection == 0) {
                            selectedAircraftIndex = selectedAircraftIndex < 0 ? 0 : (selectedAircraftIndex + 1) % aircraft.length;
                        } else {
                            selectedDifficultyIndex = (selectedDifficultyIndex + 1) % difficulties.length;
                        }
                        repaint();
                    } else if (key == KeyEvent.VK_1) {
                        if (selectedMenuSection == 0) {
                            selectedAircraftIndex = 0;
                        } else {
                            selectedDifficultyIndex = 0;
                        }
                        repaint();
                    } else if (key == KeyEvent.VK_2) {
                        if (selectedMenuSection == 0) {
                            selectedAircraftIndex = 1;
                        } else {
                            selectedDifficultyIndex = 1;
                        }
                        repaint();
                    } else if (key == KeyEvent.VK_3) {
                        if (selectedMenuSection == 0 && aircraft.length > 2) {
                            selectedAircraftIndex = 2;
                        } else if (selectedMenuSection == 1) {
                            selectedDifficultyIndex = 2;
                        }
                        repaint();
                    } else if (key == KeyEvent.VK_ENTER) {
                        if (selectedMenuSection == 0) {
                            aircraftMenuOpen = !aircraftMenuOpen;
                        } else {
                            difficultyMenuOpen = !difficultyMenuOpen;
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
                    paused = !paused;
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
        private static final Tone STOP = new Tone(0, 0, 0, 0, 0);
        private final LinkedBlockingQueue<Tone> queue = new LinkedBlockingQueue<>(96);
        private volatile double volume = 0.55;

        private AudioEngine() {
            Thread thread = new Thread(this::runAudioLoop, "JetBattle-Audio");
            thread.setDaemon(true);
            thread.start();
        }

        private void setVolume(double volume) {
            this.volume = Math.max(0, Math.min(1, volume));
        }

        private void playTone(double frequency, int durationMs, double gain, double harmonicMix) {
            playSweep(frequency, frequency, durationMs, gain, harmonicMix);
        }

        private void playSweep(double startFrequency, double endFrequency, int durationMs, double gain, double harmonicMix) {
            if (volume <= 0 || durationMs <= 0 || startFrequency <= 0 || endFrequency <= 0) {
                return;
            }
            Tone tone = new Tone(startFrequency, endFrequency, durationMs, gain, harmonicMix);
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
            line.write(data, 0, data.length);
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

            private Tone(double startFrequency, double endFrequency, int durationMs, double gain, double harmonicMix) {
                this.startFrequency = startFrequency;
                this.endFrequency = endFrequency;
                this.durationMs = durationMs;
                this.gain = gain;
                this.harmonicMix = harmonicMix;
            }
        }
    }

    private enum Difficulty {
        EASY("Easy", "简单", 570, 115, 1050),
        MEDIUM("Medium", "中等", 720, 145, 780),
        HARD("Hard", "困难", 870, 190, 560);

        private final String name;
        private final String zhName;
        private final int aiAttack;
        private final int aiSpeed;
        private final int aiAttackInterval;

        Difficulty(String name, String zhName, int aiAttack, int aiSpeed, int aiAttackInterval) {
            this.name = name;
            this.zhName = zhName;
            this.aiAttack = aiAttack;
            this.aiSpeed = aiSpeed;
            this.aiAttackInterval = aiAttackInterval;
        }

        private String displayName(boolean chinese) {
            return chinese ? zhName : name;
        }

    }

    private enum Aircraft {
        TAIL_FLAME("Tail Flame", "尾焰", new Color(210, 65, 62), 720, 400, 18000, 1.4, 145),
        BLUE_GLOW("Blue Glow", "蓝光", new Color(70, 133, 232), 960, 320, 19000, 1.55, 150),
        NEUTRON_STAR("Neutron Star", "中子星", new Color(150, 85, 225), 700, 460, 22000, 1.5, 140);

        private final String name;
        private final String zhName;
        private final Color color;
        private final int attack;
        private final int defense;
        private final int maxHp;
        private final double skillBonus;
        private final int speed;

        Aircraft(String name, String zhName, Color color, int attack, int defense, int maxHp, double skillBonus, int speed) {
            this.name = name;
            this.zhName = zhName;
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
            };
        }

        private String skillDescription(boolean chinese) {
            return switch (this) {
                case TAIL_FLAME -> chinese ? "六枚追踪导弹" : "Six homing missiles";
                case BLUE_GLOW -> chinese ? "连续型蓝色激光" : "Continuous blue laser";
                case NEUTRON_STAR -> chinese ? "慢速奇点光球" : "Slow singularity orb";
            };
        }
    }

    private record Point2D(double x, double y) {
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
        private final boolean fromBlue;
        private final Color color;
        private final String hitMessage;
        private double chargeMultiplier;
        private final AmmoType ammoType;
        private final LaserType laserType;
        private boolean neutronSkillOrb;
        private boolean neutronDeflected;
        private boolean neutronImpactStarted;
        private Fighter neutronOwner;
        private AmmoType neutronResolvedType;
        private double neutronFlightRemaining;
        private double neutronPullRemaining;
        private double neutronDamageCarry;
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
            } else if (ammoType == AmmoType.LASER && laserType == LaserType.NON_CONTINUOUS && !missile) {
                drawEnergyOrb(g);
            } else if (missile) {
                drawMissile(g);
            } else {
                drawBeam(g);
            }
        }

        private void startNeutronImpact(AmmoType resolvedType, Fighter target) {
            neutronImpactStarted = true;
            neutronResolvedType = resolvedType;
            vx = 0;
            vy = 0;
            x = target.x;
            y = target.y;
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
            int r = (int) Math.round(neutronImpactStarted ? radius + 18 : radius + 10);
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
