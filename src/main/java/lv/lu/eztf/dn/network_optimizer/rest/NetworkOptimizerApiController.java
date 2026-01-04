package lv.lu.eztf.dn.network_optimizer.rest;

import ai.timefold.solver.core.api.score.ScoreExplanation;
import ai.timefold.solver.core.api.score.analysis.ScoreAnalysis;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatch;
import ai.timefold.solver.core.api.score.constraint.Indictment;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.api.solver.SolverStatus;
import lombok.extern.slf4j.Slf4j;
import lv.lu.eztf.dn.network_optimizer.domain.DeploymentPlan;
import lv.lu.eztf.dn.network_optimizer.domain.Request;
import lv.lu.eztf.dn.network_optimizer.domain.Server;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@RestController
@Slf4j
@RequestMapping("/api")
public class NetworkOptimizerApiController {
    private final SolverManager<DeploymentPlan, String> solverManager;
    private final SolutionManager<DeploymentPlan, HardSoftScore> solutionManager;
    private final ConcurrentMap<String, Job> jobIdToJob = new ConcurrentHashMap<>();

    public NetworkOptimizerApiController(SolverManager<DeploymentPlan, String> solverManager,
                                         SolutionManager<DeploymentPlan, HardSoftScore> solutionManager) {
        this.solverManager = solverManager;
        this.solutionManager = solutionManager;
    }

    @GetMapping
    public Collection<String> list() {
        Collection<String> existingJobIds = jobIdToJob.keySet();
        return existingJobIds;
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
    public DeploymentPlan getSolution(
            @PathVariable("jobId") String jobId) {
        DeploymentPlan solution = getSolutionAndCheckForExceptions(jobId);
        SolverStatus solverStatus = solverManager.getSolverStatus(jobId);
        solution.solverStatus = solverStatus;
        return solution;
    }
    
    @GetMapping(value = "/{jobId}/download", produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<String> downloadSolution(@PathVariable("jobId") String jobId) throws JsonProcessingException {
        // Saņem risinājumu un solver statusu
        DeploymentPlan solution = getSolutionAndCheckForExceptions(jobId);
        SolverStatus solverStatus = solverManager.getSolverStatus(jobId);
        solution.setSolverStatus(solverStatus);

        // Konvertē uz JSON
        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(solution);

        // Sagatavo HTTP atbildi ar "attachment" galveni, lai browsers piedāvātu lejuplādi
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"deployment_plan_" + jobId + ".json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }


    @GetMapping(value = "/score/{jobId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ScoreAnalysis<HardSoftScore> analyze(
             @PathVariable("jobId") String jobId) {
        DeploymentPlan solution = getSolutionAndCheckForExceptions(jobId);
        return solutionManager.analyze(solution);
    }

    @GetMapping(value = "/explanation/{jobId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ExplanationDTO explain(@PathVariable("jobId") String jobId) {
        DeploymentPlan solution = getSolutionAndCheckForExceptions(jobId);
        ScoreExplanation<DeploymentPlan, HardSoftScore> explanation =
                solutionManager.explain(solution);

        // Get server costs
        List<ServerCostDTO> serverCosts = explanation.getIndictmentMap().entrySet().stream()
                .filter(entry -> entry.getKey() instanceof Server)
                .map(entry -> {
                    Server server = (Server) entry.getKey();
                    Indictment<HardSoftScore> indictment = entry.getValue();

                    Map<String, Integer> costByConstraint = indictment.getConstraintMatchSet()
                            .stream()
                            .collect(Collectors.groupingBy(
                                    match -> match.getConstraintRef().constraintName(),
                                    Collectors.summingInt(match ->
                                            match.getScore().softScore())
                            ));

                    return new ServerCostDTO(
                            server.getId(),
                            server.getName(),
                            indictment.getScore().softScore(),
                            costByConstraint
                    );
                })
                .collect(Collectors.toList());

        // Get problematic requests
        List<RequestIssueDTO> problematicRequests = explanation.getIndictmentMap().entrySet().stream()
                .filter(entry -> entry.getKey() instanceof Request) // Or your Request entity class
                .filter(entry -> entry.getValue().getScore().hardScore() < 0 ||
                        entry.getValue().getScore().softScore() < 0)
                .map(entry -> {
                    Request request = (Request) entry.getKey();
                    Indictment<HardSoftScore> indictment = entry.getValue();

                    // Get constraint violations for this request
                    Map<String, ConstraintDetailDTO> violations = indictment.getConstraintMatchSet()
                            .stream()
                            .filter(match -> match.getScore().hardScore() < 0 ||
                                    match.getScore().softScore() < 0)
                            .collect(Collectors.groupingBy(
                                    match -> match.getConstraintRef().constraintName(),
                                    Collectors.collectingAndThen(
                                            Collectors.toList(),
                                            matches -> {
                                                HardSoftScore totalScore = matches.stream()
                                                        .map(ConstraintMatch::getScore)
                                                        .reduce(HardSoftScore.ZERO, HardSoftScore::add);

                                                // Get justification objects (the entities involved)
                                                List<Object> justifications = matches.stream()
                                                        .flatMap(match -> match.getJustificationList().stream())
                                                        .distinct()
                                                        .collect(Collectors.toList());

                                                return new ConstraintDetailDTO(
                                                        totalScore.hardScore(),
                                                        totalScore.softScore(),
                                                        justifications
                                                );
                                            }
                                    )
                            ));

                    return new RequestIssueDTO(
                            request.getId(),
                            request.getServiceName(),
                            indictment.getScore().hardScore(),
                            indictment.getScore().softScore(),
                            violations
                    );
                })
                .collect(Collectors.toList());

        return new ExplanationDTO(serverCosts, problematicRequests);
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

    public record ServerCostDTO(
            long serverId,
            String serverName,
            int softCost,
            Map<String, Integer> costByConstraint
    ) {}

    public record ExplanationDTO(
            List<ServerCostDTO> serverCosts,
            List<RequestIssueDTO> problematicRequests
    ) {}

    public record RequestIssueDTO(
            long requestId,
            String requestName,
            int hardScore,
            int softScore,
            Map<String, ConstraintDetailDTO> violations
    ) {}

    public record ConstraintDetailDTO(
            int hardScore,
            int softScore,
            List<Object> involvedEntities // The justifications (servers, deployments, etc.)
    ) {}

}
