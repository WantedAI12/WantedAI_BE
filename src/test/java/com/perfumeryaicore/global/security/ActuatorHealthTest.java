package com.perfumeryaicore.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * BE-089: 로드밸런서 등이 인증 없이 호출할 수 있어야 한다. 비로그인 상태에서는 상세 정보
 * (DB 연결 등)가 아니라 UP/DOWN 상태만 보여야 한다(show-details: when-authorized).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActuatorHealthTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void health_endpoint_is_reachable_without_authentication_and_hides_component_details() throws Exception {
		MvcResult result = mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andReturn();

		String body = result.getResponse().getContentAsString();
		assertThat(body).contains("\"status\":\"UP\"");
		assertThat(body).doesNotContain("\"db\"");
	}
}
