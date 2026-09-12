package com.perfumeryaicore.domain.project.service;

import com.perfumeryaicore.domain.project.dto.response.ProjectImageResponse;
import com.perfumeryaicore.domain.project.dto.response.ProjectImageUploadResponse;
import com.perfumeryaicore.domain.project.entity.Project;
import com.perfumeryaicore.domain.project.entity.ProjectImageAsset;
import com.perfumeryaicore.domain.project.repository.ProjectImageAssetRepository;
import com.perfumeryaicore.domain.project.repository.ProjectRepository;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import com.perfumeryaicore.global.storage.S3FileStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 프로젝트 이미지 업로드·연결·조회·해제(COR-B01).
 *
 * <p>프로젝트 생성 화면에서는 프로젝트가 만들어지기 전에 이미지를 먼저 고르므로, 업로드는
 * 프로젝트와 무관한 임시 자산({@link ProjectImageAsset#pending})으로 먼저 받고, 이후
 * {@link #attach}로 프로젝트에 연결하는 2단계 흐름을 쓴다. 업로드만 되고 끝내 연결되지 않는
 * PENDING 자산의 정리(만료된 임시 자산의 S3 오브젝트 삭제)는 이번 범위 밖이다 — 후속 배치 작업이 필요하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectImageService {

	private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
	private static final long MAX_BYTES = 5L * 1024 * 1024;
	private static final Duration DOWNLOAD_URL_TTL = Duration.ofMinutes(15);

	private final ProjectImageAssetRepository assetRepository;
	private final ProjectRepository projectRepository;
	private final ProjectAccessGuard accessGuard;
	private final S3FileStorage s3FileStorage;

	@Transactional
	public ProjectImageUploadResponse uploadTemp(MultipartFile file, Long memberId) {
		validate(file);
		String key = "project-images/tmp/%s%s".formatted(UUID.randomUUID(), extensionOf(file));
		s3FileStorage.upload(key, readBytes(file), file.getContentType());
		ProjectImageAsset asset = assetRepository.save(
				ProjectImageAsset.pending(key, file.getContentType(), file.getSize(), memberId));
		log.info("[PROJECT] image asset id={} uploaded by={} bytes={}", asset.getId(), memberId, file.getSize());
		return ProjectImageUploadResponse.from(asset);
	}

	/** 최초 연결과 교체를 겸한다 — 이미 연결된 이미지가 있으면 ORPHANED 처리 후 새 자산을 연결한다. */
	@Transactional
	public ProjectImageResponse attach(Long projectId, Long memberId, Long assetId) {
		accessGuard.requireRole(projectId, memberId, ProjectRole.ORG_ADMIN, ProjectRole.PROJECT_MANAGER);
		ProjectImageAsset asset = assetRepository.findById(assetId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_IMAGE_ASSET_NOT_FOUND));
		if (!asset.isUploadedBy(memberId)) {
			throw new BusinessException(ErrorCode.PROJECT_IMAGE_ASSET_ACCESS_DENIED);
		}
		Project project = findProject(projectId);

		orphanCurrentImage(project);
		asset.attachTo(projectId);
		project.attachImage(asset.getId());
		log.info("[PROJECT] id={} image attached asset={} by={}", projectId, assetId, memberId);
		return new ProjectImageResponse(asset.getId(), s3FileStorage.presignedGetUrl(asset.getObjectKey(), DOWNLOAD_URL_TTL));
	}

	public ProjectImageResponse get(Long projectId, Long memberId) {
		accessGuard.requireMember(projectId, memberId);
		Project project = findProject(projectId);
		if (project.getImageAssetId() == null) {
			throw new BusinessException(ErrorCode.PROJECT_IMAGE_NOT_FOUND);
		}
		ProjectImageAsset asset = assetRepository.findById(project.getImageAssetId())
				.orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_IMAGE_NOT_FOUND));
		return new ProjectImageResponse(asset.getId(), s3FileStorage.presignedGetUrl(asset.getObjectKey(), DOWNLOAD_URL_TTL));
	}

	@Transactional
	public void unlink(Long projectId, Long memberId) {
		accessGuard.requireRole(projectId, memberId, ProjectRole.ORG_ADMIN, ProjectRole.PROJECT_MANAGER);
		Project project = findProject(projectId);
		orphanCurrentImage(project);
		project.clearImage();
		log.info("[PROJECT] id={} image unlinked by={}", projectId, memberId);
	}

	private void orphanCurrentImage(Project project) {
		if (project.getImageAssetId() == null) {
			return;
		}
		assetRepository.findById(project.getImageAssetId()).ifPresent(ProjectImageAsset::markOrphaned);
	}

	private Project findProject(Long projectId) {
		return projectRepository.findById(projectId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
	}

	private void validate(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "이미지 파일이 비어 있습니다.");
		}
		String contentType = file.getContentType();
		if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
			throw new BusinessException(ErrorCode.PROJECT_IMAGE_UNSUPPORTED_FORMAT);
		}
		if (file.getSize() > MAX_BYTES) {
			throw new BusinessException(ErrorCode.PROJECT_IMAGE_TOO_LARGE);
		}
	}

	private static byte[] readBytes(MultipartFile file) {
		try {
			return file.getBytes();
		} catch (IOException e) {
			throw new UncheckedIOException("이미지 업로드 처리에 실패했습니다.", e);
		}
	}

	private static String extensionOf(MultipartFile file) {
		String name = file.getOriginalFilename();
		int dot = name == null ? -1 : name.lastIndexOf('.');
		return dot >= 0 ? name.substring(dot) : "";
	}
}
