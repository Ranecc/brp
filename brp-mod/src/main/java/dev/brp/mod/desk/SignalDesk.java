package dev.brp.mod.desk;

/**
 * The agent-fed count of pending signal orders (P2-10): the Dispatch
 * Terminal's status line reads it. Zero or no poll yet = the desk is calm.
 */
public final class SignalDesk {

    private static volatile int pendingCount;

    private SignalDesk() {}

    public static void publish(int count) {
        pendingCount = Math.max(0, count);
    }

    public static int pendingCount() {
        return pendingCount;
    }

    /** Tests only. */
    static void clear() {
        pendingCount = 0;
    }
}
