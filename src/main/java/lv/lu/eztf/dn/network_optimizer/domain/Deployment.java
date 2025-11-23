package lv.lu.eztf.dn.network_optimizer.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import lombok.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@PlanningEntity
public class Deployment {

    @PlanningId
    int id;

    // Service placement was a bad idea, timefold can assign planning variables
    @PlanningVariable(valueRangeProviderRefs = {"serviceRange"}, nullable = true)
    private Service service;

    @PlanningVariable(valueRangeProviderRefs = {"serverRange"}, nullable = true)
    private Server server;

    // Planning variable: When to start the deployment
    @PlanningVariable(valueRangeProviderRefs = {"dateRange"})
    Date dateFrom;

    // Planning variable: When to end the deployment
    @PlanningVariable(valueRangeProviderRefs = {"dateRange"})
    Date dateTo;

    // Planning list variable: Which requests this deployment serves
    @PlanningListVariable(valueRangeProviderRefs = {"requestRange"})
    List<Request> requests;

    /**
     * Checks if this deployment is active (has placement and requests)
     */
    public boolean isActive() {
        return server != null && service != null && requests != null && !requests.isEmpty();
    }

    /**
     * Checks if dates are valid (dateTo after dateFrom)
     */
    public boolean hasValidDates() {
        return dateFrom != null && dateTo != null && (dateTo.equals(dateFrom) || dateTo.after(dateFrom));
    }

    /**
     * Gets the total number of requests this deployment is serving
     */
    public int getRequestCount() {
        return requests != null ? requests.size() : 0;
    }
}
