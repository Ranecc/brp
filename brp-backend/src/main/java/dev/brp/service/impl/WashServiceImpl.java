package dev.brp.service.impl;

import dev.brp.common.annotation.Atomic;
import dev.brp.entity.AccountEntity;
import dev.brp.entity.JournalEntryEntity;
import dev.brp.entity.WashFindingEntity;
import dev.brp.repository.AccountRepository;
import dev.brp.repository.JournalEntryRepository;
import dev.brp.repository.WashFindingRepository;
import dev.brp.service.NotificationService;
import dev.brp.service.WashService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Wash-cycle detection (docs/12 §11): inside one tenant, every journal is
 * a directed weighted edge DEBIT account -> CREDIT account. Kosaraju SCC;
 * a finding is an SCC (size >= 2) containing cycles of length <= 4, scored
 * by roundness (round amounts), tightness (legs close in time) and share
 * of the week's volume. Cycles per se are legal commerce — the score
 * carries the accusation, and the accusation is paperwork, not punishment.
 */
@Service
public class WashServiceImpl implements WashService {

    private static final int MAX_CYCLE_LEN = 4;
    private static final int MAX_DFS_STEPS = 20_000;   // belt and braces on hostile graphs

    private final JournalEntryRepository journal;
    private final AccountRepository accounts;
    private final WashFindingRepository findings;
    private final NotificationService notifications;
    private final ZoneId zone;
    private final int windowDays;

    public WashServiceImpl(JournalEntryRepository journal, AccountRepository accounts,
                           WashFindingRepository findings, NotificationService notifications,
                           @Value("${brp.reports.zone:UTC}") String zoneId,
                           @Value("${brp.wash.window-days:7}") int windowDays) {
        this.journal = journal;
        this.accounts = accounts;
        this.findings = findings;
        this.notifications = notifications;
        this.zone = ZoneId.of(zoneId);
        this.windowDays = windowDays;
    }

    @Override
    public ScanReport scan() {
        LocalDate weekOf = LocalDate.now(zone).with(DayOfWeek.MONDAY);
        Instant since = Instant.now().minus(windowDays, ChronoUnit.DAYS);
        List<String> tenants = accounts.findAll().stream()
                .map(AccountEntity::getTenantId).distinct().sorted().toList();
        int filed = 0;
        for (String tenant : tenants) {
            filed += scanTenant(tenant, weekOf, since);
        }
        return new ScanReport(tenants.size(), filed);
    }

    @Override
    public ScanReport scan(String tenantId) {
        LocalDate weekOf = LocalDate.now(zone).with(DayOfWeek.MONDAY);
        Instant since = Instant.now().minus(windowDays, ChronoUnit.DAYS);
        return new ScanReport(1, scanTenant(tenantId, weekOf, since));
    }

