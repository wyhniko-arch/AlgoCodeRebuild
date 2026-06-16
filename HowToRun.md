# 如何运行

编译指令：

./gradlew :game-logic:clean :game-logic:installDist

运行指令（Windows端，其他端自己看）：

.\game-logic\build\install\game-logic\bin\game-logic.bat

更新了词法系统，加入了选关系统，游戏进度数据落盘存储。注意！存档位置取决于运行时 cwd，不是相对 .bat 或 jar 本身。

# Q&A：

## pull后是否需要运行gradle wrapper --gradle-version 8.9

不需要（详见 docs 里 2026 年 6 月 4 日的 log01.txt ）

gradlew

gradlew.bat

gradle-wrapper.properties

gradle-wrapper.jar

已经是“自带Gradle版本”的工程了。只要执行：

./gradlew :game-logic:clean :game-logic:installDist

它会自动根据 gradle-wrapper.properties 下载并使用 Gradle 8.9