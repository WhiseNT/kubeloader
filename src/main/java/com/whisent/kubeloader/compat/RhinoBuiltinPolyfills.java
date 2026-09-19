package com.whisent.kubeloader.compat;

import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Scriptable;

/**
 * 给 KubeJS 的 Rhino 补上它缺失、但现代脚本很常用的内建。
 *
 * <p>实测（rhino-416294-7104526）缺这些：{@code globalThis}、{@code [].at()}、
 * {@code ''.at()}、{@code Object.hasOwn}、{@code Object.fromEntries}、
 * {@code ''.replaceAll()}、{@code [].flat()}、{@code [].flatMap()}、
 * {@code ''.matchAll()}。它们都是纯函数级的东西，可以在 JS 侧补出来，
 * 不必动引擎。补完之后 {@link ModernJSSyntaxGuard} 对它们的告警也就自然消失了。</p>
 *
 * <p>注意 {@code Promise} 和 {@code Proxy} 不在这里：它们不是纯函数能够补出来的
 * （前者要事件循环，后者要引擎能力），需要时请给脚本单独加 {@code //engine: graaljs}。</p>
 *
 * <p>{@link #PRELUDE} 里一律用 ES5 写法（{@code var} / {@code function} / 不用模板串），
 * 因为它是在<b>转换之前</b>直接求值的——万一用了 {@code ??}、{@code ...} 这些
 * Rhino 不支持或会算错的写法，补丁自己就先炸了。</p>
 */
public class RhinoBuiltinPolyfills {

