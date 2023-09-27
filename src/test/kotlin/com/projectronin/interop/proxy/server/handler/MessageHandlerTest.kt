package com.projectronin.interop.proxy.server.handler

import com.projectronin.ehr.dataauthority.client.EHRDataAuthorityClient
import com.projectronin.ehr.dataauthority.models.FoundResourceIdentifiers
import com.projectronin.ehr.dataauthority.models.IdentifierSearchResponse
import com.projectronin.ehr.dataauthority.models.IdentifierSearchableResourceTypes
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.inputs.EHRMessageInput
import com.projectronin.interop.ehr.inputs.EHRRecipient
import com.projectronin.interop.ehr.inputs.FHIRIdentifiers
import com.projectronin.interop.ehr.inputs.IdentifierVendorIdentifier
import com.projectronin.interop.fhir.r4.CodeSystem
import com.projectronin.interop.fhir.r4.datatype.Identifier
import com.projectronin.interop.fhir.r4.datatype.primitive.Id
import com.projectronin.interop.fhir.r4.datatype.primitive.Uri
import com.projectronin.interop.fhir.r4.datatype.primitive.asFHIR
import com.projectronin.interop.fhir.r4.resource.Patient
import com.projectronin.interop.fhir.r4.resource.Practitioner
import com.projectronin.interop.proxy.server.context.getAuthorizedTenantId
import com.projectronin.interop.proxy.server.input.MessageInput
import com.projectronin.interop.proxy.server.input.MessagePatientInput
import com.projectronin.interop.proxy.server.input.MessageRecipientInput
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import graphql.schema.DataFetchingEnvironment
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.HttpClientErrorException

class MessageHandlerTest {
    private lateinit var ehrFactory: EHRFactory
    private lateinit var tenantService: TenantService
    private lateinit var messageHandler: MessageHandler
    private lateinit var ehrDataAuthorityClient: EHRDataAuthorityClient
    private lateinit var dfe: DataFetchingEnvironment

    private var provIdentifier = Identifier(system = Uri("system"), value = "1234".asFHIR())
    private var fhirIdentifier1 = Identifier(system = CodeSystem.RONIN_FHIR_ID.uri, value = "doc1".asFHIR())
    private var fhirIdentifier2 = Identifier(system = CodeSystem.RONIN_FHIR_ID.uri, value = "pool1".asFHIR())
    private var identifierVendorIdentifier = IdentifierVendorIdentifier(provIdentifier)

    @BeforeEach
    fun initTest() {
        ehrFactory = mockk()
        tenantService = mockk()

        val searchIdentifier =
            com.projectronin.ehr.dataauthority.models.Identifier(CodeSystem.RONIN_MRN.uri.value!!, "MRN#1")
        val fhirIdentifier =
            com.projectronin.ehr.dataauthority.models.Identifier(CodeSystem.RONIN_FHIR_ID.uri.value!!, "FHIRID")
        ehrDataAuthorityClient = mockk {
            coEvery {
                getResourceIdentifiers(
                    "TEST_TENANT",
                    IdentifierSearchableResourceTypes.Patient,
                    listOf(searchIdentifier)
                )
            } returns
                listOf(
                    IdentifierSearchResponse(
                        searchedIdentifier = searchIdentifier,
                        foundResources = listOf(
                            FoundResourceIdentifiers(
                                "FHIRID",
                                listOf(searchIdentifier, fhirIdentifier)
                            )
                        )
                    )
                )
        }
        messageHandler = MessageHandler(ehrFactory, tenantService, ehrDataAuthorityClient)
        dfe = mockk()
    }

    @Test
    fun `patient not found`() {
        every { dfe.getAuthorizedTenantId() } returns "TEST_TENANT"
        val tenant = mockk<Tenant> {
            every { mnemonic } returns "TEST_TENANT"
        }
        every { tenantService.getTenantForMnemonic("TEST_TENANT") } returns tenant

        val searchIdentifier =
            com.projectronin.ehr.dataauthority.models.Identifier(CodeSystem.RONIN_MRN.uri.value!!, "MRN#1")
        coEvery {
            ehrDataAuthorityClient.getResourceIdentifiers(
                "TEST_TENANT",
                IdentifierSearchableResourceTypes.Patient,
                listOf(searchIdentifier)
            )
        } returns listOf(IdentifierSearchResponse(searchIdentifier, emptyList()))

        val messageInput = MessageInput("Test Message", MessagePatientInput("MRN#1", null), listOf())
        assertEquals(
            "Attempted to send message for patient with MRN MRN#1 who does not exist in EHR Data Authority.",
            messageHandler.sendMessage("TEST_TENANT", messageInput, dfe).errors.first().message
        )
    }

