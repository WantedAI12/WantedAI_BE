package com.perfumeryaicore.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * BE-081: {@code cors.allowed-origins}에 설정된 오리진은 허용하고, 그 외 오리진은
 * 거부하는지 실제 Preflight(OPTIONS) 요청으로 검증한다. (테스트 설정값:
 * {@code http://localhost:3000,http://localhost:5173})
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorsConfigurationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void an_allowed_origin_receives_the_access_control_allow_origin_header() throws Exception {
		mockMvc.perform(options("/auth/login")
						.header(HttpHeaders.ORIGIN, "http://localhost:3000")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
	}

	@Test
	void an_origin_not_on_the_allow_list_is_rejected() throws Exception {
		mockMvc.perform(options("/auth/login")
						.header(HttpHeaders.ORIGIN, "https://evil.example.com")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isForbidden());
	}
}
