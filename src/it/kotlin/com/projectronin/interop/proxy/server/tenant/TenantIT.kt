package com.projectronin.interop.proxy.server.tenant

import com.projectronin.interop.common.vendor.VendorType
import com.projectronin.interop.proxy.server.client.ProxyClient
import com.projectronin.interop.proxy.server.tenant.model.Cerner
import com.projectronin.interop.proxy.server.tenant.model.Epic
import com.projectronin.interop.proxy.server.tenant.model.Tenant
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalTime

class TenantIT : BaseTenantControllerIT() {
    private val urlPart = "/tenants"
    private val url = "$serverUrl$urlPart"

    @BeforeEach
    fun `add ehr`() {
        populateTenantData()
    }

    @Test
    fun `can read all tenants`() {
        val response = ProxyClient.get(url)

        val body = runBlocking { response.body<List<Tenant>>() }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(2, body.size)
        assertEquals("epic", body[0].mnemonic)
        assertEquals(VendorType.EPIC, body[0].vendor.vendorType)
        assertTrue(body[0].monitoredIndicator!!)
        assertEquals("cerner", body[1].mnemonic)
        assertEquals(VendorType.CERNER, body[1].vendor.vendorType)
        assertNull(body[1].monitoredIndicator)
    }

    @Test
    fun `can read a specific tenant by mnemonic`() {
        val response = ProxyClient.get("$url/epic")

        val body = runBlocking { response.body<Tenant>() }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("epic", body.mnemonic)
        assertEquals("EPIC", body.vendor.vendorType.toString())
    }

    @Test
    fun `404 for fake tenant`() {
        val response = ProxyClient.get("$url/fullyFhirCompliant")

        val body = runBlocking { response.body<String>() }
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("", body)
    }

    @Test
    fun `can insert a tenant - epic`() {
        val vendor =
            Epic(
                release = "1.0",
                serviceEndpoint = "https://apporchard.epic.com/interconnect-aocurprd-oauth",
                authEndpoint = "https://apporchard.epic.com/interconnect-aocurprd-oauth/oauth2/token",
                ehrUserId = "1",
                messageType = "1",
                practitionerProviderSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.836982",
                practitionerUserSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.697780",
                patientMRNSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.5.737384.14",
                patientInternalSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.698084",
                encounterCSNSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.5.737384.8",
                patientMRNTypeText = "MRN",
                hsi = null,
                instanceName = "Epic Sandbox",
                departmentInternalSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.686980",
                patientOnboardedFlagId = null,
                orderSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.798268",
            )
        val newTenant =
            Tenant(
                id = 0,
                mnemonic = "CoolNewBoi",
                availableStart = LocalTime.of(22, 0),
                availableEnd = LocalTime.of(6, 0),
                vendor = vendor,
                name = "coolest boi hospital",
                timezone = "America/Chicago",
            )

        val response = ProxyClient.post(url, newTenant)

        val body = runBlocking { response.body<Tenant>() }
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(vendor, body.vendor)
        assertEquals("CoolNewBoi", body.mnemonic)
    }

    @Test
    fun `can insert a tenant - cerner`() {
        val vendor =
            Cerner(
                serviceEndpoint = "serviceEndpoint",
                authEndpoint = "authEndpoint",
                patientMRNSystem = "patientMRNSystem",
                instanceName = "Cerner Sandbox",
                messagePractitioner = "Practitioner1",
                messageTopic = "Ronin Alert",
                messageCategory = "alert",
                messagePriority = "routine",
            )

        val newTenant =
            Tenant(
                id = 0,
                mnemonic = "CoolNewBoi",
                availableStart = LocalTime.of(22, 0),
                availableEnd = LocalTime.of(6, 0),
                vendor = vendor,
                name = "coolest boi hospital",
                timezone = "America/Chicago",
            )
        val response = ProxyClient.post(url, newTenant)

        val body = runBlocking { response.body<Tenant>() }
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(vendor, body.vendor)
        assertEquals("CoolNewBoi", body.mnemonic)
    }

    @Test
    fun `can insert a tenant with custom MRN`() {
        val vendor =
            Epic(
                release = "1.0",
                serviceEndpoint = "https://apporchard.epic.com/interconnect-aocurprd-oauth",
                authEndpoint = "https://apporchard.epic.com/interconnect-aocurprd-oauth/oauth2/token",
                ehrUserId = "1",
                messageType = "1",
                practitionerProviderSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.836982",
                practitionerUserSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.697780",
                patientMRNSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.5.737384.14",
                patientInternalSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.698084",
                encounterCSNSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.5.737384.8",
                patientMRNTypeText = "Custom MRN",
                hsi = null,
                instanceName = "Epic Sandbox",
                departmentInternalSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.686980",
                patientOnboardedFlagId = null,
                orderSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.798268",
            )

        val newTenant =
            Tenant(
                id = 0,
                mnemonic = "CoolNewBoi",
                availableStart = LocalTime.of(22, 0),
                availableEnd = LocalTime.of(6, 0),
                vendor = vendor,
                name = "coolest boi hospital",
                timezone = "America/New_York",
            )

        val response = ProxyClient.post(url, newTenant)

        val body = runBlocking { response.body<Tenant>() }
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(vendor, body.vendor)
        assertEquals("CoolNewBoi", body.mnemonic)
    }