    @Atomic
    int scanTenant(String tenantId, LocalDate weekOf, Instant since) {
        Map<Long, String> names = accounts.findByTenantIdOrderById(tenantId).stream()
                .collect(Collectors.toMap(AccountEntity::getId, AccountEntity::getAccountNo));
        if (names.size() < 2) {
            return 0;   // one account cannot launder against itself, however hard it tries
        }
        List<JournalEntryEntity> entries = journal
                .findByTenantIdAndCreatedAtGreaterThanEqual(tenantId, since);
        if (entries.isEmpty()) {
            return 0;
        }

        // journal -> edge: DEBIT account pays CREDIT account
        Map<String, List<JournalEntryEntity>> byJournal = entries.stream()
                .collect(Collectors.groupingBy(JournalEntryEntity::getJournalId,
                        LinkedHashMap::new, Collectors.toList()));
        Map<String, Set<String>> adj = new HashMap<>();
        Map<String, Long> outAmount = new HashMap<>();
        List<JournalEntryEntity> sccLines = new ArrayList<>();
        Map<String, List<JournalEntryEntity>> linesByAccount = new HashMap<>();
        Map<Long, String> accountNode = new HashMap<>();   // graph nodes are account NOS
        long weekVolume = 0;
        for (var lineGroup : byJournal.values()) {
            JournalEntryEntity debit = null;
            JournalEntryEntity credit = null;
            for (JournalEntryEntity e : lineGroup) {
                sccLines.add(e);
                weekVolume += e.getAmountCents();
                String node = names.getOrDefault(e.getAccountId(),
                        "?" + e.getAccountId());
                accountNode.putIfAbsent(e.getAccountId(), node);
                linesByAccount.computeIfAbsent(node, k -> new ArrayList<>()).add(e);
                if ("DEBIT".equals(e.getDirection())) {
                    debit = e;
                } else {
                    credit = e;
                }
            }
            if (debit == null || credit == null) {
                continue;
            }
            String from = names.getOrDefault(debit.getAccountId(), "?" + debit.getAccountId());
            String to = names.getOrDefault(credit.getAccountId(), "?" + credit.getAccountId());
            if (from.equals(to)) {
                continue;
            }
            adj.computeIfAbsent(from, k -> new HashSet<>()).add(to);
            outAmount.merge(from, debit.getAmountCents(), Long::sum);
        }
        if (adj.isEmpty()) {
            return 0;
        }

        int filed = 0;
        for (Set<String> scc : kosaraju(adj).values()
                .stream().filter(c -> c.size() >= 2).toList()) {
            long cycles = countShortCycles(scc, adj);
            if (cycles == 0) {
                continue;
            }
            List<String> sortedAccounts = new ArrayList<>(new TreeSet<>(scc));
            String hash = Integer.toHexString(sortedAccounts.hashCode());
            if (findings.existsByTenantIdAndAccountsHashAndWeekOf(tenantId, hash, weekOf)) {
                continue;   // one suspicion per account set per week
            }
            List<JournalEntryEntity> lines = new ArrayList<>();
            for (String account : sortedAccounts) {
                lines.addAll(linesByAccount.getOrDefault(account, List.of()));
            }
            double score = score(lines, amountOf(lines), weekVolume);
            findings.save(new WashFindingEntity(tenantId, weekOf,
                    String.join(",", sortedAccounts), hash,
                    minCycleLen(scc, adj), (int) Math.min(cycles, Integer.MAX_VALUE), score));
            filed++;
        }
        if (filed > 0) {
            notifications.enqueue("monetary.wash.suspected", "monetary.wash.suspected",
                    tenantId, filed);
        }
        return filed;
    }

    /** Roundness 40% + tightness 30% + volume share 30%; all in [0,1]. */
    static double score(List<JournalEntryEntity> lines, long amountSum, long weekVolume) {
        if (lines.isEmpty()) {
            return 0;
        }
        long round = lines.stream().filter(e -> e.getAmountCents() % 1000 == 0
                && e.getAmountCents() > 0).count();
        double roundness = (double) round / lines.size();
        Instant min = lines.stream().map(JournalEntryEntity::getCreatedAt).min(Instant::compareTo).orElseThrow();
        Instant max = lines.stream().map(JournalEntryEntity::getCreatedAt).max(Instant::compareTo).orElseThrow();
        long spanMinutes = ChronoUnit.MINUTES.between(min, max);
        double tightness = spanMinutes <= 60 ? 1.0 : spanMinutes <= 1440 ? 0.5 : 0.0;
        double volumeShare = weekVolume > 0
                ? Math.min(1.0, (double) amountSum / weekVolume) : 0;
        return Math.round((roundness * 0.4 + tightness * 0.3 + volumeShare * 0.3) * 100.0) / 100.0;
    }

    private static long amountOf(List<JournalEntryEntity> lines) {
        return lines.stream().filter(e -> "DEBIT".equals(e.getDirection()))
                .mapToLong(JournalEntryEntity::getAmountCents).sum();
    }

    // --- graph machinery: Kosaraju SCC, then bounded cycle counting ---

