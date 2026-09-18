package com.launchfleet.backend.approvals.adapters.mongodb;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.launchfleet.backend.approvals.domain.ApprovalRequest;
import com.launchfleet.backend.approvals.domain.ApprovalStatus;
import com.launchfleet.backend.approvals.domain.CancellationReason;
import com.launchfleet.backend.approvals.domain.ProposedConfig;
import com.launchfleet.backend.approvals.ports.ApprovalRequestConflictException;
import com.launchfleet.backend.approvals.ports.ApprovalRequestStore;
import com.launchfleet.backend.featureflags.domain.Allocation;
import com.launchfleet.backend.featureflags.domain.Condition;
import com.launchfleet.backend.featureflags.domain.ConditionOperator;
import com.launchfleet.backend.featureflags.domain.ConditionType;
import com.launchfleet.backend.featureflags.domain.Rollout;
import com.launchfleet.backend.featureflags.domain.TargetingRule;
import com.launchfleet.backend.shared.ApiException;

import jakarta.annotation.PostConstruct;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Translates between the pure domain ApprovalRequest and its Mongo-mapped
 * ApprovalRequestDocument - mirrors MongoFeatureFlagConfigStore's shape exactly. Also
 * owns:
 * <ul>
 * <li>the one-PENDING-per-scope backstop: a partial unique index (status="PENDING"
 * only) created at startup, since Spring Data's @CompoundIndex annotation has no
 * partialFilterExpression attribute to declare this declaratively;</li>
 * <li>lifecycle concurrency safety: updating an EXISTING request is a conditional
 * MongoDB findAndModify on {@code _id} AND {@code persistenceVersion}, not a plain
 * repository.save() - see ApprovalRequest's Javadoc on persistenceVersion and
 * ApprovalRequestConflictException for why. Only a brand-new request (no id yet) is
 * a plain insert; there is nothing to race against before it has ever been
 * persisted.</li>
 * </ul>
 */
@Component
class MongoApprovalRequestStore implements ApprovalRequestStore {

	private static final int CONFLICT = 409;

	private final SpringDataApprovalRequestRepository repository;

	private final MongoTemplate mongoTemplate;

	MongoApprovalRequestStore(SpringDataApprovalRequestRepository repository, MongoTemplate mongoTemplate) {
		this.repository = repository;
		this.mongoTemplate = mongoTemplate;
	}

	@PostConstruct
	void ensurePendingUniquenessIndex() {
		Index index = new Index().on("featureFlagId", org.springframework.data.domain.Sort.Direction.ASC)
				.on("environmentId", org.springframework.data.domain.Sort.Direction.ASC).unique().named("one_pending_per_scope")
				.partial(PartialIndexFilter.of(Criteria.where("status").is(ApprovalStatus.PENDING.name())));

		mongoTemplate.indexOps(ApprovalRequestDocument.class).createIndex(index);
	}

	@Override
	public Optional<ApprovalRequest> findById(String id) {
		return repository.findById(id).map(this::toDomain);
	}

	@Override
	public List<ApprovalRequest> findByProjectId(String projectId) {
		return repository.findByProjectIdOrderBySubmittedAtDesc(projectId).stream().map(this::toDomain).toList();
	}

	@Override
	public Optional<ApprovalRequest> findPendingByFeatureFlagIdAndEnvironmentId(String featureFlagId,
			String environmentId) {
		return repository
				.findByFeatureFlagIdAndEnvironmentIdAndStatus(featureFlagId, environmentId,
						ApprovalStatus.PENDING.name())
				.map(this::toDomain);
	}

	@Override
	public List<ApprovalRequest> findPendingOrScheduledByFeatureFlagId(String featureFlagId) {
		return repository
				.findByFeatureFlagIdAndStatusIn(featureFlagId,
						List.of(ApprovalStatus.PENDING.name(), ApprovalStatus.SCHEDULED.name()))
				.stream().map(this::toDomain).toList();
	}

	@Override
	public List<ApprovalRequest> findPendingOrScheduledByEnvironmentId(String environmentId) {
		return repository
				.findByEnvironmentIdAndStatusIn(environmentId,
						List.of(ApprovalStatus.PENDING.name(), ApprovalStatus.SCHEDULED.name()))
				.stream().map(this::toDomain).toList();
	}

	@Override
	public List<ApprovalRequest> findScheduledAtOrBefore(Instant now) {
		return repository.findByStatusAndScheduledAtLessThanEqual(ApprovalStatus.SCHEDULED.name(), now).stream()
				.map(this::toDomain).toList();
	}

	@Override
	public ApprovalRequest save(ApprovalRequest request) {
		if (request.getId() == null) {
			return insert(request);
		}

		return updateWithVersionCheck(request);
	}

	/** A brand-new request has never been persisted, so there is nothing to race against yet - a plain insert. */
	private ApprovalRequest insert(ApprovalRequest request) {
		try {
			return toDomain(repository.save(toDocument(request)));
		} catch (DuplicateKeyException exception) {
			// The application-level check in SubmitApprovalRequest already covers the
			// common case; this only fires when two submissions raced past that check -
			// see this class's Javadoc and ApprovalRequestStore.save's.
			throw new ApiException(CONFLICT, "PENDING_REQUEST_EXISTS",
					"A pending approval request already exists for this flag and environment.");
		}
	}

