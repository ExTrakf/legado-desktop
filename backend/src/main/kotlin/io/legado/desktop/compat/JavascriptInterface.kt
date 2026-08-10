package io.legado.desktop.compat

/**
 * 兼容注解：替代 android.webkit.JavascriptInterface。
 * 桌面版 JS 引擎为 Rhino，脚本可访问性由 RhinoClassShutter 控制，此注解仅作方法标记。
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
@Retention(AnnotationRetention.RUNTIME)
annotation class JavascriptInterface
