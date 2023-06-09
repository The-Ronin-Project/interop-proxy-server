package com.projectronin.interop.proxy.server.graphql

import com.projectronin.interop.common.jackson.JacksonManager
import com.projectronin.interop.fhir.generators.datatypes.address
import com.projectronin.interop.fhir.generators.datatypes.identifier
import com.projectronin.interop.fhir.generators.datatypes.name
import com.projectronin.interop.fhir.generators.primitives.of
import com.projectronin.interop.fhir.generators.resources.patient
import com.projectronin.interop.fhir.r4.datatype.ContactPoint
import com.projectronin.interop.fhir.r4.datatype.primitive.Code
import com.projectronin.interop.fhir.r4.datatype.primitive.Date
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.proxy.server.client.MockEHRClient
import com.projectronin.interop.proxy.server.client.ProxyClient
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
class PatientIT : BaseGraphQLIT() {

    // we only ever put stuff in mock-ehr, so no need to have a tenant specific data
    private fun addTenantData() {
        val patient = patient {
            id of Id("PatientFHIRID1")
            identifier of listOf(
                identifier {
                    value of "0202497"
                    system of "mockEHRMRNSystem"
                },
                identifier {
                    value of "     Z4572"
                    system of "mockPatientInternalSystem"
                },
                identifier { // just a random identifier we don't use to test
                    value of "987654321A"
                    system of "https://open.epic.com/FHIR/StructureDefinition/PayerMemberId"
                }
            )
            name of listOf(
                name {
                    use of "official"
                    family of "Mychart"
                    given of listOf("Allison")
                },
                name {
                    use of "usual" // required
                    family of "Mychart"
                    given of listOf("Ali")
                }
            )
            gender of "female"
            birthDate of Date("1987-01-15")
            address of listOf(
                address {
                    city of "Madison"
                    line of listOf("123 Main St.".asFHIR())
                    postalCode of "53703"
                    state of "WI"
                    use of "home"
                }
            )
            telecom of listOf(
                ContactPoint(
                    system = Code("phone"),
                    use = Code("home"),
                    value = "608-123-4567".asFHIR()
                ),
                ContactPoint(
                    system = Code("email"),
                    use = null,
                    value = "beau@beau.com".asFHIR()
                )
            )
        }
        MockEHRClient.addResourceWithID(patient, "PatientFHIRID1")
    }

    /**
     * This test is only here to help with debugging.  If the private key isn't set other tests will fail, but this
     * will quickly let us know why.
     */
    @Test
    fun `check private key is set correctly`() {
        val epicDO = ehrDAO.getByInstance("Epic Sandbox")
        assertEquals(System.getenv("AO_SANDBOX_KEY"), epicDO?.privateKey)
    }

    @ParameterizedTest
    @MethodSource("tenantMnemonics")
    fun `server handles patient query`(testTenant: String) {
        addTenantData()
        val query = this::class.java.getResource("/graphql/patientByNameAndDOB.graphql")!!
            .readText()
            .replace("__tenant_mnemonic__", testTenant)
        val expectedJSON = this::class.java.getResource("/testPatientGraphQLResults.json")!!
            .readText()
            .replace("__return__id__", "$testTenant-PatientFHIRID1")
        val response = ProxyClient.query(query, testTenant)

        val body = runBlocking { response.body<String>() }
        val resultJSONNode = JacksonManager.objectMapper.readTree(body)
        val expectedJSONObject = JacksonManager.objectMapper.readTree(expectedJSON)
        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(resultJSONNode.has("errors"))
        assertEquals(expectedJSONObject.toString(), resultJSONNode.toString())
    }

    @Test
    fun `server handles missing data`() {
        val testTenant = "epic"
        addTenantData()

        val given = "Allison"
        val birthdate = "1987-01-15"

        val query = """
            |query {
            |   patientsByNameAndDOB(tenantId: "$testTenant", given: "$given", birthdate: "$birthdate") 
            |   {id}
            |}
        """.trimMargin()
        val response = ProxyClient.query(query, testTenant)
        val body = runBlocking { response.body<String>() }
        val resultJSONNode = JacksonManager.objectMapper.readTree(body)
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(resultJSONNode.has("errors"))
    }

