package com.cinescout.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JobsProperties.class)
class JobsConfig {

    /**
     * The in-app timer: runs the jobs while the app is awake. On a host that sleeps when idle it misses runs, which the
     * scheduled workflow makes up for; the jobs do not mind running twice.
     */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = "cinescout.jobs.scheduler", havingValue = "true", matchIfMissing = true)
    static class Timer {

        private static final Logger log = LoggerFactory.getLogger(Timer.class);
        private static final Duration LIMIT = Duration.ofMinutes(5);

        private final List<Job> jobs;

        Timer(List<Job> jobs) {
            this.jobs = jobs;
        }

        @Scheduled(cron = "${cinescout.jobs.follow-ups:0 17 * * * *}")
        void followUps() {
            run("follow-ups");
        }

        /** Daily: the forecast changes slowly, and each run asks the weather service once a venue. */
        @Scheduled(cron = "${cinescout.jobs.weather-watch:0 23 6 * * *}", zone = "UTC")
        void weatherWatch() {
            run("weather-watch");
        }

        private void run(String name) {
            jobs.stream().filter(job -> job.name().equals(name)).findFirst().ifPresent(job -> {
                try {
                    job.run().block(LIMIT);
                } catch (RuntimeException e) {
                    log.warn("Scheduled job {} failed: {}", name, e.toString());
                }
            });
        }
    }
}
