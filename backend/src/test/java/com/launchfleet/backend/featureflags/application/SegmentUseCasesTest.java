package com.launchfleet.backend.featureflags.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.launchfleet.backend.featureflags.domain.Condition;
import com.launchfleet.backend.featureflags.domain.ConditionOperator;
import com.launchfleet.backend.featureflags.domain.ConditionType;
import com.launchfleet.backend.featureflags.domain.Segment;
import com.launchfleet.backend.featureflags.domain.SegmentStatus;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.shared.ApiException;

/**
 * Application use case tests using fake, in-memory port implementations - no Spring
 * context, no MongoDB.
 */
class SegmentUseCasesTest {

	private static final List<Condition> A_CONDITION = List
			.of(new Condition(ConditionType.ATTRIBUTE, "plan", ConditionOperator.EQUALS, List.of("gold")));

	private final InMemorySegmentStore segmentStore = new InMemorySegmentStore();

	private final InMemoryProjectLookup projectLookup = new InMemoryProjectLookup();

	private final InMemoryFeatureFlagConfigStore featureFlagConfigStore = new InMemoryFeatureFlagConfigStore();

	private SegmentLookup lookup;

	private CreateSegment createSegment;

	private ListSegments listSegments;

	private GetSegment getSegment;

	private UpdateSegment updateSegment;

	private RetireSegment retireSegment;

	private ProjectRef project;

	@BeforeEach
	void setUp() {
		lookup = new SegmentLookup(segmentStore, projectLookup);
		createSegment = new CreateSegment(segmentStore, lookup, new InMemoryActivityRecorder());
		listSegments = new ListSegments(segmentStore, lookup);
		getSegment = new GetSegment(lookup);
		updateSegment = new UpdateSegment(segmentStore, lookup);
		retireSegment = new RetireSegment(segmentStore, featureFlagConfigStore, lookup, new InMemoryActivityRecorder());

		project = projectLookup.addProject("acme");
	}

	@Test
	void createSucceedsWithAtLeastOneCondition() {
		Segment segment = createSegment.execute("acme", "beta-users", "Beta Users", A_CONDITION, "actor");

		assertThat(segment.getId()).isNotBlank();
		assertThat(segment.getStatus()).isEqualTo(SegmentStatus.ACTIVE);
	}

	@Test
	void duplicateSegmentKeyWithinTheSameProjectIsRejected() {
		createSegment.execute("acme", "dup", "First", A_CONDITION, "actor");

		assertThatThrownBy(() -> createSegment.execute("acme", "dup", "Second", A_CONDITION, "actor"))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.getStatusCode()).isEqualTo(409));
	}

	@Test
	void listOnlyReturnsSegmentsForTheRequestedProject() {
		projectLookup.addProject("globex");
		createSegment.execute("acme", "acme-seg", "Acme Segment", A_CONDITION, "actor");
		createSegment.execute("globex", "globex-seg", "Globex Segment", A_CONDITION, "actor");

		assertThat(listSegments.execute("acme")).hasSize(1);
		assertThat(listSegments.execute("acme").get(0).getKey()).isEqualTo("acme-seg");
	}

	@Test
	void getReturnsNotFoundForAnUnknownSegmentKey() {
		assertThatThrownBy(() -> getSegment.execute("acme", "nope")).isInstanceOfSatisfying(ApiException.class,
				exception -> assertThat(exception.getStatusCode()).isEqualTo(404));
	}

	@Test
	void updateChangesNameAndConditions() {
		createSegment.execute("acme", "key", "Original", A_CONDITION, "actor");

		Segment updated = updateSegment.execute("acme", "key", "Renamed", A_CONDITION, "editor");

		assertThat(updated.getName()).isEqualTo("Renamed");
	}

	@Test
	void retireTransitionsStatusWhenNotReferenced() {
		createSegment.execute("acme", "key", "Name", A_CONDITION, "actor");

		Segment retired = retireSegment.execute("acme", "key", "actor");

		assertThat(retired.getStatus()).isEqualTo(SegmentStatus.RETIRED);
	}

	@Test
	void segmentsAreProjectIsolatedAcrossOperations() {
		projectLookup.addProject("globex");
		createSegment.execute("acme", "shared-key", "Acme Segment", A_CONDITION, "actor");

		assertThatThrownBy(() -> getSegment.execute("globex", "shared-key")).isInstanceOfSatisfying(
				ApiException.class, exception -> assertThat(exception.getStatusCode()).isEqualTo(404));
	}
}
