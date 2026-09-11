/** Executes only property accessors using the explicit external API substitutes in the local jar.
 * No Android lifecycle, WebView, account, or network operation is executed. */
public final class ProgressSetterCheck {
    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName("com.luma.downloader.auth.EmbeddedLoginActivity");
        Object instance = type.getConstructor().newInstance();
        java.lang.reflect.Method getter = type.getDeclaredMethod("getPageLoadProgress");
        java.lang.reflect.Method setter = type.getDeclaredMethod("setPageLoadProgress", int.class);
        getter.setAccessible(true);
        setter.setAccessible(true);
        if (!Integer.valueOf(0).equals(getter.invoke(instance))) throw new AssertionError("initial state");
        for (int value : new int[]{1, 25, 99, 100, 0}) {
            setter.invoke(instance, value);
            if (!Integer.valueOf(value).equals(getter.invoke(instance))) throw new AssertionError("state " + value);
        }
        for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
            if (method.getName().equals("setProgress")) throw new AssertionError("colliding setter declared");
        }
        // The framework method is inherited, not replaced or hidden by the renamed property.
        if (type.getMethod("setProgress", int.class).getDeclaringClass() == type) throw new AssertionError("base method overridden");
        System.out.println("PASS: initial 0, writes/reads 1/25/99/100/0, no declared setProgress, inherited API retained.");
        System.out.println("SCOPE: local JVM with explicit external type substitutes, NOT Android/Compose rendering.");
    }
}