    @Test
    fun `can update a tenant - epic`() {
        val vendor =
            Epic(
                release = "2.0",
                serviceEndpoint = "https://apporchard.epic.com/interconnect-aocurprd-oauth",
                authEndpoint = "https://apporchard.epic.com/interconnect-aocurprd-oauth/oauth2/token",
                ehrUserId = "1",
                messageType = "1",
                practitionerProviderSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.836982",
                practitionerUserSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.697780",
                patientMRNSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.5.737384.14",
                patientInternalSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.698084",
                encounterCSNSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.5.737384.8",
                patientMRNTypeText = "MRN",
                hsi = null,
                instanceName = "Epic Sandbox",
                departmentInternalSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.686980",
                patientOnboardedFlagId = "135124",
                orderSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.798268",
            )

        val updatedTenant =
            Tenant(
                id = 1001,
                mnemonic = "epic",
                availableStart = LocalTime.of(22, 0),
                availableEnd = LocalTime.of(6, 0),
                vendor = vendor,
                name = "App Orchard Test",
                timezone = "America/Denver",
                monitoredIndicator = false,
            )

        val response = ProxyClient.put("$url/epic", updatedTenant)

        val body = runBlocking { response.body<Tenant>() }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(updatedTenant.mnemonic, body.mnemonic)
    }

    @Test
    fun `can update a tenant - cerner`() {
        val vendor =
            Cerner(
                serviceEndpoint = "new serviceEndpoint",
                authEndpoint = "new authEndpoint",
                patientMRNSystem = "new patientMRNSystem",
                instanceName = "Cerner Sandbox",
                messagePractitioner = "NewPractitioner1",
                messageTopic = null,
                messageCategory = null,
                messagePriority = null,
            )

        val updatedTenant =
            Tenant(
                id = 2002,
                mnemonic = "cerner",
                availableStart = LocalTime.of(22, 0),
                availableEnd = LocalTime.of(6, 0),
                vendor = vendor,
                name = "App Orchard Test",
                timezone = "America/Denver",
                monitoredIndicator = true,
            )
        val response = ProxyClient.put("$url/cerner", updatedTenant)

        val body = runBlocking { response.body<Tenant>() }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(updatedTenant.mnemonic, body.mnemonic)
    }

    @Test
    fun `can update a tenant with custom MRN`() {
        val vendor =
            Epic(
                release = "2.0",
                serviceEndpoint = "https://apporchard.epic.com/interconnect-aocurprd-oauth",
                authEndpoint = "https://apporchard.epic.com/interconnect-aocurprd-oauth/oauth2/token",
                ehrUserId = "1",
                messageType = "1",
                practitionerProviderSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.836982",
                practitionerUserSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.697780",
                patientMRNSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.5.737384.14",
                patientInternalSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.698084",
                encounterCSNSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.5.737384.8",
                patientMRNTypeText = "Custom MRN",
                hsi = null,
                instanceName = "Epic Sandbox",
                departmentInternalSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.686980",
                patientOnboardedFlagId = null,
                orderSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.798268",
            )

        val updatedTenant =
            Tenant(
                id = 1001,
                mnemonic = "epic",
                availableStart = LocalTime.of(22, 0),
                availableEnd = LocalTime.of(6, 0),
                vendor = vendor,
                name = "App Orchard Test",
                timezone = "America/Los_Angeles",
                monitoredIndicator = false,
            )

        val response = ProxyClient.put("$url/epic", updatedTenant)

        val body = runBlocking { response.body<Tenant>() }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(updatedTenant.mnemonic, body.mnemonic)
    }

    @Test
    fun `inserting without important flag defaults to true`() {
        val vendor =
            Epic(
                release = "1.0",
                serviceEndpoint = "https://apporchard.epic.com/interconnect-aocurprd-oauth",
                authEndpoint = "https://apporchard.epic.com/interconnect-aocurprd-oauth/oauth2/token",
                ehrUserId = "1",
                messageType = "1",
                practitionerProviderSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.836982",
                practitionerUserSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.697780",
                patientMRNSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.5.737384.14",
                patientInternalSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.698084",
                encounterCSNSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.5.737384.8",
                patientMRNTypeText = "MRN",
                hsi = null,
                instanceName = "Epic Sandbox",
                departmentInternalSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.686980",
                patientOnboardedFlagId = null,
                orderSystem = "urn:oid:1.2.840.114350.1.13.0.1.7.2.798268",
            )

        val newTenant =
            Tenant(
                id = 0,
                mnemonic = "CoolNewBoi",
                availableStart = LocalTime.of(22, 0),
                availableEnd = LocalTime.of(6, 0),
                vendor = vendor,
                name = "coolest boi hospital",
                timezone = "America/Chicago",
            )

        val response = ProxyClient.post(url, newTenant)

        val body = runBlocking { response.body<Tenant>() }
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(vendor, body.vendor)
        assertTrue(body.monitoredIndicator!!)
    }

    @Test
    fun `updating without the important flag defaults to true`() {
        val vendor =
            Cerner(
                serviceEndpoint = "new serviceEndpoint",
                authEndpoint = "new authEndpoint",
                patientMRNSystem = "new patientMRNSystem",
                instanceName = "Cerner Sandbox",
                messagePractitioner = "NewPractitioner1",
                messageTopic = null,
                messageCategory = null,
                messagePriority = null,
            )

        val updatedTenant =
            Tenant(
                id = 2002,
                mnemonic = "cerner",
                availableStart = LocalTime.of(22, 0),
                availableEnd = LocalTime.of(6, 0),
                vendor = vendor,
                name = "App Orchard Test",
                timezone = "America/Denver",
            )

        val response = ProxyClient.put("$url/cerner", updatedTenant)

        val body = runBlocking { response.body<Tenant>() }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.monitoredIndicator!!)
    }
}
