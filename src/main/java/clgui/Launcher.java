package clgui;

/**
 * Non-JavaFX entry point used by the shaded jar.
 *
 * When JavaFX is bundled inside a single fat jar, the JVM refuses
 * to run a class that extends {@code Application} as the main
 * class. A tiny launcher class that just forwards to
 * {@code App.main} sidesteps that restriction.
 */
public final class Launcher {
    public static void main(String[] args) {
        App.main(args);
    }
}
