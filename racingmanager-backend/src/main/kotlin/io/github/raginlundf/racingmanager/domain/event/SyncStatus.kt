package io.github.raginlundf.racingmanager.domain.event

/** Lifecycle of an event with respect to hosted↔local sync (design §I.4).
    Null on an [EventEntity] means "never checked out" — the vast majority of
    events, which never touch a local instance at all. */
enum class SyncStatus {
    /** Freshly imported into a local instance (Slice H), not yet acted on. */
    IMPORTED,

    /** The local instance is actively preparing/running this event. */
    LOCAL_ACTIVE,

    /** Checked out from hosted (bootstrap package issued) — hosted's copy is
        [EventEntity.lockedForSync] until results are synced back. */
    SYNC_PENDING,

    /** Results have been synced back to hosted; hosted's copy is unlocked. */
    SYNCED,

    /** Reserved for a detected divergence between hosted and local state.
        No automatic resolution — see plan deviation notes on why full
        conflict detection is deferred to a later sync release. */
    CONFLICT,
}
