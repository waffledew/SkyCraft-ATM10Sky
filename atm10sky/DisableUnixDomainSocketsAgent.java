import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import sun.misc.Unsafe;

/** Optional Java 21 workaround for Windows systems with an unusable AF_UNIX pipe. */
public final class DisableUnixDomainSocketsAgent {
    public static void premain(String arguments, Instrumentation instrumentation) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Unsafe unsafe = (Unsafe) field.get(null);
        Class<?> sockets = Class.forName("sun.nio.ch.UnixDomainSockets");
        Field supported = sockets.getDeclaredField("supported");
        unsafe.putBoolean(unsafe.staticFieldBase(supported), unsafe.staticFieldOffset(supported), false);
    }
}