    /** @return canonical representative -> members (every SCC, any size). */
    static Map<String, Set<String>> kosaraju(Map<String, Set<String>> adj) {
        Set<String> nodes = new HashSet<>(adj.keySet());
        adj.values().forEach(nodes::addAll);
        List<String> finish = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (String start : nodes) {
            if (visited.contains(start)) {
                continue;
            }
            Deque<Object[]> stack = new ArrayDeque<>();
            stack.push(new Object[]{start, adj.getOrDefault(start, Set.of()).iterator()});
            visited.add(start);
            while (!stack.isEmpty()) {
                Object[] frame = stack.peek();
                @SuppressWarnings("unchecked")
                var it = (java.util.Iterator<String>) frame[1];
                if (it.hasNext()) {
                    String next = it.next();
                    if (visited.add(next)) {
                        stack.push(new Object[]{next, adj.getOrDefault(next, Set.of()).iterator()});
                    }
                } else {
                    finish.add((String) frame[0]);
                    stack.pop();
                }
            }
        }
        Map<String, Set<String>> reverse = new HashMap<>();
        for (var entry : adj.entrySet()) {
            for (String to : entry.getValue()) {
                reverse.computeIfAbsent(to, k -> new HashSet<>())
                        .add(entry.getKey());
            }
        }
        Map<String, Set<String>> components = new HashMap<>();
        Set<String> assigned = new HashSet<>();
        for (int i = finish.size() - 1; i >= 0; i--) {
            String root = finish.get(i);
            if (assigned.contains(root)) {
                continue;
            }
            Set<String> component = new HashSet<>();
            Deque<String> stack = new ArrayDeque<>();
            stack.push(root);
            assigned.add(root);
            while (!stack.isEmpty()) {
                String n = stack.pop();
                component.add(n);
                for (String next : reverse.getOrDefault(n, Set.of())) {
                    if (assigned.add(next)) {
                        stack.push(next);
                    }
                }
            }
            String canonical = component.stream().min(String::compareTo).orElseThrow();
            components.put(canonical, component);
        }
        return components;
    }

    /** Simple cycles of <= 4 nodes; each counted once (start = minimal node). */
    static long countShortCycles(Set<String> nodes, Map<String, Set<String>> adj) {
        long cycles = 0;
        int budget = MAX_DFS_STEPS;
        for (String start : nodes) {
            Deque<List<String>> stack = new ArrayDeque<>();
            stack.push(List.of(start));
            while (!stack.isEmpty() && budget-- > 0) {
                List<String> path = stack.pop();
                String last = path.get(path.size() - 1);
                for (String next : adj.getOrDefault(last, Set.of())) {
                    if (next.equals(start)) {
                        if (path.size() >= 2 && start.equals(
                                path.stream().min(String::compareTo).orElse(start))) {
                            cycles++;
                        }
                        continue;
                    }
                    if (path.size() >= MAX_CYCLE_LEN || path.contains(next)
                            || !nodes.contains(next)) {
                        continue;
                    }
                    List<String> longer = new ArrayList<>(path);
                    longer.add(next);
                    stack.push(longer);
                }
            }
        }
        return cycles;
    }

    private static int minCycleLen(Set<String> scc, Map<String, Set<String>> adj) {
        for (int len = 2; len <= MAX_CYCLE_LEN; len++) {
            for (String start : scc) {
                Deque<List<String>> stack = new ArrayDeque<>();
                stack.push(List.of(start));
                while (!stack.isEmpty()) {
                    List<String> path = stack.pop();
                    String last = path.get(path.size() - 1);
                    for (String next : adj.getOrDefault(last, Set.of())) {
                        if (next.equals(start) && path.size() == len - 1) {
                            return len;
                        }
                        if (path.size() >= len || path.contains(next) || !scc.contains(next)) {
                            continue;
                        }
                        List<String> longer = new ArrayList<>(path);
                        longer.add(next);
                        stack.push(longer);
                    }
                }
            }
        }
        return 0;
    }

    @Override
    public List<dev.brp.dto.MonetaryDtos.WashFindingResponse> findings() {
        return findings.findTop100ByOrderByScoreDesc().stream()
                .map(f -> new dev.brp.dto.MonetaryDtos.WashFindingResponse(
                        f.getId(), f.getTenantId(), f.getAccountsCsv(),
                        f.getCycleLen(), f.getScore(), String.valueOf(f.getCreatedAt())))
                .toList();
    }

    @Scheduled(fixedDelayString = "${brp.wash.scan-ms:3600000}")
    public void scheduled() {
        scan();
    }
}
