package com.cinescout.outreach;

import com.cinescout.llm.LlmClient;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.OutreachDraftRepository;
import com.cinescout.repository.UserRepository;
import com.cinescout.resilience.GuardFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires outreach generation. It needs only the LLM, so it is conditional on the watsonx.ai key alone
 * (not on scouting's search key). Without the key the app still starts, drafts can still be listed,
 * edited and deleted, and only generating one is unavailable.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("!'${cinescout.llm.watsonx.api-key:}'.isBlank()")
class OutreachConfig {

    @Bean
    OutreachGenerationService outreachGenerationService(LlmClient llm, GuardFactory guards, LocationRepository locations,
                                                        OutreachDraftRepository drafts, UserRepository users,
                                                        BlockingTransactions db) {
        return new OutreachGenerationService(llm, guards, locations, drafts, users, db);
    }
}
