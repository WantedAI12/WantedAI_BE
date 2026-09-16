package com.perfumeryaicore.domain.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.perfumeryaicore.domain.project.entity.Project;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * BE-090~098 일부: 프로젝트 시작일/마감일 순서 검증과 부분 수정(null=유지) 규칙을 검증한다.
 */
class ProjectScheduleTest {

	private static final LocalDate START = LocalDate.of(2026, 6, 1);
	private static final LocalDate DUE = LocalDate.of(2026, 6, 30);

	@Test
	void create_allows_a_due_date_on_or_after_the_start_date() {
		Project project = Project.create("프로젝트", null, START, START);
		assertThat(project.getStartDate()).isEqualTo(START);
		assertThat(project.getDueDate()).isEqualTo(START);
	}

	@Test
	void create_rejects_a_due_date_before_the_start_date() {
		assertThatThrownBy(() -> Project.create("프로젝트", null, DUE, START))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
	}

	@Test
	void updateSchedule_with_null_dates_leaves_the_existing_dates_unchanged() {
		Project project = Project.create("프로젝트", null, START, DUE);

		project.updateSchedule(null, null, null);

		assertThat(project.getStartDate()).isEqualTo(START);
		assertThat(project.getDueDate()).isEqualTo(DUE);
	}

	@Test
	void updateSchedule_rejects_moving_only_the_start_date_past_the_existing_due_date() {
		Project project = Project.create("프로젝트", null, START, DUE);

		assertThatThrownBy(() -> project.updateSchedule(DUE.plusDays(1), null, null))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
		assertThat(project.getStartDate()).isEqualTo(START);
	}

	@Test
	void updateSchedule_with_a_null_assignee_leaves_the_existing_assignee_unchanged() {
		Project project = Project.create("프로젝트", null, START, DUE);
		project.updateSchedule(null, null, 7L);

		project.updateSchedule(null, null, null);

		assertThat(project.getAssigneeMemberId()).isEqualTo(7L);
	}
}
