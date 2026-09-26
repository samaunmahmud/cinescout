package com.cinescout.web;

import org.junit.jupiter.api.Test;

class HealthApiTest extends ApiTest {

    @Test
    void healthIsOpenForContainerChecksAndSaysNoMoreThanUpOrDown() {
        web.get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody().json("{\"status\":\"UP\"}", true);
    }

    @Test
    void noOtherActuatorEndpointIsExposed() {
        Account ada = register("Ada");
        for (String endpoint : new String[] {"/actuator/env", "/actuator/beans", "/actuator/configprops", "/actuator/info"}) {
            ada.client().get().uri(endpoint).exchange().expectStatus().isNotFound();
        }
        web.get().uri("/actuator/env").exchange().expectStatus().isUnauthorized();
    }
}
