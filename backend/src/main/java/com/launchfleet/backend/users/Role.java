package com.launchfleet.backend.users;

/**
 * Ranked so higher roles imply the permissions of lower ones (ADMIN implies
 * EDITOR implies VIEWER). Rank is an explicit field, not enum declaration
 * order (ordinal()) - reordering or inserting a constant here can't silently
 * change the hierarchy.
 */
public enum Role {

	VIEWER(0), EDITOR(1), ADMIN(2);

	private final int rank;

	Role(int rank) {
		this.rank = rank;
	}

	public boolean atLeast(Role required) {
		return this.rank >= required.rank;
	}
}
