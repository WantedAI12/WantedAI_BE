package com.perfumeryaicore.domain.ops;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.perfumeryaicore.domain.job.entity.Job;
import com.perfumeryaicore.domain.job.entity.JobType;
import com.perfumeryaicore.domain.job.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 서비스 운영 상태 API를 로그인부터 응답 JSON까지 실제 흐름으로 검증한다. 특히 "내가 속한 프로젝트
 * 기준"이 게스트에게도 그대로 적용되는지 - 게스트마다 본인이 만든 프로젝트의 기록만 보이고 다른
 * 게스트의 작업은 보이지 않아야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpsControllerTest {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JobRepository jobRepository;

	private JsonNode data(String responseBody) throws Exception {
		return OBJECT_MAPPER.readTree(responseBody).get("data");
	}

	private String guestToken() throws Exception {
		String body = mockMvc.perform(post("/auth/guest")).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return data(body).get("accessToken").asText();
	}

	private long createProject(String token, String name) throws Exception {
		String body = mockMvc.perform(post("/projects")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"" + name + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return data(body).get("projectId").asLong();
	}

	@Test
	void operations_endpoints_require_authentication() throws Exception {
		mockMvc.perform(get("/ops/overview")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/ops/events")).andExpect(status().isUnauthorized());
	}

	@Test
	void a_guest_sees_only_the_work_in_the_project_that_guest_created() throws Exception {
		String guestA = guestToken();
		long projectA = createProject(guestA, "게스트 A의 프로젝트");
		jobRepository.save(Job.pending(projectA, JobType.CANDIDATE_GENERATION, 1L, null));

		String guestB = guestToken();
		createProject(guestB, "게스트 B의 프로젝트");

		mockMvc.perform(get("/ops/overview").header(HttpHeaders.AUTHORIZATION, "Bearer " + guestA))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.projectCount").value(1))
				.andExpect(jsonPath("$.data.queue.waiting").value(1))
				.andExpect(jsonPath("$.data.queue.running").value(0))
				.andExpect(jsonPath("$.data.generationTime.averageSeconds").doesNotExist())
				.andExpect(jsonPath("$.data.abstention.count").value(0));

		mockMvc.perform(get("/ops/events?limit=10").header(HttpHeaders.AUTHORIZATION, "Bearer " + guestA))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.category=='JOB')].event").value("후보 조향식 생성"))
				.andExpect(jsonPath("$.data[?(@.category=='JOB')].statusLabel").value("대기"))
				.andExpect(jsonPath("$.data[?(@.category=='JOB')].projectName").value("게스트 A의 프로젝트"));

		// 다른 게스트의 프로젝트에는 A의 작업이 보이지 않는다.
		mockMvc.perform(get("/ops/overview").header(HttpHeaders.AUTHORIZATION, "Bearer " + guestB))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.projectCount").value(1))
				.andExpect(jsonPath("$.data.queue.waiting").value(0));
		mockMvc.perform(get("/ops/events").header(HttpHeaders.AUTHORIZATION, "Bearer " + guestB))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.category=='JOB')]").isEmpty());
	}

	@Test
	void a_guest_who_has_not_created_anything_gets_empty_metrics() throws Exception {
		String token = guestToken();

		mockMvc.perform(get("/ops/overview").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.projectCount").value(0))
				.andExpect(jsonPath("$.data.queue.waiting").value(0))
				.andExpect(jsonPath("$.data.abstention.ratePercent").doesNotExist());
		mockMvc.perform(get("/ops/events").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isEmpty());
	}
}
