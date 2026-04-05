//package com.whisent.kubeloader.graal.wrapper;
//
//import org.graalvm.polyglot.Context;
//import org.graalvm.polyglot.Value;
//
//// 这俩空壳真的还有留着的必要吗
//public class WrapperHelper {
//    public static Object wrapObject(Object obj) {
//        return obj;
//    }
//
//    public static Object wrapReturnValue(Object result) {
//        return result;
//    }
//
//    public static Object unwrap(Object obj) {
//        return obj;
//    }
//
//    public static void registerInContext(Context context) {
//        context.getBindings("js").putMember("WrapperHelper", new WrapperHelperBindings());
//    }
//
//    public static class WrapperHelperBindings {
//        public Object wrap(Object obj) {
//            return obj;
//        }
//
//        public void registerWrapper(Class<?> targetClass, Value wrapperFunction) {
//            TypeWrapperRegistry.register(targetClass, value -> {
//                if (wrapperFunction.canExecute()) {
//                    Value result = wrapperFunction.execute(value);
//                    if (result.isHostObject()) return result.asHostObject();
//                }
//                return null;
//            });
//        }
//    }
//}
