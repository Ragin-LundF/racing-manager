package io.github.raginlundf.racingmanager.infrastructure.security

/** Source of JWT signing/verification keys. A [DeploymentMode.LOCAL] deployment
    generates and persists its own key ([LocalJwtKeyProvider]); a
    [DeploymentMode.HOSTED] deployment loads keys from deployment configuration
    ([HostedJwtKeyProvider]). Configuration must never contain a committed
    secret, and no implementation may log raw key material. */
interface JwtKeyProvider {
    /** The key currently used to sign newly issued tokens. */
    fun signingKey(): SigningKey

    /** Look up a key by `kid` to verify a token's signature, regardless of
        whether that key is still used for signing (support key rotation:
        older tokens must keep validating until they expire). */
    fun verificationKey(kid: String): SigningKey?
}
