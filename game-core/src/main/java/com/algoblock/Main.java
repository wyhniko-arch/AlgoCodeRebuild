package com.algoblock;

import java.util.Scanner;

import com.algoblock.logic.Logic;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, World! Engine Starting...");
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("请输入关卡索引 (从0开始)，或输入 exit 退出:");
            String input = scanner.nextLine().trim();
            if ("exit".equalsIgnoreCase(input)) {
                break;
            }

            try {
                int levelIndex = Integer.parseInt(input);
                Logic core = new Logic();
                core.run(levelIndex);
            } catch (NumberFormatException e) {
                System.out.println("[ERROR] 输入无效，请输入数字。");
            }
        }

        scanner.close();
        System.out.println("已退出。");
    }
}