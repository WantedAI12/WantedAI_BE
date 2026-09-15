package com.perfumeryaicore.global.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;

/**
 * AWS SES로 발송한다. 발송 실패는 호출부(예: 비밀번호 재설정)의 계정 존재 여부 비노출 원칙을
 * 지키기 위해 예외를 던지지 않고 로그만 남긴다 — 이메일이 실패했다고 API 응답이 달라지면
 * 그 자체로 가입 여부를 추측할 수 있는 신호가 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SesEmailSender implements EmailSender {

	private static final String CHARSET = "UTF-8";

	private final SesClient sesClient;
	private final SesEmailProperties properties;

	@Override
	public void send(String to, String subject, String body) {
		try {
			sesClient.sendEmail(SendEmailRequest.builder()
					.source(properties.senderEmail())
					.destination(Destination.builder().toAddresses(to).build())
					.message(Message.builder()
							.subject(Content.builder().charset(CHARSET).data(subject).build())
							.body(Body.builder()
									.text(Content.builder().charset(CHARSET).data(body).build())
									.build())
							.build())
					.build());
			log.info("[EMAIL] sent subject={}", subject);
		} catch (SesException e) {
			log.error("[EMAIL] send failed subject={} reason={}", subject, e.getMessage());
		}
	}
}
