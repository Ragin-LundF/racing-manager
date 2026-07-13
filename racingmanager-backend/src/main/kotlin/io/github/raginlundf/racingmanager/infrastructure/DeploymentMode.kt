package io.github.raginlundf.racingmanager.infrastructure

/** Explicit deployment mode. Security-relevant behavior (JWT signing key
    source, tenant self-registration, bootstrap/sync availability) must
    branch on this value, never on whether a network connection is present. */
enum class DeploymentMode {
    /** Single implicit tenant, no cloud dependency; signing keys are
        generated and persisted locally. */
    LOCAL,

    /** Multiple tenants share one backend; signing keys come from
        deployment configuration. */
    HOSTED,
    ;

    companion object {
        fun from(value: String): DeploymentMode {
            return when (value.lowercase()) {
                "local" -> LOCAL
                "hosted" -> HOSTED
                else -> error("Unknown racingmanager.mode '$value' — expected 'local' or 'hosted'")
            }
        }
    }
}
