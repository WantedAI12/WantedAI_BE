package com.perfumeryaicore.domain.ops.service;

import com.perfumeryaicore.domain.formula.repository.GenerationRejectionRepository;
import com.perfumeryaicore.domain.job.entity.Job;
import com.perfumeryaicore.domain.job.entity.JobStatus;
import com.perfumeryaicore.domain.job.entity.JobType;
import com.perfumeryaicore.domain.job.repository.JobRepository;
import com.perfumeryaicore.domain.ops.dto.response.OpsEventResponse;
import com.perfumeryaicore.domain.ops.dto.response.OpsOverviewResponse;
import com.perfumeryaicore.domain.project.entity.Project;
import com.perfumeryaicore.domain.project.entity.ProjectMember;
import com.perfumeryaicore.domain.project.entity.ProjectMemberAuditLog;
import com.perfumeryaicore.domain.project.repository.ProjectMemberAuditLogRepository;
import com.perfumeryaicore.domain.project.repository.ProjectMemberRepository;
import com.perfumeryaicore.domain.project.repository.ProjectRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서비스 운영 상태 화면용 조회. 전체 관리자 개념이 없어, 호출한 회원이 속한 프로젝트만 집계한다 -
 * 서비스 전체를 합산하면 다른 사용자의 작업·실패 기록이 아무 회원에게나 노출된다. 게스트도 본인이
 * 만든 프로젝트의 멤버라 같은 규칙으로 본인 기준이 된다. 새로 저장하는 값은 없고 이미 쌓이는
 * 작업·기권·멤버 변경 기록을 읽기만 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OpsService {

	public static final int DEFAULT_EVENT_LIMIT = 20;
	public static final int MAX_EVENT_LIMIT = 100;
	/** 평균 생성 시간에 쓰는 최근 완료 작업 수 - 오래된 이력이 지금의 속도를 왜곡하지 않게 자른다. */
	static final int AVERAGE_SAMPLE_SIZE = 50;

	private final ProjectMemberRepository projectMemberRepository;
	private final ProjectRepository projectRepository;
	private final JobRepository jobRepository;
	private final GenerationRejectionRepository generationRejectionRepository;
	private final ProjectMemberAuditLogRepository projectMemberAuditLogRepository;

	public OpsOverviewResponse overview(Long memberId) {
		List<Long> projectIds = projectIdsOf(memberId);
		if (projectIds.isEmpty()) {
			return new OpsOverviewResponse(0, new OpsOverviewResponse.Queue(0, 0),
					new OpsOverviewResponse.GenerationTime(null, 0), new OpsOverviewResponse.Abstention(0, null));
		}

		long waiting = countGeneration(projectIds, JobStatus.PENDING);
		long running = countGeneration(projectIds, JobStatus.RUNNING);
		long succeeded = countGeneration(projectIds, JobStatus.SUCCEEDED);
		long abstentions = generationRejectionRepository.countByProjectIdIn(projectIds);

		List<Job> recent = jobRepository.findRecentWithAiCallStarted(projectIds, JobType.CANDIDATE_GENERATION,
				JobStatus.SUCCEEDED, PageRequest.of(0, AVERAGE_SAMPLE_SIZE));
		List<Double> seconds = recent.stream()
				.map(j -> Duration.between(j.getAiCallStartedAt(), j.getUpdatedAt()).toMillis() / 1000.0)
				.filter(s -> s >= 0)
				.toList();
		Double average = seconds.isEmpty()
				? null
				: seconds.stream().mapToDouble(Double::doubleValue).average().orElseThrow();

		long finished = succeeded + abstentions;
		Double ratePercent = finished == 0 ? null : abstentions * 100.0 / finished;

		return new OpsOverviewResponse(projectIds.size(),
				new OpsOverviewResponse.Queue(waiting, running),
				new OpsOverviewResponse.GenerationTime(average, seconds.size()),
				new OpsOverviewResponse.Abstention(abstentions, ratePercent));
	}

	/**
	 * 작업 이력과 팀원 변경 이력을 합쳐 최신순으로 돌려준다. 각 출처에서 {@code limit}건씩만 읽어도
	 * 합친 목록의 상위 {@code limit}건은 그 안에 반드시 들어 있어 전체를 읽지 않아도 된다.
	 */
	public List<OpsEventResponse> events(Long memberId, Integer limit) {
		int size = clampLimit(limit);
		List<Long> projectIds = projectIdsOf(memberId);
		if (projectIds.isEmpty()) {
			return List.of();
		}

		List<Job> jobs = jobRepository.findByProjectIdInOrderByUpdatedAtDesc(projectIds, PageRequest.of(0, size));
		List<ProjectMemberAuditLog> audits = projectMemberAuditLogRepository
				.findByProjectIdInOrderByCreatedAtDesc(projectIds, PageRequest.of(0, size));

		Set<Long> shownProjectIds = new HashSet<>();
		jobs.forEach(j -> shownProjectIds.add(j.getProjectId()));
		audits.forEach(a -> shownProjectIds.add(a.getProjectId()));
		Map<Long, String> projectNames = shownProjectIds.isEmpty()
				? Map.of()
				: projectRepository.findAllById(shownProjectIds).stream()
						.collect(Collectors.toMap(Project::getId, Project::getName, (a, b) -> a));

		List<OpsEventResponse> events = new ArrayList<>();
		for (Job job : jobs) {
			events.add(toEvent(job, projectNames));
		}
		for (ProjectMemberAuditLog audit : audits) {
			events.add(toEvent(audit, projectNames));
		}
		return events.stream()
				.sorted(Comparator.comparing(OpsEventResponse::occurredAt).reversed())
				.limit(size)
				.toList();
	}

	private List<Long> projectIdsOf(Long memberId) {
		return projectMemberRepository.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
				.map(ProjectMember::getProjectId)
				.distinct()
				.toList();
	}

	private long countGeneration(List<Long> projectIds, JobStatus status) {
		return jobRepository.countByProjectIdInAndJobTypeAndStatus(projectIds, JobType.CANDIDATE_GENERATION, status);
	}

	static int clampLimit(Integer limit) {
		if (limit == null) {
			return DEFAULT_EVENT_LIMIT;
		}
		return Math.max(1, Math.min(limit, MAX_EVENT_LIMIT));
	}

	private OpsEventResponse toEvent(Job job, Map<Long, String> projectNames) {
		String detail = job.getStatus() == JobStatus.FAILED ? job.getFailureReason() : null;
		return new OpsEventResponse(job.getUpdatedAt(), "JOB", jobEventName(job.getJobType()),
				job.getStatus().name(), jobStatusLabel(job.getStatus()), detail,
				job.getProjectId(), projectNames.get(job.getProjectId()), job.getId());
	}

	private OpsEventResponse toEvent(ProjectMemberAuditLog audit, Map<Long, String> projectNames) {
		String event;
		String detail;
		switch (audit.getAction()) {
			case ADDED -> {
				event = "팀원 추가";
				detail = "역할: " + audit.getNewRole();
			}
			case ROLE_CHANGED -> {
				event = "팀원 역할 변경";
				detail = audit.getPreviousRole() + " → " + audit.getNewRole();
			}
			case REMOVED -> {
				event = "팀원 제거";
				detail = "이전 역할: " + audit.getPreviousRole();
			}
			default -> throw new IllegalStateException("처리하지 않은 멤버 변경 종류: " + audit.getAction());
		}
		return new OpsEventResponse(audit.getCreatedAt(), "MEMBER", event, "RECORDED", "기록됨", detail,
				audit.getProjectId(), projectNames.get(audit.getProjectId()), audit.getTargetMemberId());
	}

	private static String jobEventName(JobType type) {
		return switch (type) {
			case REQUEST_STRUCTURING -> "향 요청 구조화";
			case CANDIDATE_GENERATION -> "후보 조향식 생성";
			case CATALOG_SYNC -> "원료 카탈로그 동기화";
			case EVIDENCE_REPORT -> "증거 보고서 생성";
			case SUPPLY_IMPACT_ANALYSIS -> "공급 변경 영향 분석";
			case PREDICTION -> "성능 예측 재계산";
		};
	}

	private static String jobStatusLabel(JobStatus status) {
		return switch (status) {
			case PENDING -> "대기";
			case RUNNING -> "실행 중";
			case SUCCEEDED -> "완료";
			case FAILED -> "실패";
			case CANCELLED -> "취소";
		};
	}
}
