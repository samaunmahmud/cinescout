package com.cinescout.web;

import com.cinescout.jobs.Job;
import com.cinescout.jobs.JobRun;
import com.cinescout.jobs.JobsProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runs a scheduled job on request, for a timer outside the app (a GitHub Actions workflow): the app's own timer does
 * not run while the host sleeps. The caller proves itself with the shared secret, not a user's login.
 */
@RestController
@Tag(name = "Jobs", description = "Scheduled work, run by an outside timer with the shared job secret.")
class JobController {

    static final String SECRET_HEADER = "X-Job-Secret";
    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    private final Map<String, Job> jobs;
    private final JobsProperties props;

    JobController(List<Job> jobs, JobsProperties props) {
        this.jobs = jobs.stream().collect(Collectors.toMap(Job::name, Function.identity()));
        this.props = props;
    }

    /** 404 when the endpoint is off (no secret configured) or there is no such job; 401 when the secret is wrong. */
    @Operation(summary = "Run a scheduled job", description = "Jobs: follow-ups. Needs the X-Job-Secret header.")
    @SecurityRequirements
    @PostMapping("/api/internal/jobs/{name}")
    Mono<ResponseEntity<JobRun>> run(@PathVariable String name, @RequestHeader(name = SECRET_HEADER, required = false) String secret) {
        if (!props.endpointEnabled()) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        if (secret == null || !MessageDigest.isEqual(props.secret().getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8))) {
            log.warn("A job call came without the right secret");
            return Mono.just(ResponseEntity.status(401).build());
        }
        Job job = jobs.get(name);
        return job == null ? Mono.just(ResponseEntity.notFound().build()) : job.run().map(ResponseEntity::ok);
    }
}
