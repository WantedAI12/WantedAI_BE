package com.perfumeryaicore.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * 회원가입 → 로그인 → 인증 요청 → 토큰 회전 → 재사용 탐지까지의 전체 흐름 통합 테스트.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Autowired
	private MockMvc mockMvc;

	@Test
	void signup_login_and_authenticated_request() throws Exception {
		String email = randomEmail();

		mockMvc.perform(post("/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(signupBody(email, "password123", "테스터")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.email").value(email));

		mockMvc.perform(post("/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(signupBody(email, "password123", "테스터")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));

		String accessToken = login(email).get("accessToken").asText();

		mockMvc.perform(get("/members/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.email").value(email))
				.andExpect(jsonPath("$.data.projects").isArray());
	}

	@Test
	void protected_endpoint_without_token_returns_401() throws Exception {
		mockMvc.perform(get("/members/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	@Test
	void refresh_rotates_token_and_detects_reuse() throws Exception {
		String email = randomEmail();
		signup(email);

		String firstRefresh = login(email).get("refreshToken").asText();

		JsonNode rotated = perform(post("/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content(refreshBody(firstRefresh)), status().isOk());
		String secondRefresh = rotated.get("refreshToken").asText();
		assertThat(secondRefresh).isNotEqualTo(firstRefresh);

		// 이미 회전된(폐기된) 토큰 재사용 → 재사용 탐지
		mockMvc.perform(post("/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(refreshBody(firstRefresh)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_REUSE_DETECTED"));

		// 재사용 탐지로 회원의 모든 토큰이 폐기됨 → 방금 발급된 토큰도 더 이상 사용 불가
		mockMvc.perform(post("/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(refreshBody(secondRefresh)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_REUSE_DETECTED"));
	}

	private JsonNode login(String email) throws Exception {
		return perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginBody(email, "password123")), status().isOk());
	}

	private void signup(String email) throws Exception {
		mockMvc.perform(post("/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(signupBody(email, "password123", "테스터")))
				.andExpect(status().isCreated());
	}

	private JsonNode perform(org.springframework.test.web.servlet.RequestBuilder request, ResultMatcher expected)
			throws Exception {
		MvcResult result = mockMvc.perform(request).andExpect(expected).andReturn();
		return OBJECT_MAPPER.readTree(result.getResponse().getContentAsString()).get("data");
	}

	private String signupBody(String email, String password, String name) {
		return "{\"email\":\"%s\",\"password\":\"%s\",\"name\":\"%s\"}".formatted(email, password, name);
	}

	private String loginBody(String email, String password) {
		return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
	}

	private String refreshBody(String refreshToken) {
		return "{\"refreshToken\":\"%s\"}".formatted(refreshToken);
	}

	private String randomEmail() {
		return "user-" + System.nanoTime() + "@example.com";
	}
}
