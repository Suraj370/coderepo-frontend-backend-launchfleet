package com.launchfleet.backend.featureflags.ports;

import java.util.Optional;

/**
 * Read-only access to the Project this module needs (existence + id resolution
 * from a key) - deliberately narrow rather than depending on the projects module's
 * own MongoRepository directly.
 */
public interface ProjectLookup {

	Optional<ProjectRef> findByKey(String key);

}
