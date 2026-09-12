package com.perfumeryaicore.domain.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.project.dto.response.ProjectImageResponse;
import com.perfumeryaicore.domain.project.dto.response.ProjectImageUploadResponse;
import com.perfumeryaicore.domain.project.entity.Project;
import com.perfumeryaicore.domain.project.entity.ProjectImageAsset;
import com.perfumeryaicore.domain.project.entity.ProjectImageAssetStatus;
import com.perfumeryaicore.domain.project.entity.ProjectMember;
import com.perfumeryaicore.domain.project.repository.ProjectImageAssetRepository;
import com.perfumeryaicore.domain.project.repository.ProjectMemberRepository;
import com.perfumeryaicore.domain.project.repository.ProjectRepository;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.domain.project.service.ProjectImageService;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import com.perfumeryaicore.global.storage.S3FileStorage;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class ProjectImageServiceTest {

	private static final long PROJECT_ID = 10L;
	private static final long UPLOADER_ID = 1L;

	private final ProjectImageAssetRepository assetRepository = mock(ProjectImageAssetRepository.class);
	private final ProjectRepository projectRepository = mock(ProjectRepository.class);
	private final ProjectMemberRepository projectMemberRepository = mock(ProjectMemberRepository.class);
	private final ProjectAccessGuard accessGuard = new ProjectAccessGuard(projectMemberRepository);
	private final S3FileStorage s3FileStorage = mock(S3FileStorage.class);
	private final ProjectImageService service =
			new ProjectImageService(assetRepository, projectRepository, accessGuard, s3FileStorage);

	@BeforeEach
	void actorIsProjectManager() {
		when(projectMemberRepository.findByProjectIdAndMemberId(PROJECT_ID, UPLOADER_ID))
				.thenReturn(Optional.of(ProjectMember.create(PROJECT_ID, UPLOADER_ID, ProjectRole.PROJECT_MANAGER)));
	}

	private static ProjectImageAsset withId(ProjectImageAsset asset, long id) {
		try {
			Field field = ProjectImageAsset.class.getDeclaredField("id");
			field.setAccessible(true);
			field.set(asset, id);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return asset;
	}

	private static Project withId(Project project, long id) {
		try {
			Field field = Project.class.getDeclaredField("id");
			field.setAccessible(true);
			field.set(project, id);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return project;
	}

	@Test
	void upload_rejects_unsupported_content_type() {
		MockMultipartFile file = new MockMultipartFile("file", "a.gif", "image/gif", new byte[]{1, 2, 3});

		assertThatThrownBy(() -> service.uploadTemp(file, UPLOADER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_IMAGE_UNSUPPORTED_FORMAT);
		verify(s3FileStorage, never()).upload(anyString(), any(), anyString());
	}

	@Test
	void upload_rejects_a_file_over_5mb() {
		MockMultipartFile file = new MockMultipartFile(
				"file", "big.png", "image/png", new byte[6 * 1024 * 1024]);

		assertThatThrownBy(() -> service.uploadTemp(file, UPLOADER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_IMAGE_TOO_LARGE);
		verify(s3FileStorage, never()).upload(anyString(), any(), anyString());
	}

	@Test
	void upload_stores_the_file_and_returns_a_pending_asset() {
		MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3});
		when(assetRepository.save(any(ProjectImageAsset.class)))
				.thenAnswer(inv -> withId(inv.<ProjectImageAsset>getArgument(0), 100L));

		ProjectImageUploadResponse response = service.uploadTemp(file, UPLOADER_ID);

		assertThat(response.assetId()).isEqualTo(100L);
		assertThat(response.status()).isEqualTo(ProjectImageAssetStatus.PENDING);
		verify(s3FileStorage).upload(anyString(), any(byte[].class), org.mockito.ArgumentMatchers.eq("image/png"));
	}

	@Test
	void attach_is_forbidden_for_a_non_manager() {
		when(projectMemberRepository.findByProjectIdAndMemberId(PROJECT_ID, 2L))
				.thenReturn(Optional.of(ProjectMember.create(PROJECT_ID, 2L, ProjectRole.PERFUMER)));

		assertThatThrownBy(() -> service.attach(PROJECT_ID, 2L, 100L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
	}

	@Test
	void attach_rejects_an_asset_uploaded_by_someone_else() {
		ProjectImageAsset asset = withId(ProjectImageAsset.pending("key", "image/png", 10, 999L), 100L);
		when(assetRepository.findById(100L)).thenReturn(Optional.of(asset));

		assertThatThrownBy(() -> service.attach(PROJECT_ID, UPLOADER_ID, 100L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_IMAGE_ASSET_ACCESS_DENIED);
	}

	@Test
	void attach_links_a_pending_asset_and_returns_a_presigned_url() {
		ProjectImageAsset asset = withId(ProjectImageAsset.pending("key-1", "image/png", 10, UPLOADER_ID), 100L);
		Project project = withId(Project.create("향수 프로젝트", null), PROJECT_ID);
		when(assetRepository.findById(100L)).thenReturn(Optional.of(asset));
		when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
		when(s3FileStorage.presignedGetUrl("key-1", Duration.ofMinutes(15))).thenReturn("https://signed/key-1");

		ProjectImageResponse response = service.attach(PROJECT_ID, UPLOADER_ID, 100L);

		assertThat(response.assetId()).isEqualTo(100L);
		assertThat(response.imageUrl()).isEqualTo("https://signed/key-1");
		assertThat(asset.getStatus()).isEqualTo(ProjectImageAssetStatus.ATTACHED);
		assertThat(project.getImageAssetId()).isEqualTo(100L);
	}

	@Test
	void attaching_a_new_image_orphans_the_previous_one() {
		ProjectImageAsset oldAsset = withId(ProjectImageAsset.pending("old-key", "image/png", 10, UPLOADER_ID), 1L);
		oldAsset.attachTo(PROJECT_ID);
		ProjectImageAsset newAsset = withId(ProjectImageAsset.pending("new-key", "image/png", 10, UPLOADER_ID), 2L);
		Project project = withId(Project.create("향수 프로젝트", null), PROJECT_ID);
		project.attachImage(1L);

		when(assetRepository.findById(2L)).thenReturn(Optional.of(newAsset));
		when(assetRepository.findById(1L)).thenReturn(Optional.of(oldAsset));
		when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
		when(s3FileStorage.presignedGetUrl(anyString(), any())).thenReturn("https://signed");

		service.attach(PROJECT_ID, UPLOADER_ID, 2L);

		assertThat(oldAsset.getStatus()).isEqualTo(ProjectImageAssetStatus.ORPHANED);
		assertThat(newAsset.getStatus()).isEqualTo(ProjectImageAssetStatus.ATTACHED);
		assertThat(project.getImageAssetId()).isEqualTo(2L);
	}

	@Test
	void attach_rejects_reusing_an_already_attached_asset() {
		ProjectImageAsset asset = withId(ProjectImageAsset.pending("key", "image/png", 10, UPLOADER_ID), 100L);
		asset.attachTo(20L);
		Project project = withId(Project.create("향수 프로젝트", null), PROJECT_ID);
		when(assetRepository.findById(100L)).thenReturn(Optional.of(asset));
		when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

		assertThatThrownBy(() -> service.attach(PROJECT_ID, UPLOADER_ID, 100L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_IMAGE_ASSET_NOT_PENDING);
	}

	@Test
	void get_without_an_attached_image_is_not_found() {
		Project project = withId(Project.create("향수 프로젝트", null), PROJECT_ID);
		when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

		assertThatThrownBy(() -> service.get(PROJECT_ID, UPLOADER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_IMAGE_NOT_FOUND);
	}

	@Test
	void unlink_orphans_the_current_asset_and_clears_the_project() {
		ProjectImageAsset asset = withId(ProjectImageAsset.pending("key", "image/png", 10, UPLOADER_ID), 100L);
		asset.attachTo(PROJECT_ID);
		Project project = withId(Project.create("향수 프로젝트", null), PROJECT_ID);
		project.attachImage(100L);
		when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
		when(assetRepository.findById(100L)).thenReturn(Optional.of(asset));

		service.unlink(PROJECT_ID, UPLOADER_ID);

		assertThat(asset.getStatus()).isEqualTo(ProjectImageAssetStatus.ORPHANED);
		assertThat(project.getImageAssetId()).isNull();
	}
}
