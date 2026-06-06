package com.algoblock;

import com.algoblock.logic.Logic;

import java.util.Arrays;
import java.util.Scanner;

public class Main {
    // ==========================================
    // main：演示用，a = interact(输入)，打印 a
    // ==========================================
 
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            String[] a = Logic.interact(line);
            System.out.println(Arrays.toString(a));
        }
    }
}