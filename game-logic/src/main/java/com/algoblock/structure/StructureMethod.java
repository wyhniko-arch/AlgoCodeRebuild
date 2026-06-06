package com.algoblock.structure;
import com.algoblock.context.RuntimeContext;

public interface StructureMethod {
    /**
     * 返回带有通配符的模板字符串，例如 "Queue(@).add"
     */
    String getPattern();

    /**
     * @param args   由引擎统一提取的变量参数数组（对应模板中的 @）
     * @param context 运行时上下文
     */
    void execute(String[] args, RuntimeContext context);
}