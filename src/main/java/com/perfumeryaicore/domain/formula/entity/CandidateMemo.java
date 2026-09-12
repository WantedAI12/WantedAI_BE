package com.perfumeryaicore.domain.formula.entity;

import com.perfumeryaicore.global.common.BaseTimeEntity;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 후보에 딸린 메모 한 건({@link CandidateMemoType}당 후보에 최대 1건). 원본 브리프({@code FragranceRequest})나
 * 다른 후보의 데이터는 건드리지 않는다 — 이 후보만의 부가 텍스트다.
 *
 * <p>{@code revision}으로 낙관적 잠금을 구현한다: 수정 요청은 자신이 마지막으로 읽은 revision을
 * 같이 보내야 하고, 그 사이 다른 사람이 먼저 저장해 revision이 바뀌었으면 {@link ErrorCode#CANDIDATE_MEMO_CONFLICT}로
 * 거부한다(마지막 저장이 조용히 덮어쓰는 것을 막는다).
 */
@Entity
@Getter
@Table(
		name = "candidate_memos",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_candidate_memos_candidate_type", columnNames = {"candidate_id", "memo_type"}),
		indexes = @Index(name = "idx_candidate_memos_candidate_id", columnList = "candidate_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CandidateMemo extends BaseTimeEntity {

	public static final int CONTENT_MAX = 4000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "candidate_id", nullable = false)
	private Long candidateId;

	@Enumerated(EnumType.STRING)
	@Column(name = "memo_type", nullable = false, length = 30)
	private CandidateMemoType memoType;

	@Lob
	@Column(nullable = false)
	private String content;

	/** 마지막으로 저장했을 때 후보의 현재 버전. 표시용 컨텍스트일 뿐, 버전 데이터 자체는 바꾸지 않는다. */
	@Column(name = "candidate_version_id")
	private Long candidateVersionId;

	@Column(name = "created_by", nullable = false)
	private Long createdBy;

	@Column(name = "last_edited_by", nullable = false)
	private Long lastEditedBy;

	@Column(nullable = false)
	private int revision;

	private CandidateMemo(Long candidateId, CandidateMemoType memoType, String content,
			Long candidateVersionId, Long authorId) {
		this.candidateId = candidateId;
		this.memoType = memoType;
		this.content = content;
		this.candidateVersionId = candidateVersionId;
		this.createdBy = authorId;
		this.lastEditedBy = authorId;
		this.revision = 1;
	}

	public static CandidateMemo create(Long candidateId, CandidateMemoType memoType, String content,
			Long candidateVersionId, Long authorId) {
		return new CandidateMemo(candidateId, memoType, content, candidateVersionId, authorId);
	}

	/**
	 * @param expectedRevision 클라이언트가 마지막으로 읽은 revision. 현재 값과 다르면 그 사이 다른
	 *     사용자가 먼저 저장한 것이므로 {@link ErrorCode#CANDIDATE_MEMO_CONFLICT}로 거부한다.
	 */
	public void update(String content, int expectedRevision, Long candidateVersionId, Long editorId) {
		if (this.revision != expectedRevision) {
			throw new BusinessException(ErrorCode.CANDIDATE_MEMO_CONFLICT);
		}
		this.content = content;
		this.candidateVersionId = candidateVersionId;
		this.lastEditedBy = editorId;
		this.revision++;
	}
}
