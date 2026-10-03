package com.cinescout.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@EnableConfigurationProperties(MailProperties.class)
class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Bean
    ReplyAddresses replyAddresses(MailProperties props) {
        ReplyAddresses addresses = new ReplyAddresses(props.replyDomain(), props.replyLocalPart());
        ReplyAddresses.install(addresses);
        return addresses;
    }

    /**
     * The inbound provider's parser, when one is fully configured; otherwise none, and replies are recorded by hand
     * (the webhook answers 404). A null bean: read it through an {@code ObjectProvider}.
     */
    @Bean
    InboundMailParser inboundMailParser(MailProperties props, ObjectMapper mapper) {
        MailProperties.Inbound inbound = props.inbound();
        if (inbound == null || inbound.provider() == null || inbound.provider().isBlank()) {
            return null;
        }
        if (!"postmark".equalsIgnoreCase(inbound.provider())) {
            log.warn("Unknown inbound mail provider '{}'; replies will be recorded by hand", inbound.provider());
            return null;
        }
        if (blank(inbound.username()) || blank(inbound.password()) || !props.replyAddresses()) {
            log.warn("Postmark inbound needs MAIL_REPLY_DOMAIN, MAIL_INBOUND_USERNAME and MAIL_INBOUND_PASSWORD; replies will be recorded by hand");
            return null;
        }
        return new PostmarkInboundParser(mapper, inbound.username(), inbound.password());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
