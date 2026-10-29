package dev.brp.service;

import dev.brp.dto.LoadDtos.DeferredJobResponse;
import dev.brp.dto.LoadDtos.FileDeferredJobRequest;

import java.util.List;

/**
 * Demand response (docs/13 §5): tenants file deferrable work, the scheduler
 * walks it into the cheapest feasible hours (填谷), the governor holds the
 * line closed outside the window, and overload sheds DR first — it is by
 * definition the interruptible load.
 */
public interface DemandResponseService {

    DeferredJobResponse file(String tenantId, FileDeferredJobRequest req);

    List<DeferredJobResponse> list(String tenantId);

    /** FILED/AWARDED -> CANCELLED with reservation refunded. */
    DeferredJobResponse cancel(String tenantId, long jobId);

    /** Award FILED jobs to feasible windows; expire what missed its deadline. */
    void awardDueJobs();

    /** Dispatch OPEN signals for windows that started, CLOSE for ones that ended. */
    void dispatchDueSignals();

    /** Overload shedding: live reading passed in so tests can hold the grid still. */
    void shedIfOverloaded(double liveMspt);

    /**
     * Credit cutoff (docs/13 §6): running windows shed (CLOSE + refund),
     * un-started ones expire with refund. Filing stays blocked while the
     * credit desk holds the tenant.
     */
    void suspendTenant(String tenantId);
}
