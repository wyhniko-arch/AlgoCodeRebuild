# 去看docs里的log
README太短总有傻子跟着念

编译：

./gradlew :game-logic:clean :game-logic:installDist

运行：

Windows端：

.\game-logic\build\install\game-logic\bin\game-logic.bat

其他端自己看

# Q&A：

## cloud端拉取后是否需要运行gradle wrapper --gradle-version 8.9

不需要，去看docs里2026年6月4日的log01.txt日志，

gradlew

gradlew.bat

gradle-wrapper.properties

gradle-wrapper.jar

已经是“自带Gradle版本”的工程了。

只要执行：

./gradlew :game-core:clean :game-core:installDist

它会自动根据 gradle-wrapper.properties 下载并使用 Gradle 8.9