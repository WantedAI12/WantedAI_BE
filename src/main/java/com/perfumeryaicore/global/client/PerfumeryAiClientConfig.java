package com.perfumeryaicore.global.client;

import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.HttpHeaderNames;
import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;

/**
 * 조향 AI(Modal) 전용 {@link WebClient} 구성.
 *
 * <p>연결 제한 시간은 일반 웹 요청 수준(약 10초), 전체 응답 대기 시간은 Modal 콜드 스타트와
 * 조향식 계산을 고려해 길게(최소 130초) 둔다. Modal Proxy Token은 이 WebClient의 기본 헤더로만
 * 부착되며, 프론트엔드로 나가는 응답에는 포함되지 않는다.
 */
@Configuration
@EnableConfigurationProperties(ModalAiProperties.class)
public class PerfumeryAiClientConfig {

	/** Modal 응답이 크므로(수십 KB~) 코덱 버퍼를 넉넉히 잡는다. */
	private static final int MAX_IN_MEMORY_BYTES = 8 * 1024 * 1024;

	@Bean
	public WebClient perfumeryAiWebClient(ModalAiProperties properties) {
		HttpClient httpClient = HttpClient.create()
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
						(int) properties.connectTimeout().toMillis())
				.responseTimeout(properties.responseTimeout())
				// Modal 웹 엔드포인트는 오래 걸리는 요청을 303으로 결과 조회 주소에 넘긴다. 따라가지 않으면
				// 본문이 빈 응답을 성공으로 받게 된다. 인증 토큰이 다른 호스트로 새지 않도록 같은
				// 호스트로 가는 303만 따라간다(기본 헤더는 리다이렉트 요청에도 그대로 실린다).
				.followRedirect((request, response) -> isSameHostPollingRedirect(properties.baseUrl(), response));

		WebClient.Builder builder = WebClient.builder()
				.baseUrl(properties.baseUrl())
				.clientConnector(new ReactorClientHttpConnector(httpClient))
				.codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BYTES));

		if (properties.hasAuthToken()) {
			builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.authToken());
		}
		return builder.build();
	}

	/** 303이면서 {@code Location}이 기본 주소와 같은 호스트(상대 경로 포함)일 때만 따라간다. */
	static boolean isSameHostPollingRedirect(String baseUrl, HttpClientResponse response) {
		if (response.status().code() != 303) {
			return false;
		}
		String location = response.responseHeaders().get(HttpHeaderNames.LOCATION);
		if (location == null || location.isBlank()) {
			return false;
		}
		try {
			URI base = URI.create(baseUrl);
			URI target = base.resolve(location);
			return base.getHost() != null && base.getHost().equalsIgnoreCase(target.getHost());
		} catch (IllegalArgumentException e) {
			return false;
		}
	}
}
