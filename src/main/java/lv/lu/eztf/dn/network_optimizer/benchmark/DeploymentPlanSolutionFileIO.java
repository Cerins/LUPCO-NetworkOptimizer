package lv.lu.eztf.dn.network_optimizer.benchmark;

import ai.timefold.solver.jackson.impl.domain.solution.JacksonSolutionFileIO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lv.lu.eztf.dn.network_optimizer.domain.DeploymentPlan;

/**
 * Solution file I/O for reading and writing DeploymentPlan instances in JSON format.
 * This is used by the benchmarker to load problem datasets and optionally write solutions.
 */
public class DeploymentPlanSolutionFileIO extends JacksonSolutionFileIO<DeploymentPlan> {

    public DeploymentPlanSolutionFileIO() {
        super(DeploymentPlan.class, createObjectMapper());
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Ensure dates are written in a readable format
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Pretty print for readability
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }
}
