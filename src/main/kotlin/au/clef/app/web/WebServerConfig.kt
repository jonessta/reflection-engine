package au.clef.app.web

data class WebServerConfig(
    val port: Int = 8080,
    val corsHost: String = "localhost:63342",
    val corsScheme: String = "http"
)