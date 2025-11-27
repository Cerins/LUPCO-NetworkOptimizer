package lv.lu.eztf.dn.network_optimizer.rest;

import ai.timefold.solver.core.api.score.analysis.ScoreAnalysis;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.api.solver.SolverStatus;
import lombok.extern.slf4j.Slf4j;
import lv.lu.eztf.dn.network_optimizer.domain.DeploymentPlan;
import org.springframework.boot.autoconfigure.batch.BatchProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@RestController
@Slf4j
public class NetworkOptimizerController {
    private final SolverManager<DeploymentPlan, String> solverManager;
    private final SolutionManager<DeploymentPlan, HardSoftScore> solutionManager;
    private final ConcurrentMap<String, Job> jobIdToJob = new ConcurrentHashMap<>();

    public NetworkOptimizerController(SolverManager<DeploymentPlan, String> solverManager,
                          SolutionManager<DeploymentPlan, HardSoftScore> solutionManager) {
        this.solverManager = solverManager;
        this.solutionManager = solutionManager;
    }

    @GetMapping
    public Collection<String> list() {
        Collection<String> existingJobIds = jobIdToJob.keySet();
        Collection<String> combinedJobIds = new ArrayList<>(existingJobIds);
        combinedJobIds.add("test");
        return combinedJobIds;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public String solve(@RequestBody DeploymentPlan problem) {
        String jobId = UUID.randomUUID().toString();
        jobIdToJob.put(jobId, Job.ofSolution(problem));
        solverManager.solveBuilder()
                .withProblemId(jobId)
                .withProblemFinder(jobId_ -> jobIdToJob.get(jobId).solution)
                .withBestSolutionConsumer(solution -> jobIdToJob.put(jobId, Job.ofSolution(solution)))
                .withExceptionHandler((jobId_, exception) -> {
                    jobIdToJob.put(jobId, Job.ofException(exception));
                })
                .run();
        return jobId;
    }

    @GetMapping(value = "/{jobId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DeploymentPlan getEVRPsolution(
            @PathVariable("jobId") String jobId) {
        DeploymentPlan solution = getSolutionAndCheckForExceptions(jobId);
        SolverStatus solverStatus = solverManager.getSolverStatus(jobId);
        solution.solverStatus = solverStatus;
        return solution;
    }

    @GetMapping(value = "/score/{jobId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ScoreAnalysis<HardSoftScore> analyze(
             @PathVariable("jobId") String jobId) {
        DeploymentPlan solution = getSolutionAndCheckForExceptions(jobId);
        return solutionManager.analyze(solution);
    }

    private DeploymentPlan getSolutionAndCheckForExceptions(String jobId) {
        Job job = jobIdToJob.get(jobId);
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job ID '" + jobId + "' not found.");
        }
        if (job.exception != null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Solving job '" + jobId + "' failed: " + job.exception.getMessage(),
                    job.exception);
        }
        return job.solution;
    }

    private record Job(DeploymentPlan solution, Throwable exception) {

        static Job ofSolution(DeploymentPlan solution) {
            return new Job(solution, null);
        }

        static Job ofException(Throwable error) {
            return new Job(null, error);
        }
    }
}
