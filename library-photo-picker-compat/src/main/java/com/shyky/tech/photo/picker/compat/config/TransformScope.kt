package com.shyky.tech.photo.picker.compat.config

import com.shyky.tech.photo.picker.compat.pipeline.ItemTransform

/**
 * 条目变换的 DSL 作用域 — 在 `transforms { }` 块中使用。
 *
 * 支持 `+transformInstance` 语法添加变换到链中。
 * 变换用于过滤（返回 null 则移除）或转换数据条目。
 */
@PickerDsl
class TransformScope {
    /** 收集的变换列表 */
    internal val list = mutableListOf<ItemTransform>()

    /**
     * 一元加号操作符重载 — 支持 `+someTransform` 的 DSL 语法。
     * @param this 要添加的变换实例
     */
    operator fun ItemTransform.unaryPlus() {
        list.add(this)
    }
}
