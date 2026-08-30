package com.perfumeryaicore.domain.member.service;

import com.perfumeryaicore.domain.member.dto.request.ChangePasswordRequest;
import com.perfumeryaicore.domain.member.dto.request.UpdateProfileRequest;
import com.perfumeryaicore.domain.member.dto.response.MemberResponse;
import com.perfumeryaicore.domain.member.entity.Member;
import com.perfumeryaicore.domain.member.repository.MemberRepository;
import com.perfumeryaicore.domain.member.repository.RefreshTokenRepository;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 프로필 조회 / 수정 / 비밀번호 변경.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

	private final MemberRepository memberRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;

	public MemberResponse getMe(Long memberId) {
		Member member = findMember(memberId);
		// TODO(project): project 도메인 구현 시 소속 프로젝트/역할 목록을 채운다.
		return MemberResponse.from(member);
	}

	@Transactional
	public MemberResponse updateProfile(Long memberId, UpdateProfileRequest request) {
		Member member = findMember(memberId);
		member.updateName(request.name());
		return MemberResponse.from(member);
	}

	@Transactional
	public void changePassword(Long memberId, ChangePasswordRequest request) {
		Member member = findMember(memberId);
		if (!passwordEncoder.matches(request.currentPassword(), member.getPasswordHash())) {
			throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
		}
		member.updatePassword(passwordEncoder.encode(request.newPassword()));
		// 비밀번호 변경 시 기존 세션(Refresh Token) 전체 무효화.
		refreshTokenRepository.revokeAllByMemberId(memberId, LocalDateTime.now());
	}

	private Member findMember(Long memberId) {
		return memberRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
	}
}