    /**
     * 补丁脚本。每一项都先判断 {@code typeof ... === 'undefined'}，
     * 因此重复安装既不会报错，也不会覆盖引擎里本来就有的实现。
     */
    public static final String PRELUDE = """
            (function (global) {
                // ---- globalThis ----
                // 顶层 this 就是全局对象；GraalJS 那边本来就有 globalThis，
                // 补上它能让同一份脚本在两个引擎里表现一致
                if (typeof global.globalThis === 'undefined') {
                    global.globalThis = global;
                }

                // ---- Array.prototype.at ----
                if (typeof Array.prototype.at === 'undefined') {
                    Array.prototype.at = function (index) {
                        var len = this.length >>> 0;
                        // ToIntegerOrInfinity：NaN 当 0，±Infinity 保持无穷
                        var n = Number(index) || 0;
                        n = n < 0 ? Math.ceil(n) : Math.floor(n);
                        if (n < 0) {
                            n += len;
                        }
                        return (n >= 0 && n < len) ? this[n] : undefined;
                    };
                }

                // ---- String.prototype.at ----
                // 与原生一致：取的是单个 UTF-16 码元（和 charAt 一样），负下标从尾部算
                if (typeof String.prototype.at === 'undefined') {
                    String.prototype.at = function (index) {
                        var str = String(this);
                        var len = str.length;
                        var n = Number(index) || 0;
                        n = n < 0 ? Math.ceil(n) : Math.floor(n);
                        if (n < 0) {
                            n += len;
                        }
                        return (n >= 0 && n < len) ? str.charAt(n) : undefined;
                    };
                }

                // ---- Object.hasOwn ----
                if (typeof Object.hasOwn === 'undefined') {
                    Object.hasOwn = function (obj, key) {
                        if (obj === null || obj === undefined) {
                            throw new TypeError('Object.hasOwn called on null or undefined');
                        }
                        return Object.prototype.hasOwnProperty.call(Object(obj), key);
                    };
                }

                // ---- Object.fromEntries ----
                if (typeof Object.fromEntries === 'undefined') {
                    Object.fromEntries = function (iterable) {
                        if (iterable === null || iterable === undefined) {
                            throw new TypeError('Object.fromEntries requires an iterable');
                        }
                        var result = {};
                        // 这里用 for...of（Rhino 支持，实测 数组 / Map / Set / 生成器都能给出
                        // entry 数组）。特意不走 Map 的 forEach：它的回调是 (value, key)，
                        // 形状与 entry 不同，混在一起会算错。
                        for (var entry of iterable) {
                            result[entry[0]] = entry[1];
                        }
                        return result;
                    };
                }

                // ---- String.prototype.replaceAll ----
                // 交给 replace 去做替换：这样 $& / $1 这类替换模式的语义与原生一致
                if (typeof String.prototype.replaceAll === 'undefined') {
                    String.prototype.replaceAll = function (search, replacement) {
                        var str = String(this);
                        if (search instanceof RegExp) {
                            if (!search.global) {
                                throw new TypeError('replaceAll requires a global RegExp');
                            }
                            return str.replace(search, replacement);
                        }
                        // 字符串模式也走正则：`'a.b'.replaceAll('.', '-')` 不该把 . 当通配符
                        var escaped = String(search).replace(/[.*+?^${}()|[\\]\\\\]/g, '\\\\$&');
                        return str.replace(new RegExp(escaped, 'g'), replacement);
                    };
                }

                // ---- Array.prototype.flat ----
                if (typeof Array.prototype.flat === 'undefined') {
                    Array.prototype.flat = function (depth) {
                        var d = (depth === undefined) ? 1 : Number(depth);
                        if (isNaN(d)) {
                            d = 0;
                        } else if (!isFinite(d)) {
                            d = Infinity;
                        } else {
                            d = Math.floor(d);
                            if (d < 0) {
                                d = 0;
                            }
                        }
                        var result = [];
                        (function flatten(arr, level) {
                            var len = arr.length >>> 0;
                            for (var i = 0; i < len; i++) {
                                if (!(i in arr)) {
                                    continue; // 空位直接跳过，与原生一致
                                }
                                var v = arr[i];
                                if (level > 0 && Array.isArray(v)) {
                                    flatten(v, level - 1);
                                } else {
                                    result.push(v);
                                }
                            }
                        })(this, d);
                        return result;
                    };
                }

                // ---- Array.prototype.flatMap ----
                if (typeof Array.prototype.flatMap === 'undefined') {
                    Array.prototype.flatMap = function (callback, thisArg) {
                        var result = [];
                        var len = this.length >>> 0;
                        for (var i = 0; i < len; i++) {
                            if (!(i in this)) {
                                continue;
                            }
                            var v = callback.call(thisArg, this[i], i, this);
                            if (Array.isArray(v)) {
                                for (var j = 0; j < v.length; j++) {
                                    result.push(v[j]);
                                }
                            } else {
                                result.push(v);
                            }
                        }
                        return result;
                    };
                }

                // ---- String.prototype.matchAll ----
                // 返回数组（原生是迭代器）：数组既能 for...of 也能下标取用，更好用。
                // 正则用克隆，避免改动调用方的 lastIndex。
                if (typeof String.prototype.matchAll === 'undefined') {
                    String.prototype.matchAll = function (regexp) {
                        var str = String(this);
                        var source;
                        var flags = 'g';
                        if (regexp instanceof RegExp) {
                            if (!regexp.global) {
                                throw new TypeError('matchAll requires a global RegExp');
                            }
                            source = regexp.source;
                            if (regexp.ignoreCase) {
                                flags += 'i';
                            }
                            if (regexp.multiline) {
                                flags += 'm';
                            }
                        } else {
                            source = String(regexp);
                        }
                        var re = new RegExp(source, flags);
                        var matches = [];
                        var m;
                        while ((m = re.exec(str)) !== null) {
                            matches.push(m);
                            if (m[0] === '') {
                                re.lastIndex++; // 空匹配时手动前进，否则死循环
                            }
                        }
                        return matches;
                    };
                }
            })(this);
            """;

    private RhinoBuiltinPolyfills() {
    }

    /**
     * 把补丁装进给定的 Rhino 全局作用域；失败时直接抛出，由调用方决定怎么处理
     * （运行时那边会记日志并放行脚本，回归检查那边则让用例失败）。
     *
     * <p>这里刻意不依赖日志框架：本类要能在只有 Rhino 的回归检查里被直接调用。</p>
     */
    public static void install(Context cx, Scriptable scope) {
        cx.evaluateString(scope, PRELUDE, "kubeloader:builtin-polyfills", 1, null);
    }
}
