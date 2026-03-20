package xyz.simoneesposito.writingassistant.model

data class ModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val sizeMB: Int,
    val url: String,
    val fileName: String,
    val required: Boolean = true
)

val REQUIRED_MODELS = listOf(
    ModelInfo(
        id = "qwen2.5_1.5b",
        name = "Writing Model (Qwen2.5 1.5B)",
        description = "Powers all writing transformations on-device",
        sizeMB = 1600,
        url = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/19edb84c69a0212f29a6ef17ba0d6f278b6a1614/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        fileName = "qwen2.5_1.5b.litertlm",
        required = true
    )
)
