package com.perfumeryaicore.domain.formula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.perfumeryaicore.domain.formula.entity.Candidate;
import com.perfumeryaicore.global.common.CandidateStatus;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class CandidateStatusTransitionTest {

	private Candidate newCandidate() {
		return Candidate.create(5L, 10L, 1L, 77L);
	}

	@Test
	void follows_the_documented_linear_path() {
		Candidate candidate = newCandidate();
		assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.UNDER_REVIEW);

		candidate.transitionStatus(CandidateStatus.CONFIRMED_FOR_EXPERIMENT);
		assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.CONFIRMED_FOR_EXPERIMENT);

		candidate.transitionStatus(CandidateStatus.IN_SENSORY_TEST);
		assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.IN_SENSORY_TEST);

		candidate.transitionStatus(CandidateStatus.APPROVED);
		assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.APPROVED);
	}

	@Test
	void rejected_is_reachable_from_any_non_terminal_state() {
		for (CandidateStatus from : new CandidateStatus[] {
				CandidateStatus.UNDER_REVIEW, CandidateStatus.CONFIRMED_FOR_EXPERIMENT, CandidateStatus.IN_SENSORY_TEST}) {
			Candidate candidate = candidateAt(from);
			candidate.transitionStatus(CandidateStatus.REJECTED);
			assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.REJECTED);
		}
	}

	@Test
	void cannot_skip_stages() {
		Candidate candidate = newCandidate();

		assertThatThrownBy(() -> candidate.transitionStatus(CandidateStatus.IN_SENSORY_TEST))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CANDIDATE_STATUS_TRANSITION_INVALID);

		assertThatThrownBy(() -> candidate.transitionStatus(CandidateStatus.APPROVED))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CANDIDATE_STATUS_TRANSITION_INVALID);
	}

	@Test
	void cannot_move_out_of_terminal_states_or_back_from_beyond_confirmation() {
		Candidate approved = candidateAt(CandidateStatus.APPROVED);
		assertThatThrownBy(() -> approved.transitionStatus(CandidateStatus.REJECTED))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CANDIDATE_STATUS_TRANSITION_INVALID);

		Candidate rejected = candidateAt(CandidateStatus.REJECTED);
		assertThatThrownBy(() -> rejected.transitionStatus(CandidateStatus.UNDER_REVIEW))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CANDIDATE_STATUS_TRANSITION_INVALID);

		Candidate inSensoryTest = candidateAt(CandidateStatus.IN_SENSORY_TEST);
		assertThatThrownBy(() -> inSensoryTest.transitionStatus(CandidateStatus.UNDER_REVIEW))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CANDIDATE_STATUS_TRANSITION_INVALID);

		Candidate approvedBack = candidateAt(CandidateStatus.APPROVED);
		assertThatThrownBy(() -> approvedBack.transitionStatus(CandidateStatus.UNDER_REVIEW))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CANDIDATE_STATUS_TRANSITION_INVALID);
	}

	/** BE-103: 실험 후보 확정 직후에는 선택 해제(UNDER_REVIEW로 되돌리기)가 가능하고, 재확정도 다시 할 수 있다. */
	@Test
	void confirmed_for_experiment_can_be_deselected_back_to_under_review_and_reselected() {
		Candidate candidate = candidateAt(CandidateStatus.CONFIRMED_FOR_EXPERIMENT);

		candidate.transitionStatus(CandidateStatus.UNDER_REVIEW);
		assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.UNDER_REVIEW);

		candidate.transitionStatus(CandidateStatus.CONFIRMED_FOR_EXPERIMENT);
		assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.CONFIRMED_FOR_EXPERIMENT);
	}

	@Test
	void transitioning_to_the_same_status_is_rejected() {
		Candidate candidate = newCandidate();
		assertThatThrownBy(() -> candidate.transitionStatus(CandidateStatus.UNDER_REVIEW))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CANDIDATE_STATUS_TRANSITION_INVALID);
	}

	private Candidate candidateAt(CandidateStatus target) {
		Candidate candidate = newCandidate();
		switch (target) {
			case UNDER_REVIEW -> { }
			case CONFIRMED_FOR_EXPERIMENT -> candidate.transitionStatus(CandidateStatus.CONFIRMED_FOR_EXPERIMENT);
			case IN_SENSORY_TEST -> {
				candidate.transitionStatus(CandidateStatus.CONFIRMED_FOR_EXPERIMENT);
				candidate.transitionStatus(CandidateStatus.IN_SENSORY_TEST);
			}
			case APPROVED -> {
				candidate.transitionStatus(CandidateStatus.CONFIRMED_FOR_EXPERIMENT);
				candidate.transitionStatus(CandidateStatus.IN_SENSORY_TEST);
				candidate.transitionStatus(CandidateStatus.APPROVED);
			}
			case REJECTED -> candidate.transitionStatus(CandidateStatus.REJECTED);
		}
		return candidate;
	}
}
