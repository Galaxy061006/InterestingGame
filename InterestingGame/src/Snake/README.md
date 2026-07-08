# Snake / 贪吃蛇

## 简介 / Overview

`Snake` 是一个使用 Java Swing 编写的经典贪吃蛇小游戏。玩家先选择蛇的颜色，然后控制蛇在 25 x 25 的网格中移动、吃食物并获得分数。撞到墙壁或自己的身体后游戏结束。

`Snake` is a classic Snake game built with Java Swing. The player chooses a snake color, controls the snake on a 25 x 25 grid, eats food, and gains score. The game ends when the snake hits a wall or its own body.

## 运行方式 / How to Run

项目使用标准 Java 类库，不依赖第三方库。建议使用 JDK 17 或更高版本。

This project only uses the standard Java library and has no third-party dependencies. JDK 17 or newer is recommended.

在项目根目录执行：

Run from the project root:

```powershell
javac -encoding UTF-8 -d out src\Snake\Main.java
java -cp out Snake.Main
```

也可以在 IntelliJ IDEA 中直接运行 `Snake.Main.main()`。

You can also run `Snake.Main.main()` directly in IntelliJ IDEA.

## 默认操作 / Controls

| 场景 / Scene | 操作 / Action | 按键 / Key |
| --- | --- | --- |
| 选择颜色 / Color Selection | 上一个颜色 / Previous Color | `Left` / `A` |
| 选择颜色 / Color Selection | 下一个颜色 / Next Color | `Right` / `D` |
| 选择颜色 / Color Selection | 开始游戏 / Start Game | `Space` / `Enter` |
| 游戏中 / In Game | 移动 / Move | Arrow Keys or `W` / `A` / `S` / `D` |
| 游戏中 / In Game | 暂停/继续 / Pause/Resume | `P` or `Space` |
| 游戏结束 / Game Over | 返回颜色选择 / Return to Color Selection | `Space` |

游戏中不能直接向当前方向的反方向转向，例如向右移动时不能立刻向左。

The snake cannot immediately reverse direction. For example, it cannot turn left while moving right.

## 游戏规则 / Rules

1. 开始时先选择蛇的颜色。
2. 蛇会按固定速度自动前进。
3. 吃到红色食物后，分数加 1，蛇身增长一格。
4. 新食物会随机刷新在蛇身以外的位置。
5. 撞到边界或蛇身时游戏结束。

1. Choose the snake color before starting.
2. The snake moves automatically at a fixed speed.
3. Eating red food increases the score by 1 and grows the snake by one tile.
4. New food appears randomly on a tile not occupied by the snake.
5. The game ends when the snake hits the border or itself.

## 游戏配置 / Game Settings

| 配置 / Setting | 当前值 / Current Value |
| --- | --- |
| 网格大小 / Grid Size | `25 x 25` |
| 单格像素 / Tile Size | `24 px` |
| 窗口大小 / Window Size | `600 x 600 px` |
| 移动间隔 / Move Interval | `110 ms` |
| 初始长度 / Initial Length | `3` |

## 可选颜色 / Available Colors

游戏内置 5 种蛇身配色：

The game includes 5 snake color themes:

- Green
- Blue
- Yellow
- Pink
- White

## 代码结构 / Code Structure

当前目录包含：

Current directory:

```text
src/Snake/
├── Main.java
└── README.md
```

`Main.java` 内部包含：

`Main.java` contains:

- `main`: 创建 Swing 窗口并加载游戏面板。Creates the Swing window and loads the game panel.
- `SnakeGame`: 主游戏面板，负责游戏循环、绘制、输入处理和状态切换。Main game panel for the game loop, rendering, input handling, and state changes.
- `Direction`: 蛇移动方向枚举。Snake movement direction enum.
- `Point`: 网格坐标。Grid coordinate record.
- `SnakeColor`: 蛇头和蛇身的颜色配置。Color configuration for the snake head and body.

## 后续扩展建议 / Future Improvements

- 增加难度选择，调整移动速度。Add difficulty selection and adjustable movement speed.
- 增加最高分记录。Add high score tracking.
- 增加障碍物、特殊食物或限时模式。Add obstacles, special food, or timed mode.
- 将游戏配置拆出为独立常量或配置类，方便后续扩展。Move game settings into separate constants or configuration classes for easier expansion.
