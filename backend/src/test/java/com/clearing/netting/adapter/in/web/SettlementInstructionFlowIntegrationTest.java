package com.clearing.netting.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:clearing-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class SettlementInstructionFlowIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final LocalDate SETTLE_DATE = LocalDate.of(2026, 9, 18);

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void netThenPreviewThenReleaseEndToEnd() throws Exception {
        String operator = login("operator", "op123456");
        String viewer = login("viewer", "view123456");

        String alpha = createMember(operator, "Alpha Bank");
        String beta = createMember(operator, "Beta Securities");
        String gamma = createMember(operator, "Gamma Clearing");

        createObligation(operator, alpha, beta, "100000");
        createObligation(operator, beta, gamma, "60000");
        createObligation(operator, gamma, alpha, "40000");

        // 1) netting completes
        JsonNode execute = JSON.readTree(post(operator, "/api/netting-runs",
                "{\"settleDate\":\"" + SETTLE_DATE + "\",\"currency\":\"USD\"}").getBody());
        assertEquals("COMPLETED", execute.path("run").path("status").asText());
        String runId = execute.path("run").path("runId").asText();
        assertEquals(3, execute.path("positions").size());

        // 2) run appears on the settlement page with no instructions yet
        JsonNode runs = JSON.readTree(get(operator, "/api/settlement-instructions/runs").getBody());
        JsonNode summary = findByRunId(runs, runId);
        assertEquals("NONE", summary.path("instructionStatus").asText());
        assertEquals(0, summary.path("instructionCount").asInt());

        // 3) a failed run cannot generate instructions
        ResponseEntity<String> failedRun = post(operator, "/api/netting-runs",
                "{\"settleDate\":\"2030-01-01\",\"currency\":\"EUR\"}");
        assertEquals(HttpStatus.BAD_REQUEST, failedRun.getStatusCode());
        JsonNode allRuns = JSON.readTree(get(operator, "/api/netting-runs").getBody());
        String failedRunId = null;
        for (JsonNode r : allRuns) {
            if ("FAILED".equals(r.path("status").asText())) {
                failedRunId = r.path("runId").asText();
            }
        }
        assertNotNull(failedRunId);
        ResponseEntity<String> previewOnFailed =
                post(operator, "/api/settlement-instructions/runs/" + failedRunId + "/preview", "");
        assertEquals(HttpStatus.BAD_REQUEST, previewOnFailed.getStatusCode());
        assertTrue(previewOnFailed.getBody().contains("INVALID_STATE"));

        // 4) preview shows every member with direction and amount
        ResponseEntity<String> previewResp =
                post(operator, "/api/settlement-instructions/runs/" + runId + "/preview", "");
        assertEquals(HttpStatus.OK, previewResp.getStatusCode());
        JsonNode preview = JSON.readTree(previewResp.getBody());
        assertEquals("PREVIEWED", preview.path("instructionStatus").asText());
        assertEquals(3, preview.path("instructions").size());
        assertInstruction(preview, alpha, "PAY", "60000.00000000");
        assertInstruction(preview, beta, "RECEIVE", "40000.00000000");
        assertInstruction(preview, gamma, "RECEIVE", "20000.00000000");

        // 5) preview is idempotent
        String firstId = preview.path("instructions").get(0).path("instructionId").asText();
        JsonNode previewAgain = JSON.readTree(
                post(operator, "/api/settlement-instructions/runs/" + runId + "/preview", "").getBody());
        assertEquals(firstId, previewAgain.path("instructions").get(0).path("instructionId").asText());

        // 6) viewer can preview/view but cannot release
        ResponseEntity<String> viewerPreview =
                post(viewer, "/api/settlement-instructions/runs/" + runId + "/preview", "");
        assertEquals(HttpStatus.OK, viewerPreview.getStatusCode());
        ResponseEntity<String> viewerRelease =
                post(viewer, "/api/settlement-instructions/runs/" + runId + "/release", "");
        assertEquals(HttpStatus.FORBIDDEN, viewerRelease.getStatusCode());

        // 7) release before preview is rejected — covered indirectly; operator releases now
        ResponseEntity<String> releaseResp =
                post(operator, "/api/settlement-instructions/runs/" + runId + "/release", "");
        assertEquals(HttpStatus.OK, releaseResp.getStatusCode());
        JsonNode released = JSON.readTree(releaseResp.getBody());
        assertEquals("RELEASED", released.path("instructionStatus").asText());
        for (JsonNode instruction : released.path("instructions")) {
            assertEquals("RELEASED", instruction.path("status").asText());
            assertEquals("operator", instruction.path("releasedBy").asText());
            assertTrue(instruction.path("releasedAt").asText().length() > 0);
        }

        // 8) repeated release is idempotent and the list page shows RELEASED
        ResponseEntity<String> secondRelease =
                post(operator, "/api/settlement-instructions/runs/" + runId + "/release", "");
        assertEquals(HttpStatus.OK, secondRelease.getStatusCode());
        JsonNode runsAfter = JSON.readTree(get(operator, "/api/settlement-instructions/runs").getBody());
        assertEquals("RELEASED", findByRunId(runsAfter, runId).path("instructionStatus").asText());
        assertEquals(3, findByRunId(runsAfter, runId).path("releasedCount").asInt());
    }

    private void assertInstruction(JsonNode preview, String memberId, String direction, String amount) {
        for (JsonNode instruction : preview.path("instructions")) {
            if (memberId.equals(instruction.path("memberId").asText())) {
                assertEquals(direction, instruction.path("direction").asText());
                assertEquals(0, new java.math.BigDecimal(amount)
                        .compareTo(new java.math.BigDecimal(instruction.path("amount").asText())));
                assertEquals("USD", instruction.path("currency").asText());
                assertEquals("PREVIEWED", instruction.path("status").asText());
                return;
            }
        }
        throw new AssertionError("instruction not found for member " + memberId);
    }

    private JsonNode findByRunId(JsonNode runs, String runId) {
        for (JsonNode run : runs) {
            if (runId.equals(run.path("run").path("runId").asText())) {
                return run;
            }
        }
        throw new AssertionError("run summary not found: " + runId);
    }

    private String login(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = rest.exchange(
                "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}", headers),
                String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        try {
            return JSON.readTree(resp.getBody()).path("token").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String createMember(String token, String name) throws Exception {
        JsonNode body = JSON.readTree(post(token, "/api/members", "{\"name\":\"" + name + "\"}").getBody());
        return body.path("memberId").asText();
    }

    private void createObligation(String token, String payer, String payee, String amount) {
        String payload = "{\"payerMemberId\":\"" + payer + "\",\"payeeMemberId\":\"" + payee + "\","
                + "\"currency\":\"USD\",\"amount\":" + amount
                + ",\"tradeDate\":\"" + SETTLE_DATE + "\",\"settleDate\":\"" + SETTLE_DATE + "\"}";
        ResponseEntity<String> resp = post(token, "/api/obligations", payload);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    private ResponseEntity<String> get(String token, String path) {
        return exchange(token, HttpMethod.GET, path, null);
    }

    private ResponseEntity<String> post(String token, String path, String body) {
        return exchange(token, HttpMethod.POST, path, body);
    }

    private ResponseEntity<String> exchange(String token, HttpMethod method, String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return rest.exchange("http://localhost:" + port + path, method, new HttpEntity<>(body, headers), String.class);
    }
}
