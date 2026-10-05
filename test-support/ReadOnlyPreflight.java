import java.nio.file.Path;
import java.lang.reflect.*;

/** Only loads the existing policy; starts neither plugin nor worker. */
public final class ReadOnlyPreflight {
    public static void main(String[] args) throws Exception {
        Class<?> policy = Class.forName("com.nordfjell.nordcommandspaper.CommandSettings");
        Method load = policy.getDeclaredMethod("load", Path.class); load.setAccessible(true);
        Object result = load.invoke(null, Path.of(args[0]));
        Method labels = policy.getDeclaredMethod("allowed"); labels.setAccessible(true);
        System.out.println("COMMAND_POLICY_READ_ONLY_OK labels=" + ((java.util.Set<?>)labels.invoke(result)).size());
    }
}
