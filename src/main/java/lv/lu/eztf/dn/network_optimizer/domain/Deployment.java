package lv.lu.eztf.dn.network_optimizer.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import ai.timefold.solver.core.api.domain.variable.ShadowVariable;
import lombok.*;

import java.util.*;

@Setter
@Getter
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
        // Actually deployment because of service dependencies results
        // in the fact that active deployments are those which have a server and service
        return server != null && service != null; // && requests != null && !requests.isEmpty();
    }

    /**
     * Checks if dates are valid (dateTo after dateFrom)
     */
    public boolean hasValidDates() {
        return dateFrom != null && dateTo != null && (dateTo.equals(dateFrom) || dateTo.after(dateFrom));
    }

    public List<Service> getImpactedServices() {
        if(this.getService() != null) {
            return this.getService().totalContainedServices();
        }
        return new ArrayList<>();
    }

    /**
     * Gets the total number of requests this deployment is serving
     */
    public int getRequestCount() {
        return requests != null ? requests.size() : 0;
    }

    /**
     * Custom constructor without shadow variable
     */
    public Deployment(int id, Service service, Server server, Date dateFrom, Date dateTo, List<Request> requests) {
        this.id = id;
        this.service = service;
        this.server = server;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.requests = requests;
        // requestLatencies is managed by Timefold
    }

    /**
     * Gets the computed latency for a specific request in this deployment
     */
//    public Float getLatencyForRequest(Request request) {
//        if (requestLatencies == null) {
//            return null;
//        }
//        return requestLatencies.get(request);
//    }

}
