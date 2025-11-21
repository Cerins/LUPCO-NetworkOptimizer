package lv.lu.eztf.dn.network_optimizer;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.SolverConfig;
import lv.lu.eztf.dn.network_optimizer.domain.*;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class SetupTest {
    public static void main(String[] args) {

        SolverFactory<DeploymentPlan> solverFactory = SolverFactory.create(
                new SolverConfig()
                        .withSolutionClass(DeploymentPlan.class)
                        .withEntityClasses(ServicePlacement.class, Deployment.class)
                        .withEasyScoreCalculatorClass(TrivialScoreCalculation.class)
                        .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
        );

        Solver<DeploymentPlan> solver = solverFactory.buildSolver();

        DeploymentPlan plan = new DeploymentPlan();

        // Servers
        Server s1 = new Server(1, "Server A", 8, 32f, 500f, null, null);
        Server s2 = new Server(2, "Server B", 4, 16f, 250f, null, null);
        plan.setServerList(List.of(s1, s2));

        // Requests
        Request r1 = new Request(1, "Request Alpha", new Date(), 0, 0f, null);
        Request r2 = new Request(2, "Request Beta", new Date(), 0, 0f, null);
        plan.setRequests(List.of(r1, r2));

        // Services
        Service svc1 = new Service(1, "Auth", 2f, 4f, 10f, 0);
        Service svc2 = new Service(2, "DB", 4f, 8f, 50f, 0);

        // Service Placements
        ServicePlacement sp1 = new ServicePlacement(1, s1, svc1);
        ServicePlacement sp2 = new ServicePlacement(2, s2, svc2);
        plan.setPlacements(List.of(sp1, sp2));

        // Dates for deployments
        Date now = new Date();
        Calendar cal = Calendar.getInstance();

        cal.setTime(now);
        cal.add(Calendar.DAY_OF_MONTH, 10);
        Date tenDaysLater = cal.getTime();

        cal.setTime(now);
        cal.add(Calendar.DAY_OF_MONTH, 5);
        Date fiveDaysLater = cal.getTime();

        // Deployments
        Deployment d1 = new Deployment(1, null, null, now, tenDaysLater);
        Deployment d2 = new Deployment(2, null, null, now, fiveDaysLater);
        plan.setDeployments(List.of(d1, d2));

        // Solve
        DeploymentPlan result = solver.solve(plan);
        System.out.println(result);
    }
}
