package com.perfumeryaicore.global.email;

/** 트랜잭션 이메일 발송 추상화. 구현을 바꿔도(SES 등) 호출부는 영향받지 않는다. */
public interface EmailSender {

	void send(String to, String subject, String body);
}