    @Test
    fun `multiple patients found`() {
        every { dfe.getAuthorizedTenantId() } returns "TEST_TENANT"
        val tenant = mockk<Tenant> {
            every { mnemonic } returns "TEST_TENANT"
        }
        every { tenantService.getTenantForMnemonic("TEST_TENANT") } returns tenant

        val searchIdentifier =
            com.projectronin.ehr.dataauthority.models.Identifier(CodeSystem.RONIN_MRN.uri.value!!, "MRN#1")
        coEvery {
            ehrDataAuthorityClient.getResourceIdentifiers(
                "TEST_TENANT",
                IdentifierSearchableResourceTypes.Patient,
                listOf(searchIdentifier)
            )
        } returns listOf(
            IdentifierSearchResponse(
                searchIdentifier,
                listOf(FoundResourceIdentifiers("udp1", emptyList()), FoundResourceIdentifiers("udp2", emptyList()))
            )
        )

        val messageInput = MessageInput("Test Message", MessagePatientInput("MRN#1", null), listOf())
        assertEquals(
            "More than 1 patient found for MRN MRN#1 with tenant TEST_TENANT",
            messageHandler.sendMessage("TEST_TENANT", messageInput, dfe).errors.first().message
        )
    }

    @Test
    fun `patient not found and no matching search identifier returned`() {
        every { dfe.getAuthorizedTenantId() } returns "TEST_TENANT"
        val tenant = mockk<Tenant> {
            every { mnemonic } returns "TEST_TENANT"
        }
        every { tenantService.getTenantForMnemonic("TEST_TENANT") } returns tenant

        val searchIdentifier =
            com.projectronin.ehr.dataauthority.models.Identifier(CodeSystem.RONIN_MRN.uri.value!!, "MRN#1")
        coEvery {
            ehrDataAuthorityClient.getResourceIdentifiers(
                "TEST_TENANT",
                IdentifierSearchableResourceTypes.Patient,
                listOf(searchIdentifier)
            )
        } returns listOf(
            IdentifierSearchResponse(
                com.projectronin.ehr.dataauthority.models.Identifier(
                    CodeSystem.RONIN_MRN.uri.value!!,
                    "MRN#2"
                ),
                emptyList()
            )
        )

        val messageInput = MessageInput("Test Message", MessagePatientInput("MRN#1", null), listOf())
        assertEquals(
            "Attempted to send message for patient with MRN MRN#1 who does not exist in EHR Data Authority.",
            messageHandler.sendMessage("TEST_TENANT", messageInput, dfe).errors.first().message
        )
    }

    @Test
    fun `unknown tenant returns an error`() {
        every { dfe.getAuthorizedTenantId() } returns "TEST_TENANT"
        every { tenantService.getTenantForMnemonic("TEST_TENANT") } returns null

        val messageInput = MessageInput("Test Message", MessagePatientInput("MRN#1", null), listOf())
        val exception = assertThrows<HttpClientErrorException> {
            messageHandler.sendMessage("TEST_TENANT", messageInput, dfe)
        }

        assertEquals("404 Invalid Tenant: TEST_TENANT", exception.message)
    }

    @Test
    fun `unknown vendor returns an error`() {
        every { dfe.getAuthorizedTenantId() } returns "TEST_TENANT"
        val tenant = mockk<Tenant> {
            every { mnemonic } returns "TEST_TENANT"
        }
        every { tenantService.getTenantForMnemonic("TEST_TENANT") } returns tenant

        every { ehrFactory.getVendorFactory(tenant) } throws IllegalStateException("Error")

        val messageInput = MessageInput("Test Message", MessagePatientInput("MRN#1", null), listOf())
        val exception = assertThrows<IllegalStateException> {
            messageHandler.sendMessage("TEST_TENANT", messageInput, dfe)
        }

        assertEquals("Error", exception.message)
    }

    @Test
    fun `ensure message can be sent`() {
        every { dfe.getAuthorizedTenantId() } returns "TEST_TENANT"
        val tenant = mockk<Tenant> {
            every { mnemonic } returns "TEST_TENANT"
        }
        every { tenantService.getTenantForMnemonic("TEST_TENANT") } returns tenant
        val expectedEHRMessageInput = EHRMessageInput("Test Message", "FHIRID", listOf())
        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { messageService } returns mockk {
                every { sendMessage(tenant, expectedEHRMessageInput) } returns ("messageId#1")
            }
        }

        val messageInput = MessageInput("Test Message", MessagePatientInput("MRN#1", null), listOf())
        val actualResponse = messageHandler.sendMessage("TEST_TENANT", messageInput, dfe).data

