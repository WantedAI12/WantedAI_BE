package com.perfumeryaicore.global.client;

import io.netty.channel.ChannelOption;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

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
				.responseTimeout(properties.responseTimeout());

		WebClient.Builder builder = WebClient.builder()
				.baseUrl(properties.baseUrl())
				.clientConnector(new ReactorClientHttpConnector(httpClient))
				.codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BYTES));

		if (properties.hasAuthToken()) {
			builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.authToken());
		}
		return builder.build();
	}
}
