// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}

// Restore debug.keystore automatically if missing (e.g., in CI or clean git clones)
val debugKeystore = file("${rootDir}/debug.keystore")
val debugKeystoreBase64 = file("${rootDir}/debug.keystore.base64")
if (!debugKeystore.exists() && debugKeystoreBase64.exists()) {
  try {
    val decoded = java.util.Base64.getDecoder().decode(debugKeystoreBase64.readText().trim())
    debugKeystore.writeBytes(decoded)
  } catch (_: Exception) {}
}
