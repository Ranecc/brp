package dev.brp;

import dev.brp.common.annotation.Atomic;
import dev.brp.common.annotation.Cached;
import dev.brp.common.annotation.Idempotent;
import dev.brp.player.PlayerEntity;
import dev.brp.player.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the shared annotation kit end-to-end on real beans (docs/06 section 7). */
@SpringBootTest
@Import(AnnotationKitTest.Target.class)
class AnnotationKitTest {

    @Autowired Target target;
    @Autowired PlayerRepository players;

    @Component
    static class Target {
        private final AtomicInteger idemCalls = new AtomicInteger();
        private final AtomicInteger cacheCalls = new AtomicInteger();
        final PlayerRepository players;

        Target(PlayerRepository players) {
            this.players = players;
        }

        // Counters are read via methods: the CGLIB proxy's own fields stay
        // uninitialized, so field reads on the injected bean would NPE.
        int idemCalls() { return idemCalls.get(); }
        int cacheCalls() { return cacheCalls.get(); }

        @Idempotent(key = "#k")
        public String idem(String k) {
            return "v-" + k + "-" + idemCalls.incrementAndGet();
        }

        @Cached(key = "#k", ttlSeconds = 60)
        public String cached(String k) {
            return "c-" + k + "-" + cacheCalls.incrementAndGet();
        }

        @Atomic
        public void atomicInsert(String uuid, boolean boom) {
            players.save(new PlayerEntity(uuid, "someone"));
            if (boom) {
                throw new IllegalStateException("boom");
            }
        }
    }

    @Test
    void idempotentReplaysStoredResponseWithoutReExecuting() {
        String first = target.idem("k1");
        String replay = target.idem("k1");
        // Same stored response, underlying method ran exactly once
        assertThat(replay).isEqualTo(first);
        assertThat(target.idemCalls()).isEqualTo(1);
        // A different key is a different operation
        target.idem("k2");
        assertThat(target.idemCalls()).isEqualTo(2);
    }

    @Test
    void cachedSkipsExecutionForHotKeys() {
        String a1 = target.cached("hot");
        String a2 = target.cached("hot");
        assertThat(a2).isEqualTo(a1);
        assertThat(target.cacheCalls()).isEqualTo(1);
        target.cached("cold");
        assertThat(target.cacheCalls()).isEqualTo(2);
    }

    @Test
    void atomicRollsBackAllWritesOnFailure() {
        String uuid = UUID.randomUUID().toString();
        assertThatThrownBy(() -> target.atomicInsert(uuid, true))
                .isInstanceOf(IllegalStateException.class);
        assertThat(players.existsById(uuid)).isFalse();

        target.atomicInsert(uuid, false);
        assertThat(players.existsById(uuid)).isTrue();
    }
}
