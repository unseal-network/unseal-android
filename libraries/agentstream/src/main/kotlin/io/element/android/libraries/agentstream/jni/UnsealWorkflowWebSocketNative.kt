package io.element.android.libraries.agentstream.jni

internal object UnsealWorkflowWebSocketNative {
    init {
        System.loadLibrary("unseal_agent_stream")
    }

    @JvmStatic external fun nativeCreateWorkflowSession(taskId: String, wsBaseUrl: String, accessToken: String): Long
    @JvmStatic external fun nativeDestroyWorkflowSession(ptr: Long)
    @JvmStatic external fun nativeConnectWorkflow(ptr: Long)
    @JvmStatic external fun nativeDisconnectWorkflow(ptr: Long)
    @JvmStatic external fun nativePollWorkflowMessage(ptr: Long): String
}