	/**
	 * The lifecycle-concurrency guard itself: a real MongoDB findAndModify whose
	 * query filter includes the persistenceVersion this `request` was loaded at. If
	 * another operation already committed a transition since then, the persisted
	 * version has moved and this query matches nothing - the write never happens and
	 * this throws rather than silently overwriting whatever that other operation
	 * decided (see ApprovalRequestConflictException's Javadoc).
	 */
	private ApprovalRequest updateWithVersionCheck(ApprovalRequest request) {
		Query query = Query.query(where("_id").is(request.getId()).and("persistenceVersion")
				.is(request.getPersistenceVersion()));
		Update update = new Update().set("status", request.getStatus().name())
				.set("reviewedBy", request.getReviewedBy()).set("reviewedAt", request.getReviewedAt())
				.set("approvalComment", request.getApprovalComment())
				.set("rejectionComment", request.getRejectionComment()).set("scheduledAt", request.getScheduledAt())
				.set("appliedVersion", request.getAppliedVersion())
				.set("cancellationReason",
						request.getCancellationReason() == null ? null : request.getCancellationReason().name())
				.set("cancelledBy", request.getCancelledBy()).set("cancelledAt", request.getCancelledAt())
				.set("persistenceVersion", request.getPersistenceVersion() + 1);

		ApprovalRequestDocument updated = mongoTemplate.findAndModify(query, update,
				FindAndModifyOptions.options().returnNew(true), ApprovalRequestDocument.class);

		if (updated == null) {
			throw new ApprovalRequestConflictException(
					"This approval request was changed by another operation before this one could be saved.");
		}

		return toDomain(updated);
	}

	@Override
	public void deleteAll() {
		repository.deleteAll();
	}

	private ApprovalRequest toDomain(ApprovalRequestDocument document) {
		List<TargetingRule> targetingRules = document.getProposedTargetingRules() == null ? List.of()
				: document.getProposedTargetingRules().stream().map(this::toDomain).toList();
		Rollout rollout = document.getProposedRollout() == null ? null : toDomain(document.getProposedRollout());
		ProposedConfig proposedConfig = new ProposedConfig(document.isProposedEnabled(),
				document.getProposedDefaultVariantId(), targetingRules, rollout);

		return ApprovalRequest.reconstitute(document.getId(), document.getProjectId(), document.getFeatureFlagId(),
				document.getEnvironmentId(), document.getBaseConfigVersion(), proposedConfig,
				ApprovalStatus.valueOf(document.getStatus()), document.getSubmittedBy(), document.getSubmittedAt(),
				document.getReviewedBy(), document.getReviewedAt(), document.getApprovalComment(),
				document.getRejectionComment(), document.getScheduledAt(), document.getAppliedVersion(),
				document.getCancellationReason() == null ? null : CancellationReason.valueOf(document.getCancellationReason()),
				document.getCancelledBy(), document.getCancelledAt(), document.getPersistenceVersion());
	}

	private TargetingRule toDomain(ApprovalTargetingRuleDocument document) {
		List<Condition> conditions = document.getConditions().stream().map(this::toDomain).toList();
		return TargetingRule.reconstitute(document.getId(), document.getPriority(), conditions,
				document.getVariantId());
	}

	private Condition toDomain(ApprovalConditionDocument document) {
		return new Condition(ConditionType.valueOf(document.getType()), document.getAttribute(),
				ConditionOperator.valueOf(document.getOperator()), document.getValues());
	}

	private Rollout toDomain(ApprovalRolloutDocument document) {
		return new Rollout(document.getAllocations().stream().map(this::toDomain).toList());
	}

	private Allocation toDomain(ApprovalAllocationDocument document) {
		return new Allocation(document.getVariantId(), document.getPercentage());
	}

	private ApprovalRequestDocument toDocument(ApprovalRequest request) {
		ApprovalRequestDocument document = new ApprovalRequestDocument();
		document.setId(request.getId());
		document.setProjectId(request.getProjectId());
		document.setFeatureFlagId(request.getFeatureFlagId());
		document.setEnvironmentId(request.getEnvironmentId());
		document.setBaseConfigVersion(request.getBaseConfigVersion());
		document.setProposedEnabled(request.getProposedConfig().enabled());
		document.setProposedDefaultVariantId(request.getProposedConfig().defaultVariantId());
		document.setProposedTargetingRules(
				request.getProposedConfig().targetingRules().stream().map(this::toDocument).toList());
		document.setProposedRollout(
				request.getProposedConfig().rollout() == null ? null : toDocument(request.getProposedConfig().rollout()));
		document.setStatus(request.getStatus().name());
		document.setSubmittedBy(request.getSubmittedBy());
		document.setSubmittedAt(request.getSubmittedAt());
		document.setReviewedBy(request.getReviewedBy());
		document.setReviewedAt(request.getReviewedAt());
		document.setApprovalComment(request.getApprovalComment());
		document.setRejectionComment(request.getRejectionComment());
		document.setScheduledAt(request.getScheduledAt());
		document.setAppliedVersion(request.getAppliedVersion());
		document.setCancellationReason(request.getCancellationReason() == null ? null
				: request.getCancellationReason().name());
		document.setCancelledBy(request.getCancelledBy());
		document.setCancelledAt(request.getCancelledAt());
		document.setPersistenceVersion(request.getPersistenceVersion());

		return document;
	}

	private ApprovalTargetingRuleDocument toDocument(TargetingRule rule) {
		List<ApprovalConditionDocument> conditions = rule.getConditions().stream().map(this::toDocument).toList();
		return new ApprovalTargetingRuleDocument(rule.getId(), rule.getPriority(), conditions, rule.getVariantId());
	}

	private ApprovalConditionDocument toDocument(Condition condition) {
		return new ApprovalConditionDocument(condition.type().name(), condition.attribute(),
				condition.operator().name(), condition.values());
	}

	private ApprovalRolloutDocument toDocument(Rollout rollout) {
		return new ApprovalRolloutDocument(rollout.allocations().stream().map(this::toDocument).toList());
	}

	private ApprovalAllocationDocument toDocument(Allocation allocation) {
		return new ApprovalAllocationDocument(allocation.variantId(), allocation.percentage());
	}
}
