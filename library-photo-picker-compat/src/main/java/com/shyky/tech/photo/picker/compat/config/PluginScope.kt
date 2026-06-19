package com.shyky.tech.photo.picker.compat.config

import com.shyky.tech.photo.picker.compat.pipeline.PickerPlugin

/**
 * 插件的 DSL 作用域 — 在 `plugins { }` 块中使用。
 *
 * 支持 `+pluginInstance` 语法添加插件到选择器中。
 * 插件可注册自定义装饰器、触摸拦截器或覆盖层。
 */
@PickerDsl
class PluginScope {
    /** 收集的插件列表 */
    internal val list = mutableListOf<PickerPlugin>()

    /**
     * 一元加号操作符重载 — 支持 `+somePlugin` 的 DSL 语法。
     * @param this 要添加的插件实例
     */
    operator fun PickerPlugin.unaryPlus() {
        list.add(this)
    }
}
