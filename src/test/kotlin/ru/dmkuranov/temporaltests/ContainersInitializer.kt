package ru.dmkuranov.temporaltests

import mu.KotlinLogging
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy
import ru.dmkuranov.temporaltests.temporal.TemporalConfiguration
import java.time.Duration

object ContainersInitializer {

    private const val temporalServerVersion = "1.27.2"
    private val containerNetwork = Network.newNetwork()!!
    private const val postgresInternalHost = "postgres-host"
    private const val temporalServerInternalHost = "temporal-server-host"
    private const val temporalServerInternalPort = 7233
    private const val temporalServerStoragePlugin = "postgres12"
    private const val temporalServerStoragePluginSchemaPathPrefix = "./schema/postgresql/v12"
    private val postgresInternalPort = PostgreSQLContainer.POSTGRESQL_PORT.toString()
    const val postgresUser = "postgresuser"
    const val postgresPassword = "postgrespassword"
    const val temporalDbSetupDbDbNameApp = "temporal-tests-app"
    private const val temporalDbSetupDbDbNameServer = "temporal-server"
    private const val temporalDbSetupDbDbNameVisibility = "temporal-visibility"

    val postgresContainer = PostgreSQLContainer("postgres:12.9")
        .withNetwork(containerNetwork)
        .withNetworkAliases(postgresInternalHost)
        .withUsername(postgresUser)
        .withPassword(postgresPassword)!!

    private val temporalAdminContainer = GenericContainer("temporalio/admin-tools:1.27")
        .withNetwork(containerNetwork)
        .withCreateContainerCmdModifier {
            it.withEntrypoint("temporal-sql-tool")
        }
        .withStartupCheckStrategy(OneShotStartupCheckStrategy().withTimeout(Duration.ofSeconds(30)))!!

    val temporalServerContainer = GenericContainer("temporalio/server:$temporalServerVersion")
        .withNetwork(containerNetwork)
        .withNetworkAliases(temporalServerInternalHost)
        .withExposedPorts(temporalServerInternalPort)
        .withEnv("TEMPORAL_BROADCAST_ADDRESS", "0.0.0.0")
        .withEnv("BIND_ON_IP", "0.0.0.0")
        .withEnv("NUM_HISTORY_SHARDS", "10")
        .withEnv("SERVICES", "history,matching,worker,frontend")
        .withEnv("LOG_LEVEL", "info")
        .withEnv("DB", temporalServerStoragePlugin)
        .withEnv("DBNAME", temporalDbSetupDbDbNameServer)
        .withEnv("VISIBILITY_DBNAME", temporalDbSetupDbDbNameVisibility)
        .withEnv("POSTGRES_USER", postgresUser)
        .withEnv("POSTGRES_PWD", postgresPassword)!!

    fun initialize() {
        postgresContainer.start()
        log.info { "postgres started on localhost:" + postgresContainer.firstMappedPort }

        initializeTemporalDb()

        temporalServerContainer
            .withEnv("POSTGRES_SEEDS", postgresInternalHost)
            .withEnv("DB_PORT", postgresInternalPort)
            .start()

        temporalAdminContainer
            .withCreateContainerCmdModifier {
                it.withEntrypoint("tctl")
            }
            .withEnv("TEMPORAL_CLI_ADDRESS", "$temporalServerInternalHost:$temporalServerInternalPort")
            .withCommand("namespace register " + TemporalConfiguration.NAMESPACE_NAME)
            .start()
        temporalAdminContainer.close()

        val uiContainer = GenericContainer("temporalio/ui:2.14.0")
            .withNetwork(containerNetwork)
            .withExposedPorts(8080)
            .withEnv("TEMPORAL_ADDRESS", "$temporalServerInternalHost:$temporalServerInternalPort")
            .withEnv("TEMPORAL_CORS_ORIGINS", "\"*\"")
            .withEnv("TEMPORAL_DEFAULT_NAMESPACE", TemporalConfiguration.NAMESPACE_NAME)

        uiContainer.start()
        log.info { "ui started on http://localhost:" + uiContainer.firstMappedPort }
    }

    private fun initializeTemporalDb() {
        val temporalDbSetupDbCredentialOptions = listOf(
            "-ep", postgresInternalHost,
            "-p", postgresInternalPort,
            "-u", postgresUser,
            "--pw", postgresPassword,
            "--pl", temporalServerStoragePlugin
        )
        val temporalDbSetupDbDbNameKey = "--db"

        val temporalDbSetupDbCreateDatabaseCommand = "create-database"
        val temporalDbSetupDbSetupSchemaCommand = listOf("setup-schema", "-v", "0.0")
        val temporalDbSetupDbUpdateSchemaCommand = listOf("update-schema", "-d")
        val temporalDbSetupDbUpdateSchemaPathServer = "$temporalServerStoragePluginSchemaPathPrefix/temporal/versioned"
        val temporalDbSetupDbUpdateSchemaPathVisibility = "$temporalServerStoragePluginSchemaPathPrefix/visibility/versioned"
        listOf(
            temporalDbSetupDbCredentialOptions
                .plusElement(temporalDbSetupDbDbNameKey).plusElement(temporalDbSetupDbDbNameApp)
                .plusElement(temporalDbSetupDbCreateDatabaseCommand),
            temporalDbSetupDbCredentialOptions
                .plusElement(temporalDbSetupDbDbNameKey).plusElement(temporalDbSetupDbDbNameServer)
                .plusElement(temporalDbSetupDbCreateDatabaseCommand),
            temporalDbSetupDbCredentialOptions
                .plusElement(temporalDbSetupDbDbNameKey).plusElement(temporalDbSetupDbDbNameVisibility)
                .plusElement(temporalDbSetupDbCreateDatabaseCommand),

            temporalDbSetupDbCredentialOptions
                .plusElement(temporalDbSetupDbDbNameKey).plusElement(temporalDbSetupDbDbNameServer)
                .plus(temporalDbSetupDbSetupSchemaCommand),
            temporalDbSetupDbCredentialOptions
                .plusElement(temporalDbSetupDbDbNameKey).plusElement(temporalDbSetupDbDbNameVisibility)
                .plus(temporalDbSetupDbSetupSchemaCommand),

            temporalDbSetupDbCredentialOptions
                .plusElement(temporalDbSetupDbDbNameKey).plusElement(temporalDbSetupDbDbNameServer)
                .plus(temporalDbSetupDbUpdateSchemaCommand)
                .plusElement(temporalDbSetupDbUpdateSchemaPathServer),
            temporalDbSetupDbCredentialOptions
                .plusElement(temporalDbSetupDbDbNameKey).plusElement(temporalDbSetupDbDbNameVisibility)
                .plus(temporalDbSetupDbUpdateSchemaCommand)
                .plusElement(temporalDbSetupDbUpdateSchemaPathVisibility)

        ).forEach {
            temporalAdminContainer
                .withCommand(*it.toTypedArray())
                .start()
            temporalAdminContainer.close()
        }
    }

    private val log = KotlinLogging.logger {}
}
