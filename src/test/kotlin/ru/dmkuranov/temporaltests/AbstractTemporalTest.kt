package ru.dmkuranov.temporaltests

import com.google.protobuf.ByteString
import io.temporal.api.common.v1.WorkflowExecution
import io.temporal.api.filter.v1.WorkflowTypeFilter
import io.temporal.api.workflowservice.v1.DeleteWorkflowExecutionRequest
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest
import io.temporal.api.workflowservice.v1.ListClosedWorkflowExecutionsRequest
import io.temporal.api.workflowservice.v1.ListClosedWorkflowExecutionsResponse
import io.temporal.api.workflowservice.v1.ListOpenWorkflowExecutionsRequest
import io.temporal.api.workflowservice.v1.ListOpenWorkflowExecutionsResponse
import io.temporal.api.workflowservice.v1.WorkflowServiceGrpc
import io.temporal.client.WorkflowClient
import io.temporal.workflow.WorkflowInterface
import mu.KotlinLogging
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import ru.dmkuranov.temporaltests.temporal.TemporalConfiguration
import ru.dmkuranov.temporaltests.util.invocation.Invoker

abstract class AbstractTemporalTest : AbstractIntegrationTest() {

    @Autowired
    protected lateinit var workflowClient: WorkflowClient

    @Value("\${spring.temporal.workers-auto-discovery.packages}")
    protected lateinit var temporalBasePackage: String

    val workflowNames: List<String> by lazy {
        Reflections(temporalBasePackage)
            .get(Scanners.TypesAnnotated.get(WorkflowInterface::class.java).asClass<Any?>())
            .map { it.simpleName }
    }

    protected fun terminateOpenExecutions() {
        terminateExecutions(listWfApiOpen)
    }

    protected fun deleteClosedExecutions() {
        deleteExecutions(listWfApiClosed)
    }

    private fun <ApiRequest_T, ApiResponse_T> processExecutions(
        api: ListWfApi<ApiRequest_T, ApiResponse_T>,
        processor: (WorkflowExecution) -> Unit,
        description: String
    ) {
        var processedCount = 0
        val workflowService = workflowService()
        workflowNames.forEach { workflowName ->
            var executionsResponse = api.requestInitial(
                workflowService,
                api.buildRequestInitial(TemporalConfiguration.NAMESPACE_NAME, workflowName)
            )
            while (true) {
                api.extractWorkflowExecutions(executionsResponse).forEach {
                    processExecution(it, processor, api, workflowName, description)
                    processedCount++
                    Thread.sleep(20) // prevent RESOURCE_EXHAUSTED: namespace rate limit exceeded
                }
                val nextPageToken = api.extractNextPageToken(executionsResponse)
                if (!nextPageToken.isEmpty) {
                    executionsResponse = api.requestNextPage(workflowService, pageToken = nextPageToken)
                } else {
                    break
                }
            }
            Thread.sleep(20) // prevent RESOURCE_EXHAUSTED: namespace rate limit exceeded
        }
        log.info { "Processed ${api.description()} $description: $processedCount" }
    }

    private fun <ApiRequest_T, ApiResponse_T> processExecution(
        it: WorkflowExecution,
        processor: (WorkflowExecution) -> Unit,
        api: ListWfApi<ApiRequest_T, ApiResponse_T>,
        workflowName: String,
        description: String
    ) {
        val workflowService = workflowService()
        try {
            val executionDescription = workflowService.describeWorkflowExecution(
                DescribeWorkflowExecutionRequest.getDefaultInstance().toBuilder()
                    .setNamespace(TemporalConfiguration.NAMESPACE_NAME)
                    .setExecution(it)
                    .build()
            )
            try {
                Invoker.temporalOpenToClosedTransitionRetry.invoke { processor(it) }
            } catch (e: Exception) {
                log.info(e) {
                    "Processing problem ${api.description()} $workflowName $description " +
                        "workflow=$workflowName workflowId=${it.workflowId}, runId=${it.runId}, " +
                        "status=${executionDescription.workflowExecutionInfo.status}"
                }
            }
        } catch (e: Exception) {
            log.info(e) { "Processing problem ${api.description()} $workflowName $description workflowId=${it.workflowId}, runId=${it.runId}" }
        }
    }