        assertEquals("sent", actualResponse)
    }

    @Test
    fun `ensure message with one recipient can be sent`() {
        every { dfe.getAuthorizedTenantId() } returns "TEST_TENANT"
        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "TEST_TENANT"
        every { tenantService.getTenantForMnemonic("TEST_TENANT") } returns tenant

        val expectedEHRMessageInput =
            EHRMessageInput(
                "Test Message",
                "FHIRID",
                listOf(
                    EHRRecipient("doc1", identifierVendorIdentifier)
                )
            )
        val fhirIdentifiers = FHIRIdentifiers(
            id = Id("doc1"),
            identifiers = listOf(provIdentifier, fhirIdentifier1)
        )

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { messageService } returns mockk {
                every { sendMessage(tenant, expectedEHRMessageInput) } returns ("messageId#1")
            }
            every { identifierService } returns mockk {
                every { getPractitionerUserIdentifier(tenant, fhirIdentifiers) } returns (provIdentifier)
            }
        }
        coEvery {
            ehrDataAuthorityClient.getResourceAs<Practitioner>(
                "TEST_TENANT",
                "Practitioner",
                "TEST_TENANT-doc1"
            )
        } returns mockk {
            every { identifier } returns listOf(provIdentifier, fhirIdentifier1)
        }

        val messageInput =
            MessageInput(
                "Test Message",
                MessagePatientInput("MRN#1", null),
                listOf(MessageRecipientInput("TEST_TENANT-doc1"))
            )
        val actualResponse = messageHandler.sendMessage("TEST_TENANT", messageInput, dfe).data

        assertEquals("sent", actualResponse)
    }

    @Test
    fun `ensure message with multiple recipients can be sent`() {
        every { dfe.getAuthorizedTenantId() } returns "TEST_TENANT"
        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "TEST_TENANT"
        every { tenantService.getTenantForMnemonic("TEST_TENANT") } returns tenant

        val expectedEHRMessageInput =
            EHRMessageInput(
                "Test Message",
                "FHIRID",
                listOf(
                    EHRRecipient(
                        "doc1",
                        identifierVendorIdentifier
                    ),
                    EHRRecipient(
                        "pool1",
                        identifierVendorIdentifier
                    )
                )
            )
        val fhirIdentifiersDoc = FHIRIdentifiers(
            id = Id("doc1"),
            identifiers = listOf(provIdentifier, fhirIdentifier1)
        )
        val fhirIdentifiersPool = FHIRIdentifiers(
            id = Id("pool1"),
            identifiers = listOf(provIdentifier, fhirIdentifier2)
        )

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { messageService } returns mockk {
                every { sendMessage(tenant, expectedEHRMessageInput) } returns ("messageId#1")
            }
            every { identifierService } returns mockk {
                every { getPractitionerUserIdentifier(tenant, fhirIdentifiersDoc) } returns (provIdentifier)
                every {
                    getPractitionerUserIdentifier(
                        tenant,
                        fhirIdentifiersPool
                    )
                } returns (provIdentifier)
            }
        }
        val messageInput =
            MessageInput(
                "Test Message",
                MessagePatientInput("MRN#1", null),
                listOf(MessageRecipientInput("TEST_TENANT-doc1"), MessageRecipientInput("TEST_TENANT-pool1"))
            )

        coEvery {
            ehrDataAuthorityClient.getResourceAs<Practitioner>(
                "TEST_TENANT",
                "Practitioner",
                "TEST_TENANT-doc1"
            )
        } returns mockk {
            every { identifier } returns listOf(provIdentifier, fhirIdentifier1)
        }
        coEvery {
            ehrDataAuthorityClient.getResourceAs<Practitioner>(
                "TEST_TENANT",
                "Practitioner",
                "TEST_TENANT-pool1"
            )
        } returns mockk {
            every { identifier } returns listOf(provIdentifier, fhirIdentifier2)
        }
        val actualResponse = messageHandler.sendMessage("TEST_TENANT", messageInput, dfe).data

        assertEquals("sent", actualResponse)
    }

    @Test
    fun `ensure message can be sent when sent without mrn`() {
        every { dfe.getAuthorizedTenantId() } returns "TEST_TENANT"
        val tenant = mockk<Tenant> {
            every { mnemonic } returns "TEST_TENANT"
        }
        every { tenantService.getTenantForMnemonic("TEST_TENANT") } returns tenant
        val expectedEHRMessageInput = EHRMessageInput("Test Message", "fhirId", listOf())
        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { messageService } returns mockk {
                every { sendMessage(tenant, expectedEHRMessageInput) } returns ("messageId#1")
            }
        }
        coEvery {
            ehrDataAuthorityClient.getResourceIdentifiers(
                "TEST_TENANT",
                IdentifierSearchableResourceTypes.Patient,
                listOf(com.projectronin.ehr.dataauthority.models.Identifier(CodeSystem.RONIN_MRN.uri.value!!, "MRN#1"))
            )
        } throws Exception("shouldn't be hit")

        coEvery {
            ehrDataAuthorityClient.getResourceAs<Patient>("TEST_TENANT", "Patient", "TEST_TENANT-fhirId")
        } returns mockk {
            every { identifier } returns listOf(
                mockk {
                    every { system } returns CodeSystem.RONIN_FHIR_ID.uri
                    every { value } returns "fhirId".asFHIR()
                }
            )
        }

        val messageInput = MessageInput("Test Message", MessagePatientInput(null, "TEST_TENANT-fhirId"), listOf())
        val actualResponse = messageHandler.sendMessage("TEST_TENANT", messageInput, dfe).data

        assertEquals("sent", actualResponse)
    }

    @Test
    fun `error returned when patient not found with supplied FHIR ID`() {
        every { dfe.getAuthorizedTenantId() } returns "TEST_TENANT"
        val tenant = mockk<Tenant> {
            every { mnemonic } returns "TEST_TENANT"
        }
        every { tenantService.getTenantForMnemonic("TEST_TENANT") } returns tenant
        val expectedEHRMessageInput = EHRMessageInput("Test Message", "fhirId", listOf())
        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { messageService } returns mockk {
                every { sendMessage(tenant, expectedEHRMessageInput) } returns ("messageId#1")
            }
        }
        coEvery {
            ehrDataAuthorityClient.getResourceIdentifiers(
                "TEST_TENANT",
                IdentifierSearchableResourceTypes.Patient,
                listOf(com.projectronin.ehr.dataauthority.models.Identifier(CodeSystem.RONIN_MRN.uri.value!!, "MRN#1"))
            )
        } throws Exception("shouldn't be hit")

        coEvery {
            ehrDataAuthorityClient.getResourceAs<Patient>("TEST_TENANT", "Patient", "TEST_TENANT-fhirId")
        } returns null

        val messageInput = MessageInput("Test Message", MessagePatientInput(null, "TEST_TENANT-fhirId"), listOf())
        assertEquals(
            "No patient found for TEST_TENANT-fhirId",
            messageHandler.sendMessage("TEST_TENANT", messageInput, dfe).errors.first().message
        )
    }

    @Test
    fun `error returned when practitioner not found with supplied FHIR ID`() {
        every { dfe.getAuthorizedTenantId() } returns "TEST_TENANT"
        val tenant = mockk<Tenant>()
        every { tenant.mnemonic } returns "TEST_TENANT"
        every { tenantService.getTenantForMnemonic("TEST_TENANT") } returns tenant

        val expectedEHRMessageInput =
            EHRMessageInput(
                "Test Message",
                "FHIRID",
                listOf(
                    EHRRecipient("doc1", identifierVendorIdentifier)
                )
            )
        val fhirIdentifiers = FHIRIdentifiers(
            id = Id("doc1"),
            identifiers = listOf(provIdentifier, fhirIdentifier1)
        )

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { messageService } returns mockk {
                every { sendMessage(tenant, expectedEHRMessageInput) } returns ("messageId#1")
            }
            every { identifierService } returns mockk {
                every { getPractitionerUserIdentifier(tenant, fhirIdentifiers) } returns (provIdentifier)
            }
        }
        coEvery {
            ehrDataAuthorityClient.getResourceAs<Practitioner>(
                "TEST_TENANT",
                "Practitioner",
                "TEST_TENANT-doc1"
            )
        } returns null

        val messageInput =
            MessageInput(
                "Test Message",
                MessagePatientInput("MRN#1", null),
                listOf(MessageRecipientInput("TEST_TENANT-doc1"))
            )
        val exception =
            assertThrows<IllegalArgumentException> { messageHandler.sendMessage("TEST_TENANT", messageInput, dfe) }
        assertEquals("No Practitioner found for TEST_TENANT-doc1", exception.message)
    }

    @Test
    fun `ensure error when no input`() {
        every { dfe.getAuthorizedTenantId() } returns "TEST_TENANT"
        val tenant = mockk<Tenant> {
            every { mnemonic } returns "TEST_TENANT"
        }
        every { tenantService.getTenantForMnemonic("TEST_TENANT") } returns tenant
        val messageInput = MessageInput("Test Message", MessagePatientInput(null, null), listOf())
        assertEquals(
            "Either MRN or Ronin ID must be specified",
            messageHandler.sendMessage("TEST_TENANT", messageInput, dfe).errors.first().message
        )
    }
}
