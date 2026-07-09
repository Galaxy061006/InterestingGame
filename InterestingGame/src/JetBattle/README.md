# Jet Battle / 喷气战机对战

## 简介 / Overview

`JetBattle` 是一个使用 Java Swing 编写的单文件空战小游戏。玩家驾驶蓝方战机，在固定竞技场中移动、瞄准、攻击并积攒能量，与红方 AI 战机对战。

`JetBattle` is a single-file air battle game built with Java Swing. The player controls the blue aircraft, moves inside a fixed arena, aims, attacks, charges energy, and fights against a red AI aircraft.

## 运行方式 / How to Run

项目使用标准 Java 类库，不依赖第三方库。建议使用 JDK 17 或更高版本。

This project only uses the standard Java library and has no third-party dependencies. JDK 17 or newer is recommended.

在项目根目录执行：

Run from the project root:

```powershell
javac -encoding UTF-8 -d out src\JetBattle\JetBattleGame.java
java -cp out JetBattle.JetBattleGame
```

也可以在 IntelliJ IDEA 中直接运行 `JetBattleGame.main()`。

You can also run `JetBattleGame.main()` directly in IntelliJ IDEA.

## 默认操作 / Controls

| 操作 / Action | 默认按键 / Default Key |
| --- | --- |
| 移动 / Move | `W` / `A` / `S` / `D` |
| 加速 / Boost | `Shift` |
| 普通攻击 / Normal Attack | 鼠标左键 / Left Mouse Button |
| 技能攻击 / Skill Attack | 鼠标右键 / Right Mouse Button |
| 暂停/继续 / Pause/Resume | `P` or `Space` |
| 返回配置界面 / Return to Setup | `R` |
| Blue Glow 攻击模式切换 / Toggle Blue Glow Attack Mode | `C` |
| 输入法锁定 / Input Method Lock | `F12` |

设置面板中可以切换语言、调节音量并重新绑定按键。

The settings panel supports language switching, volume adjustment, and key rebinding.

## 游戏流程 / Gameplay

1. 在开始界面选择玩家战机和 AI 难度。
2. 点击开始后进入对战。
3. 通过普通攻击命中敌人来积累能量。
4. 能量满后使用当前战机的技能攻击。
5. 任一方生命值归零后游戏结束，可以按 `R` 返回配置界面。

1. Choose the player aircraft and AI difficulty on the setup screen.
2. Start the battle.
3. Hit the enemy with normal attacks to charge energy.
4. Use the current aircraft's skill when the energy bar is full.
5. The battle ends when either side reaches 0 HP. Press `R` to return to setup.

## 战机 / Aircraft

| 战机 / Aircraft | 特点 / Feature | 普通攻击 / Normal Attack | 技能 / Skill |
| --- | --- | --- | --- |
| Tail Flame | 防御较高，导弹追踪能力强 / Higher defense with strong homing missiles | 追踪导弹 / Homing missile | 六发追踪导弹 / Six homing missiles |
| Blue Glow | 攻击和速度较高，适合持续压制 / Higher attack and speed, good for pressure | 双翼射击 / Twin wing shots | 持续蓝色激光 / Continuous blue laser |
| Neutron Star | 生命和防御较高，偏控制 / Higher HP and defense, control-focused | 紫色非连续激光球 / Purple non-continuous laser orb | 慢速奇点光球 / Slow singularity orb |
| Venus | 属性均衡，可通过装甲模式强化攻防与速度 / Balanced stats with boosted offense, defense, and speed in armor mode | 机头长条激光 / Nose laser bar | 星门强化装甲 / Star-gate reinforced armor |

`Blue Glow` 可以按 `C` 在单发模式和爆发模式之间切换。爆发模式会快速打出一轮攻击，但每轮存在冷却，并且连发倍率会逐步降低。

`Blue Glow` can press `C` to switch between single-shot mode and burst mode. Burst mode fires a fast attack round, but each round has a cooldown and its burst multiplier gradually decreases.

`Venus` 会在机身后方开启星门，装甲组件从星门中飞出并与机体结合。装甲完成后持续 6.5 秒，攻击、防御和速度得到提升，并获得 1600 点额外装甲耐久；技能倍率保持不变。此时武器切换为两侧激光炮，每次点按会向光标交汇发射两束非连续激光。

`Venus` opens a star gate behind the aircraft, with armor pieces emerging and attaching to the body. Once assembled, the 6.5-second mode boosts attack, defense, and speed and grants 1600 armor HP while leaving the skill multiplier unchanged. Its weapons become twin laser cannons that fire two converging non-continuous beams toward the cursor.

