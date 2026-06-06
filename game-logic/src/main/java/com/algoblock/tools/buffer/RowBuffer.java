package com.algoblock.tools.buffer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * 全局静态单例行缓冲区（类似 System.out 的无感知调用机制）
 */
public class RowBuffer {

    // 静态私有实例，volatile 保证多线程可见性与禁止指令重排
    private static volatile RowBuffer instance = null;

    // 内部实例变量
    private final Map<Long, String> storage;
    private int capacity;
    private long sequence = 0L;

    /**
     * 私有构造函数：禁止外部 new 实例化，默认容量 256
     */
    private RowBuffer() {
        this.capacity = 256;
        this.storage = new LinkedHashMap<Long, String>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, String> eldest) {
                return size() > RowBuffer.this.capacity;
            }
        };
    }

    /**
     * 核心基础设施：双重检查锁获取单例实例（线程安全的延迟初始化）
     */
    private static RowBuffer getInstance() {
        if (instance == null) {
            synchronized (RowBuffer.class) {
                if (instance == null) {
                    instance = new RowBuffer();
                }
            }
        }
        return instance;
    }

    // ================== 对外公开的静态方法（全局随时调用） ==================

    /**
     * 1. 传入一行字符串并记录
     * 任何代码均可直接调用：RowBuffer.append("文本内容");
     */
    public static void append(String rowLine) {
        if (rowLine == null) return;
        RowBuffer buffer = getInstance();
        synchronized (buffer) {
            buffer.storage.put(buffer.sequence++, rowLine);
        }
    }

    /**
     * 2. 返回最近的 k 条记录
     * 调用：List<String> logs = RowBuffer.getRecent(10);
     */
    public static List<String> getRecent(int k) {
        RowBuffer buffer = getInstance();
        synchronized (buffer) {
            List<String> allRows = new ArrayList<>(buffer.storage.values());
            int totalSize = allRows.size();
            if (k <= 0 || k >= totalSize) {
                return allRows;
            }
            return new ArrayList<>(allRows.subList(totalSize - k, totalSize));
        }
    }

    /**
     * 2的重载. 返回全部记录
     * 调用：List<String> allLogs = RowBuffer.getRecent();
     */
    public static List<String> getRecent() {
        RowBuffer buffer = getInstance();
        synchronized (buffer) {
            return new ArrayList<>(buffer.storage.values());
        }
    }

    /**
     * 3. 修改行数上限
     * 调用：RowBuffer.setCapacity(512);
     */
    public static void setCapacity(int newCapacity) {
        if (newCapacity <= 0) {
            throw new IllegalArgumentException("容量上限必须大于0");
        }
        RowBuffer buffer = getInstance();
        synchronized (buffer) {
            buffer.capacity = newCapacity;
            if (buffer.storage.size() > buffer.capacity) {
                List<Long> keys = new ArrayList<>(buffer.storage.keySet());
                int overflowCount = buffer.storage.size() - buffer.capacity;
                for (int i = 0; i < overflowCount; i++) {
                    buffer.storage.remove(keys.get(i));
                }
            }
        }
    }

    // 新增：用于记录上次读取到的位置
    private long lastReadSequence = 0L;

    // ... 现有的构造函数、append等方法保持不变 ...

    /**
     * 新增查询方法：返回自上次读取之后的所有新记录，并更新游标。
     * 这是一种消费型查询（Consume Query），即读后即标记。
     */
    public static List<String> getUnread() {
        RowBuffer buffer = getInstance();
        synchronized (buffer) {
            List<String> unreadRows = new ArrayList<>();
            // 从当前的读取游标开始，提取后续产生的所有日志
            for (Map.Entry<Long, String> entry : buffer.storage.entrySet()) {
                if (entry.getKey() >= buffer.lastReadSequence) {
                    unreadRows.add(entry.getValue());
                }
            }
            // 更新游标为当前的最大序列号
            buffer.lastReadSequence = buffer.sequence;
            return unreadRows;
        }
    }
}