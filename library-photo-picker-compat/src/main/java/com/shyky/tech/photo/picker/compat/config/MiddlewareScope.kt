package com.shyky.tech.photo.picker.compat.config

import com.shyky.tech.photo.picker.compat.pipeline.PickerMiddleware

/**
 * 中间件的 DSL 作用域 — 在 `middleware { }` 块中使用。
 *
 * 支持 `+middlewareInstance` 语法添加中间件到链中。
 */
@PickerDsl
class MiddlewareScope {
    /** 收集的中间件列表 */
    internal val list = mutableListOf<PickerMiddleware>()

    /**
     * 一元加号操作符重载 — 支持 `+someMiddleware` 的 DSL 语法。
     * @param this 要添加的中间件实例
     */
    operator fun PickerMiddleware.unaryPlus() {
        list.add(this)
    }
}

/**
 * Kotlin DSL 标记注解 — 防止隐式接收者的作用域泄漏。
 *
 * 标记了 [PickerDsl] 的作用域中，不能隐式访问外层同名作用域的方法/属性，
 * 强制显式指定接收者，避免 DSL 嵌套时的歧义。
 */
@DslMarker
annotation class PickerDsl
