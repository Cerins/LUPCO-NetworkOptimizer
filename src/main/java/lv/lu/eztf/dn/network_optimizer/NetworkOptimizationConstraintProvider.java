package lv.lu.eztf.dn.network_optimizer;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import lv.lu.eztf.dn.network_optimizer.domain.*;
import static ai.timefold.solver.core.api.score.stream.Joiners.*;

public class NetworkOptimizationConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                // Hard constraints
                everyRequestAssigned(factory),
                noRequestMultipleAssignments(factory),
                requestMustHaveService(factory),
                requestMustHaveServer(factory),
                validDateRange(factory),
                // Soft constraints
                deploymentCosts(factory)
        };
    }
    // HARD
    Constraint everyRequestAssigned(ConstraintFactory factory) {
        return factory.forEach(Request.class)
                .ifNotExists(Deployment.class,
                        filtering((request, deployment) ->
                                deployment.getRequests().contains(request)
                        )
                )
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Request must be assigned at least once");
    }
    Constraint noRequestMultipleAssignments(ConstraintFactory factory) {
        return factory.forEachUniquePair(
                        Deployment.class,
                        filtering((d1, d2) ->
                                d1 != d2 && d1.getRequests().stream().anyMatch(d2.getRequests()::contains)
                        )
                )
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Request must not be assigned more than once");
    }

    Constraint requestMustHaveService(ConstraintFactory factory) {
        return factory.forEach(Deployment.class)
                .penalize(HardSoftScore.ONE_HARD,
                        deployment -> {
                            if (deployment.getService() == null ||
                                    deployment.getService().getName() == null) {
                                // one penalty per request
                                return deployment.getRequests().size();
                            }
                            return 0;
                        })
                .asConstraint("Assigned request must have a valid service");
    }
    Constraint requestMustHaveServer(ConstraintFactory factory) {
        return factory.forEach(Deployment.class)
                .penalize(HardSoftScore.ONE_HARD,
                        deployment -> {
                            if (deployment.getServer() == null) {
                                // one penalty per request
                                return deployment.getRequests().size();
                            }
                            return 0;
                        })
                .asConstraint("Assigned request must have a valid server");
    }
    Constraint validDateRange(ConstraintFactory factory) {
        return factory.forEach(Deployment.class)
                .filter(d -> !d.hasValidDates())
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Invalid date range");
    }
    // SOFT
    // Currently punish servers which have server running or service
    Constraint deploymentCosts(ConstraintFactory factory) {
        return factory.forEach(Deployment.class)
                .penalize(HardSoftScore.ONE_SOFT,
                        d ->
                                (d.getService() != null ? 5 : 0) +
                                (d.getServer() != null ? 3 : 0)
                                )
                .asConstraint("Deployments cost money");
    }
}