    private fun <ApiRequest_T, ApiResponse_T> deleteExecutions(api: ListWfApi<ApiRequest_T, ApiResponse_T>) =
        processExecutions(api = api, processor = { execution ->
            workflowService().deleteWorkflowExecution(
                DeleteWorkflowExecutionRequest.newBuilder()
                    .setNamespace(TemporalConfiguration.NAMESPACE_NAME)
                    .setWorkflowExecution(
                        WorkflowExecution.getDefaultInstance().toBuilder()
                            .setWorkflowId(execution.workflowId)
                            .setRunId(execution.runId)
                            .build()
                    )
                    .build()
            )
        }, description = "delete")

    private fun <ApiRequest_T, ApiResponse_T> terminateExecutions(api: ListWfApi<ApiRequest_T, ApiResponse_T>) =
        processExecutions(
            api = api, processor = { execution ->
                val workflow = workflowClient.newUntypedWorkflowStub(execution.workflowId)
                workflow.cancel()
                workflow.terminate("clean up")
            },
            description = "terminate"
        )

    private fun workflowService() =
        workflowClient.workflowServiceStubs.blockingStub()

    interface ListWfApi<ApiRequest_T, ApiResponse_T> {
        fun buildRequestInitial(namespaceName: String, workflowName: String): ApiRequest_T
        fun requestInitial(serviceStub: WorkflowServiceGrpc.WorkflowServiceBlockingStub, request: ApiRequest_T): ApiResponse_T
        fun requestNextPage(serviceStub: WorkflowServiceGrpc.WorkflowServiceBlockingStub, pageToken: ByteString): ApiResponse_T
        fun extractWorkflowExecutions(response: ApiResponse_T): List<WorkflowExecution>
        fun extractNextPageToken(response: ApiResponse_T): ByteString
        fun description(): String
    }

    private val listWfApiOpen = object : ListWfApi<ListOpenWorkflowExecutionsRequest, ListOpenWorkflowExecutionsResponse> {
        override fun buildRequestInitial(namespaceName: String, workflowName: String) =
            ListOpenWorkflowExecutionsRequest.newBuilder()
                .setNamespace(namespaceName)
                .setTypeFilter(WorkflowTypeFilter.newBuilder().setName(workflowName).build())
                .setMaximumPageSize(100)
                .build()!!

        override fun requestNextPage(
            serviceStub: WorkflowServiceGrpc.WorkflowServiceBlockingStub,
            pageToken: ByteString
        ) = serviceStub.listOpenWorkflowExecutions(
            ListOpenWorkflowExecutionsRequest.newBuilder()
                .setNextPageToken(pageToken)
                .setNamespace(TemporalConfiguration.NAMESPACE_NAME)
                .build()
        )!!

        override fun requestInitial(
            serviceStub: WorkflowServiceGrpc.WorkflowServiceBlockingStub,
            request: ListOpenWorkflowExecutionsRequest
        ) = serviceStub.listOpenWorkflowExecutions(request)!!

        override fun extractWorkflowExecutions(response: ListOpenWorkflowExecutionsResponse) =
            response.executionsList.map { it.execution }

        override fun extractNextPageToken(response: ListOpenWorkflowExecutionsResponse) =
            response.nextPageToken!!

        override fun description() = "Open"
    }

    private val listWfApiClosed = object : ListWfApi<ListClosedWorkflowExecutionsRequest, ListClosedWorkflowExecutionsResponse> {
        override fun buildRequestInitial(namespaceName: String, workflowName: String) =
            ListClosedWorkflowExecutionsRequest.newBuilder()
                .setNamespace(namespaceName)
                .setTypeFilter(WorkflowTypeFilter.newBuilder().setName(workflowName).build())
                .setMaximumPageSize(100)
                .build()!!

        override fun requestNextPage(
            serviceStub: WorkflowServiceGrpc.WorkflowServiceBlockingStub,
            pageToken: ByteString
        ) = serviceStub.listClosedWorkflowExecutions(
            ListClosedWorkflowExecutionsRequest.newBuilder()
                .setNextPageToken(pageToken)
                .setNamespace(TemporalConfiguration.NAMESPACE_NAME)
                .build()
        )!!

        override fun requestInitial(
            serviceStub: WorkflowServiceGrpc.WorkflowServiceBlockingStub,
            request: ListClosedWorkflowExecutionsRequest
        ) = serviceStub.listClosedWorkflowExecutions(request)!!

        override fun extractWorkflowExecutions(response: ListClosedWorkflowExecutionsResponse) =
            response.executionsList.map { it.execution }

        override fun extractNextPageToken(response: ListClosedWorkflowExecutionsResponse) =
            response.nextPageToken!!

        override fun description() = "Closed"
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
