package xyz.simoneesposito.writingassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.simoneesposito.writingassistant.model.REQUIRED_MODELS

class ModelInfoTest {

    @Test
    fun requiredModelsListIsNotEmpty() {
        assertTrue("REQUIRED_MODELS must not be empty", REQUIRED_MODELS.isNotEmpty())
    }

    @Test
    fun allModelsHaveNonEmptyFields() {
        REQUIRED_MODELS.forEach { model ->
            assertTrue("${model.id} has blank id", model.id.isNotBlank())
            assertTrue("${model.id} has blank name", model.name.isNotBlank())
            assertTrue("${model.id} has blank description", model.description.isNotBlank())
            assertTrue("${model.id} has blank url", model.url.isNotBlank())
            assertTrue("${model.id} has blank fileName", model.fileName.isNotBlank())
        }
    }

    @Test
    fun allModelIdsAreUnique() {
        val ids = REQUIRED_MODELS.map { it.id }
        assertEquals("Model ids must be unique", ids.size, ids.toSet().size)
    }

    @Test
    fun allModelFileNamesAreUnique() {
        val fileNames = REQUIRED_MODELS.map { it.fileName }
        assertEquals("Model file names must be unique", fileNames.size, fileNames.toSet().size)
    }

    @Test
    fun allModelSizesArePositive() {
        REQUIRED_MODELS.forEach { model ->
            assertTrue("${model.id} sizeMB (${model.sizeMB}) must be > 0", model.sizeMB > 0)
        }
    }

    @Test
    fun allUrlsUseHttps() {
        REQUIRED_MODELS.forEach { model ->
            assertTrue(
                "${model.id} url must use https: ${model.url}",
                model.url.startsWith("https://")
            )
        }
    }

    @Test
    fun allFileNamesHaveLitertlmExtension() {
        REQUIRED_MODELS.forEach { model ->
            assertTrue(
                "${model.id} fileName must end with .litertlm: ${model.fileName}",
                model.fileName.endsWith(".litertlm")
            )
        }
    }

    @Test
    fun atLeastOneModelIsRequired() {
        val hasRequired = REQUIRED_MODELS.any { it.required }
        assertTrue("At least one model must be marked required", hasRequired)
    }
}