金星强化期间的攻击间隔会从 `600ms` 缩短到 `420ms`。尾焰技能导弹采用较低初速与更高极速，发射后会呈现更明显的持续加速过程。

During Venus's reinforced mode, its attack interval is reduced from `600ms` to `420ms`. Tail Flame's skill missiles now launch more slowly but accelerate to a higher maximum speed.

金星普通激光的伤害小幅降低，但飞行速度更快；强化双炮的激光速度进一步提升，并可在命中后继续穿透。中子星速度调整为 `135`，技能光球每首次偏转一枚敌方弹药时，会恢复基础攻击命中回能的 `20%`。

Venus's normal laser deals slightly less damage but travels faster. Its reinforced twin beams are faster still and continue through targets after hitting. Neutron Star's speed is now `135`; each enemy projectile first deflected by its skill orb restores `20%` of normal hit charge.

## 自定义战机 / Custom Aircraft

欢迎玩家自行设计新的角色或战机，并将它们加入到游戏中。可以从战机名称、颜色、生命值、攻击力、防御力、速度、普通攻击和技能效果等方面进行设计。

Players are welcome to design their own characters or aircraft and add them to the game. A new aircraft can be designed around its name, color, HP, attack, defense, speed, normal attack, and skill effect.

如果你有新的玩法想法，也可以继续扩展，例如新的 AI 行为、护甲类型、弹药类型、地图机制或本地双人模式。

You can also extend the game with new ideas, such as new AI behavior, armor types, ammo types, arena mechanics, or local two-player mode.

## 难度 / Difficulty

| 难度 / Difficulty | AI 特点 / AI Behavior |
| --- | --- |
| Easy | AI 攻击、速度和出手频率较低 / Lower AI attack, speed, and attack frequency |
| Medium | 默认平衡难度 / Default balanced difficulty |
| Hard | AI 攻击、速度和出手频率更高 / Higher AI attack, speed, and attack frequency |

## 机制说明 / Mechanics

- **能量 / Energy**: 命中敌方后积累，满值后可以释放当前战机的技能。Hits on the enemy charge energy. A full energy bar allows the aircraft to use its skill.
- **加速 / Boost**: 移动时按住 `Shift` 可以短时间加速，加速能量会消耗并在停止加速后恢复。Hold `Shift` while moving to boost. Boost energy is consumed while active and recovers afterward.
- **护甲拾取物 / Armor Pickups**: 竞技场中会刷新护甲道具，拾取后获得限时效果。Armor pickups appear in the arena and grant temporary effects.
- **音效 / Audio**: 程序内置简单合成音效；如果系统没有可用音频输出，游戏仍会正常运行。The game includes simple synthesized audio. If no audio output is available, gameplay still works.
- **输入法锁定 / Input Method Lock**: 默认使用 `F12` 切换输入法锁定，避免中文输入法影响游戏按键。Press `F12` to toggle input method lock and avoid IME interference with gameplay controls.

## 代码结构 / Code Structure

当前目录包含：

Current directory:

```text
src/JetBattle/
├── JetBattleGame.java
└── README.md
```

`JetBattleGame.java` 内部包含：

`JetBattleGame.java` contains:

- `main`: 创建 Swing 窗口并加载游戏面板。Creates the Swing window and loads the game panel.
- `BattlePanel`: 主游戏循环、绘制、输入处理、AI、碰撞和战斗逻辑。Main game loop, rendering, input handling, AI, collision, and combat logic.
- `AudioEngine`: 简单音频合成与播放队列。Simple audio synthesis and playback queue.
- `Difficulty`, `Aircraft`, `AmmoType`, `LaserType`, `ArmorType`: 游戏配置枚举。Game configuration enums.
- `Fighter`, `Projectile`, `ShieldPickup`, `FloatingText`: 核心游戏对象。Core game objects.

## 后续扩展建议 / Future Improvements

- 将战机、武器、护甲拆成独立类，降低 `JetBattleGame.java` 的体积。Split aircraft, weapons, and armor into separate classes to reduce the size of `JetBattleGame.java`.
- 增加资源目录，用图片和音频文件替代纯代码绘制与合成音效。Add an asset directory and use images/audio files instead of pure code drawing and synthesized audio.
- 为伤害计算、能量积累和 AI 行为补充单元测试。Add tests for damage calculation, energy gain, and AI behavior.
- 增加本地双人模式或更多战机。Add local two-player mode or more aircraft.
