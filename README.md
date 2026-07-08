# InterestingGame / Java 小游戏合集

## 简介 / Overview

`InterestingGame` 是一个 Java Swing 小游戏合集，目前包含两个可直接运行的桌面小游戏：

`InterestingGame` is a collection of small Java Swing desktop games. It currently includes two runnable games:

- `JetBattle`: 喷气战机对战游戏 / A jet battle game against AI
- `Snake`: 经典贪吃蛇游戏 / A classic Snake game

项目使用标准 Java 类库，不依赖第三方库。

The project only uses the standard Java library and has no third-party dependencies.

## 项目结构 / Project Structure

```text
InterestingGame/
├── src/
│   ├── JetBattle/
│   │   ├── JetBattleGame.java
│   │   └── README.md
│   └── Snake/
│       ├── Main.java
│       └── README.md
├── .gitignore
└── InterestingGame.iml
```

## 运行环境 / Requirements

- JDK 17 或更高版本 / JDK 17 or newer
- IntelliJ IDEA 或命令行 Java 工具 / IntelliJ IDEA or command-line Java tools

## 运行 JetBattle / Run JetBattle

在仓库根目录执行：

Run from the repository root:

```powershell
javac -encoding UTF-8 -d out InterestingGame\src\JetBattle\JetBattleGame.java
java -cp out JetBattle.JetBattleGame
```

## 运行 Snake / Run Snake

在仓库根目录执行：

Run from the repository root:

```powershell
javac -encoding UTF-8 -d out InterestingGame\src\Snake\Main.java
java -cp out Snake.Main
```

## 游戏说明 / Game Documentation

每个游戏目录中都有独立的中英双语 README，包含更详细的玩法、按键和代码结构说明。

Each game folder has its own bilingual README with more details about gameplay, controls, and code structure.

- [JetBattle README](InterestingGame/src/JetBattle/README.md)
- [Snake README](InterestingGame/src/Snake/README.md)

## 自定义与建议 / Customization and Ideas

欢迎玩家自行设计新的角色、战机、技能、关卡或玩法机制，并尝试把它们加入到游戏中。也欢迎提出新的想法，例如新的敌人 AI、双人模式、特殊道具、计分系统或更多视觉效果。

Players are welcome to design their own characters, aircraft, skills, levels, or gameplay mechanics and try adding them to the games. New ideas are also welcome, such as improved enemy AI, two-player mode, special items, scoring systems, or more visual effects.

## 备注 / Notes

- `out/` 和 `.class` 文件是编译输出，不需要提交到 GitHub。
- `.gitignore` 已经配置为忽略常见 IDE 和编译输出文件。
- 这些游戏主要用于 Java Swing 练习和小游戏开发实验。

- `out/` and `.class` files are build outputs and do not need to be committed to GitHub.
- `.gitignore` is configured to ignore common IDE and build output files.
- These games are mainly for Java Swing practice and small game development experiments.
