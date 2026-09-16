package com.perfumeryaicore.global.email;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import software.amazon.awssdk.services.ses.model.SesException;

class SesEmailSenderTest {

	private final SesClient sesClient = mock(SesClient.class);
	private final SesEmailProperties properties = new SesEmailProperties("ap-northeast-2", "no-reply@test.local");
	private final SesEmailSender sender = new SesEmailSender(sesClient, properties);

	@Test
	void send_delegates_to_ses_with_the_configured_sender() {
		when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(SendEmailResponse.builder().build());

		sender.send("user@example.com", "제목", "본문");

		verify(sesClient).sendEmail(any(SendEmailRequest.class));
	}

	/** 발송 실패가 호출부로 전파되면 계정 존재 여부 비노출 원칙이 깨질 수 있다 - 삼켜야 한다. */
	@Test
	void send_swallows_ses_failures_instead_of_propagating() {
		when(sesClient.sendEmail(any(SendEmailRequest.class)))
				.thenThrow(SesException.builder().message("boom").build());

		assertThatCode(() -> sender.send("user@example.com", "제목", "본문")).doesNotThrowAnyException();
	}
}
