package com.projectronin.interop.proxy.server.handler

import com.projectronin.interop.common.resource.ResourceType
import com.projectronin.interop.ehr.factory.EHRFactory
import com.projectronin.interop.ehr.model.Bundle
import com.projectronin.interop.ehr.model.Patient
import com.projectronin.interop.fhir.r4.valueset.AddressUse
import com.projectronin.interop.fhir.r4.valueset.AdministrativeGender
import com.projectronin.interop.fhir.r4.valueset.ContactPointSystem
import com.projectronin.interop.fhir.r4.valueset.ContactPointUse
import com.projectronin.interop.fhir.r4.valueset.NameUse
import com.projectronin.interop.queue.QueueService
import com.projectronin.interop.queue.model.Message
import com.projectronin.interop.queue.model.MessageType
import com.projectronin.interop.tenant.config.TenantService
import com.projectronin.interop.tenant.config.model.Tenant
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PatientHandlerTest {
    private lateinit var ehrFactory: EHRFactory
    private lateinit var tenantService: TenantService
    private lateinit var queueService: QueueService
    private lateinit var patientHandler: PatientHandler

    @BeforeEach
    fun initTest() {
        ehrFactory = mockk()
        tenantService = mockk()
        queueService = mockk()
        patientHandler = PatientHandler(ehrFactory, tenantService, queueService)
    }

    @Test
    fun `unknown tenant returns an error`() {
        every { tenantService.getTenantForMnemonic("tenantId") } returns null

        every { queueService.enqueueMessages(listOf()) } just Runs

        // Run Test
        val result = patientHandler.patientsByNameAndDOB(
            tenantId = "tenantId",
            birthdate = "1984-08-31",
            given = "Josh",
            family = "Smith"
        )

        assertNotNull(result)
        assertEquals("Tenant not found: tenantId", result.errors[0].message)
    }

    @Test
    fun `unknown vendor returns an error`() {
        val tenant = mockk<Tenant>()
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant

        every { ehrFactory.getVendorFactory(tenant) } throws IllegalStateException("Error")

        every { queueService.enqueueMessages(listOf()) } just Runs

        // Run Test
        val result = patientHandler.patientsByNameAndDOB(
            tenantId = "tenantId",
            birthdate = "1984-08-31",
            given = "Josh",
            family = "Smith"
        )

        assertNotNull(result)
        assertEquals("Error", result.errors[0].message)
    }

    @Test
    fun `ensure findPatient exception is returned as error`() {
        val tenant = mockk<Tenant>()
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { patientService } returns mockk {
                every {
                    findPatient(
                        tenant = tenant,
                        birthDate = "1984-08-31",
                        familyName = "Smith",
                        givenName = "Josh"
                    )
                } throws (IllegalStateException("Error"))
            }
        }

        every { queueService.enqueueMessages(listOf()) } just Runs

        // Run Test
        val result = patientHandler.patientsByNameAndDOB(
            tenantId = "tenantId",
            birthdate = "1984-08-31",
            given = "Josh",
            family = "Smith"
        )

        assertNotNull(result)
        assertEquals("Error", result.errors[0].message)
    }

    @Test
    fun `ensure full patient is correctly returned`() {
        val response = mockk<Bundle<Patient>> {
            every { resources } returns listOf(
                mockk {
                    every { id } returns "Patient-UUID-1"
                    every { identifier } returns listOf(
                        mockk {
                            every { system } returns "http://hl7.org/fhir/sid/us-ssn"
                            every { value } returns "987-65-4321"
                        }
                    )
                    every { name } returns listOf(
                        mockk {
                            every { use } returns NameUse.USUAL
                            every { family } returns "Smith"
                            every { given } returns listOf("Josh")
                        }
                    )
                    every { birthDate } returns "1984-08-31"
                    every { gender } returns AdministrativeGender.MALE
                    every { telecom } returns listOf(
                        mockk {
                            every { system } returns ContactPointSystem.PHONE
                            every { use } returns ContactPointUse.MOBILE
                            every { value } returns "123-456-7890"
                        }
                    )
                    every { address } returns listOf(
                        mockk {
                            every { use } returns AddressUse.HOME
                            every { line } returns listOf("1234 Main St")
                            every { city } returns "Anywhere"
                            every { state } returns "FL"
                            every { postalCode } returns "37890"
                        }
                    )
                    every { raw } returns "raw JSON for patient"
                }
            )
        }

        val tenant = mockk<Tenant>()
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { patientService } returns mockk {
                every {
                    findPatient(
                        tenant = tenant,
                        birthDate = "1984-08-31",
                        familyName = "Smith",
                        givenName = "Josh"
                    )
                } returns response
            }
        }

        every {
            queueService.enqueueMessages(
                listOf(
                    Message(
                        id = null,
                        messageType = MessageType.API,
                        resourceType = ResourceType.PATIENT,
                        tenant = "tenantId",
                        text = "raw JSON for patient"
                    )
                )
            )
        } just Runs

        // Run Test
        val actualResponse = patientHandler.patientsByNameAndDOB(
            tenantId = "tenantId",
            birthdate = "1984-08-31",
            given = "Josh",
            family = "Smith"
        )

        // Assert outcome
        assertNotNull(actualResponse)

        // Patient
        assertEquals(1, actualResponse.data.size)
        val actualPatient = actualResponse.data[0]
        assertEquals("Patient-UUID-1", actualPatient.id)
        assertEquals("male", actualPatient.gender)
        assertEquals("1984-08-31", actualPatient.birthDate) // so old

        // Identifier
        assertEquals(1, actualPatient.identifier.size)
        val actualIdentifier = actualPatient.identifier[0]
        assertEquals("http://hl7.org/fhir/sid/us-ssn", actualIdentifier.system)
        assertEquals("987-65-4321", actualIdentifier.value)

        // Name
        assertEquals(1, actualPatient.name.size)
        val actualName = actualPatient.name[0]
        assertEquals("usual", actualName.use)
        assertEquals("Smith", actualName.family)
        assertEquals(listOf("Josh"), actualName.given)

        // Contact
        assertEquals(1, actualPatient.telecom.size)
        val actualContact = actualPatient.telecom[0]
        assertEquals("phone", actualContact.system)
        assertEquals("mobile", actualContact.use)
        assertEquals("123-456-7890", actualContact.value)

        // Address
        assertEquals(1, actualPatient.address.size)
        val actualAddress = actualPatient.address[0]
        assertEquals("home", actualAddress.use)
        assertEquals(listOf("1234 Main St"), actualAddress.line)
        assertEquals("Anywhere", actualAddress.city)
        assertEquals("FL", actualAddress.state)
        assertEquals("37890", actualAddress.postalCode)
    }

    @Test
    fun `ensure enqueueMessage exception still returns data to user`() {
        val response = mockk<Bundle<Patient>> {
            every { resources } returns listOf(
                mockk {
                    every { id } returns "Patient-UUID-1"
                    every { identifier } returns listOf(
                        mockk {
                            every { system } returns "http://hl7.org/fhir/sid/us-ssn"
                            every { value } returns "987-65-4321"
                        }
                    )
                    every { name } returns listOf(
                        mockk {
                            every { use } returns NameUse.USUAL
                            every { family } returns "Smith"
                            every { given } returns listOf("Josh")
                        }
                    )
                    every { birthDate } returns "1984-08-31"
                    every { gender } returns AdministrativeGender.MALE
                    every { telecom } returns listOf(
                        mockk {
                            every { system } returns ContactPointSystem.PHONE
                            every { use } returns ContactPointUse.MOBILE
                            every { value } returns "123-456-7890"
                        }
                    )
                    every { address } returns listOf(
                        mockk {
                            every { use } returns AddressUse.HOME
                            every { line } returns listOf("1234 Main St")
                            every { city } returns "Anywhere"
                            every { state } returns "FL"
                            every { postalCode } returns "37890"
                        }
                    )
                    every { raw } returns "raw JSON for patient"
                }
            )
        }

        val tenant = mockk<Tenant>()
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { patientService } returns mockk {
                every {
                    findPatient(
                        tenant = tenant,
                        birthDate = "1984-08-31",
                        familyName = "Smith",
                        givenName = "Josh"
                    )
                } returns response
            }
        }

        every {
            queueService.enqueueMessages(
                listOf(
                    Message(
                        id = null,
                        messageType = MessageType.API,
                        resourceType = ResourceType.PATIENT,
                        tenant = "tenantId",
                        text = "raw JSON for patient"
                    )
                )
            )
        } throws (Exception("exception"))

        // Run Test
        val actualResponse = patientHandler.patientsByNameAndDOB(
            tenantId = "tenantId",
            birthdate = "1984-08-31",
            given = "Josh",
            family = "Smith"
        )

        // Assert outcome
        assertNotNull(actualResponse)

        // Patient
        assertEquals(1, actualResponse.data.size)
        val actualPatient = actualResponse.data[0]
        assertEquals("Patient-UUID-1", actualPatient.id)
        assertEquals("male", actualPatient.gender)
        assertEquals("1984-08-31", actualPatient.birthDate) // so old

        // Identifier
        assertEquals(1, actualPatient.identifier.size)
        val actualIdentifier = actualPatient.identifier[0]
        assertEquals("http://hl7.org/fhir/sid/us-ssn", actualIdentifier.system)
        assertEquals("987-65-4321", actualIdentifier.value)

        // Name
        assertEquals(1, actualPatient.name.size)
        val actualName = actualPatient.name[0]
        assertEquals("usual", actualName.use)
        assertEquals("Smith", actualName.family)
        assertEquals(listOf("Josh"), actualName.given)

        // Contact
        assertEquals(1, actualPatient.telecom.size)
        val actualContact = actualPatient.telecom[0]
        assertEquals("phone", actualContact.system)
        assertEquals("mobile", actualContact.use)
        assertEquals("123-456-7890", actualContact.value)

        // Address
        assertEquals(1, actualPatient.address.size)
        val actualAddress = actualPatient.address[0]
        assertEquals("home", actualAddress.use)
        assertEquals(listOf("1234 Main St"), actualAddress.line)
        assertEquals("Anywhere", actualAddress.city)
        assertEquals("FL", actualAddress.state)
        assertEquals("37890", actualAddress.postalCode)
    }

    @Test
    fun `ensure patient with empty lists is correctly returned`() {
        val response = mockk<Bundle<Patient>> {
            every { resources } returns listOf(
                mockk {
                    every { id } returns "Patient-UUID-1"
                    every { identifier } returns listOf()
                    every { name } returns listOf()
                    every { birthDate } returns "1984-08-31"
                    every { gender } returns AdministrativeGender.MALE
                    every { telecom } returns listOf()
                    every { address } returns listOf()
                    every { raw } returns "raw JSON for patient"
                }
            )
        }

        val tenant = mockk<Tenant>()
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { patientService } returns mockk {
                every {
                    findPatient(
                        tenant = tenant,
                        birthDate = "1984-08-31",
                        familyName = "Smith",
                        givenName = "Josh"
                    )
                } returns response
            }
        }

        every {
            queueService.enqueueMessages(
                listOf(
                    Message(
                        id = null,
                        messageType = MessageType.API,
                        resourceType = ResourceType.PATIENT,
                        tenant = "tenantId",
                        text = "raw JSON for patient"
                    )
                )
            )
        } just Runs

        // Run Test
        val actualResponse = patientHandler.patientsByNameAndDOB(
            tenantId = "tenantId",
            birthdate = "1984-08-31",
            given = "Josh",
            family = "Smith"
        )

        // Assert outcome
        assertNotNull(actualResponse)

        // Patient
        assertEquals(1, actualResponse.data.size)
        val actualPatient = actualResponse.data[0]
        assertEquals("Patient-UUID-1", actualPatient.id)
        assertEquals("male", actualPatient.gender)
        assertEquals("1984-08-31", actualPatient.birthDate) // so old

        // Name
        assertEquals(0, actualPatient.name.size)

        // Identifier
        assertEquals(0, actualPatient.identifier.size)

        // Contact
        assertEquals(0, actualPatient.telecom.size)

        // Address
        assertEquals(0, actualPatient.address.size)
    }

    @Test
    fun `ensure patient with null values is correctly returned`() {
        // All nullable values are set to null
        val response = mockk<Bundle<Patient>> {
            every { resources } returns listOf(
                mockk {
                    every { id } returns "Patient-UUID-1"
                    every { identifier } returns listOf(
                        mockk {
                            every { system } returns "http://hl7.org/fhir/sid/us-ssn"
                            every { value } returns "987-65-4321"
                        }
                    )
                    every { name } returns listOf(
                        mockk {
                            every { use } returns null
                            every { family } returns null
                            every { given } returns listOf("Josh")
                        }
                    )
                    every { birthDate } returns null
                    every { gender } returns null
                    every { telecom } returns listOf(
                        mockk {
                            every { system } returns null
                            every { use } returns null
                            every { value } returns null
                        }
                    )
                    every { address } returns listOf(
                        mockk {
                            every { use } returns null
                            every { line } returns listOf("1234 Main St")
                            every { city } returns null
                            every { state } returns null
                            every { postalCode } returns null
                        }
                    )
                    every { raw } returns "raw JSON for patient"
                }
            )
        }

        val tenant = mockk<Tenant>()
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { patientService } returns mockk {
                every {
                    findPatient(
                        tenant = tenant,
                        birthDate = "1984-08-31",
                        familyName = "Smith",
                        givenName = "Josh"
                    )
                } returns response
            }
        }

        every {
            queueService.enqueueMessages(
                listOf(
                    Message(
                        id = null,
                        messageType = MessageType.API,
                        resourceType = ResourceType.PATIENT,
                        tenant = "tenantId",
                        text = "raw JSON for patient"
                    )
                )
            )
        } just Runs

        // Run Test
        val actualResponse = patientHandler.patientsByNameAndDOB(
            tenantId = "tenantId",
            birthdate = "1984-08-31",
            given = "Josh",
            family = "Smith"
        )

        // Assert outcome
        assertNotNull(actualResponse)

        // Patient
        assertEquals(1, actualResponse.data.size)
        val actualPatient = actualResponse.data[0]
        assertEquals("Patient-UUID-1", actualPatient.id)
        assertNull(actualPatient.gender)
        assertNull(actualPatient.birthDate)

        // Name
        assertEquals(1, actualPatient.name.size)
        val actualName = actualPatient.name[0]
        assertNull(actualName.use)
        assertNull(actualName.family)
        assertEquals(listOf("Josh"), actualName.given)

        // Contact
        assertEquals(1, actualPatient.telecom.size)
        val actualContact = actualPatient.telecom[0]
        assertNull(actualContact.system)
        assertNull(actualContact.use)
        assertNull(actualContact.value)

        // Address
        assertEquals(1, actualPatient.address.size)
        val actualAddress = actualPatient.address[0]
        assertNull(actualAddress.use)
        assertEquals(listOf("1234 Main St"), actualAddress.line)
        assertNull(actualAddress.city)
        assertNull(actualAddress.state)
        assertNull(actualAddress.postalCode)
    }

    @Test
    fun `ensure when ehr returns no patients no patients are returned`() {
        val response = mockk<Bundle<Patient>> {
            every { resources } returns listOf()
        }

        val tenant = mockk<Tenant>()
        every { tenantService.getTenantForMnemonic("tenantId") } returns tenant

        every { ehrFactory.getVendorFactory(tenant) } returns mockk {
            every { patientService } returns mockk {
                every {
                    findPatient(
                        tenant = tenant,
                        birthDate = "1984-08-31",
                        familyName = "Smith",
                        givenName = "Josh"
                    )
                } returns response
            }
        }

        every { queueService.enqueueMessages(listOf()) } just Runs

        // Run Test
        val actualResponse = patientHandler.patientsByNameAndDOB(
            tenantId = "tenantId",
            birthdate = "1984-08-31",
            given = "Josh",
            family = "Smith"
        )

        // Assert outcome
        assertNotNull(actualResponse)

        // Patient
        assertEquals(0, actualResponse.data.size)
    }
}
