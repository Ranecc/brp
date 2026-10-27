package dev.brp.mod.desk;

import java.util.List;

/**
 * The agent-fed cache of the tenant's PENDING forms (P2-10): the runtime
 * polls the approval inbox and publishes here; FormTerminalMenu serves what
 * the desk holds, falling back to the sample tray when the wire is quiet.
 * Pure Java — no Minecraft imports, no IO, just paper.
 */
public final class FormDesk {

    /** A pending form as the desk sees it (moved from the menu: shared shape). */
    public record PendingForm(String formKey, String title, String submitter) {}

    private static volatile List<PendingForm> pending = List.of();

    private FormDesk() {}

    public static void publish(List<PendingForm> forms) {
        pending = List.copyOf(forms);
    }

    public static List<PendingForm> forms() {
        return pending;
    }

    /** @return true when the desk has heard from the backend this session. */
    public static boolean live() {
        return !pending.isEmpty();
    }

    /** Tests only. */
    static void clear() {
        pending = List.of();
    }
}