    @ParameterizedTest
    @MethodSource("tenantMnemonics")
    fun `server handles no patient found`(testTenant: String) {
        addTenantData()

        val family = "Fake Name"
        val given = "Fake Name"
        val birthdate = "1900-01-15"

        val query = """
            |query {
            |   patientsByNameAndDOB(tenantId: "$testTenant", family: "$family", given: "$given", birthdate: "$birthdate") 
            |   {id}
            |}
        """.trimMargin()
        val response = ProxyClient.query(query, testTenant)

        val body = runBlocking { response.body<String>() }
        val resultJSONNode = JacksonManager.objectMapper.readTree(body)
        val patientSearchJSONArray = resultJSONNode["data"]["patientsByNameAndDOB"]
        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(resultJSONNode.has("errors"))
        assertEquals(0, patientSearchJSONArray.size())
    }

    @ParameterizedTest
    @MethodSource("tenantMnemonics")
    fun `server handles patient query with m2m auth`(testTenant: String) {
        addTenantData()
        val query = this::class.java.getResource("/graphql/patientByNameAndDOB.graphql")!!
            .readText()
            .replace("__tenant_mnemonic__", testTenant)
        val expectedJSON = this::class.java.getResource("/testPatientGraphQLResults.json")!!
            .readText()
            .replace("__return__id__", "$testTenant-PatientFHIRID1")
        val m2mToken = getM2MAuthentication()
        val response = ProxyClient.query(query, m2mToken)

        val body = runBlocking { response.body<String>() }
        val resultJSONNode = JacksonManager.objectMapper.readTree(body)
        val expectedJSONObject = JacksonManager.objectMapper.readTree(expectedJSON)
        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(resultJSONNode.has("errors"))
        assertEquals(expectedJSONObject.toString(), resultJSONNode.toString())
    }

    @Test
    fun `server handles bad tenant`() {
        addTenantData()

        val tenantId = "fake"
        val family = "Mychart"
        val given = "Allison"
        val birthdate = "1987-01-15"

        val query = """
            |query {
            |   patientsByNameAndDOB(tenantId: "$tenantId", family: "$family", given: "$given", birthdate: "$birthdate") 
            |   {id}
            |}
        """.trimMargin()

        val response = ProxyClient.query(query, "epic")

        val body = runBlocking { response.body<String>() }
        val resultJSONNode = JacksonManager.objectMapper.readTree(body)
        val errorJSONObject = resultJSONNode["errors"].get(0)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            "Exception while fetching data (/patientsByNameAndDOB) : 403 Requested Tenant 'fake' does not match authorized Tenant 'epic'",
            errorJSONObject["message"].asText()
        )
    }

    @Test
    fun `patientsByTenants can support searching multiple tenants`() {
        addTenantData()
        val query = this::class.java.getResource("/graphql/patientsByTenants.graphql")!!.readText()
        val m2mToken = getM2MAuthentication()
        val response = ProxyClient.query(query, m2mToken)

        val body = runBlocking { response.body<String>() }
        val resultJSONNode = JacksonManager.objectMapper.readTree(body)
        val node = resultJSONNode["data"]["patientsByTenants"]
        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(resultJSONNode.has("errors"))
        assertEquals(2, node.size())
    }

    @Test
    fun `patientsByTenants handles unknown tenant and known tenant together`() {
        addTenantData()
        val query =
            this::class.java.getResource("/graphql/patientsByTenants.graphql")!!.readText().replace("cerner", "unknown")
        val m2mToken = getM2MAuthentication()
        val response = ProxyClient.query(query, m2mToken)

        val body = runBlocking { response.body<String>() }
        val resultJSONNode = JacksonManager.objectMapper.readTree(body)
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("404 Invalid Tenant: unknown", resultJSONNode["errors"][0]["message"].asText())
        val roninTenant = resultJSONNode["data"]["patientsByTenants"][0]
        assertEquals(1, roninTenant["patients"].size())
    }
}
