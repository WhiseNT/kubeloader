package com.whisent.kubeloader.scripts.tserase;

public final class TsEraser {

    public static String eraseTypes(String src) {
        if (src == null || src.isEmpty()) return src;
        return TsEraseProcessor.erase(src);
    }

    /* ---------------------- Test -------------------------- */
    public static void main(String[] args) {
        String[] cases = {
                // 1. Line-start type with line break =
                """
        type A =
          | 'x'
          | 'y'
        const a:A = 'x'
        """,

                // 2. interface multiline + generics + inheritance
                """
        interface Foo<T>
           {
          name: string
        }
        function f(x: Foo<number>): void {}
        """,

                // 3. Function inline type parameter (should not be commented)
                """
        function f<T>(x: T): x is T { return true }
        """,

                // 4. Object literal value protection (should not erase colon)
                """
        const o = {
          key: "val",
          num: 123,
          fn: (a: number): string => String(a)
        }
        """,

                // 5. Ternary operator protection
                """
        const x = true ? 1 : 2
        const y = cond ? (a as string) : (b as number)
        """,

                // 6. Template string / string with colon
                """
        const s = `aaa:bbb`
        const t = 'ccc:ddd'
        """,

                // 7. Class generics + implements
                """
        class MyMap<K, V> implements Map<K, V> {
          get(k: K): V | undefined { return void 0 }
        }
        """,

                // 8. Nested generics + union types
                """
        type Deep = Map<string, Array<{ id: number; name: string } | null>>
        """
        };

        for (int i = 0; i < cases.length; i++) {
            System.out.println("========== CASE " + (i + 1) + " ==========");
            System.out.println(TsEraser.eraseTypes(cases[i]));
        }
    }
}
