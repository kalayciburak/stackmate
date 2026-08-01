package io.github.kalayciburak.stackmate.inference.jvm;

import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.tensor.KvBufferCache;
import java.lang.reflect.Field;

/** Contains the Jlama 0.8.4 session-cache compatibility workaround inside the vendor adapter. */
final class JlamaSessionCleaner {

    private static final Field CACHE_FIELD = cacheField();

    private JlamaSessionCleaner() {}

    static void clear(AbstractModel model) {
        try {
            ((KvBufferCache) CACHE_FIELD.get(model)).close();
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("cannot clear the Jlama session cache", failure);
        }
    }

    static boolean supported() {
        return CACHE_FIELD.trySetAccessible();
    }

    private static Field cacheField() {
        try {
            Field field = AbstractModel.class.getDeclaredField("kvBufferCache");
            if (!field.trySetAccessible()) {
                throw new IllegalStateException("Jlama session cache is not accessible");
            }
            return field;
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
}
